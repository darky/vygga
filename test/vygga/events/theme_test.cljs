(ns vygga.events.theme-test
  (:require
   [cljs.test :refer-macros [deftest is]]
   [re-frame.core :as rf]
   [re-frame.db :as rdb]
   [vygga.db :refer [app-db]]
   [vygga.events.theme]))

(deftest test-theme-set-scheme
  (reset! rdb/app-db app-db)
  (rf/dispatch-sync [:theme/set-scheme :light])
  (is (= :light (:preferred-scheme @rdb/app-db)))
  (rf/dispatch-sync [:theme/set-scheme :dark])
  (is (= :dark (:preferred-scheme @rdb/app-db))))
