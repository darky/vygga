(ns vygga.yggstack
  (:require [clojure.string :as str]
            ["expo-notifications" :as Notifications]
            ["react-native-battery-optimization-check" :refer [RequestDisableOptimization]]
            ["react-native" :as rn]))

(defonce native-module
  (try (-> (js/require "react-native") .-NativeModules .-YggstackModule)
       (catch js/Error _ nil)))

(defn generate-config []
  (.generateConfig native-module))

(defn start [config-json socks-addr nameserver]
  (.start native-module config-json socks-addr nameserver))

(defn stop []
  (.stop native-module))

(defn get-peers []
  (.getPeersJSON native-module))

(defn get-address []
  (.getAddress native-module))

(defn get-public-key []
  (.getPublicKey native-module))

(defn extract-private-key [config-json]
  (let [parsed (try (js/JSON.parse config-json) (catch js/Error _ nil))]
    (when parsed (.-PrivateKey parsed))))

(defn build-config-json [private-key peers]
  (let [peers-str (if (seq peers)
                    (str "[" (str/join ", " (map #(str "\"" % "\"") peers)) "]")
                    "[]")]
    (str "{"
         "\"PrivateKey\": \"" private-key "\","
         "\"Certificate\": null,"
         "\"Peers\": " peers-str ","
         "\"InterfacePeers\": {},"
         "\"Listen\": [\"tcp://[::]:0\"],"
         "\"AdminListen\": \"none\","
         "\"MulticastInterfaces\": [],"
         "\"AllowedPublicKeys\": [],"
         "\"IfName\": \"none\","
         "\"IfMTU\": 65535,"
         "\"NodeInfoPrivacy\": false,"
         "\"NodeInfo\": null"
         "}")))

;; ---- Foreground Service ----

(defonce fg-service-channel-created (atom false))

(defn ensure-fg-channel! []
  (when-not @fg-service-channel-created
    (reset! fg-service-channel-created true)
    (Notifications/setNotificationChannelAsync "yggdrasil_channel"
                                               #js {:name "Yggdrasil Messenger"
                                                    :description "Keeps the app alive for message receiving"
                                                    :importance (.-LOW (.-AndroidImportance Notifications))
                                                    :enableVibration false})))

(defn start-foreground-service [title text]
  (ensure-fg-channel!)
  (when native-module
    (.setForegroundServiceActive native-module true))
  (-> (Notifications/scheduleNotificationAsync
       #js {:identifier "yggdrasil-fg"
            :content #js {:channelId "yggdrasil_channel"
                          :title title
                          :body text
                          :sticky true
                          :priority "low"
                          :autoDismiss false}
            :trigger nil})
      (.catch (fn [e] (js/console.warn "FG notification error:" e)))))

(defn stop-foreground-service []
  (when native-module
    (.setForegroundServiceActive native-module false))
  (-> (Notifications/dismissNotificationAsync "yggdrasil-fg")
      (.catch (fn [e] (js/console.warn "FG notification stop error:" e)))))

;; ---- Battery Optimization ----

(defn open-battery-optimization-settings []
  (RequestDisableOptimization))

;; ---- Exit App ----

(defn exit-app []
  (stop-foreground-service)
  (stop)
  (.exitApp (.-BackHandler rn)))
