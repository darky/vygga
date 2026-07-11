(ns vygga.voip
  (:require [vygga.crypto :as crypto]
            ["expo-audio" :as audio]))

(defonce stream (atom nil))
(defonce audio-track-module
  (try (-> (js/require "react-native") .-NativeModules .-AudioTrackModule)
       (catch js/Error _ nil)))

(def ^:const sample-rate 16000)

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
                    (let [view (js/Uint8Array. (.-data buf))]
                      (on-chunk view))))
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

(defn play-pcm-buffer!
  [^js buf]
  (when audio-track-module
    (let [b64 (crypto/bytes->base64 buf)]
      (if @track-initialized?
        (-> (.write audio-track-module b64)
            (.catch (fn [e] (js/console.warn "audio track write error:" e))))
        (do
          (init-audio-track!)
          (-> (.write audio-track-module b64)
              (.catch (fn [e] (js/console.warn "audio track write error:" e)))))))))

(defn stop-audio-track!
  []
  (when audio-track-module
    (reset! track-initialized? false)
    (-> (.stop audio-track-module)
        (.catch (fn [e] (js/console.warn "audio track stop error:" e))))))
