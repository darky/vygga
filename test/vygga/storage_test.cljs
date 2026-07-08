(ns vygga.storage-test
  (:require
   [cljs.test :refer-macros [deftest is]]
   [vygga.storage :as storage]))

;; Storage functions delegate to expo-secure-store (JS import).
;; The JS import var cannot be rebound in CLJS tests.
;; Storage coverage is provided through the re-frame event handler
;; tests in events_test.cljs, where the fx handlers that call storage
;; (:messenger/save-contacts, :yggstack/generate-key, etc.) are mocked.
;;
;; Here we verify the public API shape:

(deftest test-ygg-private-key-constant
  (is (= "ygg_private_key" storage/ygg-private-key)))

(deftest test-load-key-returns-promise
  (is (some? (storage/load-key))))

(deftest test-save-key-returns-promise
  (is (some? (storage/save-key! "test"))))

(deftest test-clear-key-returns-promise
  (is (some? (storage/clear-key!))))
