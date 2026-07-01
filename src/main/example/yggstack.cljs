(ns example.yggstack
  (:require [re-frame.core :as rf]
            [clojure.string :as str]))

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
