(ns vygga.messenger
  (:require [vygga.tcp-server :as tcp-server]
            [vygga.tcp-client :as tcp-client]))

(defonce native-module
  (try (-> (js/require "react-native") .-NativeModules .-YggstackModule)
       (catch js/Error _ nil)))

(defonce messenger-port 7777)

(defn- has-method [name]
  (and native-module (not (nil? (aget native-module name)))))

;; ---- Message sending via CLJS SOCKS5 ----

(defn send-message [target-ip edn-str]
  (tcp-client/send-message! target-ip edn-str))

;; ---- Messenger server (CLJS TCP server + Go remote mapping) ----

(defn start-server! [port]
  (tcp-server/start! (or port messenger-port)))

(defn stop-server! []
  (tcp-server/stop!))

(defn add-remote-mapping [port]
  (if (has-method "addRemoteTCPMapping")
    (.addRemoteTCPMapping native-module (or port messenger-port)
                          (str "127.0.0.1:" (or port messenger-port)))
    (js/Promise.reject (js/Error. "addRemoteTCPMapping not available"))))

(defn remove-remote-mapping [port]
  (if (has-method "removeRemoteTCPMapping")
    (let [p (or port messenger-port)]
      (.removeRemoteTCPMapping native-module p (str "127.0.0.1:" p)))
    (js/Promise.reject (js/Error. "removeRemoteTCPMapping not available"))))


