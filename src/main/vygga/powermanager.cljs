(ns vygga.powermanager
  (:require ["react-native" :as rn]))

(defonce keep-awake-module
  (try (js/require "expo-keep-awake")
       (catch js/Error _ nil)))

(defonce wifi-module
  (try (-> rn .-NativeModules .-WifiLockModule)
       (catch js/Error _ nil)))

(defn activate-wake-lock []
  (when keep-awake-module
    (try (-> (.-activateKeepAwakeAsync keep-awake-module)
             (.catch (fn [e] (js/console.warn "keep-awake error:" e))))
         (catch js/Error e (js/console.warn "keep-awake error:" e)))))

(defn deactivate-wake-lock []
  (when keep-awake-module
    (try (-> (.-deactivateKeepAwake keep-awake-module)
             (.catch (fn [e] (js/console.warn "keep-awake error:" e))))
         (catch js/Error e (js/console.warn "keep-awake error:" e)))))

(defn acquire-wifi-lock []
  (when wifi-module
    (-> (.acquire wifi-module "Vygga::WifiLock")
        (.catch (fn [e] (js/console.warn "wifi-lock acquire error:" e))))))

(defn release-wifi-lock []
  (when wifi-module
    (-> (.release wifi-module)
        (.catch (fn [e] (js/console.warn "wifi-lock release error:" e))))))

(defn request-mobile-network []
  (when wifi-module
    (-> (.requestMobileNetwork wifi-module)
        (.catch (fn [e] (js/console.warn "mobile network request error:" e))))))

(defn release-mobile-network []
  (when wifi-module
    (-> (.releaseMobileNetwork wifi-module)
        (.catch (fn [e] (js/console.warn "mobile network release error:" e))))))

(defn acquire-all []
  (activate-wake-lock)
  (acquire-wifi-lock)
  (request-mobile-network))

(defn release-all []
  (deactivate-wake-lock)
  (release-wifi-lock)
  (release-mobile-network))
