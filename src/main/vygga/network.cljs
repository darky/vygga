(ns vygga.network
  (:require ["@react-native-community/netinfo" :as netinfo]
            [re-frame.core :as rf]))

(defonce listener
  (let [prev-connected (atom nil)]
    (.addEventListener netinfo
                       (fn [state]
                         (let [connected (.-isConnected state)]
                           (when (and (some? @prev-connected)
                                      (not @prev-connected)
                                      connected)
                             (rf/dispatch [:yggstack/on-network-restored]))
                           (reset! prev-connected connected))))))
