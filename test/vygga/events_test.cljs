(ns vygga.events-test
  (:require
   [cljs.test :refer-macros [deftest is testing use-fixtures]]
   [re-frame.core :as rf]
   [re-frame.db :as rdb]
   [vygga.db :refer [app-db default-peers]]
   [vygga.crypto :as crypto]
   [vygga.events]))

(def captured (atom {}))

(defn mock-fx [key]
  (fn [opts] (swap! captured assoc key opts)))

(defn setup []
  (reset! captured {})
  (reset! rdb/app-db app-db))

(rf/reg-fx :yggstack/start-daemon (mock-fx :yggstack/start-daemon))
(rf/reg-fx :yggstack/generate-key (mock-fx :yggstack/generate-key))
(rf/reg-fx :yggstack/load-and-start (mock-fx :yggstack/load-and-start))
(rf/reg-fx :yggstack/regenerate-identity (mock-fx :yggstack/regenerate-identity))
(rf/reg-fx :yggstack/stop-daemon (mock-fx :yggstack/stop-daemon))
(rf/reg-fx :yggstack/retry-peers-now (mock-fx :yggstack/retry-peers-now))
(rf/reg-fx :yggstack/refresh-peer-count (mock-fx :yggstack/refresh-peer-count))
(rf/reg-fx :yggstack/start-foreground-service (mock-fx :yggstack/start-foreground-service))
(rf/reg-fx :yggstack/stop-foreground-service (mock-fx :yggstack/stop-foreground-service))
(rf/reg-fx :yggstack/battery-opt-out-fx (mock-fx :yggstack/battery-opt-out-fx))
(rf/reg-fx :app/exit-fx (mock-fx :app/exit-fx))
(rf/reg-fx :messenger/start-tcp-server (mock-fx :messenger/start-tcp-server))
(rf/reg-fx :messenger/stop-tcp-server (mock-fx :messenger/stop-tcp-server))
(rf/reg-fx :messenger/send-via-socks (mock-fx :messenger/send-via-socks))
(rf/reg-fx :messenger/load-contacts (mock-fx :messenger/load-contacts))
(rf/reg-fx :messenger/show-incoming-notification (mock-fx :messenger/show-incoming-notification))

(use-fixtures :each (fn [t] (setup) (t)))

(defn- byte->hex [b]
  (let [s (.toString b 16)]
    (if (= 1 (.-length s)) (str "0" s) s)))

(deftest test-initialize-db
  (rf/dispatch-sync [:initialize-db])
  (is (= app-db @rdb/app-db))
  (is (contains? @captured :yggstack/load-and-start))
  (is (contains? @captured :messenger/load-contacts)))

(deftest test-navigation-set-root-state
  (let [nav-state {:some :state}]
    (rf/dispatch-sync [:navigation/set-root-state nav-state])
    (is (= nav-state (get-in @rdb/app-db [:navigation :root-state])))))

(deftest test-yggstack-start-with-key
  (let [privkey "test-private-key"
        db-with-key (assoc-in app-db [:yggstack :private-key] privkey)]
    (reset! rdb/app-db db-with-key)
    (rf/dispatch-sync [:yggstack/start])
    (is (= :starting (get-in @rdb/app-db [:yggstack :status])))
    (let [daemon-opts (get @captured :yggstack/start-daemon)]
      (is (map? daemon-opts))
      (is (:config-json daemon-opts))
      (let [parsed (js/JSON.parse (:config-json daemon-opts))]
        (is (= privkey (.-PrivateKey parsed)))))))

(deftest test-yggstack-start-without-key
  (rf/dispatch-sync [:yggstack/start])
  (is (= :starting (get-in @rdb/app-db [:yggstack :status])))
  (is (contains? @captured :yggstack/generate-key)))

(deftest test-yggstack-generate-new-identity
  (let [db-with-key (assoc-in app-db [:yggstack :private-key] "old-key")]
    (reset! rdb/app-db db-with-key)
    (rf/dispatch-sync [:yggstack/generate-new-identity])
    (is (nil? (get-in @rdb/app-db [:yggstack :private-key])))
    (is (contains? @captured :yggstack/regenerate-identity))))

(deftest test-yggstack-set-status-running
  (rf/dispatch-sync [:yggstack/set-status :running])
  (is (= :running (get-in @rdb/app-db [:yggstack :status])))
  (reset! captured {})
  (rf/dispatch-sync [:messenger/start-server])
  (is (= true (get-in @rdb/app-db [:messenger :server-running])))
  (is (contains? @captured :messenger/start-tcp-server))
  (reset! captured {})
  (rf/dispatch-sync [:yggstack/start-foreground-service])
  (is (contains? @captured :yggstack/start-foreground-service)))

(deftest test-yggstack-set-status-stopped
  (rf/dispatch-sync [:yggstack/set-status :stopped])
  (is (= :stopped (get-in @rdb/app-db [:yggstack :status])))
  (reset! captured {})
  (rf/dispatch-sync [:yggstack/stop-foreground-service])
  (is (contains? @captured :yggstack/stop-foreground-service)))

(deftest test-yggstack-set-status-stopped-with-server
  (reset! rdb/app-db (assoc-in app-db [:messenger :server-running] true))
  (rf/dispatch-sync [:yggstack/set-status :stopped])
  (is (= :stopped (get-in @rdb/app-db [:yggstack :status])))
  (reset! captured {})
  (rf/dispatch-sync [:yggstack/stop-foreground-service])
  (is (contains? @captured :yggstack/stop-foreground-service))
  (reset! captured {})
  (rf/dispatch-sync [:messenger/stop-server])
  (is (contains? @captured :messenger/stop-tcp-server)))

(deftest test-yggstack-stop
  (rf/dispatch-sync [:yggstack/stop])
  (is (= :stopping (get-in @rdb/app-db [:yggstack :status])))
  (is (contains? @captured :yggstack/stop-daemon)))

(deftest test-yggstack-on-network-restored-when-running
  (reset! rdb/app-db (assoc-in app-db [:yggstack :status] :running))
  (reset! captured {})
  (rf/dispatch-sync [:yggstack/on-network-restored])
  (is (contains? @captured :yggstack/retry-peers-now))
  (is (contains? @captured :yggstack/refresh-peer-count)))

(deftest test-yggstack-on-network-restored-when-stopped
  (reset! rdb/app-db (assoc-in app-db [:yggstack :status] :stopped))
  (reset! captured {})
  (rf/dispatch-sync [:yggstack/on-network-restored])
  (is (not (contains? @captured :yggstack/retry-peers-now)))
  (is (not (contains? @captured :yggstack/refresh-peer-count))))

(deftest test-yggstack-update-peer-count
  (rf/dispatch-sync [:yggstack/update-peer-count 5])
  (is (= 5 (get-in @rdb/app-db [:yggstack :peer-count]))))

(deftest test-yggstack-update-address
  (rf/dispatch-sync [:yggstack/update-address "201:abcd::1"])
  (is (= "201:abcd::1" (get-in @rdb/app-db [:yggstack :address]))))

(deftest test-yggstack-update-public-key
  (rf/dispatch-sync [:yggstack/update-public-key "pubkey123"])
  (is (= "pubkey123" (get-in @rdb/app-db [:yggstack :public-key]))))

(deftest test-yggstack-set-private-key
  (rf/dispatch-sync [:yggstack/set-private-key "mykey"])
  (is (= "mykey" (get-in @rdb/app-db [:yggstack :private-key]))))

(deftest test-yggstack-add-peer
  (rf/dispatch-sync [:yggstack/add-peer "tls://newpeer:443"])
  (is (some #(= "tls://newpeer:443" %) (get-in @rdb/app-db [:yggstack :peers])))
  (let [peers (get-in @rdb/app-db [:yggstack :peers])
        count-before (count peers)]
    (rf/dispatch-sync [:yggstack/add-peer (first default-peers)])
    (is (= count-before (count (get-in @rdb/app-db [:yggstack :peers]))))))

(deftest test-yggstack-remove-peer
  (let [target (first default-peers)]
    (rf/dispatch-sync [:yggstack/remove-peer target])
    (is (not-any? #(= target %) (get-in @rdb/app-db [:yggstack :peers])))))

(deftest test-yggstack-start-foreground-service
  (rf/dispatch-sync [:yggstack/start-foreground-service])
  (is (contains? @captured :yggstack/start-foreground-service)))

(deftest test-yggstack-stop-foreground-service
  (rf/dispatch-sync [:yggstack/stop-foreground-service])
  (is (contains? @captured :yggstack/stop-foreground-service)))

(deftest test-yggstack-battery-opt-out
  (rf/dispatch-sync [:yggstack/battery-opt-out])
  (is (contains? @captured :yggstack/battery-opt-out-fx)))

(deftest test-app-exit
  (rf/dispatch-sync [:app/exit])
  (is (contains? @captured :app/exit-fx)))

(deftest test-messenger-start-server-standalone
  (reset! rdb/app-db (assoc-in app-db [:messenger :server-port] 9999))
  (rf/dispatch-sync [:messenger/start-server])
  (is (= true (get-in @rdb/app-db [:messenger :server-running])))
  (let [opts (get @captured :messenger/start-tcp-server)]
    (is (map? opts))
    (is (= 9999 (:port opts)))))

(deftest test-messenger-stop-server-standalone
  (reset! rdb/app-db (assoc-in app-db [:messenger :server-running] true))
  (rf/dispatch-sync [:messenger/stop-server])
  (is (= false (get-in @rdb/app-db [:messenger :server-running])))
  (is (contains? @captured :messenger/stop-tcp-server)))

(deftest test-messenger-add-contact
  (let [addr "201:abcd::1"]
    (rf/dispatch-sync [:messenger/add-contact {:address addr}])
    (let [contacts (get-in @rdb/app-db [:messenger :contacts])
          c (get contacts addr)]
      (is (= 1 (count contacts)))
      (is (contains? contacts addr))
      (is (not (contains? c :name)))
      (is (= addr (:address c)))
      (is (= [] (:messages c)))
      (is (= {} (:msg-index c)))
      (is (= {:address addr :messages [] :msg-index {} :unread-count 0} c)))))

(deftest test-messenger-add-contact-duplicate
  (let [addr "201:abcd::1"]
    (rf/dispatch-sync [:messenger/add-contact {:address addr}])
    (reset! captured {})
    (rf/dispatch-sync [:messenger/add-contact {:address addr}])
    (let [contacts (get-in @rdb/app-db [:messenger :contacts])]
      (is (= 1 (count contacts)) "duplicate add should not create a second contact")
      (is (contains? contacts addr) "contact should be keyed by address"))))

(deftest test-messenger-set-current-contact
  (let [cid "test-contact-1"
        db-with-unread (assoc-in app-db
                                 [:messenger :contacts cid]
                                 {:address "201::1" :unread-count 3})]
    (reset! rdb/app-db db-with-unread)
    (rf/dispatch-sync [:messenger/set-current-contact cid])
    (is (= cid (get-in @rdb/app-db [:messenger :current-contact])))
    (is (= 0 (get-in @rdb/app-db [:messenger :contacts cid :unread-count])))))

(deftest test-messenger-send-message
  (let [cid "test-contact"
        address "201:abcd::2"
        db-with-contact (-> app-db
                            (assoc-in [:messenger :contacts cid]
                                      {:address address
                                       :messages []})
                            (assoc-in [:yggstack :address] "201:abcd::1")
                            (assoc-in [:yggstack :private-key] "privkey")
                            (assoc-in [:yggstack :public-key] "pubkey"))]
    (reset! rdb/app-db db-with-contact)
    (rf/dispatch-sync [:messenger/send-message cid "Hello!"])
    (let [contact (get-in @rdb/app-db [:messenger :contacts cid])
          msg (first (:messages contact))]
      (is (= "Hello!" (:text msg)))
      (is (true? (:from-me msg)))
      (is (= :sending (:status msg)))
      (is (string? (:id msg))))
    (is (contains? @captured :messenger/send-via-socks))))

(deftest test-messenger-message-sent
  (let [cid "test-contact"
        msg-id "msg-1"
        db-with-sending (-> app-db
                            (assoc-in [:messenger :contacts cid]
                                      {:messages [{:id msg-id :text "hi"
                                                   :from-me true :status :sending}]
                                       :msg-index {msg-id 0}}))]
    (reset! rdb/app-db db-with-sending)
    (rf/dispatch-sync [:messenger/message-sent cid msg-id])
    (let [msg (first (get-in @rdb/app-db [:messenger :contacts cid :messages]))]
      (is (= :sent (:status msg))))))

(deftest test-messenger-message-failed
  (let [cid "test-contact"
        msg-id "msg-2"
        db-with-sending (-> app-db
                            (assoc-in [:messenger :contacts cid]
                                      {:messages [{:id msg-id :text "hi"
                                                   :from-me true :status :sending}]
                                       :msg-index {msg-id 0}}))]
    (reset! rdb/app-db db-with-sending)
    (rf/dispatch-sync [:messenger/message-failed cid msg-id])
    (let [msg (first (get-in @rdb/app-db [:messenger :contacts cid :messages]))]
      (is (= :failed (:status msg))))))

(deftest test-messenger-resend-message
  (let [cid "test-contact"
        msg-id "msg-3"
        address "201:abcd::2"
        db-with-failed (-> app-db
                           (assoc-in [:messenger :contacts cid]
                                     {:address address
                                      :messages [{:id msg-id :text "hi"
                                                  :from-me true :status :failed}]
                                      :msg-index {msg-id 0}})
                           (assoc-in [:yggstack :address] "201:abcd::1")
                           (assoc-in [:yggstack :private-key] "privkey")
                           (assoc-in [:yggstack :public-key] "pubkey"))]
    (reset! rdb/app-db db-with-failed)
    (rf/dispatch-sync [:messenger/resend-message cid msg-id])
    (let [msg (first (get-in @rdb/app-db [:messenger :contacts cid :messages]))]
      (is (= :sending (:status msg))))
    (is (contains? @captured :messenger/send-via-socks))
    (let [opts (get @captured :messenger/send-via-socks)]
      (is (= address (:address opts)))
      (is (= cid (:contact-id opts)))
      (is (= "hi" (:text opts)))
      (is (= msg-id (:msg-id opts))))))

(deftest test-messenger-receive-incoming-unread-viewing
  (let [seed (doto (js/Uint8Array. 32) (aset 0 99))
        kp (.. js/tweetnacl -sign -keyPair (fromSeed seed))
        pubkey (apply str (map byte->hex (array-seq (.-publicKey kp))))
        privkey (apply str (map byte->hex (array-seq (.-secretKey kp))))
        address "201:cccc::1"
        text "viewing test"
        msg-id "view-msg-1"
        ts 7000
        data-to-sign (str text "|" msg-id "|" ts)
        sig (crypto/sign-message privkey data-to-sign)
        db-with-contact (-> app-db
                            (assoc-in [:messenger :contacts address]
                                      {:address address
                                       :messages [{:text "earlier"}]
                                       :msg-index {"earlier-id" 0}
                                       :unread-count 0})
                            (assoc-in [:messenger :current-contact] address))]
    (reset! rdb/app-db db-with-contact)
    (reset! captured {})
    (rf/dispatch-sync [:messenger/receive-incoming
                       address text msg-id ts pubkey sig])
    (let [msgs (get-in @rdb/app-db [:messenger :contacts address :messages])]
      (is (= 2 (count msgs)) "message is appended when viewing"))
    (let [contact (get-in @rdb/app-db [:messenger :contacts address])]
      (is (= 0 (:unread-count contact)) "unread stays 0 when viewing the contact"))))

(deftest test-messenger-restore-contacts
  (let [contacts {"cid1" {:address "201::1"}
                  "cid2" {:address "201::2"
                          :messages [{:id "m1" :text "hi" :from-me true}
                                     {:id "m2" :text "bye" :from-me false}]
                          :unread-count 3}}]
    (rf/dispatch-sync [:messenger/restore-contacts contacts])
    (let [msngr (:messenger @rdb/app-db)
          alice (get-in msngr [:contacts "201::1"])
          bob (get-in msngr [:contacts "201::2"])]
      (is (= 2 (count (:contacts msngr))))
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
      (is (not (contains? (:contacts msngr) "cid1")) "old UUID key should be re-keyed to address")
      (is (not (contains? (:contacts msngr) "cid2")) "old UUID key should be re-keyed to address"))))

(deftest test-messenger-restore-contacts-limit-to-5
  (let [msgs (vec (for [i (range 7)]
                    {:id (str "msg-" i) :text (str "msg " i) :from-me true}))
        contacts {"cid1" {:address "201::1" :messages msgs}}]
    (rf/dispatch-sync [:messenger/restore-contacts contacts])
    (let [restored-msgs (get-in @rdb/app-db [:messenger :contacts "201::1" :messages])
          idx (get-in @rdb/app-db [:messenger :contacts "201::1" :msg-index])]
      (is (= 5 (count restored-msgs)) "only last 5 messages survive restore")
      (is (= "msg 2" (get-in restored-msgs [0 :text])) "first restored is index 2 (3rd)")
      (is (= "msg 6" (get-in restored-msgs [4 :text])) "last restored is index 6 (7th)")
      (is (= 0 (get idx "msg-2")) "msg-index is rebuilt for restored messages")
      (is (= 4 (get idx "msg-6"))))))

(deftest test-messenger-restore-contacts-dedup
  (let [contacts {"cid1" {:address "201::1"}
                  "cid2" {:address "201::1"} ;; duplicate address — last-wins
                  "cid3" {:address "201::2"}}]
    (rf/dispatch-sync [:messenger/restore-contacts contacts])
    (let [contacts-map (get-in @rdb/app-db [:messenger :contacts])]
      (is (= 2 (count contacts-map)) "re-keying deduplicates by address")
      (is (contains? contacts-map "201::1"))
      (is (contains? contacts-map "201::2")))))

(deftest test-messenger-receive-incoming-unsigned
  (rf/dispatch-sync [:messenger/receive-incoming
                     "201::1" "hi" "new-id" 100 nil nil])
  (is (empty? (get-in @rdb/app-db [:messenger :contacts]))))

(deftest test-messenger-receive-incoming-valid
  (let [seed (doto (js/Uint8Array. 32) (aset 0 42))
        kp (.. js/tweetnacl -sign -keyPair (fromSeed seed))
        pubkey (apply str (map byte->hex (array-seq (.-publicKey kp))))
        privkey (apply str (map byte->hex (array-seq (.-secretKey kp))))
        text "Hello from mesh!"
        msg-id "fresh-id"
        ts 5000
        data-to-sign (str text "|" msg-id "|" ts)
        sig (crypto/sign-message privkey data-to-sign)]
    (testing "unknown sender creates new contact"
      (reset! captured {})
      (rf/dispatch-sync [:messenger/receive-incoming
                         "201:aaaa::1" text msg-id ts pubkey sig])
      (let [contacts (get-in @rdb/app-db [:messenger :contacts])
            contact (val (first contacts))]
        (is (= 1 (count contacts)))
        (is (some? contact))
        (is (= text (get-in contact [:messages 0 :text])))
        (is (false? (get-in contact [:messages 0 :from-me])))
        (is (= 1 (:unread-count contact)) "new contact from incoming starts at unread-count 1"))
      (let [notif-opts (get @captured :messenger/show-incoming-notification)]
        (is (map? notif-opts) "notification effect should fire for new contact")
        (is (= "201:aaaa::1" (:from-addr notif-opts)))
        (is (= "Hello from mesh!" (:text notif-opts)))))
    (testing "existing sender appends message"
      (let [address "201:bbbb::1"
            msg2-text "Second msg"
            msg2-id "fresh-id-2"
            msg2-ts 6000
            data2-to-sign (str msg2-text "|" msg2-id "|" msg2-ts)
            sig2 (crypto/sign-message privkey data2-to-sign)
        db-with-contact (assoc-in app-db
                                  [:messenger :contacts address]
                                  {:address address
                                   :messages [{:text "prev"}]
                                   :unread-count 2})]
    (reset! rdb/app-db db-with-contact)
    (reset! captured {})
    (rf/dispatch-sync [:messenger/receive-incoming
                       address msg2-text msg2-id msg2-ts pubkey sig2])
    (let [msgs (get-in @rdb/app-db [:messenger :contacts address :messages])]
      (is (= 2 (count msgs)))
      (is (= "Second msg" (:text (last msgs))))
      (is (false? (:from-me (last msgs)))))
    (let [contact (get-in @rdb/app-db [:messenger :contacts address])]
      (is (= 3 (:unread-count contact)) "unread-count incremented from 2 to 3"))
    (let [notif-opts (get @captured :messenger/show-incoming-notification)]
          (is (map? notif-opts) "notification effect should fire for existing contact")
          (is (= address (:from-addr notif-opts)))
          (is (= msg2-text (:text notif-opts))))))))
