(ns vygga.tcp-server-test
  (:require
   [cljs.test :as t :refer-macros [deftest is use-fixtures]]
   [re-frame.db :as rdb]
   [vygga.tcp-server :as tcp-server]
    [vygga.events.messenger]
   [vygga.crypto :as crypto]
   [vygga.db :refer [app-db]]))

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

(defn setup []
  (reset! rdb/app-db app-db)
  (reset! vygga.tcp-server/server-instance nil))

(use-fixtures :each {:before setup})

;; ---- parse-and-dispatch ----

(deftest test-parse-and-dispatch-incoming
  (let [msg-edn (make-signed-msg-edn "hello" "201::1" "m1" 100)]
    (t/async done
      (tcp-server/parse-and-dispatch msg-edn)
      (js/setTimeout
       (fn []
         (let [contacts (get-in @rdb/app-db [:messenger :contacts])]
           (is (= 1 (count contacts)))
           (is (= "hello" (get-in (val (first contacts)) [:messages 0 :text]))))
         (done))
       50))))

(deftest test-parse-and-dispatch-invalid-edn
  (let [warn-msgs (atom [])
        orig-warn js/console.warn]
    (set! js/console.warn (fn [& args] (swap! warn-msgs conj (apply str args))))
    (tcp-server/parse-and-dispatch "#unknown/tag \"data\"")
    (set! js/console.warn orig-warn)
    (is (= 1 (count @warn-msgs)) "malformed EDN should log a warning")))

(deftest test-parse-and-dispatch-multiple
  (let [msg1-edn (make-signed-msg-edn "a" "201::1" "m1" 100)
        msg2-edn (make-signed-msg-edn "b" "201::2" "m2" 200)]
    (t/async done
      (tcp-server/parse-and-dispatch msg1-edn)
      (tcp-server/parse-and-dispatch msg2-edn)
      (js/setTimeout
       (fn []
         (let [contacts (get-in @rdb/app-db [:messenger :contacts])]
           (is (= 2 (count contacts))))
         (done))
       50))))

(deftest test-parse-and-dispatch-ignores-non-message-type
  (let [edn (pr-str {:type "presence" :from "201::1" :status "online"})]
    (tcp-server/parse-and-dispatch edn)
    (is (empty? (get-in @rdb/app-db [:messenger :contacts]))
        "non-message type should not create a contact")))

(deftest test-parse-and-dispatch-ignores-missing-from
  (let [edn (pr-str {:type "message" :text "no from" :id "m1" :ts 1})]
    (tcp-server/parse-and-dispatch edn)
    (is (empty? (get-in @rdb/app-db [:messenger :contacts]))
        "message without :from should not create a contact")))

;; ---- TCP server lifecycle ----

(deftest test-tcp-server-start-stop
  (t/async done
    (-> (tcp-server/start! 7777)
        (.then (fn [result]
                 (is (true? result))
                 (is (true? (tcp-server/running?)))
                 (-> (tcp-server/stop!)
                     (.then (fn []
                              (is (false? (tcp-server/running?)))
                              (done)))))))))

(deftest test-tcp-server-start-twice-rejects
  (t/async done
    (-> (tcp-server/start! 7777)
        (.then (fn []
                 (-> (tcp-server/start! 7777)
                     (.then (fn [] (is false "should have rejected") (done))
                            (fn [err]
                              (is (some? err))
                              (-> (tcp-server/stop!) (.then #(done)))))))))))
