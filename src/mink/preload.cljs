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

(ns mink.preload
 (:require
  [mink.browser :as otb]))

(defonce conn
  (when-not (otb/alive?)
    (otb/connect (str "ws://" (or otb/host-name "localhost") ":" (or otb/ws-port 9001)))))
