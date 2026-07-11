(ns vygga.yggstack-test
  (:require
   [cljs.test :as t :refer-macros [deftest is testing]]
   [vygga.yggstack :as ygg]))

(def sample-private-key "abc123")
(def sample-peers ["tls://peer1:443" "tls://peer2:1337"])

(deftest test-extract-private-key
  (testing "extracts PrivateKey from valid JSON"
    (let [config (str "{\"PrivateKey\": \"" sample-private-key "\"}")
          key (ygg/extract-private-key config)]
      (is (= sample-private-key key))))
  (testing "returns nil for invalid JSON"
    (is (nil? (ygg/extract-private-key "not json")))
    (is (nil? (ygg/extract-private-key nil))))
  (testing "returns nil when PrivateKey is missing"
    (is (nil? (ygg/extract-private-key "{\"foo\": \"bar\"}")))))

(deftest test-build-config-json
  (testing "generates parseable JSON with correct PrivateKey"
    (let [config (ygg/build-config-json sample-private-key sample-peers)
          parsed (js/JSON.parse config)]
      (is (= sample-private-key (.-PrivateKey parsed)))
      (is (nil? (.-Certificate parsed)))
      (is (= "none" (.-AdminListen parsed)))))
  (testing "includes peers as JSON array"
    (let [config (ygg/build-config-json sample-private-key sample-peers)
          parsed (js/JSON.parse config)
          peers (.-Peers parsed)]
      (is (= 2 (.-length peers)))
      (is (= "tls://peer1:443" (aget peers 0)))
      (is (= "tls://peer2:1337" (aget peers 1)))))
  (testing "works with empty peers"
    (let [config (ygg/build-config-json sample-private-key [])
          parsed (js/JSON.parse config)
          peers (.-Peers parsed)]
      (is (= 0 (.-length peers)))))
  (testing "Listen section is empty (no incoming connections on mobile)"
    (let [config (ygg/build-config-json sample-private-key sample-peers)
          parsed (js/JSON.parse config)
          listen (.-Listen parsed)]
      (is (= 0 (.-length listen))))))

(def mod-calls (atom []))

(defn- reset-calls! []
  (reset! mod-calls []))

(defn make-mock-module []
  #js {:generateConfig (fn [] (swap! mod-calls conj :generateConfig) (js/Promise.resolve "{\"PrivateKey\":\"test\"}"))
       :start          (fn [c s n] (swap! mod-calls conj [:start c s n]) (js/Promise.resolve))
       :stop           (fn [] (swap! mod-calls conj :stop) (js/Promise.resolve))
       :retryPeersNow  (fn [] (swap! mod-calls conj :retryPeersNow) (js/Promise.resolve))
       :getPeersJSON   (fn [] (swap! mod-calls conj :getPeersJSON) (js/Promise.resolve "[]"))
       :getAddress     (fn [] (swap! mod-calls conj :getAddress) (js/Promise.resolve "201::1"))
       :getPublicKey   (fn [] (swap! mod-calls conj :getPublicKey) (js/Promise.resolve "pubkey123"))})

(deftest test-generate-config
  (t/async done
    (reset-calls!)
    (with-redefs [vygga.yggstack/native-module (make-mock-module)]
      (-> (ygg/generate-config)
          (.then (fn [result]
                   (is (= "{\"PrivateKey\":\"test\"}" result))
                   (is (= [:generateConfig] @mod-calls))
                   (done)))))))

(deftest test-start
  (t/async done
    (reset-calls!)
    (with-redefs [vygga.yggstack/native-module (make-mock-module)]
      (-> (ygg/start "config" "127.0.0.1:1080" "")
          (.then (fn [_]
                   (is (= [[:start "config" "127.0.0.1:1080" ""]] @mod-calls))
                   (done)))))))

(deftest test-stop
  (t/async done
    (reset-calls!)
    (with-redefs [vygga.yggstack/native-module (make-mock-module)]
      (-> (ygg/stop)
          (.then (fn [_]
                   (is (= [:stop] @mod-calls))
                   (done)))))))

(deftest test-get-peers
  (t/async done
    (reset-calls!)
    (with-redefs [vygga.yggstack/native-module (make-mock-module)]
      (-> (ygg/get-peers)
          (.then (fn [result]
                   (is (= "[]" result))
                   (is (= [:getPeersJSON] @mod-calls))
                   (done)))))))

(deftest test-get-address
  (t/async done
    (reset-calls!)
    (with-redefs [vygga.yggstack/native-module (make-mock-module)]
      (-> (ygg/get-address)
          (.then (fn [result]
                   (is (= "201::1" result))
                   (is (= [:getAddress] @mod-calls))
                   (done)))))))

(deftest test-get-public-key
  (t/async done
    (reset-calls!)
    (with-redefs [vygga.yggstack/native-module (make-mock-module)]
      (-> (ygg/get-public-key)
          (.then (fn [result]
                   (is (= "pubkey123" result))
                   (is (= [:getPublicKey] @mod-calls))
                   (done)))))))

(deftest test-retry-peers-now
  (t/async done
    (reset-calls!)
    (with-redefs [vygga.yggstack/native-module (make-mock-module)]
      (-> (ygg/retry-peers-now)
          (.then (fn [_]
                   (is (= [:retryPeersNow] @mod-calls))
                   (done)))))))

(deftest test-start-foreground-service
  (let [log-msgs (atom [])
        orig-log js/console.log]
    (set! js/console.log (fn [& args] (swap! log-msgs conj (apply str args))))
    (ygg/start-foreground-service "Title" "Body")
    (set! js/console.log orig-log)
    (is (= 1 (count @log-msgs)))
    (is (.includes (first @log-msgs) "Foreground service active"))))

(deftest test-stop-foreground-service
  (let [log-msgs (atom [])
        orig-log js/console.log]
    (set! js/console.log (fn [& args] (swap! log-msgs conj (apply str args))))
    (ygg/stop-foreground-service)
    (set! js/console.log orig-log)
    (is (= 1 (count @log-msgs)))
    (is (.includes (first @log-msgs) "Foreground service stopped"))))

(deftest test-exit-app
  (let [log-msgs (atom [])
        orig-log js/console.log]
    (set! js/console.log (fn [& args] (swap! log-msgs conj (apply str args))))
    (with-redefs [vygga.yggstack/native-module #js {:stop (fn [] (swap! mod-calls conj :stop) (js/Promise.resolve))}]
      (reset-calls!)
      (ygg/exit-app))
    (set! js/console.log orig-log)
    (is (= 1 (count @log-msgs)))
    (is (= :stop (first @mod-calls)))))

(deftest test-open-battery-optimization-settings
  (is (nil? (ygg/open-battery-optimization-settings))))
