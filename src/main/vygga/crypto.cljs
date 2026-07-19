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

(defn random-hex
  [n]
  (let [bytes (nacl/randomBytes n)]
    (apply str (map (fn [b] (.slice (str "0" (.toString b 16)) -2)) (array-seq bytes)))))


