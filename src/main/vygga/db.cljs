(ns vygga.db)

(defonce default-peers
  ["tls://45.95.202.21:443"
   "tls://box.paulll.cc:13338"
   "tls://91.98.161.68:9001?key=0e638944bfd6b277fa5e0dddbeb4444778eea8bece63a9862c661797022a8f05"
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
                             :current-contact nil}})
