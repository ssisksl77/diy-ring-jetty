(ns yhnam.diy-ring-jetty
  (:gen-class)
  (:require [yhnam.jetty-wrapper :as jw]))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "jetty is running...")
  (jw/run-jetty (-> jw/hello-world-json
                    jw/wrap-json-response)
                {}))
