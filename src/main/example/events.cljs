(ns example.events
  (:require
   [re-frame.core :as rf]
   [example.yggstack :as ygg]
   [example.db :as db :refer [app-db]]))

(rf/reg-event-db
 :initialize-db
 (fn [_ _]
   app-db))

(rf/reg-event-db
 :inc-counter
 (fn [db [_ _]]
   (update db :counter inc)))

(rf/reg-event-db
 :navigation/set-root-state
 (fn [db [_ navigation-root-state]]
   (assoc-in db [:navigation :root-state] navigation-root-state)))

;; ---- Yggdrasil events ----

(rf/reg-event-fx
 :yggstack/start
 (fn [{db :db} _]
   (let [private-key (get-in db [:yggstack :private-key])
         peers (get-in db [:yggstack :peers])]
     (if private-key
       (let [config-json (ygg/build-config-json private-key peers)]
         {:db (assoc-in db [:yggstack :status] :starting)
          :yggstack/start-daemon {:config-json config-json
                                  :socks-address "127.0.0.1:1080"
                                  :nameserver ""}})
       {:db (assoc-in db [:yggstack :status] :starting)
        :yggstack/generate-key nil}))))

(rf/reg-fx
 :yggstack/generate-key
 (fn [_]
   (-> (ygg/generate-config)
       (.then (fn [config-json]
                (let [key (ygg/extract-private-key config-json)]
                  (rf/dispatch-sync [:yggstack/set-private-key key])
                  (rf/dispatch [:yggstack/start]))))
       (.catch (fn [e]
                 (js/console.error "keygen error:" e)
                 (rf/dispatch [:yggstack/set-status :error]))))))

(rf/reg-fx
 :yggstack/start-daemon
 (fn [{:keys [config-json socks-address nameserver]}]
   (ygg/start-polling)
   (-> (ygg/start config-json socks-address nameserver)
       (.then (fn [_] (rf/dispatch [:yggstack/set-status :running])))
       (.catch (fn [e]
                 (js/console.error "start error:" e)
                 (rf/dispatch [:yggstack/set-status :error]))))))

(rf/reg-event-db
 :yggstack/set-status
 (fn [db [_ status]]
   (assoc-in db [:yggstack :status] status)))

(rf/reg-event-fx
 :yggstack/stop
 (fn [{db :db} _]
   {:db (assoc-in db [:yggstack :status] :stopping)
    :yggstack/stop-daemon nil}))

(rf/reg-fx
 :yggstack/stop-daemon
 (fn [_]
   (ygg/stop-polling)
   (-> (ygg/stop)
       (.then (fn [_] (rf/dispatch [:yggstack/set-status :stopped])))
       (.catch (fn [e] (js/console.error "stop error:" e))))))

(rf/reg-event-db
 :yggstack/update-peer-count
 (fn [db [_ count]]
   (assoc-in db [:yggstack :peer-count] count)))

(rf/reg-event-db
 :yggstack/update-address
 (fn [db [_ addr]]
   (assoc-in db [:yggstack :address] addr)))

(rf/reg-event-db
 :yggstack/update-public-key
 (fn [db [_ pk]]
   (assoc-in db [:yggstack :public-key] pk)))

(rf/reg-event-db
 :yggstack/set-private-key
 (fn [db [_ key]]
   (assoc-in db [:yggstack :private-key] key)))

(rf/reg-event-db
 :yggstack/add-peer
 (fn [db [_ uri]]
   (update-in db [:yggstack :peers]
     (fn [peers]
       (if (some #(= % uri) peers) peers (conj peers uri))))))

(rf/reg-event-db
 :yggstack/remove-peer
 (fn [db [_ uri]]
   (update-in db [:yggstack :peers]
     (fn [peers] (vec (remove #(= % uri) peers))))))

(rf/reg-event-db
 :yggstack/set-peers
 (fn [db [_ peers]]
   (assoc-in db [:yggstack :peers] (vec peers))))
