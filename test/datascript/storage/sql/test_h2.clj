(ns datascript.storage.sql.test-h2
  (:require
    [clojure.test :as t :refer [deftest is are testing]]
    [datascript.core :as d]
    [datascript.storage.sql.core :as storage-sql]
    [datascript.storage.sql.test-core :as test-core])
  (:import
    [java.nio.file Files Path]
    [java.sql DriverManager]))

(deftest test-h2 []
  (test-core/test-storage
    {:dbtype     :h2
     :connect-fn #(DriverManager/getConnection "jdbc:h2:./target/db.h2")
     :reset-fn   #(Files/deleteIfExists (Path/of "target/db.h2" (make-array String 0)))}))

(comment
  (t/run-test-var #'test-h2)
  (t/run-tests *ns*))