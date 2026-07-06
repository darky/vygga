(ns vygga.events-test
  (:require
   [cljs.test :refer-macros [deftest is testing use-fixtures]]
   [re-frame.core :as rf]
   [re-frame.db :as rdb]
   [vygga.db :refer [app-db default-peers]]
   [vygga.crypto :as crypto]
   [vygga.events]
   [vygga.messenger :as msg]
   [vygga.yggstack :as ygg]))

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
(rf/reg-fx :yggstack/start-foreground-service (mock-fx :yggstack/start-foreground-service))
(rf/reg-fx :yggstack/stop-foreground-service (mock-fx :yggstack/stop-foreground-service))
(rf/reg-fx :yggstack/battery-opt-out-fx (mock-fx :yggstack/battery-opt-out-fx))
(rf/reg-fx :app/exit-fx (mock-fx :app/exit-fx))
(rf/reg-fx :messenger/start-tcp-server (mock-fx :messenger/start-tcp-server))
(rf/reg-fx :messenger/stop-tcp-server (mock-fx :messenger/stop-tcp-server))
(rf/reg-fx :messenger/send-via-socks (mock-fx :messenger/send-via-socks))
(rf/reg-fx :messenger/load-meta (mock-fx :messenger/load-meta))
(rf/reg-fx :messenger/load-initial-page (mock-fx :messenger/load-initial-page))
(rf/reg-fx :messenger/load-older-batch (mock-fx :messenger/load-older-batch))
(rf/reg-fx :messenger/load-next-chunk (mock-fx :messenger/load-next-chunk))
(rf/reg-fx :messenger/delete-contact-msgs (mock-fx :messenger/delete-contact-msgs))
(rf/reg-fx :persist/messenger-meta (mock-fx :persist/messenger-meta))
(rf/reg-fx :persist/messenger-write (mock-fx :persist/messenger-write))
(rf/reg-fx :persist/messenger-update-msg (mock-fx :persist/messenger-update-msg))

(use-fixtures :each (fn [t] (setup) (t)))

(deftest test-initialize-db
  (rf/dispatch-sync [:initialize-db])
  (is (= app-db @rdb/app-db))
  (is (contains? @captured :yggstack/load-and-start))
  (is (contains? @captured :messenger/load-meta)))

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

(deftest test-messenger-add-contact
  (let [contact {:name "Alice" :address "201:abcd::1"}]
    (rf/dispatch-sync [:messenger/add-contact contact])
    (let [contacts (get-in @rdb/app-db [:messenger :contacts])]
      (is (= 1 (count contacts)))
      (let [[cid c] (first contacts)]
        (is (string? cid))
        (is (= "Alice" (:name c)))
        (is (= "201:abcd::1" (:address c)))
        (is (= [] (:messages c)))
        (is (= [] (:message-index c)))
        (is (= 0 (:consumed-count c))))))
  (is (contains? @captured :persist/messenger-meta)))

(deftest test-messenger-set-current-contact
  (let [cid "test-contact-1"]
    (reset! rdb/app-db (assoc-in app-db [:messenger :contacts cid] {:name "Bob"}))
    (rf/dispatch-sync [:messenger/set-current-contact cid])
    (is (= cid (get-in @rdb/app-db [:messenger :current-contact])))))

(deftest test-messenger-send-message
  (let [cid "test-contact"
        address "201:abcd::2"
        db-with-contact (-> app-db
                            (assoc-in [:messenger :contacts cid]
                                      {:name "Test" :address address
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
    (is (contains? @captured :messenger/send-via-socks))
    (is (contains? @captured :persist/messenger-meta))
    (is (contains? @captured :persist/messenger-write))))

(deftest test-messenger-message-sent
  (let [cid "test-contact"
        msg-id "msg-1"
        db-with-sending (-> app-db
                            (assoc-in [:messenger :contacts cid]
                                      {:messages [{:id msg-id :text "hi"
                                                   :from-me true :status :sending}]}))]
    (reset! rdb/app-db db-with-sending)
    (rf/dispatch-sync [:messenger/message-sent cid msg-id])
    (let [msg (first (get-in @rdb/app-db [:messenger :contacts cid :messages]))]
      (is (= :sent (:status msg))))
    (is (contains? @captured :persist/messenger-update-msg))))

(deftest test-messenger-message-failed
  (let [cid "test-contact"
        msg-id "msg-2"
        db-with-sending (-> app-db
                            (assoc-in [:messenger :contacts cid]
                                      {:messages [{:id msg-id :text "hi"
                                                   :from-me true :status :sending}]}))]
    (reset! rdb/app-db db-with-sending)
    (rf/dispatch-sync [:messenger/message-failed cid msg-id])
    (let [msg (first (get-in @rdb/app-db [:messenger :contacts cid :messages]))]
      (is (= :failed (:status msg))))))

(deftest test-messenger-set-contact-messages
  (let [cid "test-contact"
        msgs [{:id "m1" :text "hi"}]
        index-entries [{:id "m1" :ts 100}]
        consumed 1
        total 2
        pending ["_chunk2"]
        has-more? true]
    (rf/dispatch-sync [:messenger/set-contact-messages
                       cid msgs index-entries consumed total pending has-more?])
    (let [c (get-in @rdb/app-db [:messenger :contacts cid])]
      (is (= (reverse msgs) (:messages c)))
      (is (= index-entries (:message-index c)))
      (is (= consumed (:consumed-count c)))
      (is (= total (:total-count c)))
      (is (= pending (:pending-chunks c)))
      (is (= has-more? (:has-more? c)))
      (is (= false (get-in @rdb/app-db [:messenger :messages-loading]))))))

(deftest test-messenger-prepend-older-messages
  (let [cid "test-contact"
        existing [{:id "m2" :text "later"}]
        older [{:id "m1" :text "earlier"}]
        db-with-msg (assoc-in app-db [:messenger :contacts cid]
                              {:messages existing})]
    (reset! rdb/app-db db-with-msg)
    (rf/dispatch-sync [:messenger/prepend-older-messages cid older 1 true])
    (let [msgs (get-in @rdb/app-db [:messenger :contacts cid :messages])]
      (is (= 2 (count msgs)))
      (is (= 1 (get-in @rdb/app-db [:messenger :contacts cid :consumed-count])))
      (is (= true (get-in @rdb/app-db [:messenger :contacts cid :has-more?]))))))

(deftest test-messenger-restore-meta
  (let [data {:seen-ids #{"id1" "id2"}
              :contacts {"cid1" {:name "Alice" :address "201::1"}}}]
    (rf/dispatch-sync [:messenger/restore-meta data])
    (let [msngr (:messenger @rdb/app-db)]
      (is (= #{"id1" "id2"} (:seen-ids msngr)))
      (is (contains? (:contacts msngr) "cid1"))
      (is (= "Alice" (get-in (:contacts msngr) ["cid1" :name])))
      (is (= [] (get-in (:contacts msngr) ["cid1" :messages]))))))

(deftest test-messenger-receive-incoming-duplicate
  (let [db-with-seen (assoc-in app-db [:messenger :seen-ids] #{"dup-id"})]
    (reset! rdb/app-db db-with-seen)
    (rf/dispatch-sync [:messenger/receive-incoming
                       "201::1" "hi" "dup-id" 100 nil nil])
    (is (= #{"dup-id"} (get-in @rdb/app-db [:messenger :seen-ids])))))

(deftest test-messenger-receive-incoming-unsigned
  (rf/dispatch-sync [:messenger/receive-incoming
                     "201::1" "hi" "new-id" 100 nil nil])
  (is (empty? (get-in @rdb/app-db [:messenger :contacts]))))

(defn- byte->hex [b]
  (let [s (.toString b 16)]
    (if (= 1 (.-length s)) (str "0" s) s)))

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
      (rf/dispatch-sync [:messenger/receive-incoming
                         "201:aaaa::1" text msg-id ts pubkey sig])
      (let [contacts (get-in @rdb/app-db [:messenger :contacts])
            cid (first (keys contacts))
            contact (val (first contacts))]
        (is (= 1 (count contacts)))
        (is (some? contact))
        (is (= text (get-in contact [:messages 0 :text])))
        (is (false? (get-in contact [:messages 0 :from-me])))))
    (testing "existing sender appends message"
      (let [address "201:bbbb::1"
            existing-cid "existing-contact"
            msg2-text "Second msg"
            msg2-id "fresh-id-2"
            msg2-ts 6000
            data2-to-sign (str msg2-text "|" msg2-id "|" msg2-ts)
            sig2 (crypto/sign-message privkey data2-to-sign)
            db-with-contact (-> app-db
                                (assoc-in [:messenger :contacts existing-cid]
                                          {:name "Bob" :address address
                                           :messages [{:text "prev"}]})
                                (assoc-in [:messenger :seen-ids] #{}))]
        (reset! rdb/app-db db-with-contact)
        (rf/dispatch-sync [:messenger/receive-incoming
                           address msg2-text msg2-id msg2-ts pubkey sig2])
        (let [msgs (get-in @rdb/app-db [:messenger :contacts existing-cid :messages])]
          (is (= 2 (count msgs)))
          (is (= "Second msg" (:text (last msgs))))
          (is (false? (:from-me (last msgs)))))))))
