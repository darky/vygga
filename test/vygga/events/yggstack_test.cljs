(ns vygga.events.yggstack-test
  (:require
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [re-frame.core :as rf]
   [re-frame.db :as rdb]
   [vygga.db :refer [app-db default-peers]]
   [vygga.events.yggstack]))

(def captured (atom {}))

(defn mock-fx [key]
  (fn [opts] (swap! captured assoc key opts)))

(defn setup []
  (reset! captured {})
  (reset! rdb/app-db app-db)
  (rf/reg-fx :yggstack/start-daemon (mock-fx :yggstack/start-daemon))
  (rf/reg-fx :yggstack/generate-key (mock-fx :yggstack/generate-key))
  (rf/reg-fx :yggstack/load-and-start (mock-fx :yggstack/load-and-start))
  (rf/reg-fx :yggstack/regenerate-identity (mock-fx :yggstack/regenerate-identity))
  (rf/reg-fx :yggstack/stop-daemon (mock-fx :yggstack/stop-daemon))
  (rf/reg-fx :yggstack/retry-peers-now (mock-fx :yggstack/retry-peers-now))
  (rf/reg-fx :yggstack/refresh-peer-count (mock-fx :yggstack/refresh-peer-count))
  (rf/reg-fx :yggstack/start-foreground-service (mock-fx :yggstack/start-foreground-service))
  (rf/reg-fx :yggstack/stop-foreground-service (mock-fx :yggstack/stop-foreground-service))
  (rf/reg-fx :yggstack/battery-opt-out-fx (mock-fx :yggstack/battery-opt-out-fx)))

(use-fixtures :each (fn [t] (setup) (t)))

(deftest test-yggstack-start-with-key
  (let [privkey "test-private-key"
        db-with-key (assoc-in app-db [:yggstack :private-key] privkey)]
    (reset! rdb/app-db db-with-key)
    (rf/dispatch-sync [:yggstack/start])
    (is (= :starting (get-in @rdb/app-db [:yggstack :status])))
    (let [daemon-opts (get @captured :yggstack/start-daemon)]
      (is (map? daemon-opts))
      (is (:config-json daemon-opts))
      (let [parsed (js/JSON.parse (:config-json daemon-opts))]
        (is (= privkey (.-PrivateKey parsed)))))))

(deftest test-yggstack-start-without-key
  (rf/dispatch-sync [:yggstack/start])
  (is (= :starting (get-in @rdb/app-db [:yggstack :status])))
  (is (contains? @captured :yggstack/generate-key)))

(deftest test-yggstack-generate-new-identity
  (let [db-with-key (assoc-in app-db [:yggstack :private-key] "old-key")]
    (reset! rdb/app-db db-with-key)
    (rf/dispatch-sync [:yggstack/generate-new-identity])
    (is (nil? (get-in @rdb/app-db [:yggstack :private-key])))
    (is (contains? @captured :yggstack/regenerate-identity))))

(deftest test-yggstack-set-status-running
  (rf/dispatch-sync [:yggstack/set-status :running])
  (is (= :running (get-in @rdb/app-db [:yggstack :status])))
  (reset! captured {})
  (rf/dispatch-sync [:yggstack/start-foreground-service])
  (is (contains? @captured :yggstack/start-foreground-service)))

(deftest test-yggstack-set-status-stopped
  (rf/dispatch-sync [:yggstack/set-status :stopped])
  (is (= :stopped (get-in @rdb/app-db [:yggstack :status])))
  (reset! captured {})
  (rf/dispatch-sync [:yggstack/stop-foreground-service])
  (is (contains? @captured :yggstack/stop-foreground-service)))

(deftest test-yggstack-set-status-stopped-with-server
  (reset! rdb/app-db (assoc-in app-db [:messenger :server-running] true))
  (rf/dispatch-sync [:yggstack/set-status :stopped])
  (is (= :stopped (get-in @rdb/app-db [:yggstack :status])))
  (reset! captured {})
  (rf/dispatch-sync [:yggstack/stop-foreground-service])
  (is (contains? @captured :yggstack/stop-foreground-service)))

(deftest test-yggstack-stop
  (rf/dispatch-sync [:yggstack/stop])
  (is (= :stopping (get-in @rdb/app-db [:yggstack :status])))
  (is (contains? @captured :yggstack/stop-daemon)))

(deftest test-yggstack-on-network-restored-when-running
  (reset! rdb/app-db (assoc-in app-db [:yggstack :status] :running))
  (reset! captured {})
  (rf/dispatch-sync [:yggstack/on-network-restored])
  (is (contains? @captured :yggstack/retry-peers-now))
  (is (contains? @captured :yggstack/refresh-peer-count)))

(deftest test-yggstack-on-network-restored-when-stopped
  (reset! rdb/app-db (assoc-in app-db [:yggstack :status] :stopped))
  (reset! captured {})
  (rf/dispatch-sync [:yggstack/on-network-restored])
  (is (not (contains? @captured :yggstack/retry-peers-now)))
  (is (not (contains? @captured :yggstack/refresh-peer-count))))

(deftest test-yggstack-update-peer-count
  (rf/dispatch-sync [:yggstack/update-peer-count 5])
  (is (= 5 (get-in @rdb/app-db [:yggstack :peer-count]))))

(deftest test-yggstack-update-address
  (rf/dispatch-sync [:yggstack/update-address "201:abcd::1"])
  (is (= "201:abcd::1" (get-in @rdb/app-db [:yggstack :address]))))

(deftest test-yggstack-update-public-key
  (rf/dispatch-sync [:yggstack/update-public-key "pubkey123"])
  (is (= "pubkey123" (get-in @rdb/app-db [:yggstack :public-key]))))

(deftest test-yggstack-set-private-key
  (rf/dispatch-sync [:yggstack/set-private-key "mykey"])
  (is (= "mykey" (get-in @rdb/app-db [:yggstack :private-key]))))

(deftest test-yggstack-add-peer
  (rf/dispatch-sync [:yggstack/add-peer "tls://newpeer:443"])
  (is (some #(= "tls://newpeer:443" %) (get-in @rdb/app-db [:yggstack :peers])))
  (let [peers (get-in @rdb/app-db [:yggstack :peers])
        count-before (count peers)]
    (rf/dispatch-sync [:yggstack/add-peer (first default-peers)])
    (is (= count-before (count (get-in @rdb/app-db [:yggstack :peers]))))))

(deftest test-yggstack-remove-peer
  (let [target (first default-peers)]
    (rf/dispatch-sync [:yggstack/remove-peer target])
    (is (not-any? #(= target %) (get-in @rdb/app-db [:yggstack :peers])))))

(deftest test-yggstack-start-foreground-service
  (rf/dispatch-sync [:yggstack/start-foreground-service])
  (is (contains? @captured :yggstack/start-foreground-service)))

(deftest test-yggstack-stop-foreground-service
  (rf/dispatch-sync [:yggstack/stop-foreground-service])
  (is (contains? @captured :yggstack/stop-foreground-service)))

(deftest test-yggstack-battery-opt-out
  (rf/dispatch-sync [:yggstack/battery-opt-out])
  (is (contains? @captured :yggstack/battery-opt-out-fx)))
