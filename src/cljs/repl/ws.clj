(ns cljs.repl.ws
  (:require
   [clojure.edn :as edn]
   [clojure.string :as string]
   [cljs.repl :as repl]
   [cljs.compiler :as cmp]
   [cljs.stacktrace :as st]
   [cljs.util :as util]
   [cljs.build.api :as b])
  (:import
   [java.nio ByteBuffer]
   [org.java_websocket.server WebSocketServer]))

;; state
(defonce state
  (atom {:server nil
         :clients nil
         :get-next-client-id nil
         :started nil
         :repl->out nil
         :repl->env nil
         :repl->client nil
         :current-client-id 1}))

(def response (atom nil))

;; utils
(defn repl-id []
  (if-let [kv (first (filter #(= *out* (val %)) (:repl->out @state)))]
    (key kv)
    (let [repl (.getName (Thread/currentThread))]
      (swap! state assoc-in [:repl->out repl] *out*)
      repl)))

(defn valid-client? [client-id]
  (-> @state :clients (contains? client-id)))

(defn wait-for-client [] @(:started @state))


(defn set-client! [client-id]
  (when (valid-client? client-id)
    (let [repl (repl-id)]
      (swap! state assoc-in [:repl->client repl] client-id)
      (swap! state assoc :current-client-id client-id))))

(defn current-client []
  (let [client-id (:current-client-id @state)]
    (if (valid-client? client-id)
      client-id
      (let [first-id (some-> @state :clients first key (or 1))]
        (set-client! first-id)
        first-id))))

(defn send! [msg]
  (when-let [ws (-> @state :clients (get (current-client)))]
    (.send ws (pr-str msg))))

(defn send-for-eval! [js]
  (send! {:op :eval-js :code js :repl (repl-id)}))

;; impl
(defn ws-server-impl [host port open error close str-msg bb-msg start]
  (proxy [WebSocketServer] [(java.net.InetSocketAddress. host port)]
    (onOpen [client client-handshake]
      (open {:client client :client-handshake client-handshake}))
    (onClose [client code reason remote]
      (close {:client client :code code :reason reason :remote remote})
      (.close client))
    (onMessage [client msg]
      (condp instance? msg
        String (str-msg {:client client :msg msg})
        ByteBuffer (bb-msg {:client client :msg msg})))
    (onError [client ex]
      (error {:client client :ex ex}))
    (onStart []
      (when start
        (start)))))

(defn on-open [{:keys [client client-handshake]}]
  (let [requested-id (-> client-handshake
                         (.getResourceDescriptor)
                         (string/replace #"^/?" "")
                         (not-empty)
                         (some-> Integer/parseInt))
        old-client? (and requested-id
                         (not (get-in @state [:clients requested-id])))
        client-id (if old-client?
                    requested-id
                    ((-> @state :get-next-client-id)))]
    (when-not old-client?
      (.send client (pr-str {:op :client-id :id client-id})))
    (doseq [out (-> @state :repl->out vals)]
      (binding [*out* (or out *out*)]
        (print (str "\nrepl client: :repl.ws/=>" client-id "\n"
                    #_#_#_#_ana/*cljs-ns* "/" (current-client) "=> "))
        (.flush *out*)))
    (-> (swap! state assoc-in [:clients client-id] client)
        :started (deliver true))))

(defn on-close [{:keys [client]}]
  (let [clients (:clients @state)
        client-id (first (filter (comp #{client} clients) (keys clients)))]
    (when (= client-id (current-client))
      (swap! state update :clients dissoc client-id)
      true)))

(defn server [host port & args]
  (let [{:keys [open error close str-msg bb-msg start]
         :or {open on-open
              close on-close
              str-msg (fn [{:keys [msg]}] (println "from client:" msg))
              bb-msg (fn [{:keys [msg]}] (println "from client:" msg))
              error (fn [{:keys [client ex]}] (println client "sent error:" ex))}}
        (apply hash-map args)
        ws (ws-server-impl host port open error close str-msg bb-msg start)]
    (future (.run ws))
    ws))

(def watch-opts
  {:watch "src"
   :output-dir "out"
   :asset-path "/out"
   :optimizations :none
   :browser-repl true
   :verbose true
   :watch-fn #(do (println :built!)
                  (send-for-eval! "(location.reload();)();"))})

;; start/stop

(defn stop []
  (let [stop-server (:server @state)]
    (when-not (nil? stop-server)
      (.stop stop-server 1000)
      (reset! state {:server nil
                     :get-next-client-id nil
                     :clients nil
                     :started nil
                     :repl->out nil
                     :repl->env nil
                     :repl->client nil})
      @state)))

(defn port-available? [port]
  (try
    (with-open [_socket (java.net.ServerSocket. port)]
      true)
    (catch java.net.BindException _
      false)))

(defn start
  [f & {:keys [host port]}]
  {:pre [(ifn? f)]}
  (when-not (port-available? port)
    (println "Port" port "in use, forcing cleanup...")
    (stop))
  (swap! state assoc
         :server (server host port :str-msg f)
         :clients {}
         :get-next-client-id (let [a (atom 0)] #(swap! a inc))
         :started (promise)))

(.addShutdownHook
 (Runtime/getRuntime)
 (Thread. ^Runnable #(do
                       (shutdown-agents)
                       (Thread/sleep 500)
                       (stop))))

;; msg handling
(defmulti ws-msg
  "Process msgs from client"
  {:arglists '([_ msg])}
  (fn [_ msg] (:op msg)))

(defmethod ws-msg
  :result
  [_ {:keys [value]}]
  (when-not (nil? @response)
    (deliver @response value)))

(defmethod ws-msg
  :print
  [_ msg]
  (let [{:keys [value repl]} (:value msg)]
    (when (= repl (repl-id))
      (binding [*out* (or (-> @state :repl->out (get repl)) *out*)]
        (print (read-string value))))))

(defmethod ws-msg
  :ready
  [_ _msg]
  (when-not (nil? @response)
    (deliver @response {:status :success, :repl (repl-id), :value :ready})))

(defmethod ws-msg
  :success
  [_ _msg]
  (when-not (nil? @response)
    (deliver @response :success)))

(defn list-clients []
  (let [clients (:clients @state)
        current (current-client)]
    (->> clients
         (map (fn [[id _]]
                (str (when (= id current) "*")
                     ":repl.ws=>" id)))
         (string/join "\n"))))

(defmethod ws-msg :list-clients
  [_ _]
  {:status :success
   :value (list-clients)})

;; IJavaScriptEnv implementation
(defn websocket-setup-env
  [this opts]
  (when-not (-> this :server-state deref :server)
    (start (fn [data] (ws-msg this (read-string (:msg data))))
           :host (:host this)
           :port (:port this))
    (update-in this [:server-state] #(swap! % assoc :server (:server @state)))
    (let [{:keys [host pre-connect]} this]
      (println (str "Waiting for connection at "
                    "ws://" host ":" (:port this) " ..."))
      (when pre-connect (pre-connect))
      (wait-for-client)))
  (swap! state assoc-in [:repl->out (repl-id)] *out*)
  (swap! state assoc-in [:repl->env (repl-id)] {:env this :opts opts})
  (swap! (:server-state this) update :listeners inc)
  (set-client! (-> @state :clients ffirst))
  nil)

(defn websocket-eval
  [js]
  (cond
    (re-find #"\"repl\.ws/=>(\d+)\"" js)
    (let [client-id (->> (re-find #"\"repl\.ws/=>(\d+)\"" js)
                         second
                         Integer/parseInt)]
      (set-client! client-id)
      {:status :success :value (keyword (str "repl.ws/" client-id "=> "))})

    ;; List clients command
    (re-find #"\"repl\.ws/list\"" js)
    {:status :success :value (list-clients)}

    ;; Default case - evaluate JavaScript
    :else
    (do
      (reset! response (promise))
      (send-for-eval! js)
      (let [ret @@response]
        (reset! response nil)
        ret))))

(defn load-javascript
  [_ provides _]
  (websocket-eval
   (str "goog.require('" (cmp/munge (first provides)) "')")))

(defn websocket-tear-down-env
  [ws-env]
  (let [server-state (:server-state ws-env)]
    (when (zero? (:listeners (swap! server-state update :listeners dec)))
      (swap! state update :repl->out dissoc (repl-id))
      (stop)
      (println "<< stopped server >>"))))

(defrecord WebsocketEnv []
  cljs.repl/IJavaScriptEnv
  (-setup [this opts] (websocket-setup-env this opts))
  (-evaluate [_ _ _ js] (websocket-eval js))
  (-load [this ns url] (load-javascript this ns url))
  (-tear-down [ws-env] (websocket-tear-down-env ws-env))
  repl/IReplEnvOptions
  (-repl-options [_this]
    {:browser-repl true
     :repl-requires
     '[[clojure.browser.ws] [clojure.browser.repl] [clojure.browser.repl.preload]]
     :cljs.cli/commands
     {:groups {::repl {:desc "websocket REPL options"}}
      :init
      {["-H" "--host"]
       {:group ::repl :fn #(assoc-in %1 [:repl-env-options :host] %2)
        :arg "address"
        :doc "Address to bind"}
       ["-p" "--port"]
       {:group ::repl :fn #(assoc-in %1 [:repl-env-options :port] (Integer/parseInt %2))
        :arg "number"
        :doc "Port to bind"}}}})
  repl/IParseStacktrace
  (-parse-stacktrace [this st err opts]
    (st/parse-stacktrace this st err opts))
  repl/IGetError
  (-get-error [this e env _opts]
    (edn/read-string
     (repl/evaluate-form this env "<cljs ws repl>"
                         `(when ~e
                            (pr-str
                             {:ua-product (clojure.browser.repl/get-ua-product)
                              :value (str ~e)
                              :stacktrace (.-stack ~e)}))))))

(defn repl-env*
  [{:keys [output-dir host port] :or {host "localhost" port 9001} :as opts}]
  (merge (WebsocketEnv.)
         {:host host
          :port port
          :launch-browser false
          :working-dir (->> [".repl" (util/clojurescript-version)]
                            (remove empty?) (string/join "-"))
          :static-dir (cond-> ["." "out/"] output-dir (conj output-dir))
          :preloaded-libs []
          :src "src/"
          :server-state
          (atom
           {:server nil
            :listeners 0})}
         opts
         watch-opts))

(defn repl-env
  "Create a websocket-connected REPL environment.

  Options:

  port:             The port on which the REPL server will run. Defaults to 9000.
  launch-websocket: A Boolean indicating whether a browser should be automatically
                    launched connecting back to the terminal REPL. Defaults to true.
  working-dir:      The directory where the compiled REPL client JavaScript will
                    be stored. Defaults to \".repl\" with a ClojureScript version
                    suffix, eg. \".repl-0.0-2138\".
  static-dir:       List of directories to search for static content. Defaults to
                    [\".\" \"out/\"].
  src:              The source directory containing user-defined cljs files. Used to
                    support reflection. Defaults to \"src/\".
  "
  [& {:as opts}]
  (repl-env* opts))

(def wopts
  {:watch "src"
   :watch-fn (fn [& args]
               (println :watch-fn :args args)
               (websocket-eval "(function () {location.reload();})();"))
   :output-dir "out"
   :asset-path "/out"
   :optimizations :none})

(defn launch-build [dir]
  (b/build (or dir "src") wopts)
  (repl/repl* (repl-env) wopts))

(defn watch-dir []
  (some->> *command-line-args*
           (partition-all 2 1)
           (filter #(let [flag (first %)]
                      (or (= "--watch" flag)
                          (= "-w" flag))))
           first
           second))

(when-let [wdir (watch-dir)]
  (launch-build wdir))
