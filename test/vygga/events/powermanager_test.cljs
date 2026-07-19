(ns vygga.events.powermanager-test
  (:require
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [re-frame.core :as rf]
   [re-frame.db :as rdb]
   [vygga.db :refer [app-db]]
   [vygga.events.yggstack]))

(def captured (atom {}))

(defn mock-fx [key]
  (fn [opts] (swap! captured assoc key opts)))

(defn setup []
  (reset! captured {})
  (reset! rdb/app-db app-db)
  (rf/reg-fx :powermanager/acquire (mock-fx :powermanager/acquire))
  (rf/reg-fx :powermanager/release (mock-fx :powermanager/release))
  (rf/reg-fx :yggstack/start-foreground-service (mock-fx :yggstack/start-foreground-service))
  (rf/reg-fx :yggstack/stop-foreground-service (mock-fx :yggstack/stop-foreground-service)))

(use-fixtures :each (fn [t] (setup) (t)))

(deftest test-powermanager-acquire-on-running
  (rf/dispatch-sync [:yggstack/set-status :running])
  (is (contains? @captured :powermanager/acquire)))

(deftest test-powermanager-release-on-stopped
  (rf/dispatch-sync [:yggstack/set-status :stopped])
  (is (contains? @captured :powermanager/release)))

(deftest test-powermanager-no-release-when-not-stopped
  (rf/dispatch-sync [:yggstack/set-status :starting])
  (is (not (contains? @captured :powermanager/release))))

(deftest test-powermanager-no-acquire-when-not-running
  (rf/dispatch-sync [:yggstack/set-status :stopped])
  (is (not (contains? @captured :powermanager/acquire))))
