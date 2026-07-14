(ns vygga.events.navigation-test
  (:require
   [cljs.test :refer-macros [deftest is]]
   [re-frame.core :as rf]
   [re-frame.db :as rdb]
   [vygga.db :refer [app-db]]
   [vygga.events.navigation]))

(deftest test-navigation-set-root-state
  (reset! rdb/app-db app-db)
  (let [nav-state {:some :state}]
    (rf/dispatch-sync [:navigation/set-root-state nav-state])
    (is (= nav-state (get-in @rdb/app-db [:navigation :root-state])))))
