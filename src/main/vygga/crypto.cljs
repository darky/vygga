(ns vygga.crypto
  (:require ["tweetnacl" :as nacl]))

(defn hex->bytes
  [hex-str]
  (let [len (quot (.-length hex-str) 2)
        bytes (js/Uint8Array. len)]
    (dotimes [i len]
      (aset bytes i (js/parseInt (subs hex-str (* i 2) (+ (* i 2) 2)) 16)))
    bytes))

(defn bytes->base64
  [bytes]
  (let [chars (array)]
    (doseq [i (range (.-length bytes))]
      (.push chars (js/String.fromCharCode (aget bytes i))))
    (js/btoa (.join chars ""))))

(defn base64->bytes
  [b64]
  (let [binary (js/atob b64)
        len (.-length binary)
        bytes (js/Uint8Array. len)]
    (dotimes [i len]
      (aset bytes i (.charCodeAt binary i)))
    bytes))

(defn sign-message
  [private-key-hex message]
  (let [private-key (hex->bytes private-key-hex)
        message-bytes (.encode (js/TextEncoder.) message)
        sign-detached (.-detached (.-sign nacl))
        signature (sign-detached message-bytes private-key)]
    (bytes->base64 signature)))

(defn verify-signature
  [public-key-hex message signature-b64]
  (let [public-key (hex->bytes public-key-hex)
        message-bytes (.encode (js/TextEncoder.) message)
        signature (base64->bytes signature-b64)
        verify (.-verify (.-detached (.-sign nacl)))]
    (verify message-bytes signature public-key)))

(defn generate-encryption-key
  []
  (bytes->base64 (nacl.randomBytes nacl.secretbox.keyLength)))

(defn encrypt
  [plaintext key-b64]
  (let [key (base64->bytes key-b64)
        nonce (nacl.randomBytes nacl.secretbox.nonceLength)
        plain-bytes (.encode (js/TextEncoder.) plaintext)
        secretbox (.-secretbox nacl)
        ciphertext (secretbox plain-bytes nonce key)
        combined (js/Uint8Array. (+ (.-length nonce) (.-length ciphertext)))]
    (.set combined nonce 0)
    (.set combined ciphertext (.-length nonce))
    (bytes->base64 combined)))

(defn decrypt
  [combined-b64 key-b64]
  (let [key (base64->bytes key-b64)
        combined (base64->bytes combined-b64)
        nonce (.slice combined 0 nacl.secretbox.nonceLength)
        ciphertext (.slice combined nacl.secretbox.nonceLength)
        secretbox-open (.-open (.-secretbox nacl))
        plain-bytes (secretbox-open ciphertext nonce key)]
    (when plain-bytes
      (.decode (js/TextDecoder.) plain-bytes))))
