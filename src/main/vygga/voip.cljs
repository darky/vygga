(ns vygga.voip
  (:require [vygga.crypto :as crypto]
            ["expo-audio" :as audio]))

(defonce stream (atom nil))
(defonce audio-track-module
  (try (-> (js/require "react-native") .-NativeModules .-AudioTrackModule)
       (catch js/Error _ nil)))

(def ^:const sample-rate 24000)
(def ^:const opus-app-voip 2048)

;; WASM Opus module
(defonce opus-module (atom nil))
(defonce opus-ready (atom false))

(defn init-opus!
  []
  (when-not @opus-ready
    (try
      (let [factory (js/require "./opus_enc")]
        (-> (factory)
            (.then (fn [^js mod]
                     (reset! opus-module mod)
                     (let [enc (._opus_enc_init mod sample-rate 1 opus-app-voip)
                           dec (._opus_dec_init mod sample-rate 1)]
                       (when (and (zero? enc) (zero? dec))
                         (reset! opus-ready true)
                         (js/console.log "Opus WASM codec initialized"))
                       (when-not (zero? enc)
                         (js/console.error "Opus WASM encoder init error:" enc))
                       (when-not (zero? dec)
                         (js/console.error "Opus WASM decoder init error:" dec))))
                   (fn [^js e]
                     (js/console.error "Opus WASM load error:" e)))))
      (catch js/Error e
        (js/console.error "Opus WASM module error:" e)))))

(defn encode-opus!
  [^js pcm-bytes]
  (when-let [^js mod @opus-module]
    (let [pcm-len (.-length pcm-bytes)
          frame-size (/ pcm-len 2)
          in-ptr (._malloc mod pcm-len)
          out-ptr (._malloc mod 4000)]
      (when (and in-ptr out-ptr)
        (-> mod .-HEAPU8 (.set pcm-bytes in-ptr))
        (let [result (._opus_enc_encode mod in-ptr frame-size out-ptr 4000)]
          (if (pos? result)
            (let [opus-data (-> mod .-HEAPU8 (.slice out-ptr (+ out-ptr result)))]
              (._free mod in-ptr)
              (._free mod out-ptr)
              opus-data)
            (do
              (._free mod in-ptr)
              (._free mod out-ptr)
              nil)))))))

(defn decode-opus!
  [^js opus-bytes]
  (when-let [^js mod @opus-module]
    (let [opus-len (.-length opus-bytes)
          max-pcm (-> sample-rate (* 2 60) (/ 1000) int)  ;; 60ms buffer
          in-ptr (._malloc mod opus-len)
          out-ptr (._malloc mod max-pcm)]
      (when (and in-ptr out-ptr)
        (-> mod .-HEAPU8 (.set opus-bytes in-ptr))
        (let [result (._opus_dec_decode mod in-ptr opus-len out-ptr max-pcm)]
          (if (pos? result)
            (let [pcm-data (-> mod .-HEAPU8 (.slice out-ptr (+ out-ptr result)))]
              (._free mod in-ptr)
              (._free mod out-ptr)
              pcm-data)
            (do
              (._free mod in-ptr)
              (._free mod out-ptr)
              nil)))))))

(defn request-permissions!
  []
  (-> (.requestRecordingPermissionsAsync audio)
      (.then (fn [^js result]
               (when-not (.-granted result)
                 (js/console.warn "Audio permission denied"))))))

(defn start-recording!
  [on-chunk]
  (let [am (.-AudioModule audio)
        s (new (.-AudioStream am) #js {:sampleRate sample-rate
                                       :channels 1
                                       :encoding "int16"})]
    (.addListener s "audioStreamBuffer"
                  (fn [^js buf]
                    (let [view (js/Uint8Array. (.-data buf))
                          opus-buf (encode-opus! view)]
                      (when opus-buf
                        (on-chunk opus-buf)))))
    (.then (.start s)
           (fn [] (reset! stream s))
           (fn [e] (js/console.error "start recording error:" e)))))

(defn stop-recording!
  []
  (when-let [s @stream]
    (try (.stop s) (catch js/Error _))
    (reset! stream nil)))

(defonce track-initialized? (atom false))

(defn init-audio-track!
  []
  (when (and audio-track-module (not @track-initialized?))
    (reset! track-initialized? true)
    (-> (.init audio-track-module sample-rate)
        (.catch (fn [e] (js/console.error "audio track init error:" e))))))

(defn init-codec!
  []
  (init-opus!))

(defn stop-codec!
  []
  (when-let [^js mod @opus-module]
    (._opus_destroy_all mod)
    (reset! opus-ready false)
    (reset! opus-module nil)))

(defn play-opus-buffer!
  [^js opus-buf]
  (when-let [pcm-buf (decode-opus! opus-buf)]
    (when audio-track-module
      (let [b64 (crypto/bytes->base64 pcm-buf)]
        (-> (.write audio-track-module b64)
            (.catch (fn [e] (js/console.warn "audio write error:" e))))))))

(defn create-call-channel []
  (when audio-track-module
    (-> (.createCallChannel audio-track-module)
        (.then #(js/console.log "call channel created"))
        (.catch (fn [e] (js/console.warn "call channel error:" e))))))

(defn stop-audio-track!
  []
  (when audio-track-module
    (reset! track-initialized? false)
    (-> (.stop audio-track-module)
        (.catch (fn [e] (js/console.warn "audio track stop error:" e))))))
