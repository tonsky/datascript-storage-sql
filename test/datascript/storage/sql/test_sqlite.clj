(ns datascript.storage.sql.test-sqlite
  (:require
    [clojure.test :as t :refer [deftest is are testing]]
    [datascript.core :as d]
    [datascript.storage.sql.core :as storage-sql]
    [datascript.storage.sql.test-core :as test-core])
  (:import
    [java.nio.file Files Path]
    [java.sql DriverManager]))

(deftest test-sqlite []
  (test-core/test-storage
    {:dbtype     :sqlite
     :connect-fn #(DriverManager/getConnection "jdbc:sqlite:target/db.sqlite")
     :reset-fn   #(Files/deleteIfExists (Path/of "target/db.sqlite" (make-array String 0)))}))

; (t/run-test-var #'test-sqlite)

; (t/run-tests *ns*)