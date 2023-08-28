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

(comment
  (t/run-test-var #'test-postgresql)
  (t/run-tests *ns*))
