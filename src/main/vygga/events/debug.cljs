(ns vygga.events.debug
  (:require [re-frame.core :as rf]))

(rf/reg-event-db
 :debug-log/add-entry
 (fn [db [_ entry]]
   (let [logs (get-in db [:debug :logs] [])
         new-logs (conj logs entry)]
     (if (> (count new-logs) 500)
       (assoc-in db [:debug :logs] (subvec new-logs 1))
       (assoc-in db [:debug :logs] new-logs)))))

(rf/reg-event-db
 :debug-log/clear
 (fn [db _]
   (assoc-in db [:debug :logs] [])))
