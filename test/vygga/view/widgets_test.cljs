(ns vygga.view.widgets-test
  (:require
   [cljs.test :refer-macros [deftest is]]
   [vygga.view.widgets :as widgets]))

(defn text-in-tree [root]
  (filter string? (tree-seq vector? seq root)))

(defn text-present? [root s]
  (some #(= s %) (text-in-tree root)))

(deftest test-button-renders-text
  (is (text-present? (widgets/button {} "Press Me") "Press Me")))

(deftest test-button-no-crash
  (is (some? (widgets/button {} "Go"))))

(deftest test-button-disabled-no-crash
  (is (some? (widgets/button {:disabled? true} "Off"))))

(deftest test-button-custom-style-no-crash
  (is (some? (widgets/button {:style {:background-color :red}} "Red"))))
