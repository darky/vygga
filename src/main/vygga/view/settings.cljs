(ns vygga.view.settings
  (:require [vygga.events.theme]
            [vygga.events.yggstack]
            [vygga.events.app]
            [vygga.subs]
            [vygga.config :as config]
            [vygga.theme :as theme]
            [vygga.widgets :refer [button]]
            [vygga.view.yggstatus :refer [status-label]]
            ["expo-status-bar" :refer [StatusBar]]
            [re-frame.core :as rf]
            ["react-native" :as rn]
            [reagent.core :as r]
            ["expo-clipboard" :as Clipboard]
            ["@expo/vector-icons/Ionicons" :default Ionicons]))

(defn settings [props]
  (r/with-let [status (rf/subscribe [:yggstack/status])
               peers (rf/subscribe [:yggstack/peers])
               address (rf/subscribe [:yggstack/address])
               peer-count (rf/subscribe [:yggstack/peer-count])
               *new-peer (r/atom "")
               *peer-ref (r/atom nil)
               pref (rf/subscribe [:theme/preferred-scheme])
               t (theme/use-theme @pref)]
    [:> rn/View {:style {:flex 1 :background-color (:bg t)}}
     [:> rn/ScrollView {:style {:flex 1 :padding 16}}
      [:> rn/View {:style {:background-color (:bg-card t) :padding 16 :border-radius 12 :margin-bottom 16}}
       [:> rn/Text {:style {:font-size 18 :font-weight :bold :margin-bottom 12 :color (:text-primary t)}}
        "Yggdrasil Network"]
       (let [s @status
             [color label] (status-label s t)]
         [:> rn/View {:style {:flex-direction :row :align-items :center :margin-bottom 8}}
          [:> rn/View {:style {:width 14 :height 14 :border-radius 7
                               :background-color color :margin-right 8}}]
          [:> rn/Text {:style {:font-size 16 :font-weight :500 :color (:text-primary t)}} label]])
       [:> rn/Text {:style {:font-size 13 :color (:text-secondary t) :margin-bottom 4}}
        (str "Connected Peers: " @peer-count)]
       (when-let [addr @address]
         [:> rn/TouchableOpacity {:on-press #(-> Clipboard (.setStringAsync addr))
                                  :style {:flex-direction :row :align-items :center}}
          [:> rn/Text {:style {:font-size 12 :color (:text-tertiary t) :flex-shrink 1}}
           (str "IPv6: " addr)]
          [:> Ionicons {:name "copy-outline" :size 14 :color (:text-tertiary t) :margin-left 6}]])]

      [:> rn/View {:style {:background-color (:bg-card t) :padding 16 :border-radius 12 :margin-bottom 16}}
       [:> rn/Text {:style {:font-size 18 :font-weight :bold :margin-bottom 12 :color (:text-primary t)}}
        "Appearance"]
       [:> rn/View {:style {:flex-direction :row :align-items :center :justify-content :space-between}}
        [:> rn/Text {:style {:font-size 16 :color (:text-primary t)}} "Dark Mode"]
        [:> rn/Switch {:value (= @pref :dark)
                       :on-value-change #(rf/dispatch [:theme/set-scheme (if % :dark :light)])
                       :track-color {:true (:accent t) :false (:disabled t)}}]]]

      (let [s @status]
        (if (= s :running)
          [button {:on-press #(rf/dispatch [:yggstack/stop])
                   :style {:background-color (:error t) :margin-bottom 16}}
           "Stop Yggdrasil"]
          [button {:on-press #(rf/dispatch [:yggstack/start])
                   :disabled? (= s :starting)
                   :style {:background-color (:success t) :margin-bottom 16}}
           (if (= s :starting) "Starting..." "Start Yggdrasil")])

        [:> rn/View {:style {:margin-bottom 16}}
         [button {:on-press #(rf/dispatch [:yggstack/generate-new-identity])
                  :disabled? (contains? #{:starting :stopping} @status)
                  :style {:background-color (:warning t)}}
          "Generate New Identity"]])

      [:> rn/View {:style {:border-top-width 1 :border-top-color (:border t) :padding-top 16 :margin-bottom 16}}
       [:> rn/Text {:style {:font-size 16 :font-weight :bold :margin-bottom 12 :color (:text-primary t)}}
        "Background Service"]
       [button {:on-press #(rf/dispatch [:yggstack/battery-opt-out])
                :style {:background-color (:warning t) :margin-bottom 8}}
        "Disable Battery Optimization"]
       [button {:on-press #(rf/dispatch [:app/exit])
                :style {:background-color (:error t)}}
        "Exit App"]]

      [:> rn/Text {:style {:font-size 16 :font-weight :bold :margin-bottom 8 :color (:text-primary t)}}
       (str "Peers (" (count @peers) ")")]
      (doall
       (for [[i uri] (map-indexed vector @peers)]
         [:> rn/View {:key (str i)
                      :style {:flex-direction :row :align-items :center
                              :background-color (:bg-card-alt t) :padding 8
                              :border-radius 8 :margin-bottom 4}}
          [:> rn/Text {:style {:flex 1 :font-size 12 :color (:text-primary t)}} uri]
          [:> rn/TouchableOpacity {:on-press #(rf/dispatch [:yggstack/remove-peer uri])
                                   :style {:padding 4}}
           [:> rn/Text {:style {:color (:error t) :font-size 16}} "✕"]]]))

      [:> rn/View {:style {:flex-direction :row :align-items :center :margin-top 8 :margin-bottom 16}}
       [:> rn/TextInput {:key "peer-input"
                         :style {:flex 1 :border-width 1 :border-color (:border-input t)
                                 :border-radius 8 :padding 8 :font-size 13 :margin-right 8
                                 :color (:text-primary t)}
                         :placeholder "tls://host:port"
                         :placeholder-text-color (:text-tertiary t)
                         :default-value ""
                         :ref #(reset! *peer-ref %)
                         :on-change-text #(reset! *new-peer %)}]
       [:> rn/TouchableOpacity {:style {:background-color (:accent t) :padding 10 :border-radius 8}
                                :on-press (fn []
                                            (let [uri @*new-peer]
                                              (when (seq uri)
                                                (rf/dispatch [:yggstack/add-peer uri]))
                                              (reset! *new-peer "")
                                              (when-let [r @*peer-ref] (.clear r))))}
        [:> rn/Text {:style {:color (:text-inverse t) :font-weight :600}} "Add"]]]]

     (when config/log-enabled
       [:> rn/View {:style {:border-top-width 1 :border-top-color (:border t) :padding-top 16
                            :margin-bottom 16}}
        [:> rn/Text {:style {:font-size 16 :font-weight :bold :margin-bottom 12 :color (:text-primary t)}}
         "Debug"]
        [button {:on-press #(-> props .-navigation (.navigate "Debug"))
                 :style {:background-color (:accent t)}}
         "Open Debug Logs"]])

     [:> StatusBar {:style "auto"}]]))
