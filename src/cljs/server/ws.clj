(ns cljs.server.ws
  (:require
   [cljs.repl :as repl]
   [cljs.env :as env]
   [cljs.repl.ws :as ws]
   [cljs.core.server :as server]
   [cljs.analyzer :as ana]))

(defonce envs (atom {}))

(defn env-opts->key [{:keys [host port]}]
  [host port])

(defn stale? [{:keys [server-state]}]
  (not (:server @server-state)))

(defn get-envs [env-opts]
  (let [env-opts (merge {:host "localhost" :port 9001} env-opts)
        k (env-opts->key env-opts)]
    (swap! envs
           #(cond-> %
              (or (not (contains? % k))
                  (stale? (get-in % [k 0])))
              (assoc k
                     [(ws/repl-env* env-opts)
                      (env/default-compiler-env)])))
    (get @envs k)))

(defn repl-prompt []
  (print
   (str
    ana/*cljs-ns*
    (when-let [client (ws/current-client)]
      (when (< 1 client)
        (str "/" client)))
    "=> ")))

(defn ws-repl-read
  ([request-prompt request-exit]
   (ws-repl-read request-prompt request-exit repl/*repl-opts*))
  ([request-prompt request-exit opts]
   (let [res (repl/repl-read request-prompt request-exit opts)]
     (if (and (keyword? res) (= (namespace res) "repl.ws"))
       (let [rn (name res)]
         (if (.startsWith rn "=>")
           (let [n (read-string (apply str (drop 2 rn)))]
             (println "switching to client:" n)
             (ws/set-client! n)
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
                              :read ws-repl-read
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