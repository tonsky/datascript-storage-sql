(ns datascript.storage.sql.test-postgresql
  (:require
    [clojure.test :as t :refer [deftest is are testing]]
    [datascript.core :as d]
    [datascript.storage.sql.core :as storage-sql]
    [datascript.storage.sql.test-core :as test-core])
  (:import
    [java.nio.file Files Path]
    [java.sql DriverManager]))

(deftest test-postgresql []
  (test-core/test-storage
    {:dbtype   :postgresql
     :reset-fn
     #(with-open [conn (DriverManager/getConnection "jdbc:postgresql:test_datascript")]
        (storage-sql/execute! conn "drop table if exists datascript"))
     :connect-fn
     #(DriverManager/getConnection "jdbc:postgresql:test_datascript")}))

; (t/run-test-var #'test-sqlite)

; (t/run-tests *ns*)

(comment
  (def conn
    (DriverManager/getConnection "jdbc:postgresql:tonsky"))
  
  (str
    "create table if not exists " (:table opts)
    " (addr BIGINT primary key, "
    "  content " (if (:binary? opts) "BYTEA" "TEXT") ")")
  
  (with-open [conn (DriverManager/getConnection "jdbc:postgresql:test_datascript")]
    (storage-sql/make conn {:dbtype :postgresql}))
  
  (def ^bytes bts (.getBytes "Hello" "UTF-8"))
  
  (with-open [conn (DriverManager/getConnection "jdbc:postgresql:test_datascript")]
    (with-open [stmt (.prepareStatement conn "insert into datascript (addr, content) values (?, ?)")]
      (.setLong stmt 1 2)
      (.setBytes stmt 2 bts)
      (.execute stmt)))
    
  (with-open [conn (DriverManager/getConnection "jdbc:postgresql:test_datascript")]
    (with-open [stmt (.prepareStatement conn "select content from datascript where addr = ?")]
      (.setLong stmt 1 2)
      (with-open [rs (.executeQuery stmt)]
        (when (.next rs)
          (let [bs ^bytes (.getBytes rs 1)]
            [(java.util.Arrays/toString bs) (String. bs "UTF-8")])))))
  )