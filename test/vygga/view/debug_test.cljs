(ns vygga.view.debug-test
  (:require
   [cljs.test :refer-macros [use-fixtures]]
   [vygga.view-test-utils :refer [setup-view-tests]]))

(use-fixtures :each (fn [t] (setup-view-tests) (t)))
