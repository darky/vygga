(ns vygga.view.yggstatus-test
  (:require
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [re-frame.core :as rf]
   [re-frame.db :as rdb]
   [vygga.events.yggstack]
   [vygga.subs]
   [vygga.view.yggstatus :as yggstatus]
   [vygga.theme :as theme]
   [vygga.view-test-utils :refer [setup-view-tests]]))

(use-fixtures :each (fn [t] (setup-view-tests) (t)))

(deftest test-status-label-running-no-peers
  (let [[color label] (yggstatus/status-label :running 0 theme/light)]
    (is (= (:warning theme/light) color))
    (is (= "Running" label))))

(deftest test-status-label-running-with-peers
  (let [[color label] (yggstatus/status-label :running 1 theme/light)]
    (is (= (:success theme/light) color))
    (is (= "Connected" label))))

(deftest test-status-label-starting
  (let [[color label] (yggstatus/status-label :starting 0 theme/light)]
    (is (= (:warning theme/light) color))
    (is (= "Connecting..." label))))

(deftest test-status-label-stopping
  (let [[color label] (yggstatus/status-label :stopping 0 theme/light)]
    (is (= (:warning theme/light) color))
    (is (= "Disconnecting..." label))))

(deftest test-status-label-stopped
  (let [[color label] (yggstatus/status-label :stopped 0 theme/light)]
    (is (= (:error theme/light) color))
    (is (= "Disconnected" label))))

(deftest test-status-label-unknown
  (let [[color label] (yggstatus/status-label :some-unknown-status 0 theme/light)]
    (is (= (:error theme/light) color))
    (is (= "Disconnected" label))))

(deftest test-status-indicator-smoke
  (let [result (yggstatus/status-indicator #js {:navigation #js {:navigate (fn [])}})]
    (is (some? result))))

(deftest test-yggstack-update-peer-count
  (rf/dispatch-sync [:yggstack/update-peer-count 42])
  (is (= 42 (get-in @rdb/app-db [:yggstack :peer-count]))))

(deftest test-yggstack-update-address
  (rf/dispatch-sync [:yggstack/update-address "201:abcd::1234"])
  (is (= "201:abcd::1234" (get-in @rdb/app-db [:yggstack :address]))))
