(ns vygga.network-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [vygga.network :as net]))

(defn mock-state [connected]
  #js {:isConnected connected})

(defn with-captured-log [f]
  (let [msgs (atom [])
        orig js/console.log]
    (set! js/console.log (fn [& args]
                           (swap! msgs conj (apply str args))
                           (apply orig args)))
    (try
      (f msgs)
      (finally
        (set! js/console.log orig)))))

(deftest test-handle-network-change-initial
  (testing "initial nil state does not log"
    (with-captured-log
      (fn [log-msgs]
        (let [prev (atom nil)]
          (testing "connected"
            (net/handle-network-change prev (mock-state true))
            (is (true? @prev))
            (is (zero? (count @log-msgs))))
          (reset! prev nil)
          (testing "disconnected"
            (net/handle-network-change prev (mock-state false))
            (is (false? @prev))
            (is (zero? (count @log-msgs)))))))))

(deftest test-handle-network-change-transitions
  (testing "disconnected -> connected logs restored"
    (with-captured-log
      (fn [log-msgs]
        (let [prev (atom false)]
          (net/handle-network-change prev (mock-state true))
          (is (true? @prev))
          (is (= 1 (count @log-msgs)))
          (is (.includes (first @log-msgs) "Network restored"))))))

  (testing "connected -> disconnected logs disconnected"
    (with-captured-log
      (fn [log-msgs]
        (let [prev (atom true)]
          (net/handle-network-change prev (mock-state false))
          (is (false? @prev))
          (is (= 1 (count @log-msgs)))
          (is (.includes (first @log-msgs) "Network disconnected"))))))

  (testing "connected -> connected no log"
    (let [prev (atom true)]
      (net/handle-network-change prev (mock-state true))
      (is (true? @prev))))

  (testing "disconnected -> disconnected no log"
    (let [prev (atom false)]
      (net/handle-network-change prev (mock-state false))
      (is (false? @prev)))))
