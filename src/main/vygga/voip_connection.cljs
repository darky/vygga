(ns vygga.voip-connection
  (:require [clojure.string :as str]
            ["react-native-tcp-socket" :as tcp]))

(defonce ^:const socks-port 1080)
(defonce ^:const audio-port 7778)

(defonce connection (atom nil))

(defn ipv6-to-bytes
  [ip-str]
  (let [s (str/replace ip-str #"[\[\]]" "")
        parts (str/split s #":")
        dc (.indexOf parts "")]
    (if (>= dc 0)
      (let [before (take dc parts)
            after (drop (inc dc) parts)
            missing (- 8 (count before) (count after))
            expanded (vec (concat before (repeat missing "0") after))
            result (js/Uint8Array. 16)]
        (doseq [[i part] (map-indexed vector expanded)]
          (let [val (js/parseInt part 16)]
            (aset result (* i 2) (bit-shift-right val 8))
            (aset result (inc (* i 2)) (bit-and val 0xFF))))
        result)
      (let [result (js/Uint8Array. 16)]
        (doseq [[i part] (map-indexed vector parts)]
          (let [val (js/parseInt part 16)]
            (aset result (* i 2) (bit-shift-right val 8))
            (aset result (inc (* i 2)) (bit-and val 0xFF))))
        result))))

(defn socks5-connect!
  [^js socket target-ip]
  (js/Promise.
   (fn [resolve reject]
     (let [rx-buf (atom #js [])
           step (atom 1)]
       (.on socket "data"
            (fn [data]
              (let [buf @rx-buf
                    new-buf (js/Uint8Array. (+ (.-length buf) (.-length data)))]
                (.set new-buf buf 0)
                (.set new-buf data (.-length buf))
                (reset! rx-buf new-buf))
              (case @step
                1
                (when (>= (.-length @rx-buf) 2)
                  (if (and (= (aget @rx-buf 0) 0x05) (= (aget @rx-buf 1) 0x00))
                    (do
                      (reset! step 2)
                      (reset! rx-buf #js [])
                      (let [ip (str/replace target-ip #"[\[\]]" "")
                            addr-bytes (ipv6-to-bytes ip)
                            alen (.-length addr-bytes)
                            req (js/Uint8Array. (+ 6 alen))
                            port audio-port]
                        (aset req 0 0x05)
                        (aset req 1 0x01)
                        (aset req 2 0x00)
                        (aset req 3 0x04)
                        (.set req addr-bytes 4)
                        (aset req (+ 4 alen) (bit-shift-right port 8))
                        (aset req (+ 5 alen) (bit-and port 0xFF))
                        (.write socket req)))
                    (reject (js/Error. "SOCKS5 handshake failed"))))
                2
                (when (>= (.-length @rx-buf) 2)
                  (if (and (= (aget @rx-buf 0) 0x05) (= (aget @rx-buf 1) 0x00))
                    (do
                      (reset! step 3)
                      (.removeAllListeners socket "data")
                      (resolve socket))
                    (reject (js/Error. (str "SOCKS5 connect failed, status: "
                                            (aget @rx-buf 1))))))
                nil)))
       (.write socket (js/Uint8Array. #js [0x05 0x01 0x00]))))))

(defn connect!
  [target-ip on-data on-close]
  (js/Promise.
   (fn [resolve reject]
     (let [socket (.createConnection tcp #js {:host "127.0.0.1" :port socks-port})]
       (.on socket "error" (fn [e] (reject e)))
       (.on socket "close" (fn [had-error] (on-close had-error)))
       (.on socket "connect"
            (fn []
              (.then (socks5-connect! socket target-ip)
                     (fn [^js connected-socket]
                       (.on connected-socket "data"
                            (fn [data]
                              (on-data data)))
                       (reset! connection {:socket connected-socket
                                           :target-ip target-ip})
                       (resolve true))
                     (fn [e]
                       (.destroy socket)
                       (reject e)))))))))

(defn send-audio-frame!
  [^js pcm-bytes seq]
  (when-let [c @connection]
    (let [socket (:socket c)
          pcm-len (.-length pcm-bytes)
          header (js/Uint8Array. 8)]
      (aset header 0 (bit-shift-right pcm-len 24))
      (aset header 1 (bit-shift-right pcm-len 16))
      (aset header 2 (bit-shift-right pcm-len 8))
      (aset header 3 pcm-len)
      (aset header 4 (bit-shift-right seq 24))
      (aset header 5 (bit-shift-right seq 16))
      (aset header 6 (bit-shift-right seq 8))
      (aset header 7 seq)
      (let [frame (js/Uint8Array. (+ 8 pcm-len))]
        (.set frame header 0)
        (.set frame pcm-bytes 8)
        (try
          (.write socket frame)
          (catch js/Error e
            (js/console.warn "voip send error:" e)))))))

(defn disconnect!
  []
  (when-let [c @connection]
    (let [^js socket (:socket c)]
      (try (.destroy socket) (catch js/Error _)))
    (reset! connection nil)))


