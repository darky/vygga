(ns vygga.events
  (:require
   [cljs.reader :as reader]
   [re-frame.core :as rf]
   [re-frame.db :as rdb]
   ["expo-secure-store" :as secure-store]
   [vygga.yggstack :as ygg]
   [vygga.messenger :as msg]
   [vygga.crypto :as crypto]
   [vygga.storage :as storage]
   [vygga.notifications :as notif]
   [vygga.voip :as voip]
   [vygga.db :as db :refer [app-db]]))

(rf/reg-event-db
 :theme/set-scheme
 (fn [db [_ scheme]]
   (assoc db :preferred-scheme scheme)))

(rf/reg-event-fx
 :initialize-db
 (fn [_ _]
   (notif/init!)
   {:db app-db
    :yggstack/load-and-start nil
    :messenger/load-contacts nil}))

(rf/reg-event-db
 :navigation/set-root-state
 (fn [db [_ navigation-root-state]]
   (assoc-in db [:navigation :root-state] navigation-root-state)))

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

(rf/reg-event-fx
 :app/exit
 (fn [_ _]
   {:app/exit-fx nil}))

(rf/reg-fx
 :app/exit-fx
 (fn [_]
   (ygg/exit-app)))

(rf/reg-event-fx
 :messenger/add-contact
 (fn [{db :db} [_ {:keys [address]}]]
   (if (get-in db [:messenger :contacts address])
     {:db db}
     {:db (assoc-in db [:messenger :contacts address]
                    {:address address
                     :messages [] :msg-index {}
                     :unread-count 0})
      :messenger/save-contacts nil})))

(rf/reg-event-db
 :messenger/set-current-contact
 (fn [db [_ id]]
   (-> db
       (assoc-in [:messenger :current-contact] id)
       (assoc-in [:messenger :contacts id :unread-count] 0))))

(rf/reg-fx
 :messenger/load-contacts
 (fn [_]
   (-> (secure-store/getItemAsync "vygga_contacts")
       (.then (fn [edn-str]
                (when edn-str
                  (rf/dispatch [:messenger/restore-contacts
                                (reader/read-string edn-str)]))))
       (.catch (fn [e]
                 (js/console.warn "Failed to load contacts:" e))))))

(rf/reg-event-db
 :messenger/restore-contacts
 (fn [db [_ contacts]]
   (let [rekeyed (reduce-kv
                  (fn [acc _ v]
                    (let [msgs (vec (take-last 5 (:messages v)))
                          idx (reduce-kv (fn [m i msg]
                                           (assoc m (:id msg) i))
                                         {} msgs)]
                      (assoc acc (:address v)
                             (merge {:messages msgs
                                     :msg-index idx
                                     :unread-count 0}
                                    (select-keys v [:address :public-key :unread-count])))))
                  {} contacts)]
     (assoc-in db [:messenger :contacts] rekeyed))))

(rf/reg-fx
 :messenger/save-contacts
 (fn [_]
   (let [contacts (get-in @rdb/app-db [:messenger :contacts])
         stripped (reduce-kv (fn [acc k v]
                               (assoc acc k
                                      (-> (dissoc v :msg-index)
                                          (update :messages #(vec (take-last 5 %))))))
                             {} contacts)]
     (-> (secure-store/setItemAsync "vygga_contacts" (pr-str stripped))
         (.catch (fn [e]
                   (js/console.warn "Failed to save contacts:" e)))))))

(rf/reg-fx
 :messenger/show-incoming-notification
 (fn [{:keys [from-addr text type]}]
   (let [sender (if (and from-addr (> (count from-addr) 8))
                  (subs from-addr 0 8)
                  "Unknown")]
     (if (= :call type)
       (notif/show-call! sender)
       (notif/show-message! sender text)))))

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
         (.then #(msg/add-remote-mapping p))
         (.catch (fn [e]
                   (js/console.warn "Messenger server already running:" e)))))))

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
         ts (.now js/Date)
         msg {:text text :from-me true
              :id msg-id :ts ts
              :status :sending}]
     (if (and address text (seq text))
       (let [msgs (or (get-in db [:messenger :contacts contact-id :messages]) [])
             idx (count msgs)]
         {:db (-> db
                  (assoc-in [:messenger :contacts contact-id :messages] (conj msgs msg))
                  (assoc-in [:messenger :contacts contact-id :msg-index msg-id] idx))
          :messenger/send-via-socks {:address address
                                     :my-address my-address
                                     :private-key private-key
                                     :public-key public-key
                                     :contact-id contact-id
                                     :text text
                                     :msg-id msg-id
                                     :ts ts}
          :messenger/save-contacts nil})
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
         (.catch (fn [_]
                   (rf/dispatch [:messenger/message-failed contact-id msg-id])))))))

(rf/reg-event-db
 :messenger/message-sent
 (fn [db [_ contact-id msg-id]]
   (let [idx (get-in db [:messenger :contacts contact-id :msg-index msg-id])]
     (assoc-in db [:messenger :contacts contact-id :messages idx :status] :sent))))

(rf/reg-event-db
 :messenger/message-failed
 (fn [db [_ contact-id msg-id]]
   (let [idx (get-in db [:messenger :contacts contact-id :msg-index msg-id])]
     (assoc-in db [:messenger :contacts contact-id :messages idx :status] :failed))))

(rf/reg-event-fx
 :messenger/resend-message
 (fn [{db :db} [_ contact-id msg-id]]
   (let [contact (get-in db [:messenger :contacts contact-id])
         address (:address contact)
         my-address (get-in db [:yggstack :address])
         private-key (get-in db [:yggstack :private-key])
         public-key (get-in db [:yggstack :public-key])
         idx (get-in db [:messenger :contacts contact-id :msg-index msg-id])
         msg (get-in db [:messenger :contacts contact-id :messages idx])
         text (:text msg)
         ts (:ts msg)]
     (if (and address text (seq text))
       {:db (assoc-in db [:messenger :contacts contact-id :messages idx :status] :sending)
        :messenger/send-via-socks {:address address
                                   :my-address my-address
                                   :private-key private-key
                                   :public-key public-key
                                   :contact-id contact-id
                                   :text text
                                   :msg-id msg-id
                                   :ts ts}}
       (js/console.warn "Cannot resend: missing address or text")))))

;; ---- VoIP Events ----

(rf/reg-event-fx
 :voip/call-contact
 (fn [{db :db} [_ contact-id]]
   (let [contact (get-in db [:messenger :contacts contact-id])
         address (:address contact)
         my-address (get-in db [:yggstack :address])
         private-key (get-in db [:yggstack :private-key])
         public-key (get-in db [:yggstack :public-key])
         call-id (str (random-uuid))
         ts (.now js/Date)]
     (if (and address private-key (not= :connected (get-in db [:voip :call-state])))
       {:db (-> db
                (assoc-in [:voip :call-state] :calling)
                (assoc-in [:voip :call-id] call-id)
                (assoc-in [:voip :remote-addr] address)
                (assoc-in [:voip :started-at] ts)
                (assoc-in [:voip :audio-seq] 0))
        :voip/send-signal {:call-type "offer"
                           :call-id call-id
                           :to address
                           :from (or my-address "unknown")
                           :ts ts
                           :private-key private-key
                           :public-key public-key}}
       (js/console.warn "Cannot call: missing address, key, or already in call")))))

(rf/reg-event-fx
 :voip/accept-call
 (fn [{db :db} _]
   (let [state (get-in db [:voip])
         private-key (get-in db [:yggstack :private-key])
         public-key (get-in db [:yggstack :public-key])
         my-address (get-in db [:yggstack :address])]
     (if (= :ringing (:call-state state))
       {:db (assoc-in db [:voip :call-state] :connected)
        :voip/send-signal {:call-type "accept"
                           :call-id (:call-id state)
                           :to (:remote-addr state)
                           :from (or my-address "unknown")
                           :ts (.now js/Date)
                           :private-key private-key
                           :public-key public-key}
        :voip/start-capture nil
        :voip/connect-audio {:address (:remote-addr state)
                             :call-id (:call-id state)}}
       (js/console.warn "Cannot accept: not in ringing state")))))

(rf/reg-event-fx
 :voip/reject-call
 (fn [{db :db} _]
   (let [state (get-in db [:voip])
         private-key (get-in db [:yggstack :private-key])
         public-key (get-in db [:yggstack :public-key])
         my-address (get-in db [:yggstack :address])]
     (if (= :ringing (:call-state state))
       {:db (assoc-in db [:voip] {:call-state :idle :call-id nil
                                  :remote-addr nil :started-at nil
                                  :audio-seq 0})
        :voip/send-signal {:call-type "reject"
                           :call-id (:call-id state)
                           :to (:remote-addr state)
                           :from (or my-address "unknown")
                           :ts (.now js/Date)
                           :private-key private-key
                           :public-key public-key}
        :voip/disconnect-audio nil}
       (js/console.warn "Cannot reject: not in ringing state")))))

(rf/reg-event-fx
 :voip/end-call
 (fn [{db :db} _]
   (let [state (get-in db [:voip])
         private-key (get-in db [:yggstack :private-key])
         public-key (get-in db [:yggstack :public-key])
         my-address (get-in db [:yggstack :address])]
     (when (contains? #{:calling :ringing :connected} (:call-state state))
       {:db (assoc-in db [:voip] {:call-state :idle :call-id nil
                                  :remote-addr nil :started-at nil
                                  :audio-seq 0})
        :voip/send-signal {:call-type "end"
                           :call-id (:call-id state)
                           :to (:remote-addr state)
                           :from (or my-address "unknown")
                           :ts (.now js/Date)
                           :private-key private-key
                           :public-key public-key}
        :voip/stop-capture nil
        :voip/disconnect-audio nil}))))

(rf/reg-event-fx
 :voip/incoming-signal
 (fn [{db :db} [_ msg]]
   (let [{:keys [call-type call-id from _to ts pubkey sig]} msg
         data-to-verify (str "call-signal|" call-type "|" call-id "|" ts)]
     (if (and pubkey sig (crypto/verify-signature pubkey data-to-verify sig))
       (let [current-state (get-in db [:voip :call-state])
             current-call-id (get-in db [:voip :call-id])]
         (case call-type
           "offer"
           (if (= :idle current-state)
             {:db (-> db
                      (assoc-in [:voip :call-state] :ringing)
                      (assoc-in [:voip :call-id] call-id)
                      (assoc-in [:voip :remote-addr] from)
                      (assoc-in [:voip :started-at] ts)
                      (assoc-in [:voip :audio-seq] 0))
              :messenger/show-incoming-notification {:from-addr from
                                                     :text "Incoming call"
                                                     :type :call}}
             (js/console.warn "Ignored call-offer: busy"))

           "accept"
           (if (and (= :calling current-state) (= call-id current-call-id))
             {:db (assoc-in db [:voip :call-state] :connected)
              :voip/start-capture nil
              :voip/connect-audio {:address from
                                   :call-id call-id}}
             (js/console.warn "Unexpected call-accept" call-id))

           "reject"
           (if (= :calling current-state)
             {:db (assoc-in db [:voip] {:call-state :idle :call-id nil
                                        :remote-addr nil :started-at nil
                                        :audio-seq 0})
              :voip/disconnect-audio nil}
             (js/console.warn "Unexpected call-reject" call-id))

           "end"
           (when (and current-call-id (= call-id current-call-id))
             {:db (assoc-in db [:voip] {:call-state :idle :call-id nil
                                        :remote-addr nil :started-at nil
                                        :audio-seq 0})
              :voip/stop-capture nil
              :voip/disconnect-audio nil})

           (js/console.warn "Unknown call-type:" call-type)))
       (js/console.warn "Invalid or unsigned call signal from" from)))))

;; ---- VoIP Effects ----

(rf/reg-fx
 :voip/send-signal
 (fn [{:keys [call-type call-id to from ts private-key public-key]}]
   (let [data-to-sign (str "call-signal|" call-type "|" call-id "|" ts)
         sig (crypto/sign-message private-key data-to-sign)
         msg (pr-str {:type "call-signal"
                      :call-type call-type
                      :call-id call-id
                      :to to
                      :from from
                      :ts ts
                      :pubkey public-key
                      :sig sig})]
     (.then (msg/send-message to msg) nil
            (fn [e]
              (js/console.warn "call signal send failed:" e))))))

(rf/reg-fx
 :voip/connect-audio
 (fn [{:keys [address _call-id]}]
   (when voip/audio-track-module
     (-> (.initUdpAudio voip/audio-track-module 7778 address 7778)
         (.catch (fn [e] (js/console.warn "UDP audio init error:" e)))))))

(rf/reg-fx
 :voip/disconnect-audio
 (fn [_]
   (when voip/audio-track-module
     (-> (.stopUdpAudio voip/audio-track-module)
         (.catch (fn [e] (js/console.warn "UDP audio stop error:" e)))))))

(rf/reg-fx
 :voip/start-capture
 (fn [_]
   (.then (voip/request-permissions!)
          (fn []
            (voip/start-recording-udp!
             (fn [^js view]
               (when voip/audio-track-module
                 (.encodeAndSendUdp voip/audio-track-module (js/Array.from view))))))
          (fn [e]
            (js/console.error "start capture error:" e)))))

(rf/reg-fx
 :voip/stop-capture
 (fn [_]
   (voip/stop-recording!)))

(rf/reg-event-fx
 :messenger/receive-incoming
 (fn [{db :db} [_ from-addr text id ts pubkey sig]]
   (cond
     (not (and pubkey sig))
     (js/console.warn "Rejected unsigned message from" from-addr)

     :else
     (let [data-to-verify (str text "|" id "|" ts)]
       (if (crypto/verify-signature pubkey data-to-verify sig)
         (let [contacts (get-in db [:messenger :contacts])
               existing (get contacts from-addr)
               current-contact (get-in db [:messenger :current-contact])
               pubkey-mismatch (and existing
                                    (:public-key existing)
                                    (not= pubkey (:public-key existing)))
               new-msg {:text text :from-me false
                        :id id :ts ts}]
           (if pubkey-mismatch
             (js/console.warn "Public key mismatch for" from-addr)
             (if existing
               (let [msgs (or (:messages existing) [])
                     idx (count msgs)
                     viewing? (= from-addr current-contact)]
                 {:db (cond-> (-> db
                                  (assoc-in [:messenger :contacts from-addr :messages] (conj msgs new-msg))
                                  (assoc-in [:messenger :contacts from-addr :msg-index id] idx)
                                  (assoc-in [:messenger :contacts from-addr :public-key] pubkey))
                        (not viewing?)
                        (update-in [:messenger :contacts from-addr :unread-count] (fnil inc 0)))
                  :messenger/save-contacts nil
                  :messenger/show-incoming-notification {:from-addr from-addr
                                                         :text text}})
               {:db (assoc-in db [:messenger :contacts from-addr]
                              {:address from-addr
                               :public-key pubkey
                               :messages [new-msg]
                               :msg-index {id 0}
                               :unread-count 1})
                :messenger/save-contacts nil
                :messenger/show-incoming-notification {:from-addr from-addr
                                                       :text text}})))
         (js/console.warn "Invalid signature from" from-addr))))))
