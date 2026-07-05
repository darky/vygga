(ns vygga.storage
  (:require ["expo-secure-store" :as secure-store]))

(def ygg-private-key "ygg_private_key")

(defn load-key []
  (-> (secure-store/getItemAsync ygg-private-key)
      (.then #(when-not (nil? %) %))))

(defn save-key! [key]
  (secure-store/setItemAsync ygg-private-key key))

(defn clear-key! []
  (secure-store/deleteItemAsync ygg-private-key))
