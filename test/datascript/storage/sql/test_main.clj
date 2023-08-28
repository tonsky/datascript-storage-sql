(ns datascript.storage.sql.test-main
  (:require
    [clojure.test :as t]
    [datascript.storage.sql.test-core]
    [datascript.storage.sql.test-h2]
    [datascript.storage.sql.test-mysql]
    [datascript.storage.sql.test-postgresql]
    [datascript.storage.sql.test-sqlite]))

(defn -main [& args]
  (t/run-all-tests #"datascript\.storage\.sql\..*"))

(comment
  (-main))
