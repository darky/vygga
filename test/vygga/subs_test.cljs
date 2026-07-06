(ns vygga.subs-test
  (:require
   [cljs.test :refer-macros [deftest is testing use-fixtures]]
   [re-frame.core :as rf]
   [re-frame.db :as rdb]
   [vygga.db :refer [app-db]]
   [vygga.subs]))

(defn setup []
  (reset! rdb/app-db app-db))

(use-fixtures :each (fn [t] (setup) (t)))

(deftest test-navigation-root-state
  (rf/dispatch-sync [:navigation/set-root-state {:key "nav"}])
  (is (= {:key "nav"} @(rf/subscribe [:navigation/root-state]))))

(deftest test-yggstack-status
  (rf/dispatch-sync [:yggstack/set-status :running])
  (is (= :running @(rf/subscribe [:yggstack/status]))))

(deftest test-yggstack-peer-count
  (rf/dispatch-sync [:yggstack/update-peer-count 7])
  (is (= 7 @(rf/subscribe [:yggstack/peer-count]))))

(deftest test-yggstack-peers
  (is (= 4 (count @(rf/subscribe [:yggstack/peers])))))

(deftest test-yggstack-address
  (rf/dispatch-sync [:yggstack/update-address "201::1234"])
  (is (= "201::1234" @(rf/subscribe [:yggstack/address]))))

(deftest test-messenger-contacts
  (let [contacts {"c1" {:name "Alice"}}]
    (reset! rdb/app-db (assoc-in app-db [:messenger :contacts] contacts))
    (is (= contacts @(rf/subscribe [:messenger/contacts])))))

(deftest test-messenger-server-running
  (reset! rdb/app-db (assoc-in app-db [:messenger :server-running] true))
  (is (= true @(rf/subscribe [:messenger/server-running]))))

(deftest test-messenger-current-contact
  (reset! rdb/app-db (assoc-in app-db [:messenger :current-contact] "cid1"))
  (is (= "cid1" @(rf/subscribe [:messenger/current-contact]))))

(deftest test-messenger-current-has-more
  (testing "returns false for non-existent contact"
    (is (= false @(rf/subscribe [:messenger/current-has-more]))))
  (testing "returns has-more? for current contact"
    (let [db (-> app-db
                 (assoc-in [:messenger :current-contact] "c1")
                 (assoc-in [:messenger :contacts "c1" :has-more?] true))]
      (reset! rdb/app-db db)
      (is (= true @(rf/subscribe [:messenger/current-has-more]))))))

(deftest test-messenger-messages-loading
  (is (= false @(rf/subscribe [:messenger/messages-loading])))
  (reset! rdb/app-db (assoc-in app-db [:messenger :messages-loading] true))
  (is (= true @(rf/subscribe [:messenger/messages-loading]))))
