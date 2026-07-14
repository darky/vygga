(ns vygga.view.chat-test
  (:require
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [re-frame.core :as rf]
   [re-frame.db :as rdb]
   [vygga.events]
   [vygga.subs]
   [vygga.view.chat :as chat-view]
   [vygga.theme :as theme]
   [vygga.db :refer [app-db]]
   [vygga.view-test-utils :refer [setup-view-tests text-present?]]))

(use-fixtures :each (fn [t] (setup-view-tests) (t)))

(deftest test-message-bubble-sent-text
  (let [result (chat-view/message-bubble-render
                {:id "m1" :text "Hello" :from-me true :status :sent :cid "c1"}
                theme/light)]
    (is (text-present? result "Hello"))
    (is (text-present? result "\u2713"))
    (is (not (text-present? result "Failed")))
    (is (not (text-present? result "Resend")))))

(deftest test-message-bubble-received-text
  (let [result (chat-view/message-bubble-render
                {:id "m2" :text "Hi" :from-me false :status nil :cid "c1"}
                theme/light)]
    (is (text-present? result "Hi"))
    (is (not (text-present? result "\u2713")))
    (is (not (text-present? result "Failed")))
    (is (not (text-present? result "Resend")))))

(deftest test-message-bubble-failed-text
  (let [result (chat-view/message-bubble-render
                {:id "m3" :text "Oops" :from-me true :status :failed :cid "c1"}
                theme/light)]
    (is (text-present? result "Oops"))
    (is (text-present? result "! Failed"))
    (is (text-present? result "Resend"))))

(deftest test-message-bubble-align-sent
  (let [result (chat-view/message-bubble-render
                {:id "m1" :text "Hello" :from-me true :status :sent :cid "c1"}
                theme/light)]
    (is (= :flex-end (get-in result [2 :style :align-items])))))

(deftest test-message-bubble-align-received
  (let [result (chat-view/message-bubble-render
                {:id "m2" :text "Hi" :from-me false :status nil :cid "c1"}
                theme/light)]
    (is (= :flex-start (get-in result [2 :style :align-items])))))

(deftest test-chat-smoke
  (let [result (chat-view/chat)]
    (is (some? result))))

(deftest test-message-bubble-smoke
  (let [result (chat-view/message-bubble {:id "m1" :text "hi" :from-me true :status :sent :cid "c1"})]
    (is (some? result))))

(deftest test-message-input-smoke
  (let [result (chat-view/message-input "test-cid")]
    (is (some? result))))

(deftest test-chat-send-message
  (let [cid "test-contact"
        db-with-contact (-> app-db
                            (assoc-in [:messenger :contacts cid]
                                      {:address "201::5" :messages []})
                            (assoc-in [:yggstack :address] "201::1")
                            (assoc-in [:yggstack :private-key] "privkey")
                            (assoc-in [:yggstack :public-key] "pubkey"))]
    (reset! rdb/app-db db-with-contact)
    (rf/dispatch-sync [:messenger/send-message cid "Hello World"])
    (let [msg (first (get-in @rdb/app-db [:messenger :contacts cid :messages]))]
      (is (= "Hello World" (:text msg)))
      (is (true? (:from-me msg)))
      (is (= :sending (:status msg))))))
