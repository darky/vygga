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

(defn start-recording!
  [on-chunk]
  (let [am (.-AudioModule audio)
        s (new (.-AudioStream am) #js {:sampleRate sample-rate
                                       :channels 1
                                       :encoding "int16"})]
    (.addListener s "audioStreamBuffer"
                  (fn [^js buf]
                    (let [view (js/Uint8Array. (.-data buf))]
                      (if audio-track-module
                        (-> (.encode audio-track-module (js/Array.from view))
                            (.then (fn [^js opus-arr]
                                     (on-chunk (js/Uint8Array. opus-arr))))
                            (.catch (fn [e]
                                      (js/console.warn "opus encode error:" e))))
                        (js/console.warn "no audio-track-module for encode")))))
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
  (when audio-track-module
    (-> (.initCodec audio-track-module sample-rate 24000)
        (.catch (fn [e] (js/console.error "codec init error:" e))))))

(defn stop-codec!
  []
  (when audio-track-module
    (-> (.stopCodec audio-track-module)
        (.catch (fn [e] (js/console.warn "codec stop error:" e))))))

(defn play-opus-buffer!
  [^js opus-buf]
  (when audio-track-module
    (let [arr (js/Array.from opus-buf)]
      (-> (.decodeAndPlay audio-track-module arr)
          (.catch (fn [e] (js/console.warn "opus decode/play error:" e)))))))

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
