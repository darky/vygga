(ns vygga.events.debug-test
  (:require
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [re-frame.core :as rf]
   [re-frame.db :as rdb]
   [vygga.db :refer [app-db]]
   [vygga.events.debug]))

(defn setup []
  (reset! rdb/app-db app-db))

(use-fixtures :each (fn [t] (setup) (t)))

(deftest test-debug-log-add-entry
  (rf/dispatch-sync [:debug-log/add-entry "test entry"])
  (is (= ["test entry"] (get-in @rdb/app-db [:debug :logs]))))

(deftest test-debug-log-add-entry-overflow
  (let [entries (vec (range 501))]
    (run! #(rf/dispatch-sync [:debug-log/add-entry %]) entries)
    (is (= 500 (count (get-in @rdb/app-db [:debug :logs]))))
    (is (= 1 (first (get-in @rdb/app-db [:debug :logs]))))))

(deftest test-debug-log-clear
  (rf/dispatch-sync [:debug-log/add-entry "test"])
  (rf/dispatch-sync [:debug-log/clear])
  (is (= [] (get-in @rdb/app-db [:debug :logs]))))
