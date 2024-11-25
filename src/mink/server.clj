;; Copyright (c) Rich Hickey. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

;; This library combines weasel-like websocket functionality with some of
;; clojurescript's existing functionality. Some of the code in this namespace
;; will likely resemble or match code in various namespaces in clojurescript.
;; Therefore, it is licensed under the same license, as shown above.

(ns mink.server
  (:require
   [cljs.analyzer :as ana]
   [cljs.build.api :as build]
   [cljs.closure :as cljsc]
   [cljs.core.server :as server]
   [cljs.env :as env]
   [cljs.repl :as repl]
   [mink.repl :as otr]))

(defonce envs (atom {}))

(defn env-opts->key [{:keys [host port]}]
  [host port])

(defn stale? [{:keys [server-state]}]
  (not (:server @server-state)))

(defn get-envs [env-opts]
  (let [env-opts (merge {:host "localhost" :port 9000 :ws-port 9001} env-opts)
        k (env-opts->key env-opts)]
    (swap! envs
           #(cond-> %
              (or (not (contains? % k))
                  (stale? (get-in % [k 0])))
              (assoc k
                     [(otr/repl-env* env-opts)
                      (env/default-compiler-env)])))
    (get @envs k)))

(defn refresh-ns []
  (when (not= ana/*cljs-ns* (otr/current-ns))
    (swap! otr/state assoc-in [:client->ns (otr/current-client)] (or ana/*cljs-ns* 'cljs.user))
    nil))    

(defn repl-prompt []
  (let [current-client-id (otr/current-client)
        current-repl-id (otr/repl-id)
        dyn-ns ana/*cljs-ns*]
    (refresh-ns)
    (print (str "#_" dyn-ns ":" current-repl-id "/" current-client-id "=> "))))

(defmulti repl-cmd (fn [first-two & _args] first-two))

(defmethod repl-cmd "=>"
  [ft rn res & _args]
  (if (= ft "=>")
    (let [n (read-string (apply str (drop 2 rn)))
          dest-ns (or (get-in @otr/state [:client->ns n]) 'cljs.user)]
      (set! ana/*cljs-ns* dest-ns)
      (otr/set-client! n)
      nil)
    res))

(defmethod repl-cmd "ls"
  [_ft _rn _res & _args]
  (println (otr/list-clients))
  nil)

(defn repl-read
  ([request-prompt request-exit]
   (repl-read request-prompt request-exit repl/*repl-opts*))
  ([request-prompt request-exit opts]
   (let [res (repl/repl-read request-prompt request-exit opts)]
     (if (and (keyword? res) (= (namespace res) "mink.repl"))
       (let [rn (name res)
             first-two (apply str (take 2 rn))]
         (repl-cmd first-two rn res))
       res))))

(def wopts
  {:watch-fn (fn [& _args]
               (otr/websocket-eval "(function () {location.reload();})();"))
   :watch "src"
   :output-dir "out"
   :asset-path "/out"
   :prompt
   repl-prompt})

(defn repl
  ([]
   (repl nil))
  ([{:as ctx :keys [opts env-opts]}]
   (let [[env cenv] (get-envs env-opts)
         known (select-keys env-opts cljsc/known-opts)]
     (env/with-compiler-env cenv
       (build/build (str (:watch opts "src")) (merge wopts known))
       (repl/repl* env
                   (merge {:prompt repl-prompt
                           :read repl-read
                           :output-dir "out"
                           :asset-path "/out"
                           :watch "src"}
                          opts
                          wopts))))))
#_(repl)


(defn prepl
  ([]
   (prepl nil))
  ([{:keys [opts env-opts]}]
   (let [[env cenv] (get-envs env-opts)]
     (env/with-compiler-env cenv
       (apply server/io-prepl
              (mapcat identity
                      {:repl-env env :opts opts}))))))

;; clj -J-Dclojure.server.repl="{:port 5555 :accept mink.server/repl}"
