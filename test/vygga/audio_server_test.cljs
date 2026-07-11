(ns vygga.audio-server-test
  (:require
   [cljs.test :as t :refer-macros [deftest is use-fixtures]]
   [vygga.audio-server :as audio-server]))

(defn- make-frame [payload-len seq pcm-bytes]
  (let [buf (js/Uint8Array. (+ 8 (.-length pcm-bytes)))]
    (aset buf 0 (bit-shift-right payload-len 24))
    (aset buf 1 (bit-shift-right payload-len 16))
    (aset buf 2 (bit-shift-right payload-len 8))
    (aset buf 3 payload-len)
    (aset buf 4 (bit-shift-right seq 24))
    (aset buf 5 (bit-shift-right seq 16))
    (aset buf 6 (bit-shift-right seq 8))
    (aset buf 7 seq)
    (.set buf pcm-bytes 8)
    buf))

(defn- uint8-array= [^js a ^js b]
  (and (= (.-length a) (.-length b))
       (loop [i (dec (.-length a))]
         (or (neg? i)
             (and (= (aget a i) (aget b i))
                  (recur (dec i)))))))

(defn setup []
  (reset! vygga.audio-server/server-instance nil)
  (reset! vygga.audio-server/current-connection nil))

(use-fixtures :each {:before setup})

(deftest test-parse-buffer-single-frame
  (let [pcm (js/Uint8Array. #js [0x01 0x02 0x03 0x04])
        frame (make-frame 4 42 pcm)
        {:keys [frames remainder]} (audio-server/parse-buffer frame)]
    (is (= 1 (count frames)))
    (is (= 42 (:seq (first frames))))
    (is (uint8-array= pcm (:data (first frames))))
    (is (zero? (.-length remainder)))))

(deftest test-parse-buffer-multiple-frames
  (let [pcm1 (js/Uint8Array. #js [0x01])
        pcm2 (js/Uint8Array. #js [0x02 0x03])
        frame1 (make-frame 1 0 pcm1)
        frame2 (make-frame 2 1 pcm2)
        combined (js/Uint8Array. (+ (.-length frame1) (.-length frame2)))]
    (.set combined frame1 0)
    (.set combined frame2 (.-length frame1))
    (let [{:keys [frames remainder]} (audio-server/parse-buffer combined)]
      (is (= 2 (count frames)))
      (is (= 0 (:seq (first frames))))
      (is (uint8-array= pcm1 (:data (first frames))))
      (is (= 1 (:seq (second frames))))
      (is (uint8-array= pcm2 (:data (second frames))))
      (is (zero? (.-length remainder))))))

(deftest test-parse-buffer-partial-frame
  (let [pcm (js/Uint8Array. #js [0xAA 0xBB])
        frame (make-frame 2 5 pcm)
        partial (.slice frame 0 6)
        {:keys [frames remainder]} (audio-server/parse-buffer partial)]
    (is (zero? (count frames)))
    (is (= 6 (.-length remainder)))
    (is (= (aget partial 0) (aget remainder 0)))
    (is (= (aget partial 5) (aget remainder 5)))))

(deftest test-parse-buffer-empty
  (let [buf (js/Uint8Array. 0)
        result (audio-server/parse-buffer buf)]
    (is (zero? (count (:frames result))))
    (is (zero? (.-length (:remainder result))))))

(deftest test-parse-buffer-exact-frame
  (let [pcm (js/Uint8Array. #js [0x10 0x20 0x30])
        frame (make-frame 3 99 pcm)
        result (audio-server/parse-buffer frame)]
    (is (= 1 (count (:frames result))))
    (is (= 99 (:seq (first (:frames result)))))
    (is (uint8-array= pcm (:data (first (:frames result)))))
    (is (zero? (.-length (:remainder result))))))

(deftest test-parse-buffer-oversized-payload
  (let [pcm (js/Uint8Array. 65536)
        frame (make-frame 65536 0 pcm)
        result (audio-server/parse-buffer frame)]
    (is (= 1 (count (:frames result))))
    (is (= 65536 (.-length (:data (first (:frames result))))))))

(deftest test-audio-server-start-stop
  (t/async done
    (-> (audio-server/start! 7778)
        (.then (fn [result]
                 (is (true? result))
                 (is (true? (audio-server/running?)))
                 (-> (audio-server/stop!)
                     (.then (fn []
                              (is (false? (audio-server/running?)))
                              (done)))))))))

(deftest test-audio-server-start-twice-rejects
  (t/async done
    (-> (audio-server/start! 7778)
        (.then (fn []
                 (-> (audio-server/start! 7778)
                     (.then (fn [] (is false "should have rejected") (done))
                            (fn [err]
                              (is (some? err))
                              (-> (audio-server/stop!) (.then #(done)))))))))))
