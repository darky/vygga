(ns vygga.events.voip
  (:require [re-frame.core :as rf]
            [vygga.crypto :as crypto]
            [vygga.messenger :as msg]
            [vygga.voip :as voip]))

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
                           :public-key public-key}
        :voip/show-overlay {:mode :active
                            :address address
                            :call-id call-id}}
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
        :voip/connect-audio {:address (:remote-addr state)
                             :call-id (:call-id state)}
        :voip/show-overlay {:mode :active
                            :address (:remote-addr state)
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
        :voip/disconnect-audio nil
        :voip/hide-overlay nil}
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
        :voip/disconnect-audio nil
        :voip/hide-overlay nil}))))

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
                                                     :type :call}
              :voip/show-overlay {:mode :incoming
                                  :address from
                                  :call-id call-id}}
             (js/console.warn "Ignored call-offer: busy"))

           "accept"
           (if (and (= :calling current-state) (= call-id current-call-id))
             {:db (assoc-in db [:voip :call-state] :connected)
              :voip/connect-audio {:address from
                                   :call-id call-id}
              :voip/show-overlay {:mode :active
                                  :address from
                                  :call-id call-id}}
             (js/console.warn "Unexpected call-accept" call-id))

           "reject"
           (if (= :calling current-state)
             {:db (assoc-in db [:voip] {:call-state :idle :call-id nil
                                        :remote-addr nil :started-at nil
                                        :audio-seq 0})
              :voip/disconnect-audio nil
              :voip/hide-overlay nil}
             (js/console.warn "Unexpected call-reject" call-id))

           "end"
           (when (and current-call-id (= call-id current-call-id))
             {:db (assoc-in db [:voip] {:call-state :idle :call-id nil
                                        :remote-addr nil :started-at nil
                                        :audio-seq 0})
              :voip/disconnect-audio nil
              :voip/hide-overlay nil})

           (js/console.warn "Unknown call-type:" call-type)))
       (js/console.warn "Invalid or unsigned call signal from" from)))))

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
   (-> (.then (voip/request-permissions!)
              (fn []
                (when voip/audio-track-module
                  (-> (.initUdpAudio voip/audio-track-module 7778 address 7778)
                      (.catch (fn [e] (js/console.warn "UDP audio init error:" e)))))))
       (.catch (fn [e] (js/console.error "Audio permission denied:" e))))))

(rf/reg-fx
 :voip/disconnect-audio
 (fn [_]
   (when voip/audio-track-module
     (-> (.stopUdpAudio voip/audio-track-module)
         (.catch (fn [e] (js/console.warn "UDP audio stop error:" e)))))))

(rf/reg-fx
 :voip/show-overlay
 (fn [{:keys [mode address call-id]}]
   (voip/show-overlay! mode address call-id)))

(rf/reg-fx
 :voip/hide-overlay
 (fn [_]
   (voip/hide-overlay!)))

(rf/reg-fx
 :voip/start-capture
 (fn [_]))

(rf/reg-fx
 :voip/stop-capture
 (fn [_]))
