(ns vygga.crypto-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [vygga.crypto :as crypto]))

(def test-key-b64 "9H1H9x7dQ5J8Q0X2W4V6Y8A0C2E4G6I8=")
(def test-plaintext "Hello, Vygga!")

(deftest test-hex-bytes-roundtrip
  (testing "hex->bytes and back"
    (let [hex "deadbeef"
          bytes (crypto/hex->bytes hex)
          result (apply str (map #(.toString % 16) (array-seq bytes)))]
      (is (= "deadbeef" result)))))

(deftest test-base64-roundtrip
  (testing "bytes->base64 and base64->bytes"
    (let [original (js/Uint8Array. [72 101 108 108 111])  ;; "Hello"
          b64 (crypto/bytes->base64 original)
          roundtripped (crypto/base64->bytes b64)]
      (is (= (.-length original) (.-length roundtripped)))
      (dotimes [i (.-length original)]
        (is (= (aget original i) (aget roundtripped i)))))))

(deftest test-encrypt-decrypt
  (testing "encrypt and decrypt roundtrip"
    (let [key (crypto/generate-encryption-key)
          encrypted (crypto/encrypt test-plaintext key)
          decrypted (crypto/decrypt encrypted key)]
      (is (some? encrypted))
      (is (= test-plaintext decrypted)))))

(deftest test-decrypt-wrong-key
  (testing "decrypt with wrong key returns nil"
    (let [key1 (crypto/generate-encryption-key)
          key2 (crypto/generate-encryption-key)
          encrypted (crypto/encrypt test-plaintext key1)]
      (is (nil? (crypto/decrypt encrypted key2))))))

(defn- byte->hex [b]
  (let [s (.toString b 16)]
    (if (= 1 (.-length s)) (str "0" s) s)))

(deftest test-sign-verify
  (testing "sign-message and verify-signature roundtrip"
    (let [seed (doto (js/Uint8Array. 32) (aset 0 99))
          kp' (.. js/tweetnacl -sign -keyPair (fromSeed seed))
          private-key-hex (apply str (map byte->hex (array-seq (.-secretKey kp'))))
          public-key-hex (apply str (map byte->hex (array-seq (.-publicKey kp'))))
          message "Hello from test"
          sig (crypto/sign-message private-key-hex message)]
      (is (string? sig))
      (is (crypto/verify-signature public-key-hex message sig))
      (is (not (crypto/verify-signature public-key-hex "tampered message" sig))))))

(deftest test-generate-encryption-key
  (testing "generate-encryption-key returns a string"
    (let [key (crypto/generate-encryption-key)]
      (is (string? key))
      (is (pos? (.-length key))))))
