(ns vygga.subs-test
  (:require
   [cljs.test :refer-macros [deftest is use-fixtures]]
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
  (let [contacts {"c1" {:address "201::1"}}]
    (reset! rdb/app-db (assoc-in app-db [:messenger :contacts] contacts))
    (is (= contacts @(rf/subscribe [:messenger/contacts])))))

(deftest test-messenger-current-contact
  (reset! rdb/app-db (assoc-in app-db [:messenger :current-contact] "cid1"))
  (is (= "cid1" @(rf/subscribe [:messenger/current-contact]))))

(deftest test-messenger-sorted-contacts
  (let [contacts {"b" {:address "201::b" :messages []}
                  "a" {:address "201::a" :messages [{:id "m1" :text "hello" :from-me true}]}
                  "c" {:address "201::c" :messages [] :unread-count 5}}]
    (reset! rdb/app-db (assoc-in app-db [:messenger :contacts] contacts))
    (let [sorted @(rf/subscribe [:messenger/sorted-contacts])]
      (is (= 3 (count sorted)))
      (is (= "a" (first (first sorted))))
      (is (= "201::c" (:address (second (last sorted)))))
      (is (= ["201::a" "201::b" "201::c"]
             (map (fn [[_ c]] (:address c)) sorted)))
      (let [[_ a] (first sorted)
            [_ b] (second sorted)
            [_ c] (last sorted)]
        (is (= "hello" (:text (:last-message a))) "last-message from messages")
        (is (nil? (:last-message b)) "no last-message when messages empty")
        (is (= 5 (:unread-count c)) "preserves explicit unread-count")
        (is (= 0 (:unread-count a)) "defaults to 0 when unread-count missing")))))
