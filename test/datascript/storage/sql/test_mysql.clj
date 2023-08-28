(ns datascript.storage.sql.test-mysql
  (:require
    [clojure.test :as t :refer [deftest is are testing]]
    [datascript.core :as d]
    [datascript.storage.sql.core :as storage-sql]
    [datascript.storage.sql.test-core :as test-core])
  (:import
    [java.nio.file Files Path]
    [java.sql Connection DriverManager]))

(deftest test-mysql []
  (let [connect-fn #(DriverManager/getConnection
                      "jdbc:mysql://localhost:3306/test_datascript?allowPublicKeyRetrieval=true&useSSL=false"
                      "testuser"
                      "testpasswd")]
    (test-core/test-storage
      {:dbtype     :mysql
       :reset-fn   #(with-open [conn ^Connection (connect-fn)]
                      (storage-sql/execute! conn "drop table if exists datascript"))
       :connect-fn connect-fn})))

(comment
  (t/run-test-var #'test-mysql)
  (t/run-tests *ns*))
