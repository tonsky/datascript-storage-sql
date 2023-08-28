(ns user
  (:require
    [clojure.core.server :as server]
    [clojure.tools.namespace.repl :as ns]))

(ns/set-refresh-dirs "src" "test")

(defn reload []
  (set! *warn-on-reflection* true)
  (let [res (ns/refresh)]
    (if (instance? Throwable res)
      (do
        (.printStackTrace ^Throwable res)
        (throw res))
      res)))

(def lock
  (Object.))

(defn position []
  (let [trace (->> (Thread/currentThread)
                (.getStackTrace)
                (seq))
        el    ^StackTraceElement (nth trace 4)]
    (str "[" (clojure.lang.Compiler/demunge (.getClassName el)) " " (.getFileName el) ":" (.getLineNumber el) "]")))

(defn p [form]
  `(let [t# (System/currentTimeMillis)
         res# ~form]
     (locking lock
       (println (str "#p" (position) " " '~form " => (" (- (System/currentTimeMillis) t#) " ms) " res#)))
     res#))

(defn -main [& {:as args}]
  (let [port (parse-long (get args "--port" "5555"))]
    (server/start-server
      {:name          "repl"
       :port          port
       :accept        'clojure.core.server/repl
       :server-daemon false})
    (println "Started Socket REPL server on port" port)))
