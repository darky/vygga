(ns vygga.events.navigation
  (:require [re-frame.core :as rf]))

(rf/reg-event-db
 :navigation/set-root-state
 (fn [db [_ navigation-root-state]]
   (assoc-in db [:navigation :root-state] navigation-root-state)))
