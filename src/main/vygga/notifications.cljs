(ns vygga.notifications
  (:require ["expo-notifications" :as notifications]))

(defonce ^:const channel-id "messages_channel")
(defonce ^:const call-channel-id "calls_channel")

(defn init! []
  (notifications/setNotificationHandler
   (clj->js {:handleNotification
             (fn [] (js/Promise.resolve #js {:shouldShowBanner true
                                             :shouldShowList true
                                             :shouldPlaySound true
                                             :shouldSetBadge false}))}))
  (-> (notifications/setNotificationChannelAsync channel-id
                                                 (clj->js {:name "Messages"
                                                           :importance (.-HIGH ^js (.-AndroidImportance notifications))
                                                           :sound "music_marimba_chord"
                                                           :vibrationPattern [0 100 100 100]}))
      (.catch (fn [e] (js/console.warn "notif channel error:" e))))
  (-> (notifications/setNotificationChannelAsync call-channel-id
                                                 (clj->js {:name "Calls"
                                                           :importance (.-HIGH ^js (.-AndroidImportance notifications))
                                                           :sound nil
                                                           :vibrationPattern [0 100 100 100]}))
      (.catch (fn [e] (js/console.warn "calls channel error:" e)))))

(defn show-message! [sender text]
  (-> (notifications/scheduleNotificationAsync
       (clj->js {:content {:title (str "Message from " sender)
                           :body text
                           :sound "music_marimba_chord"}
                 :trigger nil}))
      (.catch (fn [e] (js/console.warn "show notification error:" e)))))

(defn show-call! [sender]
  (-> (notifications/scheduleNotificationAsync
       (clj->js {:content {:title (str "Incoming call from " sender)
                           :body "Incoming call"
                           :sound nil}
                 :trigger nil}))
      (.catch (fn [e] (js/console.warn "show call notification error:" e)))))
