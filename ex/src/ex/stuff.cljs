(ns ex.stuff
  (:require
   [clojure.string :as str]))

(defn do-stuff []
  (println :stuff (str/join " " ["1" "2" 3 4 6])))

;; Fix: doesn't print sometimes on reload
(println :from :stuff 1)
