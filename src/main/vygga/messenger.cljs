(ns vygga.messenger
  (:require [cljs.reader :as reader]
            [re-frame.core :as rf]
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

(defonce listener-sub (atom nil))

(defn parse-and-dispatch [payload]
  (try
    (let [{:keys [type from text id ts pubkey sig]}
          (reader/read-string {:default (fn [tag _]
                                          (throw (js/Error. (str "Unknown EDN tag: #" tag))))}
                              payload)]
      (when (and (= type "message") from)
        (rf/dispatch [:messenger/receive-incoming from text id ts pubkey sig])))
    (catch js/Error e
      (js/console.warn "Failed to parse messenger message:" e))))

(defn receive-message! [edn-str]
  (parse-and-dispatch edn-str))

(defn install-message-listener! []
  (when native-module
    (when-let [remove @listener-sub]
      (remove)
      (reset! listener-sub nil))
    (let [NativeEventEmitter (.-NativeEventEmitter rn)]
      (when NativeEventEmitter
        (let [emitter (NativeEventEmitter. native-module)
              sub (.addListener emitter "onIncomingMessage"
                                (fn [msg] (receive-message! msg)))]
          (reset! listener-sub #(.remove sub)))))))

(defn uninstall-message-listener! []
  (when-let [remove @listener-sub]
    (remove)
    (reset! listener-sub nil)))
