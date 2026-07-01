(ns example.app
  (:require [example.events]
            [example.subs]
            [example.widgets :refer [button]]
            [expo.root :as expo-root]
            ["expo-status-bar" :refer [StatusBar]]
            [re-frame.core :as rf]
            ["react-native" :as rn]
            [reagent.core :as r]
            ["@react-navigation/native" :as rnn]
            ["@react-navigation/native-stack" :as rnn-stack]
            ["@expo/vector-icons/Ionicons" :default Ionicons]))

(defonce shadow-splash (js/require "../assets/shadow-cljs.png"))
(defonce cljs-splash (js/require "../assets/cljs.png"))

(defonce Stack (rnn-stack/createNativeStackNavigator))

(defn status-indicator [^js props]
  (r/with-let [status (rf/subscribe [:yggstack/status])
               peer-count (rf/subscribe [:yggstack/peer-count])
               address (rf/subscribe [:yggstack/address])]
    (let [s @status
          [color label] (case s
                          :running ["#4CAF50" "Connected"]
                          :starting ["#FF9800" "Connecting..."]
                          :stopping ["#FF9800" "Disconnecting..."]
                          ["#F44336" "Disconnected"])
          peers @peer-count
          addr @address]
      [:> rn/View {:style {:flex-direction :row
                           :align-items :center
                           :justify-content :space-between
                           :padding-horizontal 16
                           :padding-vertical 8
                           :background-color (str color "15")
                           :border-bottom-width 1
                           :border-bottom-color "#e0e0e0"}}
       [:> rn/View {:style {:flex-direction :row :align-items :center}}
        [:> rn/View {:style {:width 12 :height 12 :border-radius 6
                             :background-color color :margin-right 8}}]
        [:> rn/Text {:style {:font-size 14 :font-weight :600 :color color}}
         label]]
       [:> rn/View {:style {:flex-direction :row :align-items :center}}
        [:> rn/TouchableOpacity {:on-press #(-> props .-navigation (.navigate "Settings"))
                                 :style {:flex-direction :row :align-items :center}}
         [:> rn/Text {:style {:font-size 13 :color "#666" :margin-right 8}}
          (str "Peers: " peers)]
         [:> Ionicons {:name "settings-outline" :size 20 :color "#666"}]]
        (when (and addr (not= addr ""))
          [:> rn/Text {:style {:font-size 11 :color "#999" :margin-left 8}} (subs addr 0 15)])]])))

(defn home [^js props]
  (r/with-let [counter (rf/subscribe [:get-counter])
               tap-enabled? (rf/subscribe [:counter-tappable?])]
    [:> rn/View {:style {:flex 1 :background-color :white}}
     [status-indicator props]
     [:> rn/View {:style {:flex 1
                          :padding-vertical 30
                          :justify-content :space-between
                          :align-items :center}}
      [:> rn/View {:style {:align-items :center}}
       [:> rn/Text {:style {:font-weight :bold
                            :font-size 72
                            :color :blue
                            :margin-bottom 20}} @counter]
       [button {:on-press #(rf/dispatch [:inc-counter])
                :disabled? (not @tap-enabled?)
                :style {:background-color :blue}}
        "Tap me, I'll count"]]
      [:> rn/View {:style {:align-items :center}}
       [button {:on-press (fn [] (-> props .-navigation (.navigate "About")))}
        "Tap me, I'll navigate"]
       [button {:on-press (fn [] (-> props .-navigation (.navigate "Settings")))
                :style {:margin-top 10}}
        "Yggdrasil Settings"]]
      [:> rn/View
       [:> rn/View {:style {:flex-direction :row :align-items :center :margin-bottom 20}}
        [:> rn/Image {:style {:width 160 :height 160} :source cljs-splash}]
        [:> rn/Image {:style {:width 160 :height 160} :source shadow-splash}]]
       [:> rn/Text {:style {:font-weight :normal :font-size 15 :color :blue}}
        "Using: shadow-cljs+expo+reagent+re-frame"]]]
     [:> StatusBar {:style "auto"}]]))

(defn- about []
  (r/with-let [counter (rf/subscribe [:get-counter])]
    [:> rn/View {:style {:flex 1
                         :padding-vertical 50
                         :padding-horizontal 20
                         :justify-content :space-between
                         :align-items :flex-start
                         :background-color :white}}
     [:> rn/View {:style {:align-items :flex-start}}
      [:> rn/Text {:style {:font-weight :bold :font-size 54 :color :blue :margin-bottom 20}}
       "About Example App"]
      [:> rn/Text {:style {:font-weight :bold :font-size 20 :color :blue :margin-bottom 20}}
       (str "Counter is at: " @counter)]
      [:> rn/Text {:style {:font-weight :normal :font-size 15 :color :blue}}
       "Built with React Native, Expo, Reagent, re-frame, and React Navigation"]]
     [:> StatusBar {:style "auto"}]]))

(defn- settings []
  (r/with-let [status (rf/subscribe [:yggstack/status])
               peers (rf/subscribe [:yggstack/peers])
               address (rf/subscribe [:yggstack/address])
               peer-count (rf/subscribe [:yggstack/peer-count])
               *new-peer (r/atom "")]
    [:> rn/View {:style {:flex 1 :background-color :white}}
     [:> rn/ScrollView {:style {:flex 1 :padding 16}}
      [:> rn/View {:style {:background-color "#f5f5f5" :padding 16 :border-radius 12 :margin-bottom 16}}
       [:> rn/Text {:style {:font-size 18 :font-weight :bold :margin-bottom 12}}
        "Yggdrasil Network"]
       (let [s @status
             [color label] (case s
                             :running ["#4CAF50" "Connected"]
                             :starting ["#FF9800" "Connecting..."]
                             :stopping ["#FF9800" "Disconnecting..."]
                             ["#F44336" "Disconnected"])]
         [:> rn/View {:style {:flex-direction :row :align-items :center :margin-bottom 8}}
          [:> rn/View {:style {:width 14 :height 14 :border-radius 7
                               :background-color color :margin-right 8}}]
          [:> rn/Text {:style {:font-size 16 :font-weight :500}} label]])
       [:> rn/Text {:style {:font-size 13 :color "#666" :margin-bottom 4}}
        (str "Connected Peers: " @peer-count)]
       (when-let [addr @address]
         [:> rn/Text {:style {:font-size 12 :color "#999"}}
          (str "IPv6: " addr)])]

      (let [s @status]
        (if (= s :running)
          [button {:on-press #(rf/dispatch [:yggstack/stop])
                   :style {:background-color "#F44336" :margin-bottom 16}}
           "Stop Yggdrasil"]
          [button {:on-press #(rf/dispatch [:yggstack/start])
                   :disabled? (= s :starting)
                   :style {:background-color "#4CAF50" :margin-bottom 16}}
           (if (= s :starting) "Starting..." "Start Yggdrasil")]))

      [:> rn/Text {:style {:font-size 16 :font-weight :bold :margin-bottom 8}}
       (str "Peers (" (count @peers) ")")]
      (doall
        (for [[i uri] (map-indexed vector @peers)]
          [:> rn/View {:key (str i)
                       :style {:flex-direction :row :align-items :center
                               :background-color "#fafafa" :padding 8
                               :border-radius 8 :margin-bottom 4}}
           [:> rn/Text {:style {:flex 1 :font-size 12 :color "#333"}} uri]
           [:> rn/TouchableOpacity {:on-press #(rf/dispatch [:yggstack/remove-peer uri])
                                    :style {:padding 4}}
            [:> rn/Text {:style {:color "#F44336" :font-size 16}} "✕"]]]))

      [:> rn/View {:style {:flex-direction :row :align-items :center :margin-top 8 :margin-bottom 16}}
       [:> rn/TextInput {:style {:flex 1 :border-width 1 :border-color "#ccc"
                                 :border-radius 8 :padding 8 :font-size 13 :margin-right 8}
                         :placeholder "tls://host:port"
                         :value @*new-peer
                         :on-change-text #(reset! *new-peer %)}]
       [:> rn/TouchableOpacity {:style {:background-color :blue :padding 10 :border-radius 8}
                                :on-press (fn []
                                            (let [uri @*new-peer]
                                              (when (seq uri)
                                                (rf/dispatch [:yggstack/add-peer uri])
                                                (reset! *new-peer ""))))}
        [:> rn/Text {:style {:color :white :font-weight :600}} "Add"]]]]

     [:> StatusBar {:style "auto"}]]))

(defn root []
  (r/with-let [!root-state (rf/subscribe [:navigation/root-state])
               save-root-state! (fn [^js state]
                                  (rf/dispatch [:navigation/set-root-state state]))
               add-listener! (fn [^js navigation-ref]
                               (when navigation-ref
                                 (.addListener navigation-ref "state" save-root-state!)))]
    [:> rnn/NavigationContainer {:ref add-listener!
                                 :initialState (when @!root-state (-> @!root-state .-data .-state))}
     [:> Stack.Navigator
      [:> Stack.Screen {:name "Home"
                        :component (fn [props] (r/as-element [home props]))
                        :options {:title "Vygga"}}]
      [:> Stack.Screen {:name "About"
                        :component (fn [props] (r/as-element [about props]))
                        :options {:title "About"}}]
      [:> Stack.Screen {:name "Settings"
                        :component (fn [props] (r/as-element [settings props]))
                        :options {:title "Yggdrasil Settings"}}]]]))

(defn start
  {:dev/after-load true}
  []
  (expo-root/render-root (r/as-element [root])))

(defn init []
  (rf/dispatch-sync [:initialize-db])
  (js/setTimeout #(rf/dispatch [:yggstack/start]) 1000)
  (start))
