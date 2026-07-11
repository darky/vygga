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
    (is (= 2 (count @channel-calls)) "setNotificationChannelAsync should be called twice")
    (let [msg-channel (first @channel-calls)
          call-channel (second @channel-calls)]
      (is (= "messages_channel" (:id msg-channel)))
      (is (= "Messages" (.-name (:cfg msg-channel))))
      (is (= "calls_channel" (:id call-channel)))
      (is (= "Calls" (.-name (:cfg call-channel))))
      (is (nil? (.-sound (:cfg call-channel))) "call channel should use default sound"))
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

(deftest test-show-call
  (let [calls (atom [])
        mod (js/require "expo-notifications")
        orig (.-scheduleNotificationAsync mod)]
    (set! (.-scheduleNotificationAsync mod)
          (fn [req] (swap! calls conj req) (js/Promise.resolve "notif-id")))
    (notif/show-call! "201:aaaa::1")
    (is (= 1 (count @calls)))
    (let [req (first @calls)]
      (is (= "Incoming call from 201:aaaa::1" (.. req -content -title)))
      (is (= "Incoming call" (.. req -content -body)))
      (is (nil? (.. req -content -sound)) "call notification should use default sound")
      (is (nil? (.. req -trigger))))
    (set! (.-scheduleNotificationAsync mod) orig)))
