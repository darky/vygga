(ns vygga.voip-connection-test
  (:require
   [cljs.test :as t :refer-macros [deftest is use-fixtures]]
   [vygga.voip-connection :as voip-conn]
   ["react-native-tcp-socket" :as tcp]))

(defn setup []
  (when-let [c @voip-conn/connection]
    (try (.destroy (:socket c)) (catch js/Error _)))
  (reset! voip-conn/connection nil))

(use-fixtures :each {:before setup})

(defn- make-uint8-array [& bytes]
  (js/Uint8Array. (clj->js bytes)))

(deftest test-send-audio-frame-binary-format
  (t/async done
    (let [written (atom nil)
          socket (tcp/createConnection #js {})
          pcm (make-uint8-array 0x01 0x02 0x03 0x04)]
      (.on socket "_write"
           (fn [buf]
             (reset! written buf)
             ;; verify payload length (BE uint32) = 4
             (is (= 0 (aget buf 0)))
             (is (= 0 (aget buf 1)))
             (is (= 0 (aget buf 2)))
             (is (= 4 (aget buf 3)))
             ;; verify seq (BE uint32) = 42
             (is (= 0 (aget buf 4)))
             (is (= 0 (aget buf 5)))
             (is (= 0 (aget buf 6)))
             (is (= 42 (aget buf 7)))
             ;; verify PCM payload
             (is (= 0x01 (aget buf 8)))
             (is (= 0x04 (aget buf 11)))
             (done)))
      (reset! voip-conn/connection {:socket socket :target-ip "201::1"})
      (voip-conn/send-audio-frame! pcm 42))))

(deftest test-send-audio-frame-empty-payload
  (t/async done
    (let [written (atom nil)
          socket (tcp/createConnection #js {})
          pcm (js/Uint8Array. 0)]
      (.on socket "_write"
           (fn [buf]
             (reset! written buf)
             (is (= 8 (.-length buf)))
             (is (= 0 (aget buf 3)))
             (done)))
      (reset! voip-conn/connection {:socket socket :target-ip "201::1"})
      (voip-conn/send-audio-frame! pcm 0))))

(deftest test-send-audio-frame-no-connection
  (let [pcm (make-uint8-array 0x01 0x02)
        result (voip-conn/send-audio-frame! pcm 0)]
    (is (nil? result))))

(deftest test-send-audio-frame-large-payload
  (t/async done
    (let [socket (tcp/createConnection #js {})
          pcm (js/Uint8Array. 65535)]
      (.on socket "_write"
           (fn [buf]
             (is (= (+ 8 65535) (.-length buf)))
             (is (= 0xFF (aget buf 3)))
             (done)))
      (reset! voip-conn/connection {:socket socket :target-ip "201::1"})
      (voip-conn/send-audio-frame! pcm 0))))

(deftest test-ipv6-to-bytes
  (let [bytes (voip-conn/ipv6-to-bytes "201::1")]
    (is (= 16 (.-length bytes)))
    (is (= 0x02 (aget bytes 0)))
    (is (= 0x01 (aget bytes 1)))
    (is (= 0x00 (aget bytes 14)))
    (is (= 0x01 (aget bytes 15)))))

(deftest test-ipv6-to-bytes-full
  (let [bytes (voip-conn/ipv6-to-bytes "2001:db8:85a3::8a2e:370:7334")]
    (is (= 16 (.-length bytes)))
    (is (= 0x20 (aget bytes 0)))
    (is (= 0x01 (aget bytes 1)))
    (is (= 0x73 (aget bytes 14)))
    (is (= 0x34 (aget bytes 15)))))

(deftest test-ipv6-to-bytes-brackets
  (let [bytes (voip-conn/ipv6-to-bytes "[201::1]")]
    (is (= 16 (.-length bytes)))
    (is (= 0x02 (aget bytes 0)))
    (is (= 0x01 (aget bytes 1)))))

(deftest test-ipv6-to-bytes-all-groups
  (let [bytes (voip-conn/ipv6-to-bytes "2001:db8:85a3:0:0:8a2e:370:7334")]
    (is (= 16 (.-length bytes)))
    (is (= 0x20 (aget bytes 0)))
    (is (= 0x01 (aget bytes 1)))))
