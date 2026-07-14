(ns vygga.events.yggstack
  (:require [re-frame.core :as rf]
            [vygga.yggstack :as ygg]
            [vygga.storage :as storage]))

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
                  (storage/save-key! key)
                  (rf/dispatch-sync [:yggstack/set-private-key key])
                  (rf/dispatch [:yggstack/start]))))
       (.catch (fn [e]
                 (js/console.error "keygen error:" e)
                 (rf/dispatch [:yggstack/set-status :error]))))))

(rf/reg-fx
 :yggstack/load-and-start
 (fn [_]
   (-> (storage/load-key)
       (.then (fn [key]
                (when key
                  (rf/dispatch-sync [:yggstack/set-private-key key]))
                (rf/dispatch [:yggstack/start])))
       (.catch (fn [e]
                 (js/console.warn "yggstack load key error:" e)
                 (rf/dispatch [:yggstack/start]))))))

(rf/reg-event-fx
 :yggstack/generate-new-identity
 (fn [{db :db} _]
   {:db (assoc-in db [:yggstack :private-key] nil)
    :yggstack/regenerate-identity nil}))

(rf/reg-fx
 :yggstack/regenerate-identity
 (fn [_]
   (-> (ygg/stop)
       (.catch (fn [_]))
       (.then storage/clear-key!)
       (.then ygg/generate-config)
       (.then (fn [config-json]
                (let [key (ygg/extract-private-key config-json)]
                  (storage/save-key! key)
                  (rf/dispatch-sync [:yggstack/set-private-key key])
                  (rf/dispatch [:yggstack/set-status :stopped])
                  (js/setTimeout #(rf/dispatch [:yggstack/start]) 300))))
       (.catch (fn [e]
                 (js/console.error "regenerate error:" e)
                 (rf/dispatch [:yggstack/set-status :error]))))))

(rf/reg-fx
 :yggstack/start-daemon
 (fn [{:keys [config-json socks-address nameserver]}]
   (-> (ygg/start config-json socks-address nameserver)
       (.then (fn [_]
                (js/console.log "Yggdrasil connected")
                (rf/dispatch [:yggstack/set-status :running])
                (-> (ygg/get-address)
                    (.then #(rf/dispatch [:yggstack/update-address %]))
                    (.catch (fn [_])))
                (-> (ygg/get-public-key)
                    (.then #(rf/dispatch [:yggstack/update-public-key %]))
                    (.catch (fn [_])))
                (-> (ygg/get-peers)
                    (.then (fn [json] (rf/dispatch [:yggstack/update-peer-count
                                                    (.-length (js/JSON.parse json))])))
                    (.catch (fn [_])))))
       (.catch (fn [e]
                 (js/console.error "start error:" e)
                 (rf/dispatch [:yggstack/set-status :error]))))))

(rf/reg-event-fx
 :yggstack/set-status
 (fn [{db :db} [_ status]]
   (let [fx {:db (assoc-in db [:yggstack :status] status)}
         dispatches (cond-> []
                      (= status :running)
                      (conj [:messenger/start-server]
                            [:yggstack/start-foreground-service])
                      (= status :stopped)
                      (conj [:yggstack/stop-foreground-service])
                      (and (= status :stopped)
                           (get-in db [:messenger :server-running]))
                      (conj [:messenger/stop-server]))]
     (cond-> fx
       (seq dispatches) (assoc :dispatch-n dispatches)))))

(rf/reg-event-fx
 :yggstack/stop
 (fn [{db :db} _]
   {:db (assoc-in db [:yggstack :status] :stopping)
    :yggstack/stop-daemon nil}))

(rf/reg-fx
 :yggstack/stop-daemon
 (fn [_]
   (-> (ygg/stop)
       (.then (fn [_]
                (js/console.log "Yggdrasil disconnected")
                (rf/dispatch [:yggstack/set-status :stopped])))
       (.catch (fn [e] (js/console.error "stop error:" e))))))

(rf/reg-fx
 :yggstack/retry-peers-now
 (fn [_]
   (-> (ygg/retry-peers-now)
       (.catch (fn [e]
                 (js/console.warn "retry peers error:" e))))))

(rf/reg-fx
 :yggstack/refresh-peer-count
 (fn [_]
   (js/setTimeout
    (fn []
      (-> (ygg/get-peers)
          (.then (fn [json] (rf/dispatch [:yggstack/update-peer-count
                                          (.-length (js/JSON.parse json))])))
          (.catch (fn [_]))))
    2000)))

(rf/reg-event-fx
 :yggstack/on-network-restored
 (fn [{db :db} _]
   (if (= :running (get-in db [:yggstack :status]))
     {:yggstack/retry-peers-now nil
      :yggstack/refresh-peer-count nil}
     (js/console.log "Yggdrasil not running, skipping network-restored actions"))))

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

(rf/reg-event-fx
 :yggstack/start-foreground-service
 (fn [_ _]
   {:yggstack/start-foreground-service nil}))

(rf/reg-event-fx
 :yggstack/stop-foreground-service
 (fn [_ _]
   {:yggstack/stop-foreground-service nil}))

(rf/reg-fx
 :yggstack/start-foreground-service
 (fn [_]
   (ygg/start-foreground-service "Yggdrasil Messenger" "Listening for messages...")))

(rf/reg-fx
 :yggstack/stop-foreground-service
 (fn [_]
   (ygg/stop-foreground-service)))

(rf/reg-event-fx
 :yggstack/battery-opt-out
 (fn [_ _]
   {:yggstack/battery-opt-out-fx nil}))

(rf/reg-fx
 :yggstack/battery-opt-out-fx
 (fn [_]
   (ygg/open-battery-optimization-settings)))
