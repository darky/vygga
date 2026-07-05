(ns example.persist
  (:require ["expo-secure-store" :as secure-store]
            ["@react-native-async-storage/async-storage" :default async-storage]
            [example.crypto :as crypto]))

(def encryption-key "messenger_encryption_key")
(def messenger-meta-key "messenger_meta")
(def messenger-msgs-prefix "messenger_msgs_")

(defn- get-or-create-encryption-key!
  []
  (-> (secure-store/getItemAsync encryption-key)
      (.then (fn [k]
               (if k
                 k
                 (let [new-key (crypto/generate-encryption-key)]
                   (-> (secure-store/setItemAsync encryption-key new-key)
                       (.then (constantly new-key)))))))))

(defn- encrypt-and-save! [storage-key data]
  (-> (get-or-create-encryption-key!)
      (.then (fn [key]
               (let [plaintext (.stringify js/JSON (clj->js data))
                     encrypted (crypto/encrypt plaintext key)]
                 (.setItem async-storage storage-key encrypted))))
      (.catch (fn [e]
                (js/console.warn "Failed to persist" storage-key ":" e)))))

(defn- load-and-decrypt [storage-key]
  (-> (.getItem async-storage storage-key)
      (.then (fn [encrypted]
               (when encrypted
                 (-> (get-or-create-encryption-key!)
                     (.then (fn [key]
                              (let [plaintext (crypto/decrypt encrypted key)]
                                (when plaintext
                                  (.. js/JSON (parse plaintext) (js->clj :keywordize-keys true))))))))))
      (.catch (fn [e]
                (js/console.warn "Failed to load" storage-key ":" e)
                nil))))

(defn save-messenger-meta!
  [messenger-data]
  (let [contacts (reduce-kv (fn [acc k v]
                              (assoc acc k (dissoc v :messages)))
                            {} (:contacts messenger-data))
        meta (assoc messenger-data :contacts contacts)]
    (encrypt-and-save! messenger-meta-key meta)))

(defn load-messenger-meta
  []
  (-> (load-and-decrypt messenger-meta-key)
      (.then (fn [data]
               (when data
                 (update data :seen-ids set))))))

(defn save-contact-messages!
  [contact-id messages]
  (let [key (str messenger-msgs-prefix contact-id)]
    (encrypt-and-save! key messages)))

(defn load-contact-messages
  [contact-id]
  (let [key (str messenger-msgs-prefix contact-id)]
    (-> (load-and-decrypt key)
        (.then (fn [data]
                 (or data []))))))

(defn delete-contact-messages!
  [contact-id]
  (let [key (str messenger-msgs-prefix contact-id)]
    (-> (.removeItem async-storage key)
        (.catch (fn [e]
                  (js/console.warn "Failed to delete messages for" contact-id ":" e))))))
