(ns vygga.audio-server
  (:require [re-frame.core :as rf]
            ["react-native-tcp-socket" :as tcp]))

(defonce server-instance (atom nil))
(defonce ^:const audio-port 7778)
(defonce current-connection (atom nil))

(defn- read-uint32-be [^js buf offset]
  (+ (bit-shift-left (aget buf offset) 24)
     (bit-shift-left (aget buf (inc offset)) 16)
     (bit-shift-left (aget buf (+ offset 2)) 8)
     (aget buf (+ offset 3))))

(defn parse-buffer
  "Given a Uint8Array buffer, extract all complete frames.
   Returns {:frames [{:seq int :data (Uint8Array)} ...]
            :remainder (Uint8Array)}"
  [^js buf]
  (loop [offset 0
         frames []]
    (let [remaining (- (.-length buf) offset)]
      (if (< remaining 8)
        {:frames frames
         :remainder (if (pos? remaining) (.slice buf offset) (js/Uint8Array. 0))}
        (let [payload-len (read-uint32-be buf offset)
              total-frame (+ 8 payload-len)]
          (if (< remaining total-frame)
            {:frames frames
             :remainder (.slice buf offset)}
            (let [seq (read-uint32-be buf (+ offset 4))
                  pcm (.slice buf (+ offset 8) (+ offset total-frame))]
              (recur (+ offset total-frame)
                     (conj frames {:seq seq :data pcm})))))))))

(defn- start-frame-parser [^js socket]
  (let [buf (atom (js/Uint8Array. 0))]
    (.on socket "data"
         (fn [^js data]
           (let [combined (js/Uint8Array. (+ (.-length @buf) (.-length data)))]
             (.set combined @buf 0)
             (.set combined data (.-length @buf))
             (let [{:keys [frames remainder]} (parse-buffer combined)]
               (doseq [frame frames]
                 (rf/dispatch [:voip/incoming-audio frame]))
               (reset! buf remainder)))))))

(defn start!
  ([] (start! audio-port))
  ([port]
   (js/Promise.
    (fn [resolve reject]
      (if @server-instance
        (reject (js/Error. "Audio server already running"))
        (let [server (.createServer tcp
                                    (fn [^js socket]
                                      (when-let [^js old @current-connection]
                                        (try (.destroy old) (catch js/Error _)))
                                      (reset! current-connection socket)
                                      (start-frame-parser socket)
                                      (.on socket "close"
                                           (fn []
                                             (when (= @current-connection socket)
                                               (reset! current-connection nil))))
                                      (.on socket "error" (fn [_]))))]
          (.on server "error"
               (fn [e]
                 (reset! server-instance nil)
                 (reject e)))
          (.listen server port "127.0.0.1"
                   (fn []
                     (reset! server-instance server)
                     (resolve true)))))))))

(defn stop! []
  (js/Promise.
   (fn [resolve reject]
     (when-let [^js conn @current-connection]
       (try (.destroy conn) (catch js/Error _))
       (reset! current-connection nil))
     (if-let [server @server-instance]
       (.close server
               (fn [err]
                 (reset! server-instance nil)
                 (if err (reject err) (resolve true))))
       (resolve true)))))

(defn running? []
  (some? @server-instance))
