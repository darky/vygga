(ns vygga.persist
  (:require [cljs.reader :as reader]
            ["expo-secure-store" :as secure-store]
            ["@react-native-async-storage/async-storage" :default async-storage]
            [vygga.crypto :as crypto]))

(def encryption-key "messenger_encryption_key")
(def messenger-meta-key "messenger_meta")

(def msg-key-prefix "msg_")
(def idx-key-prefix "msg_idx_")

(def index-chunk-max 200)
(def index-chunk-split-size 100)

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
               (let [plaintext (pr-str data)
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
                                  (reader/read {:default (fn [tag _]
                                                           (throw (js/Error. (str "Unknown EDN tag: #" tag))))}
                                               plaintext)))))))))
      (.catch (fn [e]
                (js/console.warn "Failed to load" storage-key ":" e)
                nil))))

;; ---- Messenger meta ----

(defn save-messenger-meta!
  [messenger-data]
  (let [contacts (reduce-kv (fn [acc k v]
                              (assoc acc k (dissoc v :messages :message-index
                                                   :all-messages)))
                            {} (:contacts messenger-data))
        meta (assoc messenger-data :contacts contacts)]
    (encrypt-and-save! messenger-meta-key meta)))

(defn load-messenger-meta
  []
  (-> (load-and-decrypt messenger-meta-key)
      (.then (fn [data]
               (when data
                 (update data :seen-ids set))))))

;; ---- Per-message storage ----

(defn- msg-key [cid msg-id]
  (str msg-key-prefix cid "_" msg-id))

(defn- idx-key [cid chunk]
  (str idx-key-prefix cid "_" chunk))

(defn- manifest-key [cid]
  (str idx-key-prefix cid "_manifest"))

(defn save-message!
  [cid msg]
  (encrypt-and-save! (msg-key cid (:id msg)) msg))

(defn load-messages-batch
  [cid ids]
  (if (empty? ids)
    (.resolve js/Promise [])
    (let [keys (mapv #(msg-key cid %) ids)]
      (-> (.multiGet async-storage (apply array keys))
          (.then (fn [entries]
                   (-> (get-or-create-encryption-key!)
                       (.then (fn [key]
                                (let [result (array)]
                                  (doseq [[_ encrypted] (mapv identity entries)]
                                    (when encrypted
                                      (let [plaintext (crypto/decrypt encrypted key)]
                                        (when plaintext
                                          (.push result (reader/read {:default (fn [tag _]
                                                                                 (throw (js/Error. (str "Unknown EDN tag: #" tag))))}
                                                                     plaintext))))))
                                  (vec result)))))))))))

;; ---- Chunked index ----

(defn load-message-index-manifest
  [cid]
  (-> (load-and-decrypt (manifest-key cid))
      (.then (fn [data]
               (or data {:chunks ["_curr"] :total 0})))))

(defn save-message-index-manifest!
  [cid manifest]
  (encrypt-and-save! (manifest-key cid) manifest))

(defn load-message-index-chunk
  [cid chunk-name]
  (-> (load-and-decrypt (idx-key cid chunk-name))
      (.then (fn [data] (or data [])))))

(defn save-message-index-chunk!
  [cid chunk-name entries]
  (encrypt-and-save! (idx-key cid chunk-name) entries))

;; ---- Index write (prepend entry, index first, then message) ----

(defn prepend-to-index!
  [cid entry]
  (let [promise (js/Promise.resolve)]
    (-> (.then promise (fn [] (load-message-index-manifest cid)))
        (.then (fn [manifest]
                 (let [chunks (:chunks manifest)
                       curr-name (first chunks)
                       new-manifest (update manifest :total inc)]
                   (-> (load-message-index-chunk cid curr-name)
                       (.then (fn [curr-entries]
                                (let [new-curr (into [entry] curr-entries)]
                                  (if (<= (count new-curr) index-chunk-max)
                                    {:action :save-curr :cid cid :chunk curr-name
                                     :entries new-curr :manifest new-manifest}
                                    {:action :overflow :cid cid :chunks chunks
                                     :chunk curr-name :new-curr new-curr
                                     :manifest new-manifest}))))))))
        (.then (fn [result]
                 (if (= :save-curr (:action result))
                   (-> (save-message-index-chunk! (:cid result) (:chunk result) (:entries result))
                       (.then #(save-message-index-manifest! (:cid result) (:manifest result))))
                   (let [keep (subvec (:new-curr result) 0 index-chunk-split-size)
                         archive (subvec (:new-curr result) index-chunk-split-size)
                         next-num (->> (:chunks result)
                                       (filter #(re-find #"^_\d+$" %))
                                       (map #(js/parseInt (subs % 1)))
                                       (apply max -1)
                                       inc)
                         archive-name (str "_" next-num)
                         new-manifest (-> (:manifest result)
                                          (update :chunks
                                                  (fn [cs] (into [archive-name] (rest cs)))))]
                     (-> (save-message-index-chunk! (:cid result) (:chunk result) keep)
                         (.then #(save-message-index-chunk! (:cid result) archive-name archive))
                         (.then #(save-message-index-manifest! (:cid result) new-manifest))))))))))

;; ---- Delete (for cleanup) ----

(defn delete-contact-messages!
  [contact-id]
  (-> (.getAllKeys async-storage)
      (.then (fn [keys]
               (let [prefixes [(str msg-key-prefix contact-id "_")
                               (str idx-key-prefix contact-id "_")]
                     matching (filter (fn [k] (some #(.startsWith k %) prefixes))
                                      (mapv identity keys))]
                 (when (seq matching)
                   (.multiRemove async-storage (apply array matching))))))))
