(ns vygga.view-test-utils
  (:require [re-frame.core :as rf]
            [re-frame.db :as rdb]
            [vygga.db :refer [app-db]]))

(def captured (atom {}))

(defn mock-fx [key]
  (fn [opts] (swap! captured assoc key opts)))

(defn setup-view-tests []
  (reset! captured {})
  (reset! rdb/app-db app-db)
  (rf/reg-fx :yggstack/start-daemon (mock-fx :yggstack/start-daemon))
  (rf/reg-fx :yggstack/generate-key (mock-fx :yggstack/generate-key))
  (rf/reg-fx :yggstack/load-and-start (mock-fx :yggstack/load-and-start))
  (rf/reg-fx :yggstack/regenerate-identity (mock-fx :yggstack/regenerate-identity))
  (rf/reg-fx :yggstack/stop-daemon (mock-fx :yggstack/stop-daemon))
  (rf/reg-fx :yggstack/start-foreground-service (mock-fx :yggstack/start-foreground-service))
  (rf/reg-fx :yggstack/stop-foreground-service (mock-fx :yggstack/stop-foreground-service))
  (rf/reg-fx :yggstack/battery-opt-out-fx (mock-fx :yggstack/battery-opt-out-fx))
  (rf/reg-fx :app/exit-fx (mock-fx :app/exit-fx))
  (rf/reg-fx :messenger/start-tcp-server (mock-fx :messenger/start-tcp-server))
  (rf/reg-fx :messenger/stop-tcp-server (mock-fx :messenger/stop-tcp-server))
  (rf/reg-fx :messenger/send-via-socks (mock-fx :messenger/send-via-socks))
  (rf/reg-fx :contacts/load-contacts (mock-fx :contacts/load-contacts))
  (rf/reg-fx :contacts/save-contacts (mock-fx :contacts/save-contacts)))

(defn text-in-tree [root]
  (filter string? (tree-seq vector? seq root)))

(defn text-present? [root s]
  (some #(= s %) (text-in-tree root)))
