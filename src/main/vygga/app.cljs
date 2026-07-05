(ns vygga.app
  (:require [vygga.events]
            [vygga.subs]
            [vygga.notifications :as notifications]
            [vygga.widgets :refer [button]]
            [expo.root :as expo-root]
            ["expo-status-bar" :refer [StatusBar]]
            [re-frame.core :as rf]
            ["react-native" :as rn]
            [reagent.core :as r]
            ["@react-navigation/native" :as rnn]
            ["@react-navigation/native-stack" :as rnn-stack]
            ["react-native-safe-area-context" :refer [useSafeAreaInsets]]
            ["@expo/vector-icons/Ionicons" :default Ionicons]))

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

(defn- about []
  [:> rn/View {:style {:flex 1
                       :padding-vertical 50
                       :padding-horizontal 20
                       :justify-content :space-between
                       :align-items :flex-start
                       :background-color :white}}
   [:> rn/View {:style {:align-items :flex-start}}
    [:> rn/Text {:style {:font-weight :bold :font-size 54 :color :blue :margin-bottom 20}}
     "About Example App"]
    [:> rn/Text {:style {:font-weight :normal :font-size 15 :color :blue}}
     "Built with React Native, Expo, Reagent, re-frame, and React Navigation"]]
   [:> StatusBar {:style "auto"}]])

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
           (if (= s :starting) "Starting..." "Start Yggdrasil")])

        [:> rn/View {:style {:margin-bottom 16}}
         [button {:on-press #(rf/dispatch [:yggstack/generate-new-identity])
                  :disabled? (contains? #{:starting :stopping} @status)
                  :style {:background-color "#FF9800"}}
          "Generate New Identity"]])

      [:> rn/View {:style {:border-top-width 1 :border-top-color "#e0e0e0" :padding-top 16 :margin-bottom 16}}
       [:> rn/Text {:style {:font-size 16 :font-weight :bold :margin-bottom 12}}
        "Background Service"]
       [button {:on-press #(rf/dispatch [:yggstack/battery-opt-out])
                :style {:background-color "#FF9800" :margin-bottom 8}}
        "Disable Battery Optimization"]
       [button {:on-press #(rf/dispatch [:app/exit])
                :style {:background-color "#F44336"}}
        "Exit App"]]

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

;; ---- Contact List Screen ----

(defn- contact-item [^js props contact-id {:keys [name address last-message]}]
  (let [preview (when last-message (:text last-message) "")]
    [:> rn/TouchableOpacity
     {:key contact-id
      :style {:flex-direction :row :align-items :center
              :padding 14 :border-bottom-width 1
              :border-bottom-color "#f0f0f0"}
      :on-press #(do
                   (rf/dispatch [:messenger/set-current-contact contact-id])
                   (-> props .-navigation (.navigate "Chat")))}
     [:> rn/View {:style {:width 44 :height 44 :border-radius 22
                          :background-color :blue :justify-content :center
                          :align-items :center :margin-right 12}}
      [:> rn/Text {:style {:color :white :font-size 18 :font-weight :bold}}
       (-> name .charAt (.toUpperCase))]]
     [:> rn/View {:style {:flex 1}}
      [:> rn/Text {:style {:font-size 16 :font-weight :600}} name]
      (when (seq preview)
        [:> rn/Text {:style {:font-size 13 :color "#999" :margin-top 2}
                     :number-of-lines 1} preview])]
     [:> rn/Text {:style {:font-size 11 :color "#ccc"}} address]]))

(defn- contacts [props]
  (r/with-let [contacts-map (rf/subscribe [:messenger/contacts])
               server-running (rf/subscribe [:messenger/server-running])
               *show-add (r/atom false)
               *new-name (r/atom "")
               *new-addr (r/atom "")]
    [:> rn/View {:style {:flex 1 :background-color :white}}
     [status-indicator props]
     [:> rn/View {:style {:flex-direction :row :align-items :center
                          :justify-content :space-between
                          :padding-horizontal 16 :padding-vertical 10
                          :background-color (str (if @server-running "#4CAF50" "#F44336") "10")
                          :border-bottom-width 1 :border-bottom-color "#e0e0e0"}}
      [:> rn/View {:style {:flex-direction :row :align-items :center}}
       [:> rn/View {:style {:width 10 :height 10 :border-radius 5
                            :background-color (if @server-running "#4CAF50" "#F44336")
                            :margin-right 8}}]
       [:> rn/Text {:style {:font-size 14 :font-weight :500
                            :color (if @server-running "#4CAF50" "#F44336")}}
        (if @server-running "Messenger Online" "Messenger Offline")]]
      [:> rn/Pressable {:on-press #(if @server-running
                                     (rf/dispatch [:messenger/stop-server])
                                     (rf/dispatch [:messenger/start-server]))
                        :style {:padding-horizontal 12 :padding-vertical 4
                                :border-radius 12 :border-width 1
                                :border-color (if @server-running "#F44336" "#4CAF50")}}
       [:> rn/Text {:style {:font-size 12 :color (if @server-running "#F44336" "#4CAF50")}}
        (if @server-running "Stop" "Start")]]]
     [:> rn/ScrollView {:style {:flex 1}}
      (let [sorted (sort-by (fn [[_ c]] (:name c)) (vec @contacts-map))]
        (if (empty? sorted)
          [:> rn/View {:style {:padding 40 :align-items :center}}
           [:> rn/Text {:style {:font-size 16 :color "#999"}}
            "No contacts yet"]]
          (doall
           (for [[cid c] sorted]
             ^{:key cid} [contact-item props cid c]))))]
     [:> rn/Pressable {:style {:position :absolute :bottom 20 :right 20
                               :width 56 :height 56 :border-radius 28
                               :background-color :blue :justify-content :center
                               :align-items :center :elevation 4}
                       :on-press #(reset! *show-add true)}
      [:> rn/Text {:style {:color :white :font-size 28}} "+"]]
     (when @*show-add
       [:> rn/View {:style {:position :absolute :top 0 :left 0 :right 0 :bottom 0
                            :background-color "rgba(0,0,0,0.5)"
                            :justify-content :center :align-items :center}}
        [:> rn/View {:style {:background-color :white :border-radius 16
                             :padding 24 :width "85%"}}
         [:> rn/Text {:style {:font-size 20 :font-weight :bold :margin-bottom 16}}
          "Add Contact"]
         [:> rn/TextInput {:style {:border-width 1 :border-color "#ddd"
                                   :border-radius 8 :padding 12 :font-size 15
                                   :margin-bottom 12}
                           :placeholder "Display name"
                           :value @*new-name
                           :on-change-text #(reset! *new-name %)}]
         [:> rn/TextInput {:style {:border-width 1 :border-color "#ddd"
                                   :border-radius 8 :padding 12 :font-size 15
                                   :margin-bottom 20}
                           :placeholder "Yggdrasil IPv6 (e.g. 201:1234::1)"
                           :value @*new-addr
                           :on-change-text #(reset! *new-addr %)}]
         [:> rn/View {:style {:flex-direction :row :justify-content :flex-end}}
          [:> rn/Pressable {:on-press #(reset! *show-add false)
                            :style {:padding-horizontal 16 :padding-vertical 10
                                    :margin-right 12}}
           [:> rn/Text {:style {:font-size 15 :color "#999"}} "Cancel"]]
          [:> rn/Pressable {:style {:background-color :blue :padding-horizontal 20
                                    :padding-vertical 10 :border-radius 8}
                            :on-press (fn []
                                        (let [name @*new-name addr @*new-addr]
                                          (when (and (seq name) (seq addr))
                                            (rf/dispatch [:messenger/add-contact
                                                          {:name name :address addr}]))
                                          (reset! *new-name "")
                                          (reset! *new-addr "")
                                          (reset! *show-add false)))}
           [:> rn/Text {:style {:color :white :font-weight :600}} "Add"]]]]])
     [:> StatusBar {:style "auto"}]]))

;; ---- Chat Screen ----

(defn- msg->js
  [m]
  #js {:id (:id m)
       :text (:text m)
       :fromMe (:from-me m)
       :status (when (:status m) (name (:status m)))
       :ts (:ts m)})

(defn- message-bubble [{:keys [id text from-me status cid]}]
  [:> rn/View {:style {:align-items (if from-me :flex-end :flex-start)
                       :margin-bottom 8}}
   [:> rn/View {:style {:max-width "75%"
                        :background-color (if from-me "#007AFF" "#E8E8E8")
                        :border-radius 16 :padding 12}}
    [:> rn/Text {:style {:font-size 15
                         :color (if from-me :white "#333")}}
     text]
    (when (and from-me (= status :sent))
      [:> rn/Text {:style {:font-size 12 :color "#8ED1FF"
                           :text-align :right :margin-top 4}}
       "✓"])]
   (when (and from-me (= status :failed))
     [:> rn/View {:style {:flex-direction :row :align-items :center
                          :margin-top 4}}
      [:> rn/Text {:style {:font-size 12 :color "#F44336" :margin-right 8}}
       "! Failed"]
      [:> rn/TouchableOpacity {:on-press #(rf/dispatch [:messenger/resend-message cid id])
                               :style {:padding-horizontal 10 :padding-vertical 4
                                       :border-radius 8 :border-width 1
                                       :border-color "#F44336"}}
       [:> rn/Text {:style {:font-size 12 :color "#F44336" :font-weight :600}}
        "Resend"]]])])

(defn- chat []
  (r/with-let [current-id (rf/subscribe [:messenger/current-contact])
               contacts (rf/subscribe [:messenger/contacts])
               has-more (rf/subscribe [:messenger/current-has-more])
               messages-loading (rf/subscribe [:messenger/messages-loading])
               *text (r/atom "")
               *flat-ref (r/atom nil)
               *at-bottom (r/atom true)
               *loaded-contact (r/atom nil)]
    (let [cid @current-id
          c (get @contacts cid)
          msgs (:messages c [])
          has-more? @has-more
          loading? @messages-loading
          insets (useSafeAreaInsets)]
      (when (and cid (not= cid @*loaded-contact))
        (reset! *loaded-contact cid)
        (rf/dispatch [:messenger/load-contact-messages]))
      [:> rn/View {:style {:flex 1 :background-color :white}}
       [:> rn/View {:style {:padding-horizontal 16 :padding-vertical 12
                            :border-bottom-width 1 :border-bottom-color "#e0e0e0"
                            :flex-direction :row :align-items :center}}
        [:> rn/Text {:style {:font-size 17 :font-weight :600 :flex 1}}
         (or (:name c) "Unknown")]
        [:> rn/Text {:style {:font-size 12 :color "#999"}} (:address c)]]
       (if (empty? msgs)
         [:> rn/View {:style {:flex 1 :padding 40 :align-items :center}}
          [:> rn/Text {:style {:font-size 15 :color "#999"}}
           (if loading? "Loading..." "No messages yet")]]
         [:> rn/FlatList
          {:data (clj->js (mapv msg->js (reverse msgs)))
           :key-extractor (fn [item] (.-id item))
           :inverted true
           :ref #(reset! *flat-ref %)
           :style {:flex 1 :padding 12}
           :on-content-size-change (fn []
                                     (when (and @*at-bottom @*flat-ref)
                                       (try (.scrollToEnd @*flat-ref #js {:animated false})
                                            (catch js/Error _))))
           :on-scroll (fn [e]
                        (let [offset (.-y (.-contentOffset e))]
                          (reset! *at-bottom (< offset 50))))
           :on-end-reached (fn []
                             (when has-more?
                               (rf/dispatch [:messenger/load-older-messages])))
           :on-end-reached-threshold 0.3
           :initial-num-to-render 20
           :max-to-render-per-batch 20
           :window-size 7
           :ListFooterComponent (when has-more?
                                  (fn []
                                    (r/as-element
                                     [:> rn/View {:style {:padding 20 :align-items :center}}
                                      [:> rn/Text {:style {:font-size 13 :color "#999"}}
                                       (if loading? "Loading..." "Pull up to load more")]])))
           :render-item (fn [info]
                          (let [item (.-item info)
                                id (.-id item)
                                text (.-text item)
                                from-me (.-fromMe item)
                                status (when (.-status item) (keyword (.-status item)))]
                            (r/as-element
                             [message-bubble
                              {:id id :text text :from-me from-me
                               :status status :cid cid
                               :on-resend #(rf/dispatch [:messenger/resend-message cid id])}])))}])
       [:> rn/View {:style {:flex-direction :row :align-items :center
                            :padding 12 :border-top-width 1
                            :border-top-color "#e0e0e0"
                            :padding-bottom (+ 12 (.-bottom insets))}}
        [:> rn/TextInput {:style {:flex 1 :border-width 1 :border-color "#ddd"
                                  :border-radius 20 :padding-horizontal 16
                                  :padding-vertical 12 :font-size 15 :margin-right 8}
                          :placeholder "Type a message..."
                          :value @*text
                          :on-change-text #(reset! *text %)
                          :on-submit-editing #(when (seq @*text)
                                                (rf/dispatch [:messenger/send-message cid @*text])
                                                (reset! *text ""))}]
        [:> rn/Pressable {:style {:width 40 :height 40 :border-radius 20
                                  :background-color :blue :justify-content :center
                                  :align-items :center}
                          :on-press (fn []
                                      (let [t @*text]
                                        (when (seq t)
                                          (rf/dispatch [:messenger/send-message cid t])
                                          (reset! *text ""))))}
         [:> rn/Text {:style {:color :white :font-size 18}} "➤"]]]
       [:> StatusBar {:style "auto"}]])))

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
      [:> Stack.Screen {:name "Contacts"
                        :component (fn [props] (r/as-element [contacts props]))
                        :options {:title "Messenger"}}]
      [:> Stack.Screen {:name "Chat"
                        :component (fn [props] (r/as-element [chat props]))
                        :options {:title "Chat"}}]
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
  (notifications/init!)
  (start))
