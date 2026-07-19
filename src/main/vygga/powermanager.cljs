(ns vygga.powermanager
  (:require ["react-native" :as rn]))

(defonce cpu-module
  (try (-> rn .-NativeModules .-CpuLockModule)
       (catch js/Error _ nil)))

(defonce wifi-module
  (try (-> rn .-NativeModules .-WifiLockModule)
       (catch js/Error _ nil)))

(defn acquire-cpu-lock []
  (when cpu-module
    (-> (.acquire cpu-module "Vygga::CpuLock")
        (.catch (fn [e] (js/console.warn "cpu-lock acquire error:" e))))))

(defn release-cpu-lock []
  (when cpu-module
    (-> (.release cpu-module)
        (.catch (fn [e] (js/console.warn "cpu-lock release error:" e))))))

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
  (acquire-cpu-lock)
  (acquire-wifi-lock)
  (request-mobile-network))

(defn release-all []
  (release-cpu-lock)
  (release-wifi-lock)
  (release-mobile-network))
