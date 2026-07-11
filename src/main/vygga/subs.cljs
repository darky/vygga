(ns vygga.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub
 :navigation/root-state
 (fn [db _]
   (get-in db [:navigation :root-state])))

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
 :messenger/contacts
 (fn [db _]
   (get-in db [:messenger :contacts])))

(rf/reg-sub
 :messenger/current-contact
 (fn [db _]
   (get-in db [:messenger :current-contact])))

(rf/reg-sub
 :voip/call-state
 (fn [db _]
   (get-in db [:voip :call-state])))

(rf/reg-sub
 :voip/active-call
 (fn [db _]
   (let [v (:voip db)]
     (when (contains? #{:calling :ringing :connected} (:call-state v))
       {:call-state (:call-state v)
        :call-id (:call-id v)
        :remote-addr (:remote-addr v)
        :started-at (:started-at v)}))))

(rf/reg-sub
 :theme/preferred-scheme
 (fn [db _]
   (:preferred-scheme db)))

(rf/reg-sub
 :messenger/sorted-contacts
 (fn [db _]
   (->> (get-in db [:messenger :contacts])
        (sort-by (fn [[_ c]] (:address c)))
        (mapv (fn [[cid c]]
                [cid (assoc c
                            :last-message (last (:messages c))
                            :unread-count (or (:unread-count c) 0))])))))
