(ns vygga.messenger-test
  (:require
   [cljs.test :as t :refer-macros [deftest is use-fixtures]]
   [re-frame.db :as rdb]
   [vygga.messenger :as msg]
   [vygga.events]
   [vygga.crypto :as crypto]
   [vygga.db :refer [app-db]]))

(def calls (atom []))

(defn- reset-calls! []
  (reset! calls []))

(defn make-mock-module []
  #js {:sendMessage             (fn [ip payload] (swap! calls conj [:sendMessage ip payload]) (js/Promise.resolve "sent"))
       :startMessengerServer    (fn [p] (swap! calls conj [:startMessengerServer p]) (js/Promise.resolve))
       :stopMessengerServer     (fn [] (swap! calls conj [:stopMessengerServer]) (js/Promise.resolve))
       :addRemoteTCPMapping     (fn [p addr] (swap! calls conj [:addRemoteTCPMapping p addr]) (js/Promise.resolve))
       :removeRemoteTCPMapping  (fn [p addr] (swap! calls conj [:removeRemoteTCPMapping p addr]) (js/Promise.resolve))})

(defn setup []
  (reset-calls!)
  (reset! rdb/app-db app-db)
  (reset! vygga.messenger/listener-sub nil))

(use-fixtures :each {:before setup})

;; ---- Native-module wrapper tests ----

(deftest test-send-message-calls-native
  (t/async done
    (with-redefs [vygga.messenger/native-module (make-mock-module)]
      (-> (msg/send-message "201::1" "{\"text\":\"hi\"}")
          (.then (fn [result]
                   (is (= "sent" result))
                   (is (= [[:sendMessage "201::1" "{\"text\":\"hi\"}"]] @calls))
                   (done)))))))

(deftest test-send-message-rejects-when-method-missing
  (t/async done
    (with-redefs [vygga.messenger/native-module #js {}]
      (-> (msg/send-message "201::1" "payload")
          (.then (fn [_] (is false "should have rejected") (done))
                 (fn [err] (is (some? err)) (done)))))))

(deftest test-start-server-calls-native
  (t/async done
    (with-redefs [vygga.messenger/native-module (make-mock-module)]
      (-> (msg/start-server! 7777)
          (.then (fn [_]
                   (is (= [[:startMessengerServer 7777]] @calls))
                   (done)))))))

(deftest test-start-server-default-port
  (t/async done
    (with-redefs [vygga.messenger/native-module (make-mock-module)]
      (-> (msg/start-server! nil)
          (.then (fn [_]
                   (is (= [[:startMessengerServer 7777]] @calls))
                   (done)))))))

(deftest test-stop-server-calls-native
  (t/async done
    (with-redefs [vygga.messenger/native-module (make-mock-module)]
      (-> (msg/stop-server!)
          (.then (fn [_]
                   (is (= [[:stopMessengerServer]] @calls))
                   (done)))))))

(deftest test-add-remote-mapping
  (t/async done
    (with-redefs [vygga.messenger/native-module (make-mock-module)]
      (-> (msg/add-remote-mapping 7777)
          (.then (fn [_]
                   (is (= [[:addRemoteTCPMapping 7777 "127.0.0.1:7777"]] @calls))
                   (done)))))))

(deftest test-remove-remote-mapping
  (t/async done
    (with-redefs [vygga.messenger/native-module (make-mock-module)]
      (-> (msg/remove-remote-mapping 7777)
          (.then (fn [_]
                   (is (= [[:removeRemoteTCPMapping 7777 "127.0.0.1:7777"]] @calls))
                   (done)))))))

;; ---- Poll / parse-and-dispatch tests ----
;; Must use real tweetnacl keypairs because :messenger/receive-incoming
;; verifies signatures via crypto/verify-signature.

(defn- byte->hex [b]
  (let [s (.toString b 16)]
    (if (= 1 (.-length s)) (str "0" s) s)))

(defn- make-signed-msg-edn [text from-addr id ts]
  (let [seed (doto (js/Uint8Array. 32) (aset 0 42))
        kp (.. js/tweetnacl -sign -keyPair (fromSeed seed))
        pubkey (apply str (map byte->hex (array-seq (.-publicKey kp))))
        privkey (apply str (map byte->hex (array-seq (.-secretKey kp))))
        data-to-sign (str text "|" id "|" ts)
        sig (crypto/sign-message privkey data-to-sign)]
    (pr-str {:type "message" :from from-addr :text text :id id :ts ts :pubkey pubkey :sig sig})))

;; receive-message! parses EDN synchronously and dispatches via
;; re-frame. We use a 50ms timeout after the call to let the event
;; queue drain before making assertions.

(deftest test-receive-message-dispatches-incoming
  (let [msg-edn (make-signed-msg-edn "hello" "201::1" "m1" 100)]
    (t/async done
      (msg/receive-message! msg-edn)
      (js/setTimeout
       (fn []
         (let [contacts (get-in @rdb/app-db [:messenger :contacts])]
           (is (= 1 (count contacts)))
           (is (= "hello" (get-in (val (first contacts)) [:messages 0 :text]))))
         (done))
       50))))

(deftest test-receive-message-invalid-edn
  (let [warn-msgs (atom [])
        orig-warn js/console.warn]
    (set! js/console.warn (fn [& args] (swap! warn-msgs conj (apply str args))))
    (msg/receive-message! "#unknown/tag \"data\"")
    (set! js/console.warn orig-warn)
    (is (= 1 (count @warn-msgs)) "malformed EDN should log a warning")))

(deftest test-receive-message-multiple
  (let [msg1-edn (make-signed-msg-edn "a" "201::1" "m1" 100)
        msg2-edn (make-signed-msg-edn "b" "201::2" "m2" 200)]
    (t/async done
      (msg/receive-message! msg1-edn)
      (msg/receive-message! msg2-edn)
      (js/setTimeout
       (fn []
         (let [contacts (get-in @rdb/app-db [:messenger :contacts])]
           (is (= 2 (count contacts))))
         (done))
       50))))

(deftest test-receive-message-ignores-non-message-type
  (let [edn (pr-str {:type "presence" :from "201::1" :status "online"})]
    (msg/receive-message! edn)
    (is (empty? (get-in @rdb/app-db [:messenger :contacts]))
        "non-message type should not create a contact")))

(deftest test-receive-message-ignores-missing-from
  (let [edn (pr-str {:type "message" :text "no from" :id "m1" :ts 1})]
    (msg/receive-message! edn)
    (is (empty? (get-in @rdb/app-db [:messenger :contacts]))
        "message without :from should not create a contact")))

(deftest test-install-listener-noop-when-no-native-module
  (with-redefs [vygga.messenger/native-module nil]
    (msg/install-message-listener!)
    (is (nil? @vygga.messenger/listener-sub) "listener should remain nil when no native-module")))

(deftest test-install-message-listener-sets-subscription
  (with-redefs [vygga.messenger/native-module (make-mock-module)]
    (msg/install-message-listener!)
    (is (some? @vygga.messenger/listener-sub))
    (is (fn? @vygga.messenger/listener-sub))))

(deftest test-uninstall-message-listener-removes-subscription
  (with-redefs [vygga.messenger/native-module (make-mock-module)]
    (msg/install-message-listener!)
    (is (some? @vygga.messenger/listener-sub))
    (msg/uninstall-message-listener!)
    (is (nil? @vygga.messenger/listener-sub))))
