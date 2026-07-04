(ns example.messenger
  (:require [re-frame.core :as rf]
            ["react-native" :as rn]))

(defonce native-module
  (try (-> (js/require "react-native") .-NativeModules .-YggstackModule)
       (catch js/Error _ nil)))

(defonce messenger-port 7777)

;; ---- Safe native method calls ----
;; Must use .method syntax so React Native's bridge
;; properly injects Promise params for @ReactMethod fns.

(defn- has-method [name]
  (and native-module (not (nil? (aget native-module name)))))

;; ---- Message sending via native SOCKS5 ----

(defn send-message [target-ip json-str]
  (if (has-method "sendMessage")
    (.sendMessage native-module target-ip json-str)
    (js/Promise.reject (js/Error. "sendMessage not available"))))

;; ---- Messenger server (Java TCP server + Go remote mapping) ----

(defn start-server! [port]
  (if (has-method "startMessengerServer")
    (.startMessengerServer native-module (or port messenger-port))
    (js/Promise.reject (js/Error. "startMessengerServer not available"))))

(defn stop-server! []
  (if (has-method "stopMessengerServer")
    (.stopMessengerServer native-module)
    (js/Promise.reject (js/Error. "stopMessengerServer not available"))))

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

;; ---- Event listener for incoming messages ----

(defonce listener-installed (atom false))

(defn install-message-listener! []
  (when (and native-module (not @listener-installed))
    (reset! listener-installed true)
    (let [NativeEventEmitter (.-NativeEventEmitter rn)]
      (when NativeEventEmitter
        (let [emitter (NativeEventEmitter. native-module)]
          (.addListener emitter "onMessengerMessage"
                        (fn [json-str]
                          (try
                            (let [msg (js/JSON.parse json-str)
                                  type (.-type msg)
                                  from (.-from msg)
                                  text (.-text msg)
                                  id (.-id msg)
                                  ts (.-ts msg)]
                              (when (and (= type "message") from)
                                (rf/dispatch [:messenger/receive-incoming from text id ts])))
                            (catch js/Error e
                              (js/console.warn "Failed to parse messenger message:" e))))))))))
