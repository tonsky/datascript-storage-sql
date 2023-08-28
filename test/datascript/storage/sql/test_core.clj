(ns datascript.storage.sql.test-core
  (:require
    [clojure.edn :as edn]
    [clojure.test :as t :refer [deftest is are testing]]
    [datascript.core :as d]
    [datascript.storage.sql.core :as storage-sql]))

(defn test-storage [{:keys [dbtype connect-fn reset-fn]}]
  (let [schema {:id {:db/unique :db.unique/identity}}
        tx     [{:db/id 1
                 :id    "1"
                 :name  "Ivan"
                 :age   38}]]
    (reset-fn)
    (testing "Read back same conn"
      (with-open [conn (connect-fn)]
        (let [db      (d/db-with (d/empty-db schema) tx)
              storage (storage-sql/make conn
                        {:dbtype dbtype})
              _       (d/store db storage)
              db'     (d/restore storage)]
          (is (= db db')))))
    
    (testing "Read back new conn"
      (with-open [conn (connect-fn)]
        (let [db      (d/db-with (d/empty-db schema) tx)
              storage (storage-sql/make conn
                        {:dbtype dbtype})
              db'     (d/restore storage)]
          (is (= db db')))))
    
    (reset-fn)
    (testing "Rountrip binary"
      (with-open [conn (connect-fn)]
        (let [db      (d/db-with (d/empty-db schema) tx)
              storage (storage-sql/make conn
                        {:dbtype       dbtype
                         :freeze-bytes #(.getBytes (pr-str %) "UTF-8")
                         :thaw-bytes   #(edn/read-string (String. ^bytes % "UTF-8"))})
              _       (d/store db storage)
              db'     (d/restore storage)]
          (is (= db db')))))))
