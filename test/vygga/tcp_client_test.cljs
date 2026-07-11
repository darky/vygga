(ns vygga.tcp-client-test
  (:require
   [cljs.test :as t :refer-macros [deftest is]]
   [vygga.tcp-client :as tcp-client]))

;; ---- IPv6-to-bytes ----

(deftest test-ipv6-to-bytes-short
  (let [bytes (tcp-client/ipv6-to-bytes "201::1")]
    (is (= 16 (.-length bytes)))
    (is (= 0x02 (aget bytes 0)))
    (is (= 0x01 (aget bytes 1)))
    (is (= 0x00 (aget bytes 14)))
    (is (= 0x01 (aget bytes 15)))))

(deftest test-ipv6-to-bytes-full
  (let [bytes (tcp-client/ipv6-to-bytes "200:1234:abcd::1")]
    (is (= 16 (.-length bytes)))
    (is (= 0x02 (aget bytes 0)))
    (is (= 0x00 (aget bytes 1)))
    (is (= 0x12 (aget bytes 2)))
    (is (= 0x34 (aget bytes 3)))
    (is (= 0xab (aget bytes 4)))
    (is (= 0xcd (aget bytes 5)))
    (is (= 0x00 (aget bytes 14)))
    (is (= 0x01 (aget bytes 15)))))

(deftest test-ipv6-to-bytes-brackets
  (let [bytes (tcp-client/ipv6-to-bytes "[201::1]")]
    (is (= 16 (.-length bytes)))
    (is (= 0x02 (aget bytes 0)))
    (is (= 0x01 (aget bytes 1)))))

(deftest test-ipv6-to-bytes-all-groups
  (let [bytes (tcp-client/ipv6-to-bytes "2001:db8:85a3:0:0:8a2e:370:7334")]
    (is (= 16 (.-length bytes)))
    (is (= 0x20 (aget bytes 0)))
    (is (= 0x01 (aget bytes 1)))
    (is (= 0x0d (aget bytes 2)))
    (is (= 0xb8 (aget bytes 3)))
    (is (= 0x73 (aget bytes 14)))
    (is (= 0x34 (aget bytes 15)))))

;; ---- SOCKS5 send ----

(deftest test-send-message-via-socks
  (t/async done
    (-> (tcp-client/send-message! "201::1" "{:type \"message\" :text \"hi\"}")
        (.then (fn [result]
                 (is (true? result))
                 (done))
               (fn [err]
                 (is (false? (str "should not reject: " err)))
                 (done))))))
