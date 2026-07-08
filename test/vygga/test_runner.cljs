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
   [vygga.messenger-test]))

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
   'vygga.messenger-test))
