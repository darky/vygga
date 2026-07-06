(ns vygga.test-runner
  (:require
   [cljs.test :refer-macros [run-tests]]
   [vygga.crypto-test]
   [vygga.core-test]
   [vygga.yggstack-test]
   [vygga.events-test]
   [vygga.subs-test]
   [vygga.view-test]))

(defn -main [& _]
  (run-tests
   'vygga.crypto-test
   'vygga.core-test
   'vygga.yggstack-test
   'vygga.events-test
   'vygga.subs-test
   'vygga.view-test))
