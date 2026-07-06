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
                     :messages [] :message-index []
                     :consumed-count 0 :total-count 0
                     :pending-chunks [] :has-more? false})
      :persist/messenger-meta nil})))

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

;; ---- Message loading & pagination ----

(rf/reg-event-fx
 :messenger/load-contact-messages
 (fn [{db :db} _]
   (let [cid (get-in db [:messenger :current-contact])]
     (if cid
       {:db (assoc-in db [:messenger :messages-loading] true)
        :messenger/load-initial-page {:contact-id cid}}
       (js/console.warn "load-contact-messages: no current contact")))))

(rf/reg-fx
 :messenger/load-initial-page
 (fn [{:keys [contact-id]}]
   (-> (persist/load-message-index-manifest contact-id)
       (.then (fn [manifest]
                (let [total (:total manifest 0)
                      chunks (:chunks manifest)
                      curr-name (first chunks)
                      pending (vec (rest chunks))]
                  (-> (persist/load-message-index-chunk contact-id curr-name)
                      (.then (fn [index-entries]
                               (let [n (min page-size (count index-entries))
                                     page-ids (mapv :id (take n index-entries))
                                     has-more? (< n total)]
                                 (-> (persist/load-messages-batch contact-id page-ids)
                                     (.then (fn [msgs]
                                              (rf/dispatch
                                               [:messenger/set-contact-messages
                                                contact-id msgs index-entries n total pending has-more?])))))))))))
       (.catch (fn [e]
                 (js/console.error "Failed to load messages:" e)
                 (rf/dispatch [:messenger/set-contact-messages contact-id [] [] 0 0 [] false]))))))

(rf/reg-event-db
 :messenger/set-contact-messages
 (fn [db [_ contact-id msgs index-entries consumed total pending has-more?]]
   (let [oldest-first (reverse msgs)]
     (-> db
         (assoc-in [:messenger :contacts contact-id :messages] oldest-first)
         (assoc-in [:messenger :contacts contact-id :message-index] index-entries)
         (assoc-in [:messenger :contacts contact-id :consumed-count] consumed)
         (assoc-in [:messenger :contacts contact-id :total-count] total)
         (assoc-in [:messenger :contacts contact-id :pending-chunks] pending)
         (assoc-in [:messenger :contacts contact-id :has-more?] has-more?)
         (assoc-in [:messenger :messages-loading] false)))))

(rf/reg-event-fx
 :messenger/load-older-messages
 (fn [{db :db} _]
   (let [cid (get-in db [:messenger :current-contact])
         contact (get-in db [:messenger :contacts cid])]
     (if contact
       (let [index-entries (:message-index contact [])
             consumed (:consumed-count contact 0)
             total (:total-count contact 0)
             pending (:pending-chunks contact [])
             remaining (- (count index-entries) consumed)]
         (if (pos? remaining)
           (let [n (min page-size remaining)
                 ids (mapv :id (subvec index-entries consumed (+ consumed n)))
                 new-consumed (+ consumed n)
                 has-more? (< new-consumed total)]
             {:messenger/load-older-batch {:contact-id cid
                                           :ids ids
                                           :new-consumed new-consumed
                                           :has-more? has-more?}})
           (if (seq pending)
             {:messenger/load-next-chunk {:contact-id cid
                                          :total total
                                          :pending pending}}
             (js/console.warn "load-older-messages: no more messages"))))
       (js/console.warn "load-older-messages: no contact" cid)))))

(rf/reg-fx
 :messenger/load-older-batch
 (fn [{:keys [contact-id ids new-consumed has-more?]}]
   (-> (persist/load-messages-batch contact-id ids)
       (.then (fn [msgs]
                (rf/dispatch [:messenger/prepend-older-messages
                              contact-id msgs new-consumed has-more?]))))))

(rf/reg-event-db
 :messenger/prepend-older-messages
 (fn [db [_ contact-id msgs consumed has-more?]]
   (let [oldest-first (reverse msgs)]
     (-> db
         (update-in [:messenger :contacts contact-id :messages]
                    (fn [cur] (into oldest-first (vec cur))))
         (assoc-in [:messenger :contacts contact-id :consumed-count] consumed)
         (assoc-in [:messenger :contacts contact-id :has-more?] has-more?)))))

(rf/reg-fx
 :messenger/load-next-chunk
 (fn [{:keys [contact-id total pending]}]
   (let [chunk-name (first pending)
         rest-chunks (vec (rest pending))]
     (-> (persist/load-message-index-chunk contact-id chunk-name)
         (.then (fn [entries]
                  (rf/dispatch [:messenger/chunk-loaded
                                contact-id entries rest-chunks total])))))))
(rf/reg-event-fx
 :messenger/chunk-loaded
 (fn [{db :db} [_ contact-id entries rest-chunks total]]
   (let [contact (get-in db [:messenger :contacts contact-id])
         index-entries (:message-index contact [])
         consumed (:consumed-count contact 0)
         updated-index (into index-entries entries)
         remaining (- (count updated-index) consumed)]
     (if (pos? remaining)
       (let [n (min page-size remaining)
             ids (mapv :id (subvec updated-index consumed (+ consumed n)))
             new-consumed (+ consumed n)
             has-more? (or (seq rest-chunks) (< new-consumed total))]
         {:db (-> db
                  (assoc-in [:messenger :contacts contact-id :message-index] updated-index)
                  (assoc-in [:messenger :contacts contact-id :pending-chunks] rest-chunks)
                  (assoc-in [:messenger :contacts contact-id :has-more?] true))
          :messenger/load-older-batch {:contact-id contact-id
                                       :ids ids
                                       :new-consumed new-consumed
                                       :has-more? has-more?}})
       {:db (-> db
                (assoc-in [:messenger :contacts contact-id :message-index] updated-index)
                (assoc-in [:messenger :contacts contact-id :pending-chunks] rest-chunks)
                (assoc-in [:messenger :contacts contact-id :has-more?] (seq rest-chunks)))}))))

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
                                              :message-index []
                                              :consumed-count 0
                                              :has-more? false
                                              :total-count 0
                                              :pending-chunks []}
                                             v)))
                             {} (:contacts data))]
     (assoc db :messenger (assoc data :contacts contacts)))))

(rf/reg-fx
 :persist/messenger-meta
 (fn [_]
   (let [messenger (get @rdb/app-db :messenger)]
     (persist/save-messenger-meta! messenger))))

(rf/reg-fx
 :persist/messenger-write
 (fn [{:keys [contact-id index-entry msg]}]
   (-> (persist/prepend-to-index! contact-id index-entry)
       (.then #(persist/save-message! contact-id msg))
       (.catch (fn [e]
                 (js/console.error "persist write error:" e))))))

(rf/reg-fx
 :persist/messenger-update-msg
 (fn [{:keys [contact-id msg]}]
   (persist/save-message! contact-id msg)))

;; ---- Messages ----

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
   (msg/remove-remote-mapping 7777)
   (msg/uninstall-message-listener!)))

(rf/reg-event-fx
 :messenger/send-message
 (fn [{db :db} [_ contact-id text]]
   (let [contact (get-in db [:messenger :contacts contact-id])
         address (:address contact)
         my-address (get-in db [:yggstack :address])
         private-key (get-in db [:yggstack :private-key])
         public-key (get-in db [:yggstack :public-key])
         msg-id (str (random-uuid))
         ts (.now js/Date)
         msg {:text text :from-me true
              :id msg-id :ts ts
              :status :sending}]
     (if (and address text (seq text))
       {:db (update-in db [:messenger :contacts contact-id :messages]
                       (fn [msgs] (conj (vec msgs) msg)))
        :messenger/send-via-socks {:address address
                                   :my-address my-address
                                   :private-key private-key
                                   :public-key public-key
                                   :contact-id contact-id
                                   :text text
                                   :msg-id msg-id
                                   :ts ts}
        :persist/messenger-meta nil
        :persist/messenger-write {:contact-id contact-id
                                  :index-entry {:id msg-id :ts ts}
                                  :msg msg}}
       (js/console.warn "Cannot send: missing address or text")))))

(rf/reg-fx
 :messenger/send-via-socks
 (fn [{:keys [address my-address private-key public-key contact-id text msg-id ts]}]
   (let [data-to-sign (str text "|" msg-id "|" ts)
         sig (crypto/sign-message private-key data-to-sign)
         msg (pr-str {:type "message"
                      :from (or my-address "unknown")
                      :text text
                      :id msg-id
                      :ts ts
                      :pubkey public-key
                      :sig sig})]
     (-> (msg/send-message address msg)
         (.then (fn [_]
                  (rf/dispatch [:messenger/message-sent contact-id msg-id])))
         (.catch (fn [e]
                   (js/console.error "send error:" e)
                   (rf/dispatch [:messenger/message-failed contact-id msg-id])))))))

(rf/reg-event-fx
 :messenger/message-sent
 (fn [{db :db} [_ contact-id msg-id]]
   (let [db' (update-in db [:messenger :contacts contact-id :messages]
                        (fn [msgs]
                          (mapv (fn [m] (if (= (:id m) msg-id)
                                          (assoc m :status :sent)
                                          m))
                                msgs)))
         msg (some #(when (= (:id %) msg-id) %) (get-in db' [:messenger :contacts contact-id :messages]))]
     {:db db'
      :persist/messenger-update-msg {:contact-id contact-id :msg msg}})))

(rf/reg-event-fx
 :messenger/message-failed
 (fn [{db :db} [_ contact-id msg-id]]
   (let [db' (update-in db [:messenger :contacts contact-id :messages]
                        (fn [msgs]
                          (mapv (fn [m] (if (= (:id m) msg-id)
                                          (assoc m :status :failed)
                                          m))
                                msgs)))
         msg (some #(when (= (:id %) msg-id) %) (get-in db' [:messenger :contacts contact-id :messages]))]
     {:db db'
      :persist/messenger-update-msg {:contact-id contact-id :msg msg}})))

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
         text (:text msg)
         ts (:ts msg)]
     (if (and address text (seq text))
       (let [db' (update-in db [:messenger :contacts contact-id :messages]
                            (fn [msgs]
                              (mapv (fn [m] (if (= (:id m) msg-id)
                                              (assoc m :status :sending)
                                              m))
                                    msgs)))
             updated-msg (some #(when (= (:id %) msg-id) %) (get-in db' [:messenger :contacts contact-id :messages]))]
         {:db db'
          :messenger/send-via-socks {:address address
                                     :my-address my-address
                                     :private-key private-key
                                     :public-key public-key
                                     :contact-id contact-id
                                     :text text
                                     :msg-id msg-id
                                     :ts ts}
          :persist/messenger-meta nil
          :persist/messenger-update-msg {:contact-id contact-id :msg updated-msg}})
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
                   {:db (-> db
                            (update-in [:messenger :contacts contact-id :messages]
                                       (fn [msgs] (conj (vec msgs) new-msg)))
                            (assoc-in [:messenger :seen-ids] (conj seen-ids id))
                            (assoc-in [:messenger :contacts contact-id :public-key] pubkey))
                    :persist/messenger-meta nil
                    :persist/messenger-write {:contact-id contact-id
                                              :index-entry {:id id :ts ts}
                                              :msg new-msg}}
                   {:db (-> db
                            (assoc-in [:messenger :seen-ids] (conj seen-ids id))
                            (assoc-in [:messenger :contacts from-addr]
                                      {:name sender-name
                                       :address from-addr
                                       :public-key pubkey
                                       :messages [new-msg]
                                       :message-index [{:id id :ts ts}]
                                       :consumed-count 1
                                       :total-count 1
                                       :pending-chunks []
                                       :has-more? false}))
                    :persist/messenger-meta nil
                    :persist/messenger-write {:contact-id from-addr
                                              :index-entry {:id id :ts ts}
                                              :msg new-msg}}))))
           (js/console.warn "Invalid signature from" from-addr)))))))
