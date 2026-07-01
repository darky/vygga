#!/usr/bin/env bb
(import '[java.net Socket]
        '[java.io PushbackInputStream])

(defn read-bencode [is]
  (let [b (.read is)]
    (cond
      (= b -1) nil
      (Character/isDigit (char b))
      (let [sb (StringBuilder.)]
        (.append sb (char b))
        (loop []
          (let [c (.read is)]
            (if (= c (int \:))
              (let [len (parse-long (str sb))
                    ba (byte-array len)]
                (.read is ba)
                (String. ba "UTF-8"))
              (do (.append sb (char c))
                  (recur))))))
      (= b (int \i))
      (let [sb (StringBuilder.)]
        (loop []
          (let [c (.read is)]
            (if (= c (int \e))
              (parse-long (str sb))
              (do (.append sb (char c))
                  (recur))))))
      (= b (int \l))
      (loop [items []]
        (let [peek (.read is)]
          (if (= peek (int \e))
            items
            (do (.unread is peek)
                (recur (conj items (read-bencode is)))))))
      (= b (int \d))
      (loop [m {}]
        (let [peek (.read is)]
          (if (= peek (int \e))
            m
            (do (.unread is peek)
                (let [k (read-bencode is)
                      v (read-bencode is)]
                  (recur (assoc m k v)))))))
      :else nil)))

(defn encode-bencode [x]
  (cond
    (string? x)  (let [b (.getBytes x "UTF-8")] (str (count b) ":" x))
    (number? x)  (str "i" x "e")
    (keyword? x) (let [s (name x) b (.getBytes s "UTF-8")] (str (count b) ":" s))
    (map? x)     (str "d"
                      (apply str (mapcat (fn [[k v]]
                                          (str (encode-bencode (if (keyword? k) (name k) k))
                                               (encode-bencode v))) x))
                      "e")
    (vector? x)  (str "l" (apply str (map encode-bencode x)) "e")
    (seq? x)     (str "l" (apply str (map encode-bencode x)) "e")
    :else        (throw (Exception. (str "Cannot encode: " (pr-str x))))))

(defn nrepl-eval [is os session code]
  (let [msg (encode-bencode {:op "eval" :code code :session session})
        _ (.write os (.getBytes msg "UTF-8"))
        _ (.flush os)]
    (loop [out "" err "" value ""]
      (let [resp (read-bencode is)]
        (if (some #(= % "done") (get resp "status"))
          {:out out :err err :value value}
          (recur (str out (get resp "out" ""))
                 (str err (get resp "err" ""))
                 (or (get resp "value") value)))))))

(defn nrepl-send [is os msg]
  (let [data (encode-bencode msg)]
    (.write os (.getBytes data "UTF-8"))
    (.flush os)
    (read-bencode is)))

(defn -main []
  (let [port (or (some-> (try (slurp ".shadow-cljs/nrepl.port")
                              (catch Exception _ nil))
                         clojure.string/trim
                         parse-long)
                 7888)
        code (slurp *in*)
        sock (Socket. "localhost" port)
        is   (PushbackInputStream. (.getInputStream sock))
        os   (.getOutputStream sock)]
    (try
      (let [clone-resp (nrepl-send is os {:op "clone"})
            session (get clone-resp "new-session")]
        (nrepl-eval is os session "(shadow/nrepl-select :app)")
        (let [res (nrepl-eval is os session code)]
          (when-not (clojure.string/blank? (:out res))
            (print (:out res)))
          (when-not (clojure.string/blank? (:err res))
            (binding [*out* *err*] (println (:err res))))
          (when-not (clojure.string/blank? (:value res))
            (println (:value res)))))
      (finally
        (.close sock)))))

(-main)
