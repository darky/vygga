(ns vygga.voip
  (:require ["expo-audio" :as audio]
            [re-frame.core :as rf]))

(defonce audio-track-module
  (try (-> (js/require "react-native") .-NativeModules .-AudioTrackModule)
       (catch js/Error _ nil)))

(defonce overlay-module
  (try (-> (js/require "react-native") .-NativeModules .-FloatingCallOverlay)
       (catch js/Error _ nil)))

(defonce listeners-registered (atom false))

(defn ensure-listeners!
  []
  (when (and overlay-module (not @listeners-registered))
    (let [emitter (.-DeviceEventEmitter (js/require "react-native"))]
      (.addListener emitter "onOverlayAccept"
                    (fn [_] (rf/dispatch [:voip/accept-call])))
      (.addListener emitter "onOverlayReject"
                    (fn [_] (rf/dispatch [:voip/reject-call])))
      (.addListener emitter "onOverlayEnd"
                    (fn [_] (rf/dispatch [:voip/end-call])))
      (reset! listeners-registered true))))

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

(defn show-overlay!
  [mode addr call-id]
  (when overlay-module
    (ensure-listeners!)
    (let [p (if (= :incoming mode)
              (.showIncomingOverlay overlay-module addr (or call-id ""))
              (.showActiveOverlay overlay-module addr))]
      (-> p
          (.catch (fn [e] (js/console.warn "overlay show error:" e)))))))

(defn hide-overlay!
  []
  (when overlay-module
    (-> (.hideOverlay overlay-module)
        (.catch (fn [e] (js/console.warn "overlay hide error:" e))))))
