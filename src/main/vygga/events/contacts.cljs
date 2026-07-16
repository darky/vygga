(ns vygga.events.contacts
  (:require [cljs.reader :as reader]
            [re-frame.core :as rf]
            [re-frame.db :as rdb]
            ["expo-secure-store" :as secure-store]))

(rf/reg-event-fx
 :contacts/add-contact
 (fn [{db :db} [_ {:keys [address name]}]]
   (if (get-in db [:messenger :contacts address])
     {:db db}
     {:db (assoc-in db [:messenger :contacts address]
                    (merge {:address address
                            :messages [] :msg-index {}
                            :unread-count 0}
                           (when (seq name) {:name name})))
      :contacts/save-contacts nil})))

(rf/reg-event-fx
 :contacts/update-contact-name
 (fn [{db :db} [_ contact-id name]]
   (if (get-in db [:messenger :contacts contact-id])
     (let [name (when (seq name) name)]
       {:db (assoc-in db [:messenger :contacts contact-id :name] name)
        :contacts/save-contacts nil})
     {:db db})))

(rf/reg-event-fx
 :contacts/remove-contact
 (fn [{db :db} [_ contact-id]]
   {:db (update-in db [:messenger :contacts] dissoc contact-id)
    :contacts/save-contacts nil}))

(rf/reg-event-db
 :contacts/set-current-contact
 (fn [db [_ id]]
   (-> db
       (assoc-in [:messenger :current-contact] id)
       (assoc-in [:messenger :contacts id :unread-count] 0))))

(rf/reg-fx
 :contacts/load-contacts
 (fn [_]
   (-> (secure-store/getItemAsync "vygga_contacts")
       (.then (fn [edn-str]
                (when edn-str
                  (rf/dispatch [:contacts/restore-contacts
                                (reader/read-string edn-str)]))))
       (.catch (fn [e]
                 (js/console.warn "Failed to load contacts:" e))))))

(rf/reg-event-db
 :contacts/restore-contacts
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
                                    (select-keys v [:address :public-key :unread-count :name])))))
                  {} contacts)]
     (assoc-in db [:messenger :contacts] rekeyed))))

(rf/reg-fx
 :contacts/save-contacts
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
