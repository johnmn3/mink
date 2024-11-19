(ns ex.core
  (:require
   [clojure.browser.ws :as ws]
   [ex.stuff :as stuff]))

(enable-console-print!)

(when-not (ws/alive?)
  (ws/connect "ws://localhost:9001"))

(when (ws/alive?)
  (println "Loaded example"))

(stuff/do-stuff)
