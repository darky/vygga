(ns vygga.messenger-test
  (:require
   [cljs.test :as t :refer-macros [deftest is use-fixtures]]
   [re-frame.db :as rdb]
   [vygga.messenger :as msg]
   [vygga.db :refer [app-db]]))

(defn setup []
  (reset! rdb/app-db app-db))

(use-fixtures :each {:before setup})

;; ---- Native-module wrapper tests ----

(deftest test-add-remote-mapping
  (t/async done
    (-> (msg/add-remote-mapping 7777)
        (.then (fn [result]
                 (is (true? result))
                 (done))
               (fn [err]
                 (is (false? (str "add-remote-mapping rejected: " err)))
                 (done))))))

(deftest test-remove-remote-mapping
  (t/async done
    (-> (msg/remove-remote-mapping 7777)
        (.then (fn [result]
                 (is (true? result))
                 (done))
               (fn [err]
                 (is (false? (str "remove-remote-mapping rejected: " err)))
                 (done))))))
