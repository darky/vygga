(ns vygga.core-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [vygga.db :as db]
   [vygga.theme :as theme]))

(deftest test-app-db-shape
  (testing "app-db has expected top-level keys"
    (is (contains? db/app-db :preferred-scheme))
    (is (contains? db/app-db :yggstack))
    (is (contains? db/app-db :messenger)))
  (testing "preferred-scheme defaults to :dark"
    (is (= :dark (:preferred-scheme db/app-db))))
  (testing "yggstack submap"
    (let [ys (:yggstack db/app-db)]
      (is (= :stopped (:status ys)))
      (is (= 0 (:peer-count ys)))
      (is (vector? (:peers ys)))
      (is (nil? (:address ys)))
      (is (nil? (:public-key ys)))
      (is (nil? (:private-key ys)))
      (is (vector? (:logs ys)))))
  (testing "messenger submap"
    (let [msgr (:messenger db/app-db)]
      (is (= 7777 (:server-port msgr)))
      (is (false? (:server-running msgr)))
      (is (map? (:contacts msgr)))
      (is (nil? (:current-contact msgr))))))

(deftest test-default-peers
  (testing "default-peers count and format"
    (is (= 4 (count db/default-peers)))
    (doseq [peer db/default-peers]
      (is (.startsWith peer "tls://")))))

(deftest test-use-theme-returns-correct-maps
  (testing "use-theme with :dark returns the dark map"
    (is (= theme/dark (theme/use-theme :dark))))
  (testing "use-theme with :light returns the light map"
    (is (= theme/light (theme/use-theme :light)))))

(deftest test-theme-keys
  (testing "light and dark themes have same keys"
    (let [light-keys (set (keys theme/light))
          dark-keys (set (keys theme/dark))]
      (is (= light-keys dark-keys))
      (is (contains? light-keys :bg))
      (is (contains? light-keys :accent))
      (is (contains? light-keys :error))))
  (testing "light theme accent is :blue"
    (is (= :blue (:accent theme/light))))
  (testing "dark theme accent is a string"
    (is (string? (:accent theme/dark)))))
