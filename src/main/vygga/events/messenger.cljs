(ns vygga.events.messenger
  (:require [re-frame.core :as rf]
            [vygga.crypto :as crypto]
            [vygga.messenger :as msg]
            [vygga.messenger-utils :as mu]
            [vygga.notifications :as notif]))

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
       {:db (update-in db [:messenger :contacts contact-id] mu/add-message msg)
        :messenger/send-via-socks {:address address
                                   :my-address my-address
                                   :private-key private-key
                                   :public-key public-key
                                   :contact-id contact-id
                                   :text text
                                   :msg-id msg-id
                                   :ts ts}
        :contacts/save-contacts nil}
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
               (let [viewing? (= from-addr current-contact)]
                 {:db (cond-> (-> db
                                  (update-in [:messenger :contacts from-addr] mu/add-message new-msg)
                                  (assoc-in [:messenger :contacts from-addr :public-key] pubkey))
                        (not viewing?)
                        (update-in [:messenger :contacts from-addr :unread-count] (fnil inc 0)))
                  :contacts/save-contacts nil
                  :messenger/show-incoming-notification {:from-addr from-addr
                                                         :text text}})
               {:db (assoc-in db [:messenger :contacts from-addr]
                              {:address from-addr
                               :public-key pubkey
                               :messages [new-msg]
                               :msg-index {id 0}
                               :unread-count 1})
                :contacts/save-contacts nil
                :messenger/show-incoming-notification {:from-addr from-addr
                                                       :text text}})))
         (js/console.warn "Invalid signature from" from-addr))))))
