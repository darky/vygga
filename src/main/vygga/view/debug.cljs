(ns vygga.view.debug
  (:require [vygga.subs]
            [vygga.theme :as theme]
            [vygga.view.widgets :refer [button]]
            [re-frame.core :as rf]
            ["react-native" :as rn]
            [reagent.core :as r]))

(defn debug-screen []
  (r/with-let [logs (rf/subscribe [:debug-log/entries])
               pref (rf/subscribe [:theme/preferred-scheme])
               t (theme/use-theme @pref)]
    [:> rn/View {:style {:flex 1 :background-color (:bg t)}}
     [:> rn/View {:style {:flex-direction :row :justify-content :space-between
                          :align-items :center :padding 16
                          :border-bottom-width 1 :border-bottom-color (:border t)}}
      [:> rn/Text {:style {:font-size 18 :font-weight :bold :color (:text-primary t)}}
       "Debug Logs"]
      [button {:on-press #(rf/dispatch [:debug-log/clear])
               :style {:background-color (:error t) :margin-bottom 0}}
       "Clear"]]
     [:> rn/ScrollView {:style {:flex 1 :padding 12}}
      (let [entries @logs]
        (if (empty? entries)
          [:> rn/Text {:style {:padding 20 :text-align :center :color (:empty-text t)}}
           "No log entries"]
          (doall
           (for [e (rseq entries)]
             [:> rn/View {:key (str (:ts e))
                          :style {:padding-vertical 4
                                  :border-bottom-width 1
                                  :border-bottom-color (:border-light t)}}
              [:> rn/View {:style {:flex-direction :row :align-items :center :margin-bottom 2}}
               (let [level-color (case (:level e)
                                   :error (:error t)
                                   :warn (:warning t)
                                   :info (:accent t)
                                   (:text-tertiary t))]
                 [:> rn/View {:style {:width 8 :height 8 :border-radius 4
                                      :background-color level-color :margin-right 6}}])
               [:> rn/Text {:style {:font-size 11 :color (:text-tertiary t) :font-family :monospace}}
                (.slice (.toISOString (js/Date. (:ts e))) 11 23)]
               (when (= (:source e) :re-frame)
                 [:> rn/Text {:style {:font-size 10 :font-weight :bold
                                      :color (:accent t) :margin-left 6
                                      :font-family :monospace}}
                  "[RF]"])]
              [:> rn/Text {:style {:font-size 13 :color (:text-primary t)
                                   :font-family :monospace :margin-left 14}}
               (:msg e)]]))))]]))
