(defproject datascript/storage-sql "0.0.0"
  :description "SQL Storage implementation for DataScript"
  :license     {:name "MIT" :url "https://github.com/tonsky/datascript-storage-sql/blob/master/LICENSE"}
  :url         "https://github.com/tonsky/datascript-storage-sql"
  :dependencies
  [[org.clojure/clojure "1.11.1"]
   [datascript/datascript "1.5.2"]]
  :deploy-repositories
  {"clojars"
   {:url "https://clojars.org/repo"
    :username "tonsky"
    :password :env/clojars_token
    :sign-releases false}})
