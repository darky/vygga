(ns vygga.events.contacts-test
  (:require
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [re-frame.core :as rf]
   [re-frame.db :as rdb]
   [vygga.db :refer [app-db]]
   [vygga.events.contacts]))

(def captured (atom {}))

(defn mock-fx [key]
  (fn [opts] (swap! captured assoc key opts)))

(defn setup []
  (reset! captured {})
  (reset! rdb/app-db app-db)
  (rf/reg-fx :contacts/load-contacts (mock-fx :contacts/load-contacts))
  (rf/reg-fx :contacts/save-contacts (mock-fx :contacts/save-contacts)))

(use-fixtures :each (fn [t] (setup) (t)))

(deftest test-contacts-add-contact
  (let [addr "201:abcd::1"]
    (rf/dispatch-sync [:contacts/add-contact {:address addr}])
    (let [contacts (get-in @rdb/app-db [:messenger :contacts])
          c (get contacts addr)]
      (is (= 1 (count contacts)))
      (is (contains? contacts addr))
      (is (not (contains? c :name)))
      (is (= addr (:address c)))
      (is (= [] (:messages c)))
      (is (= {} (:msg-index c)))
      (is (= {:address addr :messages [] :msg-index {} :unread-count 0} c)))))

(deftest test-contacts-add-contact-with-name
  (let [addr "201:abcd::5"]
    (rf/dispatch-sync [:contacts/add-contact {:address addr :name "Alice"}])
    (let [c (get-in @rdb/app-db [:messenger :contacts addr])]
      (is (= "Alice" (:name c)))
      (is (= addr (:address c))))))

(deftest test-contacts-add-contact-with-empty-name
  (let [addr "201:abcd::6"]
    (rf/dispatch-sync [:contacts/add-contact {:address addr :name ""}])
    (let [c (get-in @rdb/app-db [:messenger :contacts addr])]
      (is (not (contains? c :name)))
      (is (= addr (:address c))))))

(deftest test-contacts-update-contact-name
  (let [cid "201:abcd::7"]
    (rf/dispatch-sync [:contacts/add-contact {:address cid}])
    (rf/dispatch-sync [:contacts/update-contact-name cid "Bob"])
    (let [c (get-in @rdb/app-db [:messenger :contacts cid])]
      (is (= "Bob" (:name c))))
    (is (contains? @captured :contacts/save-contacts))))

(deftest test-contacts-update-contact-name-overwrite
  (let [cid "201:abcd::8"]
    (rf/dispatch-sync [:contacts/add-contact {:address cid :name "Alice"}])
    (rf/dispatch-sync [:contacts/update-contact-name cid "Bob"])
    (let [c (get-in @rdb/app-db [:messenger :contacts cid])]
      (is (= "Bob" (:name c))))))

(deftest test-contacts-update-contact-name-clear
  (let [cid "201:abcd::9"]
    (rf/dispatch-sync [:contacts/add-contact {:address cid :name "Alice"}])
    (rf/dispatch-sync [:contacts/update-contact-name cid ""])
    (let [c (get-in @rdb/app-db [:messenger :contacts cid])]
      (is (nil? (:name c))))))

(deftest test-contacts-update-contact-name-nonexistent
  (rf/dispatch-sync [:contacts/update-contact-name "nonexistent" "Name"])
  (let [c (get-in @rdb/app-db [:messenger :contacts "nonexistent"])]
    (is (nil? c))
    (is (not (contains? @captured :contacts/save-contacts)))))

(deftest test-contacts-remove-contact
  (let [cid "201:abcd::10"]
    (rf/dispatch-sync [:contacts/add-contact {:address cid}])
    (is (contains? (get-in @rdb/app-db [:messenger :contacts]) cid))
    (reset! captured {})
    (rf/dispatch-sync [:contacts/remove-contact cid])
    (is (not (contains? (get-in @rdb/app-db [:messenger :contacts]) cid)))
    (is (contains? @captured :contacts/save-contacts))))

(deftest test-contacts-remove-contact-nonexistent
  (rf/dispatch-sync [:contacts/remove-contact "nonexistent"])
  (is (not (contains? (get-in @rdb/app-db [:messenger :contacts]) "nonexistent")))
  (is (contains? @captured :contacts/save-contacts)))

(deftest test-contacts-remove-contact-current
  (let [cid "201:abcd::12"]
    (rf/dispatch-sync [:contacts/add-contact {:address cid}])
    (rf/dispatch-sync [:contacts/set-current-contact cid])
    (rf/dispatch-sync [:contacts/remove-contact cid])
    (is (nil? (get-in @rdb/app-db [:messenger :contacts cid])))
    (is (= cid (get-in @rdb/app-db [:messenger :current-contact])))))

(deftest test-contacts-add-contact-duplicate
  (let [addr "201:abcd::1"]
    (rf/dispatch-sync [:contacts/add-contact {:address addr}])
    (reset! captured {})
    (rf/dispatch-sync [:contacts/add-contact {:address addr}])
    (let [contacts (get-in @rdb/app-db [:messenger :contacts])]
      (is (= 1 (count contacts)) "duplicate add should not create a second contact")
      (is (contains? contacts addr) "contact should be keyed by address"))))

(deftest test-contacts-set-current-contact
  (let [cid "test-contact-1"
        db-with-unread (assoc-in app-db
                                 [:messenger :contacts cid]
                                 {:address "201::1" :unread-count 3})]
    (reset! rdb/app-db db-with-unread)
    (rf/dispatch-sync [:contacts/set-current-contact cid])
    (is (= cid (get-in @rdb/app-db [:messenger :current-contact])))
    (is (= 0 (get-in @rdb/app-db [:messenger :contacts cid :unread-count])))))

(deftest test-contacts-restore-contacts
  (let [contacts {"cid1" {:address "201::1"}
                  "cid2" {:address "201::2"
                          :messages [{:id "m1" :text "hi" :from-me true}
                                     {:id "m2" :text "bye" :from-me false}]
                          :unread-count 3}
                  "cid3" {:address "201::3"
                          :name "Charlie"
                          :unread-count 1}}]
    (rf/dispatch-sync [:contacts/restore-contacts contacts])
    (let [msngr (:messenger @rdb/app-db)
          alice (get-in msngr [:contacts "201::1"])
          bob (get-in msngr [:contacts "201::2"])]
      (is (= 3 (count (:contacts msngr))))
      (is (contains? (:contacts msngr) "201::1"))
      (is (= "201::1" (:address alice)))
      (is (not (contains? alice :name)))
      (is (= [] (:messages alice)) "no persisted messages leaves empty vector")
      (is (= {} (:msg-index alice)) "no persisted messages leaves empty index")
      (is (= 0 (:unread-count alice)) "no persisted unread-count defaults to 0")
      (is (contains? (:contacts msngr) "201::2"))
      (is (= "201::2" (:address bob)))
      (is (= 2 (count (:messages bob))) "persisted messages are restored")
      (is (= "hi" (get-in (:messages bob) [0 :text])))
      (is (= "bye" (get-in (:messages bob) [1 :text])))
      (is (= 0 (get-in bob [:msg-index "m1"])) "msg-index is rebuilt from restored messages")
      (is (= 1 (get-in bob [:msg-index "m2"])))
      (is (= 3 (:unread-count bob)) "unread-count is preserved from persisted data")
      (let [charlie (get-in msngr [:contacts "201::3"])]
        (is (some? charlie) "contact 3 is restored")
        (is (= "Charlie" (:name charlie)) "name is preserved from persisted data")
        (is (= 1 (:unread-count charlie)) "unread-count is preserved"))
      (is (not (contains? (:contacts msngr) "cid1")) "old UUID key should be re-keyed to address")
      (is (not (contains? (:contacts msngr) "cid2")) "old UUID key should be re-keyed to address"))))

(deftest test-contacts-restore-contacts-limit-to-5
  (let [msgs (vec (for [i (range 7)]
                    {:id (str "msg-" i) :text (str "msg " i) :from-me true}))
        contacts {"cid1" {:address "201::1" :messages msgs}}]
    (rf/dispatch-sync [:contacts/restore-contacts contacts])
    (let [restored-msgs (get-in @rdb/app-db [:messenger :contacts "201::1" :messages])
          idx (get-in @rdb/app-db [:messenger :contacts "201::1" :msg-index])]
      (is (= 5 (count restored-msgs)) "only last 5 messages survive restore")
      (is (= "msg 2" (get-in restored-msgs [0 :text])) "first restored is index 2 (3rd)")
      (is (= "msg 6" (get-in restored-msgs [4 :text])) "last restored is index 6 (7th)")
      (is (= 0 (get idx "msg-2")) "msg-index is rebuilt for restored messages")
      (is (= 4 (get idx "msg-6"))))))

(deftest test-contacts-restore-contacts-dedup
  (let [contacts {"cid1" {:address "201::1"}
                  "cid2" {:address "201::1"}
                  "cid3" {:address "201::2"}}]
    (rf/dispatch-sync [:contacts/restore-contacts contacts])
    (let [contacts-map (get-in @rdb/app-db [:messenger :contacts])]
      (is (= 2 (count contacts-map)) "re-keying deduplicates by address")
      (is (contains? contacts-map "201::1"))
      (is (contains? contacts-map "201::2")))))
