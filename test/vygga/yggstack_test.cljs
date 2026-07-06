(ns vygga.yggstack-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [vygga.yggstack :as ygg]))

(def sample-private-key "abc123")
(def sample-peers ["tls://peer1:443" "tls://peer2:1337"])

(deftest test-extract-private-key
  (testing "extracts PrivateKey from valid JSON"
    (let [config (str "{\"PrivateKey\": \"" sample-private-key "\"}")
          key (ygg/extract-private-key config)]
      (is (= sample-private-key key))))
  (testing "returns nil for invalid JSON"
    (is (nil? (ygg/extract-private-key "not json")))
    (is (nil? (ygg/extract-private-key nil))))
  (testing "returns nil when PrivateKey is missing"
    (is (nil? (ygg/extract-private-key "{\"foo\": \"bar\"}")))))

(deftest test-build-config-json
  (testing "generates parseable JSON with correct PrivateKey"
    (let [config (ygg/build-config-json sample-private-key sample-peers)
          parsed (js/JSON.parse config)]
      (is (= sample-private-key (.-PrivateKey parsed)))
      (is (nil? (.-Certificate parsed)))
      (is (= "none" (.-AdminListen parsed)))))
  (testing "includes peers as JSON array"
    (let [config (ygg/build-config-json sample-private-key sample-peers)
          parsed (js/JSON.parse config)
          peers (.-Peers parsed)]
      (is (= 2 (.-length peers)))
      (is (= "tls://peer1:443" (aget peers 0)))
      (is (= "tls://peer2:1337" (aget peers 1)))))
  (testing "works with empty peers"
    (let [config (ygg/build-config-json sample-private-key [])
          parsed (js/JSON.parse config)
          peers (.-Peers parsed)]
      (is (= 0 (.-length peers)))))
  (testing "listens on TCP any"
    (let [config (ygg/build-config-json sample-private-key sample-peers)
          parsed (js/JSON.parse config)
          listen (.-Listen parsed)]
      (is (= 1 (.-length listen)))
      (is (= "tcp://[::]:0" (aget listen 0))))))
