(ns example.events
  (:require
   [re-frame.core :as rf]
   [example.yggstack :as ygg]
   [example.messenger :as msg]
   [example.storage :as storage]
   [example.db :as db :refer [app-db]]))

(rf/reg-event-fx
 :initialize-db
 (fn [_ _]
   {:db app-db
    :yggstack/load-and-start nil}))

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
   (ygg/stop-polling)
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
   (ygg/start-polling)
   (-> (ygg/start config-json socks-address nameserver)
       (.then (fn [_] (rf/dispatch [:yggstack/set-status :running])))
       (.catch (fn [e]
                 (js/console.error "start error:" e)
                 (rf/dispatch [:yggstack/set-status :error]))))))

(rf/reg-event-fx
 :yggstack/set-status
 (fn [{db :db} [_ status]]
   (let [fx {:db (assoc-in db [:yggstack :status] status)}]
     (if (= status :running)
       (assoc fx :dispatch [:messenger/start-server])
       (if (and (= status :stopped)
                (get-in db [:messenger :server-running]))
          (assoc fx :dispatch [:messenger/stop-server])
          fx)))))

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

;; ---- Messenger events ----

(rf/reg-event-db
 :messenger/add-contact
 (fn [db [_ {:keys [id name address]}]]
   (let [cid (or id (str (random-uuid)))]
     (assoc-in db [:messenger :contacts cid]
       {:name name :address address :messages []}))))

(rf/reg-event-db
 :messenger/remove-contact
 (fn [db [_ id]]
   (update-in db [:messenger :contacts] dissoc id)))

(rf/reg-event-db
 :messenger/set-current-contact
 (fn [db [_ id]]
   (assoc-in db [:messenger :current-contact] id)))

(rf/reg-event-db
 :messenger/receive-message
 (fn [db [_ contact-id {:keys [text id ts]}]]
   (update-in db [:messenger :contacts contact-id :messages]
     (fn [msgs] (conj (vec msgs) {:text text :from-me false
                                   :id (or id (str (random-uuid)))
                                   :ts (or ts (.now js/Date))})))))

(rf/reg-event-db
 :messenger/add-outgoing
 (fn [db [_ contact-id {:keys [text id ts]}]]
   (update-in db [:messenger :contacts contact-id :messages]
     (fn [msgs] (conj (vec msgs) {:text text :from-me true
                                   :id (or id (str (random-uuid)))
                                   :ts (or ts (.now js/Date))})))))

(rf/reg-event-db
 :messenger/set-server-running
 (fn [db [_ running?]]
   (assoc-in db [:messenger :server-running] running?)))

(rf/reg-event-fx
 :messenger/start-server
 (fn [{db :db} _]
   (let [port (get-in db [:messenger :server-port] 7777)]
     {:messenger/start-tcp-server {:port port}
      :db (assoc-in db [:messenger :server-running] true)})))

(rf/reg-event-fx
 :messenger/stop-server
 (fn [{db :db} _]
   {:messenger/stop-tcp-server nil
    :db (assoc-in db [:messenger :server-running] false)}))

(rf/reg-fx
 :messenger/start-tcp-server
 (fn [{:keys [port]}]
   (let [p (or port 7777)]
     (-> (msg/start-server! p)
         (.then (fn []
                  (msg/add-remote-mapping p)
                  (msg/install-message-listener!)))
         (.catch (fn [e]
                   (js/console.error "Failed to start messenger server:" e)))))))

(rf/reg-fx
 :messenger/stop-tcp-server
 (fn [_]
   (msg/stop-server!)
   (msg/remove-remote-mapping 7777)))

(rf/reg-event-fx
 :messenger/send-message
 (fn [{db :db} [_ contact-id text]]
   (let [contact (get-in db [:messenger :contacts contact-id])
         address (:address contact)
         my-address (get-in db [:yggstack :address])]
     (if (and address text (seq text))
       {:messenger/send-via-socks {:address address
                                   :my-address my-address
                                   :contact-id contact-id
                                   :text text}}
       (js/console.warn "Cannot send: missing address or text")))))

(rf/reg-fx
 :messenger/send-via-socks
 (fn [{:keys [address my-address contact-id text]}]
   (let [msg-id (str (random-uuid))
         msg (js/JSON.stringify (clj->js
                {:type "message"
                 :from (or my-address "unknown")
                 :text text
                 :id msg-id
                 :ts (.now js/Date)}))]
     (rf/dispatch [:messenger/add-outgoing contact-id
                   {:text text :from-me true :id msg-id :ts (.now js/Date)}])
     (-> (msg/send-message address msg)
         (.then (fn [result]
                  (if result
                    (js/console.log "Message sent to" address)
                    (js/console.warn "Failed to send message to" address))))
         (.catch (fn [e]
                   (js/console.error "send error:" e)))))))

(rf/reg-event-fx
 :messenger/receive-incoming
 (fn [{db :db} [_ from-addr text id ts]]
   (let [contacts (get-in db [:messenger :contacts])
         [contact-id existing]
         (reduce-kv (fn [[_ found] cid c]
                      (if found [cid true]
                        (if (= (:address c) from-addr)
                          [cid true] nil)))
                    [nil false] contacts)
         contact-id (or contact-id from-addr)]
     (if existing
       {:db (update-in db [:messenger :contacts contact-id :messages]
              (fn [msgs] (conj (vec msgs)
                               {:text text :from-me false
                                :id (or id (str (random-uuid)))
                                :ts (or ts (.now js/Date))})))}
       ;; Unknown sender — add as temporary contact
       {:db (-> db
               (assoc-in [:messenger :contacts from-addr]
                 {:name (str "unknown-" (subs from-addr 0 8))
                  :address from-addr
                  :messages [{:text text :from-me false
                              :id (or id (str (random-uuid)))
                              :ts (or ts (.now js/Date))}]}))
        :dispatch [:messenger/set-current-contact from-addr]}))))
