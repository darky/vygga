(ns vygga.network
  (:require ["@react-native-community/netinfo" :as netinfo]
            [re-frame.core :as rf]
            [vygga.events.yggstack]))

(defn handle-network-change
  [prev-connected state]
  (let [connected (.-isConnected state)]
    (cond
      (nil? @prev-connected) nil
      (and (not @prev-connected) connected)
      (do
        (js/console.log "Network restored")
        (rf/dispatch [:yggstack/on-network-restored]))
      (and @prev-connected (not connected))
      (js/console.log "Network disconnected"))
    (reset! prev-connected connected)))

(defonce listener
  (let [prev-connected (atom nil)]
    (.addEventListener netinfo #(handle-network-change prev-connected %))))
