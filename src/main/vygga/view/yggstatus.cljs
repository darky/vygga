(ns vygga.view.yggstatus
  (:require [vygga.subs]
            [vygga.theme :as theme]
            [re-frame.core :as rf]
            ["react-native" :as rn]
            [reagent.core :as r]
            ["expo-clipboard" :as Clipboard]
            ["@expo/vector-icons/Ionicons" :default Ionicons]))

(defn status-label [status peer-count t]
  (case status
    :running (if (pos? peer-count)
               [(:success t) "Connected"]
               [(:warning t) "Running"])
    :starting [(:warning t) "Connecting..."]
    :stopping [(:warning t) "Disconnecting..."]
    [(:error t) "Disconnected"]))

(defn status-indicator [^js props]
  (r/with-let [status (rf/subscribe [:yggstack/status])
               peer-count (rf/subscribe [:yggstack/peer-count])
               address (rf/subscribe [:yggstack/address])
               pref (rf/subscribe [:theme/preferred-scheme])
               t (theme/use-theme @pref)]
    (let [s @status
          peers @peer-count
          [color label] (status-label s peers t)
          addr @address]
      [:> rn/View {:style {:flex-direction :row
                           :align-items :center
                           :justify-content :space-between
                           :padding-horizontal 16
                           :padding-vertical 8
                           :background-color (str color "15")
                           :border-bottom-width 1
                           :border-bottom-color (:border t)}}
       [:> rn/View {:style {:flex-direction :row :align-items :center}}
        [:> rn/View {:style {:width 12 :height 12 :border-radius 6
                             :background-color color :margin-right 8}}]
        [:> rn/Text {:style {:font-size 14 :font-weight :600 :color color}}
         label]]
       [:> rn/View {:style {:flex-direction :row :align-items :center}}
        [:> rn/TouchableOpacity {:on-press #(-> props .-navigation (.navigate "Settings"))
                                 :style {:flex-direction :row :align-items :center}}
         [:> rn/Text {:style {:font-size 13 :color (:text-secondary t) :margin-right 8}}
          (str "Peers: " peers)]
         [:> Ionicons {:name "settings-outline" :size 20 :color (:text-secondary t)}]]
        (when (and addr (not= addr ""))
          [:> rn/TouchableOpacity {:on-press #(-> Clipboard (.setStringAsync addr))
                                   :style {:flex-direction :row :align-items :center :margin-left 8}}
           [:> rn/Text {:style {:font-size 11 :color (:text-tertiary t)}} (subs addr 0 15)]
           [:> Ionicons {:name "copy-outline" :size 12 :color (:text-tertiary t) :margin-left 4}]])]])))
