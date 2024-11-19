(ns clojure.browser.ws
  (:require
   [clojure.browser.event :as event]
   [clojure.browser.net :as net]
   [clojure.browser.repl :as brepl]
   [cljs.reader :as reader :refer [read-string]]))

(enable-console-print!)

(def ws-conn (atom nil))
(def last-repl (atom nil))

(defn repl-print
  "Send data to be printed in the REPL"
  [& args]
  (when-let [conn @ws-conn]
   (net/transmit conn {:op :print
                       :value {:status :success
                               :repl @last-repl
                               :value (apply pr-str args)}})))

(defn console-print
  "Print data to the javascript console"
  [& args]
  (.apply (.-log js/console) js/console (into-array args)))

(def print-fns
  {:repl repl-print
   :console console-print
   #{:repl :console} (fn [& args]
                       (apply console-print args)
                       (apply repl-print args))})

(defn alive?
  "Returns truthy value if the REPL is attempting to connect or is
   connected, or falsy value otherwise."
  []
  (not (nil? @ws-conn)))

(defmulti ws-msg
  "Process messages from WebSocket server"
  {:arglists '([msg])}
  :op)

(defmethod ws-msg
  :error
  [msg]
  (.error js/console (str "Websocket REPL error " (:type msg))))

(defmethod ws-msg
  :eval-js
  [msg]
  (let [{:keys [code repl]} msg]
    (when repl (reset! last-repl repl))
    {:op :result
     :value (try
              {:status :success
               :repl repl
               :value (str "\n" (js* "eval(~{code})"))}
              (catch :default e
                {:status :exception
                 :repl repl
                 :ua-product (brepl/get-ua-product)
                 :value (pr-str e)
                 :stacktrace (if (.hasOwnProperty e "stack")
                               (.-stack e)
                               "No stacktrace available.")}))}))

(defmethod ws-msg
  :client-id
  [msg]
  (.setItem js/localStorage "repl-client-id" (:id msg))
  {:op :result
   :value true})

(defn connect
  "Connects to a WebSocket REPL server from an HTML document. After the
  connection is made, the REPL will evaluate forms in the context of
  the document that called this function."
  [ws-url & {:keys [verbose on-open on-error on-close print]
             :or {verbose false print :repl}}]
  (let [stored-id (.getItem js/localStorage "repl-client-id")
        connect-url (if stored-id
                      (str ws-url "/" stored-id)
                      ws-url)
        repl (net/websocket-connection)]
    (swap! ws-conn (constantly repl))
    (event/listen repl :opened
      (fn [_evt]
        (set-print-fn! (if (fn? print) print (get print-fns print)))
        (set-print-err-fn! repl-print)
        (net/transmit repl (pr-str {:op :ready
                                    :value {:status :success
                                            :repl @last-repl}}))
        (when verbose (.info js/console "Opened Websocket REPL connection"))
        (when (fn? on-open) (on-open))))
    (event/listen repl :message
      (fn [evt]
        (let [msg (read-string (.-message evt))]
          (if (= (:op msg) :client-id)
            (do (.setItem js/localStorage "repl-client-id" (:id msg))
                (net/transmit repl {:op :result :value true}))
            (let [response (-> msg ws-msg pr-str)]
              (net/transmit repl response))))))
    (event/listen repl :closed
      (fn [_evt]
        (reset! ws-conn nil)
        (when verbose (.info js/console "Closed Websocket REPL connection"))
        (when (fn? on-close) (on-close))))
    (event/listen repl :error
      (fn [evt]
        (when verbose (.error js/console "WebSocket error" evt))
        (when (fn? on-error) (on-error evt))))
    (brepl/bootstrap)
    (net/connect repl connect-url)))
