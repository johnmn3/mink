(ns ex.core
  (:require
   [mink.browser :as otr]
   [ex.stuff :as stuff]))

(enable-console-print!)

(when-not (otr/alive?)
  (otr/connect (str "ws://" (or otr/host-name "localhost") ":" (or otr/ws-port 9001))))

(when (otr/alive?)
  (println "Loaded example"))

(stuff/do-stuff)

