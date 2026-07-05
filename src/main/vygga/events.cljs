(ns vygga.events
  (:require
   [re-frame.core :as rf]
   [re-frame.db :as rdb]
   [vygga.yggstack :as ygg]
   [vygga.messenger :as msg]
   [vygga.notifications :as notifications]
   [vygga.persist :as persist]
   [vygga.storage :as storage]
   [vygga.crypto :as crypto]
   [vygga.db :as db :refer [app-db]]))

(def page-size 50)
(defonce ^:private contact-msgs-cache (atom {}))

;; ---- Core events ----

(rf/reg-event-fx
 :initialize-db
 (fn [_ _]
   {:db app-db
    :yggstack/load-and-start nil
    :messenger/load-meta nil}))

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

(rf/reg-event-fx
 :app/exit
 (fn [_ _]
   {:app/exit-fx nil}))

(rf/reg-fx
 :app/exit-fx
 (fn [_]
   (ygg/exit-app)))

;; ---- Messenger events ----

(rf/reg-event-fx
 :messenger/add-contact
 (fn [{db :db} [_ {:keys [id name address]}]]
   (let [cid (or id (str (random-uuid)))]
     {:db (assoc-in db [:messenger :contacts cid]
                    {:name name :address address
                     :messages [] :total-count 0
                     :loaded-offset 0 :has-more? false})
      :persist/messenger-meta nil})))

(rf/reg-event-fx
 :messenger/remove-contact
 (fn [{db :db} [_ id]]
   (swap! contact-msgs-cache dissoc id)
   {:db (update-in db [:messenger :contacts] dissoc id)
    :persist/messenger-meta nil
    :messenger/delete-contact-msgs {:contact-id id}}))

(rf/reg-fx
 :messenger/delete-contact-msgs
 (fn [{:keys [contact-id]}]
   (persist/delete-contact-messages! contact-id)))

(rf/reg-event-fx
 :messenger/set-current-contact
 (fn [{db :db} [_ id]]
   (let [prev (get-in db [:messenger :current-contact])]
     {:db (assoc-in db [:messenger :current-contact] id)
      :dispatch (when (not= id prev)
                  [:messenger/load-contact-messages])})))

(defn- update-contact-msgs-in-db [db contact-id f]
  (update-in db [:messenger :contacts contact-id :messages] f))

(defn- update-cache! [contact-id f]
  (swap! contact-msgs-cache update contact-id f))

;; ---- Message loading & pagination ----

(rf/reg-event-fx
 :messenger/load-contact-messages
 (fn [{db :db} _]
   (let [cid (get-in db [:messenger :current-contact])]
     (if cid
       {:messenger/load-msgs {:contact-id cid}}
       (js/console.warn "load-contact-messages: no current contact")))))

(rf/reg-fx
 :messenger/load-msgs
 (fn [{:keys [contact-id]}]
   (-> (persist/load-contact-messages contact-id)
       (.then (fn [msgs]
                (let [full-msgs (or msgs [])]
                  (rf/dispatch [:messenger/set-contact-messages contact-id full-msgs]))))
       (.catch (fn [e]
                 (js/console.error "Failed to load messages:" e)
                 (rf/dispatch [:messenger/set-contact-messages contact-id []]))))))

(rf/reg-event-db
 :messenger/set-contact-messages
 (fn [db [_ contact-id full-msgs]]
   (let [total (count full-msgs)
         offset (max 0 (- total page-size))
         page (subvec full-msgs offset)]
     (swap! contact-msgs-cache assoc contact-id full-msgs)
     (-> db
         (assoc-in [:messenger :contacts contact-id :messages] page)
         (assoc-in [:messenger :contacts contact-id :loaded-offset] offset)
         (assoc-in [:messenger :contacts contact-id :total-count] total)
         (assoc-in [:messenger :contacts contact-id :has-more?] (> offset 0))
         (assoc-in [:messenger :messages-loading] false)))))

(rf/reg-event-fx
 :messenger/load-older-messages
 (fn [{db :db} _]
   (let [cid (get-in db [:messenger :current-contact])
         cached (get @contact-msgs-cache cid)]
     (if (and cid cached)
       (let [current-offset (get-in db [:messenger :contacts cid :loaded-offset] 0)
             new-offset (max 0 (- current-offset page-size))
             older (subvec cached new-offset current-offset)
             updated-msgs (into (vec older) (get-in db [:messenger :contacts cid :messages]))]
         {:db (-> db
                  (assoc-in [:messenger :contacts cid :messages] updated-msgs)
                  (assoc-in [:messenger :contacts cid :loaded-offset] new-offset)
                  (assoc-in [:messenger :contacts cid :has-more?] (> new-offset 0)))})
       (js/console.warn "load-older-messages: no cached messages for" cid)))))

;; ---- Meta persistence ----

(rf/reg-fx
 :messenger/load-meta
 (fn [_]
   (-> (persist/load-messenger-meta)
       (.then (fn [data]
                (when data
                  (rf/dispatch [:messenger/restore-meta data]))))
       (.catch (fn [e]
                 (js/console.warn "Failed to load messenger meta:" e))))))

(rf/reg-event-db
 :messenger/restore-meta
 (fn [db [_ data]]
   (let [contacts (reduce-kv (fn [acc k v]
                               (assoc acc k
                                      (merge {:messages []
                                              :loaded-offset 0
                                              :has-more? false
                                              :total-count 0}
                                             v)))
                             {} (:contacts data))]
     (assoc db :messenger (assoc data :contacts contacts)))))

(rf/reg-fx
 :persist/messenger-meta
 (fn [_]
   (let [messenger (get @rdb/app-db :messenger)]
     (persist/save-messenger-meta! messenger))))

(rf/reg-fx
 :persist/messenger-msgs
 (fn [{:keys [contact-id]}]
   (let [cid (or contact-id (get-in @rdb/app-db [:messenger :current-contact]))
         msgs (get @contact-msgs-cache cid)]
     (when msgs
       (persist/save-contact-messages! cid msgs)))))

;; ---- Messages ----

(rf/reg-event-fx
 :messenger/receive-message
 (fn [{db :db} [_ contact-id {:keys [text id ts]}]]
   (let [msg {:text text :from-me false
              :id (or id (str (random-uuid)))
              :ts (or ts (.now js/Date))}]
     (update-cache! contact-id (fn [msgs] (conj (vec (or msgs [])) msg)))
     {:db (update-contact-msgs-in-db db contact-id
                                     (fn [msgs] (conj (vec msgs) msg)))
      :persist/messenger-meta nil
      :persist/messenger-msgs {:contact-id contact-id}})))

(rf/reg-event-fx
 :messenger/add-outgoing
 (fn [{db :db} [_ contact-id {:keys [text id ts]}]]
   (let [msg {:text text :from-me true
              :id (or id (str (random-uuid)))
              :ts (or ts (.now js/Date))}]
     (update-cache! contact-id (fn [msgs] (conj (vec (or msgs [])) msg)))
     {:db (update-contact-msgs-in-db db contact-id
                                     (fn [msgs] (conj (vec msgs) msg)))
      :persist/messenger-meta nil
      :persist/messenger-msgs {:contact-id contact-id}})))

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
         my-address (get-in db [:yggstack :address])
         private-key (get-in db [:yggstack :private-key])
         public-key (get-in db [:yggstack :public-key])
         msg-id (str (random-uuid))
         msg {:text text :from-me true
              :id msg-id :ts (.now js/Date)
              :status :sending}]
     (if (and address text (seq text))
       (do
         (update-cache! contact-id (fn [msgs] (conj (vec (or msgs [])) msg)))
         {:db (update-contact-msgs-in-db db contact-id
                                         (fn [msgs] (conj (vec msgs) msg)))
          :messenger/send-via-socks {:address address
                                     :my-address my-address
                                     :private-key private-key
                                     :public-key public-key
                                     :contact-id contact-id
                                     :text text
                                     :msg-id msg-id}
          :persist/messenger-meta nil
          :persist/messenger-msgs {:contact-id contact-id}})
       (js/console.warn "Cannot send: missing address or text")))))

(rf/reg-fx
 :messenger/send-via-socks
 (fn [{:keys [address my-address private-key public-key contact-id text msg-id]}]
   (let [ts (.now js/Date)
         data-to-sign (str text "|" msg-id "|" ts)
         sig (crypto/sign-message private-key data-to-sign)
         msg (js/JSON.stringify (clj->js
                                 {:type "message"
                                  :from (or my-address "unknown")
                                  :text text
                                  :id msg-id
                                  :ts ts
                                  :pubkey public-key
                                  :sig sig}))]
     (-> (msg/send-message address msg)
         (.then (fn [_]
                  (rf/dispatch [:messenger/message-sent contact-id msg-id])))
         (.catch (fn [e]
                   (js/console.error "send error:" e)
                   (rf/dispatch [:messenger/message-failed contact-id msg-id])))))))

(rf/reg-event-fx
 :messenger/message-sent
 (fn [{db :db} [_ contact-id msg-id]]
   (let [update-fn (fn [msgs]
                     (mapv (fn [m] (if (= (:id m) msg-id)
                                     (assoc m :status :sent)
                                     m))
                           msgs))]
     (update-cache! contact-id update-fn)
     {:db (update-contact-msgs-in-db db contact-id update-fn)
      :persist/messenger-msgs {:contact-id contact-id}})))

(rf/reg-event-fx
 :messenger/message-failed
 (fn [{db :db} [_ contact-id msg-id]]
   (let [update-fn (fn [msgs]
                     (mapv (fn [m] (if (= (:id m) msg-id)
                                     (assoc m :status :failed)
                                     m))
                           msgs))]
     (update-cache! contact-id update-fn)
     {:db (update-contact-msgs-in-db db contact-id update-fn)
      :persist/messenger-msgs {:contact-id contact-id}})))

(rf/reg-event-fx
 :messenger/resend-message
 (fn [{db :db} [_ contact-id msg-id]]
   (let [contact (get-in db [:messenger :contacts contact-id])
         address (:address contact)
         my-address (get-in db [:yggstack :address])
         private-key (get-in db [:yggstack :private-key])
         public-key (get-in db [:yggstack :public-key])
         msgs (get-in db [:messenger :contacts contact-id :messages])
         msg (some #(when (= (:id %) msg-id) %) msgs)
         text (:text msg)]
     (if (and address text (seq text))
       (let [update-fn (fn [msgs]
                         (mapv (fn [m] (if (= (:id m) msg-id)
                                         (assoc m :status :sending)
                                         m))
                               msgs))]
         (update-cache! contact-id update-fn)
         {:db (update-contact-msgs-in-db db contact-id update-fn)
          :messenger/send-via-socks {:address address
                                     :my-address my-address
                                     :private-key private-key
                                     :public-key public-key
                                     :contact-id contact-id
                                     :text text
                                     :msg-id msg-id}
          :persist/messenger-meta nil
          :persist/messenger-msgs {:contact-id contact-id}})
       (js/console.warn "Cannot resend: missing address or text")))))

(rf/reg-event-fx
 :messenger/receive-incoming
 (fn [{db :db} [_ from-addr text id ts pubkey sig]]
   (let [seen-ids (get-in db [:messenger :seen-ids] #{})]
     (cond
       (seen-ids id)
       (js/console.warn "Rejected duplicate message:" id)

       (not (and pubkey sig))
       (js/console.warn "Rejected unsigned message from" from-addr)

       :else
       (let [data-to-verify (str text "|" id "|" ts)]
         (if (crypto/verify-signature pubkey data-to-verify sig)
           (let [contacts (get-in db [:messenger :contacts])
                 [contact-id existing]
                 (reduce-kv (fn [[_ found] cid c]
                              (if found [cid true]
                                  (if (= (:address c) from-addr)
                                    [cid true] nil)))
                            [nil false] contacts)
                 pubkey-mismatch (and existing
                                      (get-in contacts [contact-id :public-key])
                                      (not= pubkey (get-in contacts [contact-id :public-key])))
                 contact-id (or contact-id from-addr)
                 sender-name (if-let [c (get contacts contact-id)]
                               (:name c)
                               (str "unknown-" (subs from-addr 0 8)))
                 new-msg {:text text :from-me false
                          :id id :ts ts}]
             (if pubkey-mismatch
               (js/console.warn "Public key mismatch for" from-addr)
               (do
                 (notifications/show! {:title sender-name :body text})
                 (if existing
                   (do
                     (update-cache! contact-id
                                    (fn [msgs] (conj (vec (or msgs [])) new-msg)))
                     {:db (-> db
                              (update-contact-msgs-in-db contact-id
                                                         (fn [msgs] (conj (vec msgs) new-msg)))
                              (assoc-in [:messenger :seen-ids] (conj seen-ids id))
                              (assoc-in [:messenger :contacts contact-id :public-key] pubkey))
                      :persist/messenger-meta nil
                      :persist/messenger-msgs {:contact-id contact-id}})
                   {:db (-> db
                            (assoc-in [:messenger :seen-ids] (conj seen-ids id))
                            (assoc-in [:messenger :contacts from-addr]
                                      {:name sender-name
                                       :address from-addr
                                       :public-key pubkey
                                       :messages [new-msg]
                                       :total-count 1
                                       :loaded-offset 0
                                       :has-more? false}))
                    :persist/messenger-meta nil
                    :persist/messenger-msgs {:contact-id from-addr}}))))
           (js/console.warn "Invalid signature from" from-addr)))))))
