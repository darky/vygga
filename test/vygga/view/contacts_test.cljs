(ns vygga.view.contacts-test
  (:require
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [re-frame.core :as rf]
   [re-frame.db :as rdb]
   [vygga.events.contacts]
   [vygga.subs]
   [vygga.view.contacts :as contacts-view]
   [vygga.theme :as theme]
   [vygga.db :refer [app-db]]
   [vygga.view-test-utils :refer [setup-view-tests text-present?]]))

(use-fixtures :each (fn [t] (setup-view-tests) (t)))

(deftest test-contact-item-render-basic
  (let [props #js {:navigation #js {:navigate (fn [])}}
        result (contacts-view/contact-item-render
                props "c1"
                {:address "201::1" :last-message {:text "Hey!"}}
                theme/light nil)]
    (is (text-present? result "201::1"))
    (is (text-present? result "2"))
    (is (text-present? result "Hey!"))))

(deftest test-contact-item-render-no-preview
  (let [props #js {:navigation #js {:navigate (fn [])}}
        result (contacts-view/contact-item-render
                props "c2"
                {:address "201::2"}
                theme/light nil)]
    (is (text-present? result "201::2"))
    (is (text-present? result "2"))
    (is (not (text-present? result "Hey!")))))

(deftest test-contact-item-render-with-unread
  (let [props #js {:navigation #js {:navigate (fn [])}}
        result (contacts-view/contact-item-render
                props "c3"
                {:address "201::3" :unread-count 5}
                theme/light nil)]
    (is (text-present? result "201::3"))
    (is (text-present? result "5"))))

(deftest test-contact-item-render-unread-99plus
  (let [props #js {:navigation #js {:navigate (fn [])}}
        result (contacts-view/contact-item-render
                props "c4"
                {:address "201::4" :unread-count 100}
                theme/light nil)]
    (is (text-present? result "99+")))
  (let [props #js {:navigation #js {:navigate (fn [])}}
        result (contacts-view/contact-item-render
                props "c5"
                {:address "201::5" :unread-count 999}
                theme/light nil)]
    (is (text-present? result "99+"))))

(deftest test-contact-item-render-no-unread-when-zero
  (let [props #js {:navigation #js {:navigate (fn [])}}
        result (contacts-view/contact-item-render
                props "c6"
                {:address "201::6" :unread-count 0}
                theme/light nil)]
    (is (text-present? result "201::6"))
    (is (not (text-present? result "0")))))

(deftest test-contact-item-render-no-unread-when-missing
  (let [props #js {:navigation #js {:navigate (fn [])}}
        result (contacts-view/contact-item-render
                props "c7"
                {:address "201::7"}
                theme/light nil)]
    (is (text-present? result "201::7"))
    (is (not (text-present? result "0")))))

(deftest test-contact-item-render-with-name
  (let [props #js {:navigation #js {:navigate (fn [])}}
        result (contacts-view/contact-item-render
                props "c8"
                {:address "201::8" :name "Alice"}
                theme/light nil)]
    (is (text-present? result "Alice"))
    (is (text-present? result "201::8"))
    (is (text-present? result "A"))))

(deftest test-contact-item-render-with-name-and-preview
  (let [props #js {:navigation #js {:navigate (fn [])}}
        result (contacts-view/contact-item-render
                props "c9"
                {:address "201::9" :name "Bob" :last-message {:text "Hello!"}}
                theme/light nil)]
    (is (text-present? result "Bob"))
    (is (text-present? result "201::9"))
    (is (text-present? result "Hello!"))
    (is (text-present? result "B"))))

(deftest test-contact-item-render-avatar-uses-name-letter
  (let [props #js {:navigation #js {:navigate (fn [])}}
        result (contacts-view/contact-item-render
                props "c10"
                {:address "201::10" :name "Charlie"}
                theme/light nil)]
    (is (text-present? result "C"))
    (is (not (text-present? result "2")))))

(deftest test-contacts-smoke
  (let [result (contacts-view/contacts #js {:navigation #js {:navigate (fn [])}})]
    (is (some? result))))

(deftest test-contact-item-smoke
  (let [props #js {:navigation #js {:navigate (fn [])}}
        result (contacts-view/contact-item props "c1" {:address "201::1"} nil)]
    (is (some? result))))

(deftest test-contacts-add-contact
  (rf/dispatch-sync [:contacts/add-contact {:address "201::3"}])
  (let [contacts (get-in @rdb/app-db [:messenger :contacts])]
    (is (= 1 (count contacts)))
    (let [[cid c] (first contacts)]
      (is (not (contains? c :name)))
      (is (= "201::3" (:address c)))
      (is (string? cid)))))

(deftest test-contacts-set-current
  (let [cid "my-contact"]
    (reset! rdb/app-db (assoc-in app-db [:messenger :contacts cid]
                                 {:address "201::4"}))
    (rf/dispatch-sync [:contacts/set-current-contact cid])
    (is (= cid (get-in @rdb/app-db [:messenger :current-contact])))))
