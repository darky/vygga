(ns vygga.events.theme
  (:require [re-frame.core :as rf]))

(rf/reg-event-db
 :theme/set-scheme
 (fn [db [_ scheme]]
   (assoc db :preferred-scheme scheme)))
