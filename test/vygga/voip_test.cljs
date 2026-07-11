(ns vygga.voip-test
  (:require
   [cljs.test :refer-macros [deftest is testing use-fixtures]]
   [re-frame.core :as rf]
   [re-frame.db :as rdb]
   [vygga.db :refer [app-db]]
   [vygga.crypto :as crypto]
   [vygga.events]))

(def captured (atom {}))

(defn byte->hex [b]
  (let [s (.toString b 16)]
    (if (= 1 (.-length s)) (str "0" s) s)))

(defn mock-fx [key]
  (fn [opts] (swap! captured assoc key opts)))

(defn setup []
  (reset! captured {})
  (reset! rdb/app-db app-db))

(rf/reg-fx :voip/send-signal (mock-fx :voip/send-signal))
(rf/reg-fx :voip/send-audio (mock-fx :voip/send-audio))
(rf/reg-fx :voip/connect-audio (mock-fx :voip/connect-audio))
(rf/reg-fx :voip/disconnect-audio (mock-fx :voip/disconnect-audio))
(rf/reg-fx :voip/start-capture (mock-fx :voip/start-capture))
(rf/reg-fx :voip/stop-capture (mock-fx :voip/stop-capture))
(rf/reg-fx :voip/play-audio (mock-fx :voip/play-audio))

(use-fixtures :each (fn [t] (setup) (t)))

(deftest test-initial-state
  (is (= :idle (get-in @rdb/app-db [:voip :call-state])))
  (is (nil? (get-in @rdb/app-db [:voip :call-id])))
  (is (nil? (get-in @rdb/app-db [:voip :remote-addr]))))

(deftest test-call-contact-missing-address
  (rf/dispatch-sync [:voip/call-contact "nonexistent"])
  (is (= :idle (get-in @rdb/app-db [:voip :call-state])))
  (is (not (contains? @captured :voip/send-signal)))
  (is (not (contains? @captured :voip/connect-audio))))

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
        "should not connect audio before callee accepts")))

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
    (is (not (contains? @captured :voip/send-signal)) "should not send signal")))

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
    (is (contains? @captured :voip/start-capture))
    (is (contains? @captured :voip/connect-audio)
        "accepting call should connect audio for bidirectional communication")))

(deftest test-accept-call-while-idle
  (rf/dispatch-sync [:voip/accept-call])
  (is (= :idle (get-in @rdb/app-db [:voip :call-state])))
  (is (not (contains? @captured :voip/send-signal))))

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
    (is (contains? @captured :voip/disconnect-audio))))

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
        (is (contains? @captured :voip/stop-capture)
            (str "end from " s " stops capture"))
        (is (contains? @captured :voip/disconnect-audio)
            (str "end from " s " disconnects audio"))))))

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
        (is (= "201:aaaa::1" (get-in @rdb/app-db [:voip :remote-addr])))))
    (testing "unsigned offer is ignored"
      (setup)
      (let [db-with-addr (assoc-in app-db [:yggstack :address] "201:me")]
        (reset! rdb/app-db db-with-addr)
        (rf/dispatch-sync [:voip/incoming-signal
                           (assoc msg :sig nil)])
        (is (= :idle (get-in @rdb/app-db [:voip :call-state])))))
    (testing "offer while busy is ignored"
      (setup)
      (let [db-busy (-> app-db
                        (assoc-in [:yggstack :address] "201:me")
                        (assoc-in [:voip :call-state] :connected))]
        (reset! rdb/app-db db-busy)
        (rf/dispatch-sync [:voip/incoming-signal msg])
        (is (= :connected (get-in @rdb/app-db [:voip :call-state])))))))

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
        (is (contains? @captured :voip/start-capture))
        (is (contains? @captured :voip/connect-audio))))
    (testing "accept while idle is ignored"
      (setup)
      (rf/dispatch-sync [:voip/incoming-signal msg])
      (is (= :idle (get-in @rdb/app-db [:voip :call-state]))))))

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
    (is (contains? @captured :voip/stop-capture))))

(deftest test-incoming-audio
  (let [pcm-data (js/Uint8Array. #js [0 1 2 3 4 5 6 7])
        seq-num 0
        msg {:seq seq-num
             :data pcm-data}]
    (reset! rdb/app-db (-> app-db
                           (assoc-in [:voip :call-state] :connected)
                           (assoc-in [:voip :call-id] "call-id")))
    (rf/dispatch-sync [:voip/incoming-audio msg])
    (let [audio-opts (get @captured :voip/play-audio)]
      (is (map? audio-opts))
      (is (= pcm-data (:data audio-opts)))))
  (testing "audio while idle is ignored"
    (setup)
    (let [pcm-data (js/Uint8Array. #js [0 1 2 3])
          msg {:seq 0 :data pcm-data}]
      (reset! rdb/app-db (assoc-in app-db [:voip :call-state] :idle))
      (rf/dispatch-sync [:voip/incoming-audio msg])
      (is (not (contains? @captured :voip/play-audio))))))

(deftest test-audio-chunk-captured-raw-data
  (let [pcm-chunk (js/Uint8Array. #js [0x10 0x20 0x30])
        db-connected (-> app-db
                         (assoc-in [:voip :call-state] :connected)
                         (assoc-in [:voip :call-id] "call-1")
                         (assoc-in [:voip :audio-seq] 5))]
    (reset! rdb/app-db db-connected)
    (rf/dispatch-sync [:voip/audio-chunk-captured pcm-chunk])
    (let [audio-send (get @captured :voip/send-audio)]
      (is (map? audio-send))
      (is (= pcm-chunk (:data audio-send)))
      (is (= 5 (:seq audio-send))))
    (is (= 6 (get-in @rdb/app-db [:voip :audio-seq])))))

(deftest test-audio-chunk-captured-increments-seq
  (let [db-connected (-> app-db
                         (assoc-in [:voip :call-state] :connected)
                         (assoc-in [:voip :call-id] "call-1")
                         (assoc-in [:voip :audio-seq] 0))]
    (reset! rdb/app-db db-connected)
    (rf/dispatch-sync [:voip/audio-chunk-captured (js/Uint8Array. #js [1])])
    (is (= 1 (get-in @rdb/app-db [:voip :audio-seq])))
    (is (= 0 (:seq (get @captured :voip/send-audio)))
        "first chunk gets seq 0")
    (rf/dispatch-sync [:voip/audio-chunk-captured (js/Uint8Array. #js [2])])
    (is (= 2 (get-in @rdb/app-db [:voip :audio-seq])))))

(deftest test-audio-chunk-captured-idle
  (let [pcm-chunk (js/Uint8Array. #js [0x01 0x02])]
    (reset! rdb/app-db (assoc-in app-db [:voip :call-state] :idle))
    (rf/dispatch-sync [:voip/audio-chunk-captured pcm-chunk])
    (is (not (contains? @captured :voip/send-audio)))))
