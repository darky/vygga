(ns vygga.events.app-test
  (:require
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [re-frame.core :as rf]
   [re-frame.db :as rdb]
   [vygga.db :refer [app-db]]
   [vygga.events.app]))

(def captured (atom {}))

(defn mock-fx [key]
  (fn [opts] (swap! captured assoc key opts)))

(defn setup []
  (reset! captured {})
  (reset! rdb/app-db app-db)
  (rf/reg-fx :yggstack/load-and-start (mock-fx :yggstack/load-and-start))
  (rf/reg-fx :contacts/load-contacts (mock-fx :contacts/load-contacts))
  (rf/reg-fx :app/exit-fx (mock-fx :app/exit-fx)))

(use-fixtures :each (fn [t] (setup) (t)))

(deftest test-initialize-db
  (rf/dispatch-sync [:initialize-db])
  (is (= app-db @rdb/app-db))
  (is (contains? @captured :yggstack/load-and-start))
  (is (contains? @captured :contacts/load-contacts)))

(deftest test-app-exit
  (rf/dispatch-sync [:app/exit])
  (is (contains? @captured :app/exit-fx)))
