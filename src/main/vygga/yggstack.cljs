(ns vygga.yggstack
  (:require ["react-native-battery-optimization-check" :refer [RequestDisableOptimization]]
            ["react-native" :as rn]))

(defonce native-module
  (try (-> (js/require "react-native") .-NativeModules .-YggstackModule)
       (catch js/Error _ nil)))

(defn- check-module []
  (when-not native-module
    (throw (js/Error. "YggstackModule native module is not registered. Ensure YggstackPackage is added to MainApplication.kt and the app was rebuilt."))))

(defn generate-config []
  (check-module)
  (.generateConfig native-module))

(defn start [config-json socks-addr nameserver]
  (check-module)
  (.start native-module config-json socks-addr nameserver))

(defn stop []
  (check-module)
  (.stop native-module))

(defn get-peers []
  (check-module)
  (.getPeersJSON native-module))

(defn get-address []
  (check-module)
  (.getAddress native-module))

(defn get-public-key []
  (check-module)
  (.getPublicKey native-module))

(defn extract-private-key [config-json]
  (let [parsed (try (js/JSON.parse config-json) (catch js/Error _ nil))]
    (when parsed (.-PrivateKey parsed))))

(defn build-config-json [private-key peers]
  (js/JSON.stringify
   (clj->js {"PrivateKey"        private-key
             "Certificate"       nil
             "Peers"             (vec peers)
             "InterfacePeers"    {}
             "Listen"            []
             "AdminListen"       "none"
             "MulticastInterfaces" []
             "AllowedPublicKeys" []
             "IfName"            "none"
             "IfMTU"             65535
             "NodeInfoPrivacy"   false
             "NodeInfo"          nil})))

;; ---- Foreground Service ----
;; Now backed by a real Android foreground service (YggdrasilService.java).
;; The native module's start() and stop() manage the service lifecycle automatically.

(defn start-foreground-service [_title _text]
  (when native-module
    (js/console.log "Foreground service active (native)")))

(defn stop-foreground-service []
  (when native-module
    (js/console.log "Foreground service stopped (native)")))

;; ---- Battery Optimization ----

(defn open-battery-optimization-settings []
  (RequestDisableOptimization))

;; ---- Exit App ----

(defn exit-app []
  (stop-foreground-service)
  (stop)
  (.exitApp (.-BackHandler rn)))
