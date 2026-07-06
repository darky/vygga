(ns vygga.persist-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [vygga.persist :as persist]))

(def cid "contact-123")
(def msg-id "msg-456")

(deftest test-msg-key
  (testing "msg-key constructs correct storage key"
    (let [result (persist/msg-key cid msg-id)]
      (is (= "msg_contact-123_msg-456" result)))))

(deftest test-idx-key
  (testing "idx-key constructs correct index key"
    (let [result (persist/idx-key cid "_curr")]
      (is (= "msg_idx_contact-123__curr" result)))))

(deftest test-manifest-key
  (testing "manifest-key constructs correct manifest key"
    (let [result (persist/manifest-key cid)]
      (is (= "msg_idx_contact-123_manifest" result)))))
