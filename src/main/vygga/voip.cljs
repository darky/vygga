(ns vygga.voip
  (:require ["expo-audio" :as audio]))

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

(defn create-call-channel []
  (when audio-track-module
    (-> (.createCallChannel audio-track-module)
        (.then #(js/console.log "call channel created"))
        (.catch (fn [e] (js/console.warn "call channel error:" e))))))
