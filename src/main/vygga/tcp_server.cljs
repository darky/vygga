(ns vygga.tcp-server
  (:require [cljs.reader :as reader]
            [re-frame.core :as rf]
            ["react-native-tcp-socket" :as tcp]))

(defonce server-instance (atom nil))
(defonce ^:const messenger-port 7777)

(defn parse-and-dispatch [payload]
  (try
    (let [{:keys [type from text id ts pubkey sig] :as msg}
          (reader/read-string {:default (fn [tag _]
                                          (throw (js/Error. (str "Unknown EDN tag: #" tag))))}
                              payload)]
      (case type
        "message"
        (when from
          (rf/dispatch [:messenger/receive-incoming from text id ts pubkey sig]))
        "call-signal"
        (rf/dispatch [:voip/incoming-signal msg])
        "call-audio"
        (rf/dispatch [:voip/incoming-audio msg])
        (js/console.warn "Unknown message type:" type)))
    (catch js/Error e
      (js/console.warn "Failed to parse messenger message:" e))))

(defn start!
  ([] (start! messenger-port))
  ([port]
   (js/Promise.
    (fn [resolve reject]
      (if @server-instance
        (reject (js/Error. "Server already running"))
        (let [server (.createServer tcp
                                    (fn [^js socket]
                                      (let [buf (atom "")]
                                        (.setEncoding socket "utf-8")
                                        (.on socket "data"
                                             (fn [data]
                                               (let [combined (str @buf data)
                                                     parts (.split combined "\n")]
                                                 (doseq [p (butlast parts)]
                                                   (when (not= p "")
                                                     (parse-and-dispatch p)))
                                                 (reset! buf (last parts)))))
                                        (.on socket "close"
                                             (fn []
                                               (when (not= "" @buf)
                                                 (parse-and-dispatch @buf))
                                               (reset! buf "")))
                                        (.on socket "error"
                                             (fn [e]
                                               (js/console.warn "TCP server socket error:" e))))))]
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
     (if-let [server @server-instance]
       (.close server
               (fn [err]
                 (reset! server-instance nil)
                 (if err (reject err) (resolve true))))
       (resolve true)))))

(defn running? []
  (some? @server-instance))
