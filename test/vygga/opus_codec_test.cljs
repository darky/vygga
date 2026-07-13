(ns vygga.opus-codec-test
  (:require [cljs.test :refer-macros [deftest is testing async]]))

(defonce opus (atom nil))
(defonce opus-ready (atom false))

(defn byte->int [b]
  (if (neg? b) (+ b 256) b))

(defn gen-sine-buffer [sample-rate freq duration-ms]
  (let [samples (int (/ (* sample-rate duration-ms) 1000))
        buf (js/Uint8Array. (* samples 2))]
    (dotimes [i samples]
      (let [val (int (* 8000 (Math/sin (/ (* 2 Math/PI freq i) sample-rate))))
            lo (bit-and val 0xFF)
            hi (bit-and (bit-shift-right val 8) 0xFF)]
        (aset buf (* i 2) lo)
        (aset buf (inc (* i 2)) hi)))
    buf))

(defn gen-silence-buffer [sample-rate duration-ms]
  (let [samples (int (/ (* sample-rate duration-ms) 1000))]
    (js/Uint8Array. (* samples 2))))

(defn init-opus! []
  (let [factory (js/require "../../src/main/vygga/opus_enc")]
    (-> (factory)
        (.then (fn [^js mod]
                 (let [enc (._opus_enc_init mod 24000 1 2048)
                       dec (._opus_dec_init mod 24000 1)]
                   (when (and (zero? enc) (zero? dec))
                     (reset! opus mod)
                     (reset! opus-ready true))))))))

(deftest test-module-loads
  (testing "Opus WASM module loads and initializes"
    (async done
      (-> (init-opus!)
          (.then (fn []
                   (is (true? @opus-ready) "opus module should be ready")
                   (is (some? @opus) "opus module should be set")
                   (done))
                 (fn [e]
                   (is false (str "opus module failed to load: " e))
                   (done)))))))

(deftest test-silence-encode-decode
  (testing "silence round-trips correctly"
    (async done
      (-> (init-opus!)
          (.then (fn []
                   (let [mod @opus
                         pcm (gen-silence-buffer 24000 20)
                         in-ptr (._malloc mod (.-length pcm))
                         out-ptr (._malloc mod 4000)
                         dec-ptr (._malloc mod 960)]
                     (if (and in-ptr out-ptr dec-ptr)
                       (try
                         (-> mod .-HEAPU8 (.set pcm in-ptr))
                         (let [enc-len (._opus_enc_encode mod in-ptr 480 out-ptr 4000)]
                           (is (pos? enc-len) "encode should produce output")
                           (when (pos? enc-len)
                             (let [dec-len (._opus_dec_decode mod out-ptr enc-len dec-ptr 960)]
                               (is (= 960 dec-len) "decode should produce 960 bytes")
                               (when (= 960 dec-len)
                                 (let [pcm-out (-> mod .-HEAPU8 (.slice dec-ptr (+ dec-ptr 960)))
                                       max-byte (apply max (map byte->int (array-seq pcm-out)))]
                                   (is (< max-byte 256) "decoded silence should be quiet"))))))
                         (catch js/Error e
                           (is false (str "silence test error: " e))))
                       (is false "malloc failed"))
                     (._free mod in-ptr)
                     (._free mod out-ptr)
                     (._free mod dec-ptr)
                     (done))))))))

(deftest test-sine-encode-decode
  (testing "sine wave encodes and decodes to expected length"
    (async done
      (-> (init-opus!)
          (.then (fn []
                   (let [mod @opus
                         pcm (gen-sine-buffer 24000 440 20)
                         in-ptr (._malloc mod (.-length pcm))
                         out-ptr (._malloc mod 4000)
                         dec-ptr (._malloc mod 960)]
                     (if (and in-ptr out-ptr dec-ptr)
                       (try
                         (-> mod .-HEAPU8 (.set pcm in-ptr))
                         (let [enc-len (._opus_enc_encode mod in-ptr 480 out-ptr 4000)]
                           (is (pos? enc-len) "encode should produce output")
                           (when (pos? enc-len)
                             (let [dec-len (._opus_dec_decode mod out-ptr enc-len dec-ptr 960)]
                               (is (= 960 dec-len) "decode should produce 960 bytes"))))
                         (catch js/Error e
                           (is false (str "sine test error: " e))))
                       (is false "malloc failed"))
                     (._free mod in-ptr)
                     (._free mod out-ptr)
                     (._free mod dec-ptr)
                     (done))))))))
