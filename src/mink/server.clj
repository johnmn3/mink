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
   [cljs.core.server :as server]
   [cljs.env :as env]
   [cljs.repl :as repl]
   [mink.main :as otr]))

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

(defn repl-prompt []
  (print
   (str
    ana/*cljs-ns*
    (when-let [client (otr/current-client)]
      (when (< 1 client)
        (str "/" client)))
    "=> ")))

(defn repl-read
  ([request-prompt request-exit]
   (repl-read request-prompt request-exit repl/*repl-opts*))
  ([request-prompt request-exit opts]
   (let [res (repl/repl-read request-prompt request-exit opts)]
     (if (and (keyword? res) (= (namespace res) "mink.repl"))
       (let [rn (name res)]
         (if (.startsWith rn "=>")
           (let [n (read-string (apply str (drop 2 rn)))]
             (println "switching to client:" n)
             (otr/set-client! n)
             nil)
           res))
       res))))

(defn repl
  ([]
   (repl nil))
  ([{:keys [opts env-opts]}]
   (let [[env cenv] (get-envs env-opts)]
     (env/with-compiler-env cenv
       (repl/repl* env (assoc opts
                              :prompt repl-prompt
                              :read repl-read
                              :watch "src"))))))

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
