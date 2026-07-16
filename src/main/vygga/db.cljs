(ns vygga.db)

(defonce default-peers
  ["tls://45.95.202.21:443"
   "tls://box.paulll.cc:13338"
   "tls://des.8px.sk:4321"
   "tls://95.217.35.92:1337"])

(defonce app-db {:preferred-scheme :dark
                 :yggstack {:status :stopped
                            :peer-count 0
                            :peers default-peers
                            :address nil
                            :public-key nil
                            :private-key nil
                            :logs []}
                 :messenger {:server-port 7777
                             :server-running false
                             :contacts {}
                             :current-contact nil}
                 :debug {:logs []}
                 :voip {:call-state :idle
                        :call-id nil
                        :remote-addr nil
                        :started-at nil
                        :audio-seq 0
                        :session-token nil}})
