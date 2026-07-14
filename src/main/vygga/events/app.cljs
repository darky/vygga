(ns vygga.events.app
  (:require [re-frame.core :as rf]
            [vygga.db :refer [app-db]]
            [vygga.notifications :as notif]
            [vygga.yggstack :as ygg]))

(rf/reg-event-fx
 :initialize-db
 (fn [_ _]
   (notif/init!)
   {:db app-db
    :yggstack/load-and-start nil
    :messenger/load-contacts nil}))

(rf/reg-event-fx
 :app/exit
 (fn [_ _]
   {:app/exit-fx nil}))

(rf/reg-fx
 :app/exit-fx
 (fn [_]
   (ygg/exit-app)))
