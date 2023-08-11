(ns datascript.storage.sql.core
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [datascript.core :as d]
    [datascript.storage :as storage])
  (:import
    [java.sql Connection DriverManager ResultSet SQLException Statement]))

(defmacro with-tx [conn & body]
  `(let [conn# ~conn]
     (try
       (.setAutoCommit conn# false)
       ~@body
       (.commit conn#)
       (catch Exception e#
         (.rollback conn#)
         (throw e#))
       (finally
         (.setAutoCommit conn# true)))))

(defn init-db-impl [^Connection conn opts]
  (with-open [stmt (.createStatement conn)]
    (.execute stmt (:ddl opts))))

(defn store-impl [^Connection conn opts addr+data-seq]
  (let [{:keys [table binary? freeze-str freeze-bytes batch-size]} opts
        sql (str
              "insert into " table " (addr, content) "
              "values (?, ?) "
              "on conflict(addr) do update set content = ?")]
    (with-tx conn
      (with-open [stmt (.prepareStatement conn sql)]
        (doseq [part (partition-all batch-size addr+data-seq)]
          (doseq [[addr data] part]
            (.setLong stmt 1 addr)
            (if binary?
              (let [content ^bytes (freeze-bytes data)]
                (.setBytes stmt 2 content)
                (.setBytes stmt 3 content))
              (let [content ^String (freeze-str data)]
                (.setString stmt 2 content)
                (.setString stmt 3 content)))
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

(defn merge-opts [opts]
  (let [opts (merge
               {:freeze-str pr-str
                :thaw-str   edn/read-string
                :batch-size 1000
                :table      "datascript"}
               opts)
        opts (assoc opts
               :binary? (boolean (and (:freeze-bytes opts) (:thaw-bytes opts))))
        type (:dbtype opts)
        ddl  (case type
               :sqlite
               (str
                 "create table if not exists " (:table opts)
                 " (addr INTEGER primary key, "
                 "  content " (if (:binary? opts) "BLOB" "TEXT") ")")
               (throw (IllegalArgumentException. (str "Unsupported :dbtype " (pr-str type)))))
        opts (merge {:ddl ddl} opts)]
    opts))

(defn make 
  ([conn]
   (make conn {}))
  ([conn opts]
   (let [opts (merge-opts opts)]
     (init-db-impl conn opts)
     (with-meta
       {:conn conn}
       {'datascript.storage/-store
        (fn [_ addr+data-seq]
          (store-impl conn opts addr+data-seq))
        
        'datascript.storage/-restore
        (fn [_ addr]
          (restore-impl conn opts addr))
        
        'datascript.storage/-list-addresses
        (fn [_]
          (list-impl conn opts))
        
        'datascript.storage/-delete
        (fn [_ addr-seq]
          (delete-impl conn opts addr-seq))}))))

(defn close [storage]
  (.close ^Connection (:conn storage)))
