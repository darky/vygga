(ns vygga.events.voip-test
  (:require
   [cljs.test :refer-macros [deftest is testing use-fixtures]]
   [re-frame.core :as rf]
   [re-frame.db :as rdb]
   [vygga.db :refer [app-db]]
   [vygga.crypto :as crypto]
   [vygga.events.voip]))

(def captured (atom {}))

(defn byte->hex [b]
  (let [s (.toString b 16)]
    (if (= 1 (.-length s)) (str "0" s) s)))

(defn mock-fx [key]
  (fn [opts] (swap! captured assoc key opts)))

(defn setup []
  (reset! captured {})
  (reset! rdb/app-db app-db)
  (rf/reg-fx :messenger/show-incoming-notification (mock-fx :messenger/show-incoming-notification))
  (rf/reg-fx :voip/send-signal (mock-fx :voip/send-signal))
  (rf/reg-fx :voip/connect-audio (mock-fx :voip/connect-audio))
  (rf/reg-fx :voip/disconnect-audio (mock-fx :voip/disconnect-audio))
  (rf/reg-fx :voip/show-overlay (mock-fx :voip/show-overlay))
  (rf/reg-fx :voip/hide-overlay (mock-fx :voip/hide-overlay)))

(use-fixtures :each (fn [t] (setup) (t)))

(deftest test-initial-state
  (is (= :idle (get-in @rdb/app-db [:voip :call-state])))
  (is (nil? (get-in @rdb/app-db [:voip :call-id])))
  (is (nil? (get-in @rdb/app-db [:voip :remote-addr]))))

(deftest test-call-contact-missing-address
  (rf/dispatch-sync [:voip/call-contact "nonexistent"])
  (is (= :idle (get-in @rdb/app-db [:voip :call-state])))
  (is (not (contains? @captured :voip/send-signal)))
  (is (not (contains? @captured :voip/connect-audio)))
  (is (not (contains? @captured :voip/show-overlay))
      "should not show overlay on missing address"))

(deftest test-call-contact
  (let [contact-id "test-id"
        address "201:abcd::2"
        db-with-data (-> app-db
                         (assoc-in [:messenger :contacts contact-id]
                                   {:address address :messages []})
                         (assoc-in [:yggstack :address] "201:abcd::1")
                         (assoc-in [:yggstack :private-key] "privkey")
                         (assoc-in [:yggstack :public-key] "pubkey"))]
    (reset! rdb/app-db db-with-data)
    (rf/dispatch-sync [:voip/call-contact contact-id])
    (is (= :calling (get-in @rdb/app-db [:voip :call-state])))
    (is (= address (get-in @rdb/app-db [:voip :remote-addr])))
    (let [signal (get @captured :voip/send-signal)]
      (is (map? signal))
      (is (= "offer" (:call-type signal)))
      (is (= address (:to signal))))
    (is (not (contains? @captured :voip/connect-audio))
        "should not connect audio before callee accepts")
    (let [overlay (get @captured :voip/show-overlay)]
      (is (map? overlay) "show-overlay should fire")
      (is (= :active (:mode overlay)))
      (is (= address (:address overlay)))
      (is (string? (:call-id overlay))))))

(deftest test-call-contact-when-busy
  (let [contact-id "test-id"
        db-busy (-> app-db
                    (assoc-in [:messenger :contacts contact-id]
                              {:address "201::2" :messages []})
                    (assoc-in [:yggstack :address] "201::1")
                    (assoc-in [:yggstack :private-key] "key")
                    (assoc-in [:yggstack :public-key] "pk")
                    (assoc-in [:voip :call-state] :connected))]
    (reset! rdb/app-db db-busy)
    (reset! captured {})
    (rf/dispatch-sync [:voip/call-contact contact-id])
    (is (= :connected (get-in @rdb/app-db [:voip :call-state])) "should stay connected")
    (is (not (contains? @captured :voip/send-signal)) "should not send signal")
    (is (not (contains? @captured :voip/show-overlay))
        "should not show overlay when busy")))

(deftest test-accept-call-while-ringing
  (let [db-ringing (-> app-db
                       (assoc-in [:voip :call-state] :ringing)
                       (assoc-in [:voip :call-id] "call-1")
                       (assoc-in [:voip :remote-addr] "201::ring")
                       (assoc-in [:yggstack :private-key] "key")
                       (assoc-in [:yggstack :public-key] "pk")
                       (assoc-in [:yggstack :address] "201::me"))]
    (reset! rdb/app-db db-ringing)
    (rf/dispatch-sync [:voip/accept-call])
    (is (= :connected (get-in @rdb/app-db [:voip :call-state])))
    (let [signal (get @captured :voip/send-signal)]
      (is (map? signal))
      (is (= "accept" (:call-type signal))))
    (is (contains? @captured :voip/connect-audio)
        "accepting call should connect audio for bidirectional communication")
    (let [overlay (get @captured :voip/show-overlay)]
      (is (map? overlay) "show-overlay should fire on accept")
      (is (= :active (:mode overlay)))
      (is (= "201::ring" (:address overlay))))))

(deftest test-accept-call-while-idle
  (rf/dispatch-sync [:voip/accept-call])
  (is (= :idle (get-in @rdb/app-db [:voip :call-state])))
  (is (not (contains? @captured :voip/send-signal)))
  (is (not (contains? @captured :voip/show-overlay))
      "should not show overlay when accepting from idle"))

(deftest test-reject-call
  (let [db-ringing (-> app-db
                       (assoc-in [:voip :call-state] :ringing)
                       (assoc-in [:voip :call-id] "call-1")
                       (assoc-in [:voip :remote-addr] "201::ring")
                       (assoc-in [:yggstack :private-key] "key")
                       (assoc-in [:yggstack :public-key] "pk")
                       (assoc-in [:yggstack :address] "201::me"))]
    (reset! rdb/app-db db-ringing)
    (rf/dispatch-sync [:voip/reject-call])
    (is (= :idle (get-in @rdb/app-db [:voip :call-state])))
    (let [signal (get @captured :voip/send-signal)]
      (is (= "reject" (:call-type signal))))
    (is (contains? @captured :voip/disconnect-audio))
    (is (contains? @captured :voip/hide-overlay)
        "rejecting call should hide overlay")))

(deftest test-end-call
  (let [states [:calling :ringing :connected]]
    (doseq [s states]
      (setup)
      (let [db-in-call (-> app-db
                           (assoc-in [:voip :call-state] s)
                           (assoc-in [:voip :call-id] "call-1")
                           (assoc-in [:voip :remote-addr] "201::remote")
                           (assoc-in [:yggstack :private-key] "key")
                           (assoc-in [:yggstack :public-key] "pk")
                           (assoc-in [:yggstack :address] "201::me"))]
        (reset! rdb/app-db db-in-call)
        (rf/dispatch-sync [:voip/end-call])
        (is (= :idle (get-in @rdb/app-db [:voip :call-state]))
            (str "end from " s " resets to idle"))
        (is (contains? @captured :voip/send-signal)
            (str "end from " s " sends signal"))
        (is (contains? @captured :voip/disconnect-audio)
            (str "end from " s " disconnects audio"))
        (is (contains? @captured :voip/hide-overlay)
            (str "end from " s " hides overlay"))))))

(deftest test-incoming-signal-offer
  (let [seed (doto (js/Uint8Array. 32) (aset 0 77))
        kp (.. js/tweetnacl -sign -keyPair (fromSeed seed))
        pubkey (apply str (map byte->hex (array-seq (.-publicKey kp))))
        privkey (apply str (map byte->hex (array-seq (.-secretKey kp))))
        call-id "incoming-call-1"
        ts 1000
        data-to-sign (str "call-signal|offer|" call-id "|" ts)
        sig (crypto/sign-message privkey data-to-sign)
        msg {:type "call-signal"
             :call-type "offer"
             :call-id call-id
             :from "201:aaaa::1"
             :to "201:me"
             :ts ts
             :pubkey pubkey
             :sig sig}]
    (testing "incoming offer for us"
      (let [db-with-addr (assoc-in app-db [:yggstack :address] "201:me")]
        (reset! rdb/app-db db-with-addr)
        (rf/dispatch-sync [:voip/incoming-signal msg])
        (is (= :ringing (get-in @rdb/app-db [:voip :call-state])))
        (is (= call-id (get-in @rdb/app-db [:voip :call-id])))
        (is (= "201:aaaa::1" (get-in @rdb/app-db [:voip :remote-addr])))
        (let [overlay (get @captured :voip/show-overlay)]
          (is (map? overlay) "show-overlay should fire on incoming offer")
          (is (= :incoming (:mode overlay)))
          (is (= "201:aaaa::1" (:address overlay)))
          (is (= call-id (:call-id overlay))))))
    (testing "unsigned offer is ignored"
      (setup)
      (let [db-with-addr (assoc-in app-db [:yggstack :address] "201:me")]
        (reset! rdb/app-db db-with-addr)
        (rf/dispatch-sync [:voip/incoming-signal
                           (assoc msg :sig nil)])
        (is (= :idle (get-in @rdb/app-db [:voip :call-state])))
        (is (not (contains? @captured :voip/show-overlay))
            "should not show overlay for unsigned offer")))
    (testing "offer while busy is ignored"
      (setup)
      (let [db-busy (-> app-db
                        (assoc-in [:yggstack :address] "201:me")
                        (assoc-in [:voip :call-state] :connected))]
        (reset! rdb/app-db db-busy)
        (rf/dispatch-sync [:voip/incoming-signal msg])
        (is (= :connected (get-in @rdb/app-db [:voip :call-state])))
        (is (not (contains? @captured :voip/show-overlay))
            "should not show overlay when busy")))))

(deftest test-incoming-signal-accept
  (let [seed (doto (js/Uint8Array. 32) (aset 0 88))
        kp (.. js/tweetnacl -sign -keyPair (fromSeed seed))
        pubkey (apply str (map byte->hex (array-seq (.-publicKey kp))))
        privkey (apply str (map byte->hex (array-seq (.-secretKey kp))))
        call-id "my-call"
        ts 2000
        data-to-sign (str "call-signal|accept|" call-id "|" ts)
        sig (crypto/sign-message privkey data-to-sign)
        msg {:type "call-signal"
             :call-type "accept"
             :call-id call-id
             :from "201:bbbb::1"
             :to "201:me"
             :ts ts
             :pubkey pubkey
             :sig sig}]
    (testing "accept moves calling to connected"
      (let [db-calling (-> app-db
                           (assoc-in [:voip :call-state] :calling)
                           (assoc-in [:voip :call-id] call-id)
                           (assoc-in [:yggstack :private-key] "key")
                           (assoc-in [:yggstack :public-key] "pk")
                           (assoc-in [:yggstack :address] "201:me"))]
        (reset! rdb/app-db db-calling)
        (rf/dispatch-sync [:voip/incoming-signal msg])
        (is (= :connected (get-in @rdb/app-db [:voip :call-state])))
        (is (contains? @captured :voip/connect-audio))
        (let [overlay (get @captured :voip/show-overlay)]
          (is (map? overlay) "show-overlay should fire on accept signal")
          (is (= :active (:mode overlay)))
          (is (= "201:bbbb::1" (:address overlay))))))
    (testing "accept while idle is ignored"
      (setup)
      (rf/dispatch-sync [:voip/incoming-signal msg])
      (is (= :idle (get-in @rdb/app-db [:voip :call-state])))
      (is (not (contains? @captured :voip/show-overlay))
          "should not show overlay on unexpected accept"))))

(deftest test-incoming-signal-end
  (let [seed (doto (js/Uint8Array. 32) (aset 0 99))
        kp (.. js/tweetnacl -sign -keyPair (fromSeed seed))
        pubkey (apply str (map byte->hex (array-seq (.-publicKey kp))))
        privkey (apply str (map byte->hex (array-seq (.-secretKey kp))))
        call-id "active-call"
        ts 3000
        data-to-sign (str "call-signal|end|" call-id "|" ts)
        sig (crypto/sign-message privkey data-to-sign)
        msg {:type "call-signal"
             :call-type "end"
             :call-id call-id
             :from "201:bbbb::1"
             :to "201:me"
             :ts ts
             :pubkey pubkey
             :sig sig}]
    (reset! rdb/app-db (-> app-db
                           (assoc-in [:voip :call-state] :connected)
                           (assoc-in [:voip :call-id] call-id)
                           (assoc-in [:voip :remote-addr] "201:bbbb::1")))
    (rf/dispatch-sync [:voip/incoming-signal msg])
    (is (= :idle (get-in @rdb/app-db [:voip :call-state])))
    (is (contains? @captured :voip/disconnect-audio))
    (is (contains? @captured :voip/hide-overlay)
        "end signal should hide overlay")))

(deftest test-overlay-cycle
  (let [contact-id "alice"
        address "201:aaaa::2"
        db-ready (-> app-db
                     (assoc-in [:messenger :contacts contact-id]
                               {:address address :messages []})
                     (assoc-in [:yggstack :address] "201:aaaa::1")
                     (assoc-in [:yggstack :private-key] "privkey")
                     (assoc-in [:yggstack :public-key] "pubkey"))]
    (testing "outgoing call shows active overlay"
      (reset! rdb/app-db db-ready)
      (rf/dispatch-sync [:voip/call-contact contact-id])
      (is (= :active (:mode (get @captured :voip/show-overlay)))))
    (testing "end call hides overlay"
      (rf/dispatch-sync [:voip/end-call])
      (is (contains? @captured :voip/hide-overlay)))
    (testing "after end, overlay was removed from captured state"
      (is (= :idle (get-in @rdb/app-db [:voip :call-state]))))))
