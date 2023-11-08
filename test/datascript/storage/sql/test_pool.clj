(ns datascript.storage.sql.test-pool
  (:require
    [clojure.test :as t :refer [deftest is are testing]]
    [datascript.core :as d]
    [datascript.storage.sql.core :as storage-sql]
    [datascript.storage.sql.test-core :as test-core])
  (:import
    [java.nio.file Files Path]
    [javax.sql DataSource]
    [java.util.concurrent Executors Future]
    [org.sqlite SQLiteDataSource]))

(deftest test-pool []
  (Files/deleteIfExists (Path/of "target/db.sqlite" (make-array String 0)))
  (with-open [datasource  (storage-sql/pool
                            (doto (SQLiteDataSource.)
                              (.setUrl "jdbc:sqlite:target/db.sqlite"))
                            {:max-conn      10
                             :max-idle-conn 4})
              thread-pool (Executors/newFixedThreadPool 100)]
    (let [*stats      (atom {:min-idle  Long/MAX_VALUE
                             :max-idle  0
                             :min-taken Long/MAX_VALUE
                             :max-taken 0})
          _           (add-watch (:*atom datasource) ::stats
                        (fn [_ _ _ new]
                          (swap! *stats
                            #(-> %
                               (update :min-idle min (count (:idle new)))
                               (update :max-idle max (count (:idle new)))
                               (update :min-taken min (count (:taken new)))
                               (update :max-taken max (count (:taken new)))))))
          _           (with-open [conn (.getConnection datasource)]
                        (with-open [stmt (.createStatement conn)]
                          (.execute stmt "create table T (id INTEGER primary key)"))
                        (with-open [stmt (.prepareStatement conn "insert into T (id) values (?)")]
                          (dotimes [i 1000]
                            (.setLong stmt 1 i)
                            (.addBatch stmt))
                          (.executeBatch stmt)))
          select      (fn [i]
                        (with-open [conn (.getConnection datasource)
                                    stmt (doto (.prepareStatement conn "select id from T where id = ?")
                                           (.setLong 1 i))
                                    rs   (.executeQuery stmt)]
                          (.next rs)
                          (.getLong rs 1)))
          tasks       (mapv #(fn [] (select %)) (range 1000))
          futures     (.invokeAll thread-pool tasks)]
      (is (= (range 1000) (map #(.get ^Future %) futures)))
      (is (= 4 (count (:idle @(:*atom datasource)))))
      (is (= 0 (count (:taken @(:*atom datasource)))))
      (is (= 0 (:min-idle @*stats)))
      (is (= 4 (:max-idle @*stats)))
      (is (= 0 (:min-taken @*stats)))
      (is (= 10 (:max-taken @*stats))))))

(comment
  (t/run-test-var #'test-sqlite)
  (t/run-tests *ns*))
