(ns vygga.events.powermanager
  (:require [re-frame.core :as rf]
            [vygga.powermanager :as pm]))

(rf/reg-fx
 :powermanager/acquire
 (fn [_]
   (pm/acquire-all)))

(rf/reg-fx
 :powermanager/release
 (fn [_]
   (pm/release-all)))
