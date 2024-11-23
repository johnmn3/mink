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

(ns mink.browser
  (:require
   [clojure.browser.event :as event]
   [clojure.browser.net :as net]
   [cljs.reader :as reader :refer [read-string]]))

(goog-define WSPORT 9001)
(def ws-port WSPORT)

(goog-define HOST "localhost")
(def host-name HOST)

(enable-console-print!)

(def repl-conn (atom nil))
(def last-repl (atom nil))

(defn repl-print
  "Send data to be printed in the REPL"
  [& args]
  (when-let [conn @repl-conn]
   (net/transmit conn {:op :print
                       :status :success
                       :repl @last-repl
                       :value (str "\n" (apply pr-str args))})))

(set! *print-newline* true)
(set-print-fn! repl-print)
(set-print-err-fn! repl-print)

(defn console-print
  "Print data to the javascript console"
  [& args]
  (.apply (.-log js/console) js/console (clj->js args)))

(def print-fns
  {:repl repl-print
   :console console-print
   #{:repl :console}
   (fn [& args]
     (apply console-print args)
     (apply repl-print args))})

(defn alive?
  "Returns truthy value if the REPL is attempting to connect or is
   connected, or falsy value otherwise."
  []
  (not (nil? @repl-conn)))

(defmulti client-repl-msg
  "Process messages from WebSocket server"
  {:arglists '([msg])}
  :op)

(defmethod client-repl-msg
  :error
  [msg]
  (.error js/console (str "Websocket REPL error " (:type msg))))

(defn send! [msg]
  (when-let [conn @repl-conn]
    (net/transmit conn (pr-str msg))))

(defn handle-client-id [msg]
  (let [new-id (:id msg)
        stored-id (js/localStorage.getItem "mink-client-id")]
    (if stored-id
      (when (not= stored-id new-id)
        (send! {:op :update-client-id
                :old-id stored-id
                :repl @last-repl
                :new-id new-id}))
      (js/localStorage.setItem "mink-client-id" new-id))))

(defmethod client-repl-msg
  :assign-client-id
  [msg]
  (println :client-id (:id msg))
  (handle-client-id msg))

(defmethod client-repl-msg
  :eval-js
  [msg]
  (let [{:keys [code repl]} msg]
    (when repl (reset! last-repl repl))
    {:op :result
     :value (try
              {:status :success
               :repl repl
               :value (str #_"\n" (js* "eval(~{code})") #_"\n")}
              (catch :default e
                {:status :exception
                 :repl repl
                 :value (pr-str e)
                 :stacktrace (if (.hasOwnProperty e "stack")
                               (.-stack e)
                               "No stacktrace available.")}))}))

(defmethod client-repl-msg
  :client-id
  [msg]
  (.setItem js/localStorage "repl-client-id" (:id msg))
  {:op :result
   :value true})

(defn connect
  "Connects to a WebSocket REPL server from an HTML document. After the
  connection is made, the REPL will evaluate forms in the context of
  the document that called this function."
  [repl-url & {:keys [verbose on-open on-error on-close print]
               :or {verbose false print :repl}}]
  (let [repl (net/websocket-connection)]
    (swap! repl-conn (constantly repl))
    (event/listen repl :opened
      (fn [_evt]
        (set-print-fn! (if (fn? print) print (get print-fns print)))
        (set-print-err-fn! repl-print)
        (net/transmit repl (pr-str {:op :ready
                                    :status :success
                                    :repl @last-repl}))
        (when verbose (.info js/console "Opened Websocket REPL connection"))
        (when (fn? on-open) (on-open))))
    (event/listen repl :message
      (fn [evt]
        (let [msg (read-string (.-message evt))]
          (if (= (:op msg) :client-id)
            (do (.setItem js/localStorage "repl-client-id" (:id msg))
                (net/transmit repl {:op :result :value true}))
            (let [response (-> msg client-repl-msg pr-str)]
              (net/transmit repl response))))))
    (event/listen repl :closed
      (fn [_evt]
        (reset! repl-conn nil)
        (when verbose (.info js/console "Closed Websocket REPL connection"))
        (when (fn? on-close) (on-close))))
    (event/listen repl :error
      (fn [evt]
        (when verbose (.error js/console "WebSocket error" evt))
        (when (fn? on-error) (on-error evt))))
    (net/connect repl repl-url)))
