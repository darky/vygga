(ns vygga.notifications-test
  (:require
   [cljs.test :as t :refer-macros [deftest is]]
   [vygga.notifications :as notif]))

(deftest test-init-calls-handler
  (let [handler-calls (atom 0)
        mod (js/require "expo-notifications")
        orig-handler (.-setNotificationHandler mod)]
    (set! (.-setNotificationHandler mod) (fn [_] (swap! handler-calls inc)))
    (notif/init!)
    (is (= 1 @handler-calls) "setNotificationHandler should be called once")
    (set! (.-setNotificationHandler mod) orig-handler)))

(deftest test-init-calls-create-channel
  (let [channel-calls (atom [])
        mod (js/require "expo-notifications")
        orig-channel (.-setNotificationChannelAsync mod)]
    (set! (.-setNotificationChannelAsync mod) (fn [id cfg] (swap! channel-calls conj {:id id :cfg cfg}) (js/Promise.resolve)))
    (notif/init!)
    (is (= 1 (count @channel-calls)) "setNotificationChannelAsync should be called")
    (let [call (first @channel-calls)]
      (is (= "messages_channel" (:id call)))
      (is (= "Messages" (.-name (:cfg call)))))
    (set! (.-setNotificationChannelAsync mod) orig-channel)))

(deftest test-show-message-calls-schedule
  (let [calls (atom [])
        mod (js/require "expo-notifications")
        orig (.-scheduleNotificationAsync mod)]
    (set! (.-scheduleNotificationAsync mod)
          (fn [req] (swap! calls conj req) (js/Promise.resolve "notif-id")))
    (notif/show-message! "201:abcd::1" "Hello from mesh!")
    (is (= 1 (count @calls)))
    (let [req (first @calls)]
      (is (= "Message from 201:abcd::1" (.. req -content -title)))
      (is (= "Hello from mesh!" (.. req -content -body)))
      (is (= nil (.. req -trigger))))
    (set! (.-scheduleNotificationAsync mod) orig)))

(deftest test-show-message-short-sender
  (let [calls (atom [])
        mod (js/require "expo-notifications")
        orig (.-scheduleNotificationAsync mod)]
    (set! (.-scheduleNotificationAsync mod)
          (fn [req] (swap! calls conj req) (js/Promise.resolve "notif-id")))
    (notif/show-message! "ab" "short")
    (is (= 1 (count @calls)))
    (let [req (first @calls)]
      (is (= "Message from ab" (.. req -content -title))))
    (set! (.-scheduleNotificationAsync mod) orig)))
