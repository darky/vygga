(ns vygga.view.settings-test
  (:require
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [re-frame.core :as rf]
   [re-frame.db :as rdb]
    [vygga.events.yggstack]
    [vygga.events.app]
   [vygga.subs]
   [vygga.view.settings :as settings-view]
   [vygga.db :refer [app-db]]
   [vygga.view-test-utils :refer [setup-view-tests captured]]))

(use-fixtures :each (fn [t] (setup-view-tests) (t)))

(deftest test-settings-smoke
  (let [result (settings-view/settings #js {:navigation #js {:navigate (fn [])}})]
    (is (some? result))))

(deftest test-settings-start-dispatches
  (reset! rdb/app-db (assoc-in app-db [:yggstack :status] :stopped))
  (rf/dispatch-sync [:yggstack/start])
  (is (= :starting (get-in @rdb/app-db [:yggstack :status])))
  (is (contains? @captured :yggstack/generate-key)))

(deftest test-settings-stop-dispatches
  (reset! rdb/app-db (assoc-in app-db [:yggstack :status] :running))
  (rf/dispatch-sync [:yggstack/stop])
  (is (= :stopping (get-in @rdb/app-db [:yggstack :status])))
  (is (contains? @captured :yggstack/stop-daemon)))

(deftest test-settings-add-peer
  (rf/dispatch-sync [:yggstack/add-peer "tls://newpeer:443"])
  (is (some #(= "tls://newpeer:443" %) (get-in @rdb/app-db [:yggstack :peers]))))

(deftest test-settings-remove-peer
  (let [target (first (get-in @rdb/app-db [:yggstack :peers]))]
    (rf/dispatch-sync [:yggstack/remove-peer target])
    (is (not-any? #(= target %) (get-in @rdb/app-db [:yggstack :peers])))))

(deftest test-theme-set-scheme-dark
  (reset! rdb/app-db (assoc app-db :preferred-scheme :light))
  (rf/dispatch-sync [:theme/set-scheme :dark])
  (is (= :dark (:preferred-scheme @rdb/app-db))))

(deftest test-theme-set-scheme-light
  (reset! rdb/app-db (assoc app-db :preferred-scheme :dark))
  (rf/dispatch-sync [:theme/set-scheme :light])
  (is (= :light (:preferred-scheme @rdb/app-db))))

(deftest test-theme-preferred-scheme-sub
  (reset! rdb/app-db (assoc app-db :preferred-scheme :light))
  (is (= :light @(rf/subscribe [:theme/preferred-scheme])))
  (reset! rdb/app-db (assoc app-db :preferred-scheme :dark))
  (is (= :dark @(rf/subscribe [:theme/preferred-scheme]))))
