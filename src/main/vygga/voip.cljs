(ns vygga.voip
  (:require ["expo-audio" :as audio]))

(defonce stream (atom nil))
(defonce audio-track-module
  (try (-> (js/require "react-native") .-NativeModules .-AudioTrackModule)
       (catch js/Error _ nil)))

(def ^:const sample-rate 24000)

(defn request-permissions!
  []
  (-> (.requestRecordingPermissionsAsync audio)
      (.then (fn [^js result]
               (when-not (.-granted result)
                 (js/console.warn "Audio permission denied"))))))

(defn start-recording-udp!
  [on-raw-pcm]
  (let [am (.-AudioModule audio)
        s (new (.-AudioStream am) #js {:sampleRate sample-rate
                                       :channels 1
                                       :encoding "int16"})]
    (.addListener s "audioStreamBuffer"
                  (fn [^js buf]
                    (let [view (js/Uint8Array. (.-data buf))]
                      (if audio-track-module
                        (on-raw-pcm view)
                        (js/console.warn "no audio-track-module for encode")))))
    (.then (.start s)
           (fn [] (reset! stream s))
           (fn [e] (js/console.error "start recording error:" e)))))

(defn stop-recording!
  []
  (when-let [s @stream]
    (try (.stop s) (catch js/Error _))
    (reset! stream nil)))

(defn create-call-channel []
  (when audio-track-module
    (-> (.createCallChannel audio-track-module)
        (.then #(js/console.log "call channel created"))
        (.catch (fn [e] (js/console.warn "call channel error:" e))))))
