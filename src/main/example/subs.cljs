(ns example.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub
 :get-counter
 (fn [db _]
   (:counter db)))

(rf/reg-sub
 :counter-tappable?
 (fn [db _]
   (:counter-tappable? db)))

(rf/reg-sub
 :navigation/root-state
 (fn [db _]
   (get-in db [:navigation :root-state])))

;; ---- Yggdrasil subscriptions ----

(rf/reg-sub
 :yggstack/state
 (fn [db _]
   (get db :yggstack)))

(rf/reg-sub
 :yggstack/status
 (fn [db _]
   (get-in db [:yggstack :status])))

(rf/reg-sub
 :yggstack/peer-count
 (fn [db _]
   (get-in db [:yggstack :peer-count])))

(rf/reg-sub
 :yggstack/peers
 (fn [db _]
   (get-in db [:yggstack :peers])))

(rf/reg-sub
 :yggstack/address
 (fn [db _]
   (get-in db [:yggstack :address])))

(rf/reg-sub
 :yggstack/public-key
 (fn [db _]
   (get-in db [:yggstack :public-key])))
