(ns example.persist
  (:require ["expo-secure-store" :as secure-store]
            ["@react-native-async-storage/async-storage" :default async-storage]
            [example.crypto :as crypto]))

(def encryption-key "messenger_encryption_key")
(def messenger-storage-key "messenger_data")

(defn- get-or-create-encryption-key!
  []
  (-> (secure-store/getItemAsync encryption-key)
      (.then (fn [k]
               (if k
                 k
                 (let [new-key (crypto/generate-encryption-key)]
                   (-> (secure-store/setItemAsync encryption-key new-key)
                       (.then (constantly new-key)))))))))

(defn save-messenger!
  [messenger-data]
  (-> (get-or-create-encryption-key!)
      (.then (fn [key]
               (let [plaintext (.stringify js/JSON (clj->js messenger-data))
                     encrypted (crypto/encrypt plaintext key)]
                 (.setItem async-storage messenger-storage-key encrypted))))
      (.catch (fn [e]
                (js/console.warn "Failed to persist messenger data:" e)))))

(defn load-messenger
  []
  (-> (.getItem async-storage messenger-storage-key)
      (.then (fn [encrypted]
               (when encrypted
                 (-> (get-or-create-encryption-key!)
                     (.then (fn [key]
                              (let [plaintext (crypto/decrypt encrypted key)]
                                (when plaintext
                                  (-> (.. js/JSON (parse plaintext)
                                          (js->clj :keywordize-keys true))
                                      (update :seen-ids set))))))))))
      (.catch (fn [e]
                (js/console.warn "Failed to load messenger data:" e)
                nil))))
