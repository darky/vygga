(ns vygga.messenger-utils)

(defn add-message [contact msg]
  (let [msgs (:messages contact)]
    (-> contact
        (assoc-in [:msg-index (:id msg)] (count msgs))
        (update :messages conj msg))))
