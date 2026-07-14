(ns vygga.logging
  (:require [re-frame.core :as rf]))

(defonce ^:private original-log (atom nil))
(defonce ^:private original-warn (atom nil))
(defonce ^:private original-error (atom nil))
(defonce ^:private original-info (atom nil))

(defn- add-entry [level args]
  (let [msg (apply str (interpose " " (mapv str args)))]
    (rf/dispatch [:debug-log/add-entry {:ts (.now js/Date)
                                        :level level
                                        :msg msg}])))

(defn patch-console! []
  (reset! original-log js/console.log)
  (reset! original-warn js/console.warn)
  (reset! original-error js/console.error)
  (reset! original-info js/console.info)
  (set! js/console.log (fn [& args]
                         (add-entry :log args)
                         (apply @original-log args)))
  (set! js/console.warn (fn [& args]
                          (add-entry :warn args)
                          (apply @original-warn args)))
  (set! js/console.error (fn [& args]
                           (add-entry :error args)
                           (apply @original-error args)))
  (set! js/console.info (fn [& args]
                          (add-entry :info args)
                          (apply @original-info args))))
