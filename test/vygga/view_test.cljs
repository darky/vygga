(ns vygga.view-test
  (:require
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [re-frame.core :as rf]
   [re-frame.db :as rdb]
   [vygga.events]
   [vygga.subs]
   [vygga.view :as view]
   [vygga.db :refer [app-db]]
   [vygga.theme :as theme]
   [vygga.view-test-utils :refer [setup-view-tests captured]]))

(use-fixtures :each (fn [t] (setup-view-tests) (t)))

;; ---- Pure function tests ----

(deftest test-status-label-running
  (let [[color label] (view/status-label :running theme/light)]
    (is (= (:success theme/light) color))
    (is (= "Connected" label))))

(deftest test-status-label-starting
  (let [[color label] (view/status-label :starting theme/light)]
    (is (= (:warning theme/light) color))
    (is (= "Connecting..." label))))

(deftest test-status-label-stopping
  (let [[color label] (view/status-label :stopping theme/light)]
    (is (= (:warning theme/light) color))
    (is (= "Disconnecting..." label))))

(deftest test-status-label-stopped
  (let [[color label] (view/status-label :stopped theme/light)]
    (is (= (:error theme/light) color))
    (is (= "Disconnected" label))))

(deftest test-status-label-unknown
  (let [[color label] (view/status-label :some-unknown-status theme/light)]
    (is (= (:error theme/light) color))
    (is (= "Disconnected" label))))

;; ---- Helper for checking text in hiccup trees ----

(defn text-in-tree [root]
  (filter string? (tree-seq vector? seq root)))

(defn text-present? [root s]
  (some #(= s %) (text-in-tree root)))

(deftest test-message-bubble-sent-text
  (let [result (view/message-bubble-render
                {:id "m1" :text "Hello" :from-me true :status :sent :cid "c1"}
                theme/light)]
    (is (text-present? result "Hello"))
    (is (text-present? result "\u2713"))
    (is (not (text-present? result "Failed")))
    (is (not (text-present? result "Resend")))))

(deftest test-message-bubble-received-text
  (let [result (view/message-bubble-render
                {:id "m2" :text "Hi" :from-me false :status nil :cid "c1"}
                theme/light)]
    (is (text-present? result "Hi"))
    (is (not (text-present? result "\u2713")))
    (is (not (text-present? result "Failed")))
    (is (not (text-present? result "Resend")))))

(deftest test-message-bubble-failed-text
  (let [result (view/message-bubble-render
                {:id "m3" :text "Oops" :from-me true :status :failed :cid "c1"}
                theme/light)]
    (is (text-present? result "Oops"))
    (is (text-present? result "! Failed"))
    (is (text-present? result "Resend"))))

(deftest test-message-bubble-align-sent
  (let [result (view/message-bubble-render
                {:id "m1" :text "Hello" :from-me true :status :sent :cid "c1"}
                theme/light)]
    (is (= :flex-end (get-in result [2 :style :align-items])))))

(deftest test-message-bubble-align-received
  (let [result (view/message-bubble-render
                {:id "m2" :text "Hi" :from-me false :status nil :cid "c1"}
                theme/light)]
    (is (= :flex-start (get-in result [2 :style :align-items])))))

(deftest test-contact-item-render-basic
  (let [props #js {:navigation #js {:navigate (fn [])}}
        result (view/contact-item-render
                props "c1"
                {:address "201::1" :last-message {:text "Hey!"}}
                theme/light)]
    (is (text-present? result "201::1"))
    (is (text-present? result "2"))
    (is (text-present? result "Hey!"))))

(deftest test-contact-item-render-no-preview
  (let [props #js {:navigation #js {:navigate (fn [])}}
        result (view/contact-item-render
                props "c2"
                {:address "201::2"}
                theme/light)]
    (is (text-present? result "201::2"))
    (is (text-present? result "2"))
    (is (not (text-present? result "Hey!")))))

;; ---- Component smoke tests ----

(deftest test-status-indicator-smoke
  (let [result (view/status-indicator #js {:navigation #js {:navigate (fn [])}})]
    (is (some? result))))

(deftest test-settings-smoke
  (let [result (view/settings)]
    (is (some? result))))

(deftest test-contacts-smoke
  (let [result (view/contacts #js {:navigation #js {:navigate (fn [])}})]
    (is (some? result))))

(deftest test-chat-smoke
  (let [result (view/chat)]
    (is (some? result))))

(deftest test-root-smoke
  (let [result (view/root)]
    (is (some? result))))

;; ---- Event dispatching integration tests ----

(deftest test-settings-start-dispatches
  (reset! rdb/app-db (assoc-in app-db [:yggstack :status] :stopped))
  (rf/dispatch-sync [:yggstack/start])
  (is (= :starting (get-in @rdb/app-db [:yggstack :status])))
  (is (contains? @captured :yggstack/generate-key)))

(deftest test-settings-stop-dispatches
  (reset! rdb/app-db (assoc-in app-db [:yggstack :status] :running))
  (rf/dispatch-sync [:yggstack/stop])
  (is (= :stopping (get-in @rdb/app-db [:yggstack :status])))
  (is (contains? @captured :yggstack/stop-daemon)))

(deftest test-settings-add-peer
  (rf/dispatch-sync [:yggstack/add-peer "tls://newpeer:443"])
  (is (some #(= "tls://newpeer:443" %) (get-in @rdb/app-db [:yggstack :peers]))))

(deftest test-settings-remove-peer
  (let [target (first (get-in @rdb/app-db [:yggstack :peers]))]
    (rf/dispatch-sync [:yggstack/remove-peer target])
    (is (not-any? #(= target %) (get-in @rdb/app-db [:yggstack :peers])))))

(deftest test-contacts-add-contact
  (rf/dispatch-sync [:messenger/add-contact {:address "201::3"}])
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
    (rf/dispatch-sync [:messenger/set-current-contact cid])
    (is (= cid (get-in @rdb/app-db [:messenger :current-contact])))))

(deftest test-chat-send-message
  (let [cid "test-contact"
        db-with-contact (-> app-db
                            (assoc-in [:messenger :contacts cid]
                                      {:address "201::5" :messages []})
                            (assoc-in [:yggstack :address] "201::1")
                            (assoc-in [:yggstack :private-key] "privkey")
                            (assoc-in [:yggstack :public-key] "pubkey"))]
    (reset! rdb/app-db db-with-contact)
    (rf/dispatch-sync [:messenger/send-message cid "Hello World"])
    (let [msg (first (get-in @rdb/app-db [:messenger :contacts cid :messages]))]
      (is (= "Hello World" (:text msg)))
      (is (true? (:from-me msg)))
      (is (= :sending (:status msg))))))

(deftest test-navigation-set-root-state
  (let [nav-state {:some :data}]
    (rf/dispatch-sync [:navigation/set-root-state nav-state])
    (is (= nav-state (get-in @rdb/app-db [:navigation :root-state])))))

(deftest test-yggstack-update-peer-count
  (rf/dispatch-sync [:yggstack/update-peer-count 42])
  (is (= 42 (get-in @rdb/app-db [:yggstack :peer-count]))))

(deftest test-yggstack-update-address
  (rf/dispatch-sync [:yggstack/update-address "201:abcd::1234"])
  (is (= "201:abcd::1234" (get-in @rdb/app-db [:yggstack :address]))))
