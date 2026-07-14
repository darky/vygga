(ns vygga.view.navigation-test
  (:require
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [re-frame.core :as rf]
   [re-frame.db :as rdb]
    [vygga.events.navigation]
   [vygga.subs]
   [vygga.view.navigation :as nav-view]
   [vygga.view-test-utils :refer [setup-view-tests]]))

(use-fixtures :each (fn [t] (setup-view-tests) (t)))

(deftest test-root-smoke
  (let [result (nav-view/root)]
    (is (some? result))))

(deftest test-navigation-set-root-state
  (let [nav-state {:some :data}]
    (rf/dispatch-sync [:navigation/set-root-state nav-state])
    (is (= nav-state (get-in @rdb/app-db [:navigation :root-state])))))
