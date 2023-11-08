(ns datascript.storage.sql.core
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [datascript.core :as d]
    [datascript.storage :as storage])
  (:import
    [java.sql Connection DriverManager ResultSet SQLException Statement]
    [javax.sql DataSource]
    [java.lang.reflect InvocationHandler Method Proxy]))

(defmacro with-conn [[conn datasource] & body]
  (let [conn (vary-meta conn assoc :tag Connection)]
    `(let [^DataSource datasource# ~datasource]
       (with-open [~conn (.getConnection datasource#)]
         (locking ~conn
           ~@body)))))

(defmacro with-tx [conn & body]
  `(let [conn# ~conn]
     (try
       (.setAutoCommit conn# false)
       ~@body
       (.commit conn#)
       (catch Exception e#
         (try
           (.rollback conn#)
           (catch Exception ee#
             (.addSuppressed e# ee#)))
         (throw e#))
       (finally
         (.setAutoCommit conn# true)))))

(defn execute! [^Connection conn sql]
  (with-open [stmt (.createStatement conn)]
    (.execute stmt sql)))

(defmulti upsert-dml :dbtype)

(defmethod upsert-dml :h2 [opts]
  (str "merge into " (:table opts) " key (addr) values (?, ?)"))

(defmethod upsert-dml :mysql [opts]
  (str
    "insert into " (:table opts) " (addr, content) "
    "values (?, ?) "
    "ON DUPLICATE KEY UPDATE content = ?"))

(defmethod upsert-dml :default [opts]
  (str
    "insert into " (:table opts) " (addr, content) "
    "values (?, ?) "
    "on conflict(addr) do update set content = ?"))

(defn store-impl [^Connection conn opts addr+data-seq]
  (let [{:keys [table binary? freeze-str freeze-bytes batch-size]} opts
        sql (upsert-dml opts)
        cnt (count (re-seq #"\?" sql))]
    (with-tx conn
      (with-open [stmt (.prepareStatement conn sql)]
        (doseq [part (partition-all batch-size addr+data-seq)]
          (doseq [[addr data] part]
            (.setLong stmt 1 addr)
            (if binary?
              (let [content ^bytes (freeze-bytes data)]
                (.setBytes stmt 2 content)
                (when (= 3 cnt)
                  (.setBytes stmt 3 content)))
              (let [content ^String (freeze-str data)]
                (.setString stmt 2 content)
                (when (= 3 cnt)
                  (.setString stmt 3 content))))
            (.addBatch stmt))
          (.executeBatch stmt))))))

(defn restore-impl [^Connection conn opts addr]
  (let [{:keys [table binary? thaw-str thaw-bytes]} opts
        sql (str "select content from " table " where addr = ?")]
    (with-open [stmt (.prepareStatement conn sql)]
      (.setLong stmt 1 addr)
      (with-open [rs (.executeQuery stmt)]
        (when (.next rs)
          (if binary?
            (thaw-bytes (.getBytes rs 1))
            (thaw-str (.getString rs 1))))))))

(defn list-impl [^Connection conn opts]
  (with-open [stmt (.prepareStatement conn (str "select addr from " (:table opts)))
              rs   (.executeQuery stmt)]
    (loop [res (transient [])]
      (if (.next rs)
        (recur (conj! res (.getLong rs 1)))
        (persistent! res)))))

(defn delete-impl [^Connection conn opts addr-seq]
  (with-tx conn
    (with-open [stmt (.prepareStatement conn (str "delete from " (:table opts) " where addr = ?"))]
      (doseq [part (partition-all (:batch-size opts) addr-seq)]
        (doseq [addr part]
          (.setLong stmt 1 addr)
          (.addBatch stmt))
        (.executeBatch stmt)))))

(defmulti ddl
  (fn [opts]
    (:dbtype opts)))

(defmethod ddl :sqlite [{:keys [table binary?]}]
  (str
    "create table if not exists " table
    " (addr INTEGER primary key, "
    "  content " (if binary? "BLOB" "TEXT") ")"))

(defmethod ddl :h2 [{:keys [table binary?]}]
  (str
    "create table if not exists " table
    " (addr BIGINT primary key, "
    "  content " (if binary? "BINARY VARYING" "CHARACTER VARYING") ")"))

(defmethod ddl :mysql [{:keys [table binary?]}]
  (str
    "create table if not exists " table
    " (addr BIGINT primary key, "
    "  content " (if binary? "LONGBLOB" "LONGTEXT") ")"))

(defmethod ddl :postgresql [{:keys [table binary?]}]
  (str
    "create table if not exists " table
    " (addr BIGINT primary key, "
    "  content " (if binary? "BYTEA" "TEXT") ")"))

(defmethod ddl :default [{:keys [dbtype]}]
  (throw (IllegalArgumentException. (str "Unsupported :dbtype " (pr-str dbtype)))))

(defn merge-opts [opts]
  (let [opts (merge
               {:freeze-str pr-str
                :thaw-str   edn/read-string
                :batch-size 1000
                :table      "datascript"}
               opts)
        opts (assoc opts
               :binary? (boolean (and (:freeze-bytes opts) (:thaw-bytes opts))))]
    (merge {:ddl (ddl opts)} opts)))

(defn make 
  ([datasource]
   {:pre [(instance? DataSource datasource)]}
   (make datasource {}))
  ([datasource opts]
   (let [opts (merge-opts opts)]
     (with-conn [conn datasource]
       (execute! conn (:ddl opts)))
     (with-meta
       {:datasource datasource}
       {'datascript.storage/-store
        (fn [_ addr+data-seq]
          (with-conn [conn datasource]
            (store-impl conn opts addr+data-seq)))
        
        'datascript.storage/-restore
        (fn [_ addr]
          (with-conn [conn datasource]
            (restore-impl conn opts addr)))
        
        'datascript.storage/-list-addresses
        (fn [_]
          (with-conn [conn datasource]
            (list-impl conn opts)))
        
        'datascript.storage/-delete
        (fn [_ addr-seq]
          (with-open [conn datasource]
            (delete-impl conn opts addr-seq)))}))))

(defn swap-return! [*atom f & args]
  (let [*res (volatile! nil)]
    (swap! *atom
      (fn [atom]
        (let [[res atom'] (apply f atom args)]
          (vreset! *res res)
          atom')))
    @*res))

(defrecord Pool [*atom ^DataSource datasource opts]
  java.lang.AutoCloseable
  (close [_]
    (let [[{:keys [taken free]} _] (swap-vals! *atom #(-> % (update :taken empty) (update :idle empty)))]
      (doseq [conn (concat free taken)]
        (try
          (.close conn)
          (catch Exception e
            (.printStackTrace e))))))
  
  DataSource
  (getConnection [_]
    (let [conn (swap-return! *atom
                 (fn [atom]
                   (if-some [conn (peek (:idle atom))]
                     [conn (-> atom
                             (update :taken conj conn)
                             (update :idle pop))]
                     [nil atom])))
          conn (or conn
                 (let [conn (.getConnection datasource)]
                   (swap! *atom update :taken conj conn)
                   conn))
          conn ^Connection conn
          *closed? (volatile! false)]
      (Proxy/newProxyInstance
        (.getClassLoader Connection)
        (into-array Class [Connection])
        (reify InvocationHandler
          (invoke [this proxy method args]
            (let [method ^Method method]
              (case (.getName method)
                "close"
                (do
                  (when-not (.getAutoCommit conn)
                    (.rollback conn)
                    (.setAutoCommit conn true))
                  (vreset! *closed? true)
                  (when-some [conn (swap-return! *atom
                                     (fn [atom]
                                       (if (>= (count (:idle atom)) (:max-conn opts))
                                         [conn (update atom :taken disj conn)]
                                         [nil  (-> atom
                                                 (update :taken disj conn)
                                                 (update :idle conj conn))])))]
                    (.close conn))
                  nil)
                
                "isClosed"
                (or @*closed? (.invoke method conn args))
                
                ;; else
                (.invoke method conn args)))))))))

(defn pool
  ([datasource]
   (pool datasource {}))
  ([datasource opts]
   (Pool.
     (atom {:taken #{}
            :idle  []})
     datasource
     (merge
       {:max-conn 4}
       opts))))
