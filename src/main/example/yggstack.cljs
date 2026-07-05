(ns example.yggstack
  (:require [re-frame.core :as rf]
            [clojure.string :as str]
            ["expo-notifications" :default Notifications]
            ["react-native-battery-optimization-check" :refer [BatteryOptEnabled
                                                               OpenOptimizationSettings
                                                               openRequestDisableOptimization]]
            ["react-native" :as rn]))

(defonce native-module
  (try (-> (js/require "react-native") .-NativeModules .-YggstackModule)
       (catch js/Error _ nil)))

(def default-peers
  ["tls://45.95.202.21:443"
   "tls://box.paulll.cc:13338"
   "tls://91.98.161.68:9001?key=0e638944bfd6b277fa5e0dddbeb4444778eea8bece63a9862c661797022a8f05"
   "tls://95.217.35.92:1337"])

(defn generate-config []
  (.generateConfig native-module))

(defn start [config-json socks-addr nameserver]
  (.start native-module config-json socks-addr nameserver))

(defn stop []
  (.stop native-module))

(defonce poll-timer (atom nil))

(defn start-polling []
  (when @poll-timer (js/clearInterval @poll-timer))
  (reset! poll-timer
          (js/setInterval
           (fn []
             (when native-module
               (-> (.getPeersJSON native-module)
                   (.then (fn [json] (rf/dispatch [:yggstack/update-peer-count
                                                   (.-length (js/JSON.parse json))])))
                   (.catch (fn [_])))
               (-> (.getAddress native-module)
                   (.then (fn [a] (when a (rf/dispatch [:yggstack/update-address a]))))
                   (.catch (fn [_])))
               (-> (.getPublicKey native-module)
                   (.then (fn [k] (when k (rf/dispatch [:yggstack/update-public-key k]))))
                   (.catch (fn [_])))))
           5000)))

(defn stop-polling []
  (when @poll-timer
    (js/clearInterval @poll-timer)
    (reset! poll-timer nil)))

(defn extract-private-key [config-json]
  (let [parsed (try (js/JSON.parse config-json) (catch js/Error _ nil))]
    (when parsed (.-PrivateKey parsed))))

(defn add-remote-tcp-mapping [port local-addr]
  (.addRemoteTCPMapping native-module port local-addr))

(defn remove-remote-tcp-mapping [port local-addr]
  (.removeRemoteTCPMapping native-module port local-addr))

(defn clear-remote-mappings []
  (.clearRemoteMappings native-module))

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
    (.setNotificationChannelAsync Notifications "yggdrasil_channel"
      #js {:name "Yggdrasil Messenger"
           :description "Keeps the app alive for message receiving"
           :importance (.-LOW (.-AndroidImportance Notifications))
           :enableVibration false})))

(defn start-foreground-service [title text]
  (ensure-fg-channel!)
  (when native-module
    (.setForegroundServiceActive native-module true))
  (-> (.scheduleNotificationAsync Notifications
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
  (-> (.dismissNotificationAsync Notifications "yggdrasil-fg")
      (.catch (fn [e] (js/console.warn "FG notification stop error:" e)))))

;; ---- Battery Optimization ----

(defn battery-opt-enabled? []
  (BatteryOptEnabled))

(defn open-battery-optimization-settings []
  (openRequestDisableOptimization))

;; ---- Exit App ----

(defn exit-app []
  (stop-foreground-service)
  (stop)
  (stop-polling)
  (.exitApp (.-BackHandler rn)))
