(ns vygga.test-runner
  (:require
   [cljs.test :refer-macros [run-tests]]
   [vygga.crypto-test]
   [vygga.core-test]
   [vygga.yggstack-test]
   [vygga.events-test]
   [vygga.subs-test]
   [vygga.view-test]
   [vygga.widgets-test]
   [vygga.storage-test]
   [vygga.messenger-test]
   [vygga.tcp-server-test]
   [vygga.tcp-client-test]
   [vygga.voip-test]
   [vygga.audio-server-test]
   [vygga.voip-connection-test]))

(defn -main [& _]
  (run-tests
   'vygga.crypto-test
   'vygga.core-test
   'vygga.yggstack-test
   'vygga.events-test
   'vygga.subs-test
   'vygga.view-test
   'vygga.widgets-test
   'vygga.storage-test
   'vygga.messenger-test
   'vygga.tcp-server-test
   'vygga.tcp-client-test
   'vygga.voip-test
   'vygga.audio-server-test
   'vygga.voip-connection-test))
