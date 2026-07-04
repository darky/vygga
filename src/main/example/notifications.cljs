(ns example.notifications
  (:require ["expo-notifications" :as Notifications]))

(defonce channel-created (atom false))

(defn ensure-channel!
  []
  (when-not @channel-created
    (reset! channel-created true)
    (-> (Notifications/setNotificationChannelAsync "messages_channel"
                                                   #js {:name "Messages"
                                                        :importance 6
                                                        :description "Incoming chat messages"
                                                        :sound "default"
                                                        :enableVibrate true})
        (.catch (fn [e]
                  (js/console.warn "Failed to create notification channel:" e))))))

(defn init!
  []
  (Notifications/setNotificationHandler
   #js {:handleNotification
        (fn []
          (js/Promise.resolve
           #js {:shouldShowBanner true
                :shouldShowList true
                :shouldPlaySound true
                :shouldSetBadge false}))})
  (ensure-channel!)
  (-> (Notifications/requestPermissionsAsync)
      (.then (fn [^js result]
               (when (.-granted result)
                 (js/console.log "Notification permissions granted"))))
      (.catch (fn [e]
                (js/console.warn "Notification permission request failed:" e)))))

(defn show!
  [{:keys [title body]}]
  (-> (Notifications/scheduleNotificationAsync
       #js {:content #js {:title (or title "")
                          :body (or body "")
                          :sound "default"}
            :trigger nil})
      (.catch (fn [e]
                (js/console.warn "Failed to show notification:" e)))))
