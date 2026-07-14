(ns vygga.view.calls
  (:require [vygga.subs]
            [re-frame.core :as rf]
            ["react-native" :as rn]
            [reagent.core :as r]
            ["@expo/vector-icons/Ionicons" :default Ionicons]
            ["react-native-safe-area-context" :refer [useSafeAreaInsets]]))

(def with-safe-area-insets
  (let [f (fn [props]
            (let [insets (useSafeAreaInsets)]
              (r/as-element [(.-children props) insets])))]
    (set! (.-displayName f) "withSafeAreaInsets")
    (r/adapt-react-class f)))

(defn call-button [{_contact-id :contact-id _t :t}]
  (let [call-state (rf/subscribe [:voip/call-state])]
    (fn [{:keys [contact-id t]}]
      (let [s @call-state]
        [:> rn/TouchableOpacity
         {:on-press #(rf/dispatch [:voip/call-contact contact-id])
          :disabled (not= :idle s)
          :style {:padding 8 :margin-left 8
                  :opacity (if (= :idle s) 1 0.5)}}
         [:> Ionicons {:name "call-outline" :size 22 :color (:call-accept t)}]]))))

(defn active-call-bar [{_t :t}]
  (let [call (rf/subscribe [:voip/active-call])]
    (fn [{:keys [t]}]
      (when-let [c @call]
        (let [state (:call-state c)
              addr (:remote-addr c)]
          [with-safe-area-insets
           (fn [insets]
             [:> rn/View {:style {:flex-direction :row :align-items :center
                                  :justify-content :space-between
                                  :padding-horizontal 16 :padding-vertical 8
                                  :padding-bottom (+ 8 (.-bottom insets))
                                  :background-color (:call-bg t)
                                  :border-bottom-width 1
                                  :border-bottom-color (:border t)}}
              [:> rn/View {:style {:flex-direction :row :align-items :center}}
               [:> rn/View {:style {:width 10 :height 10 :border-radius 5
                                    :background-color (:call-accept t) :margin-right 8}}]
               [:> rn/Text {:style {:font-size 14 :color (:call-accept t) :font-weight :600}}
                (case state
                  :calling "Calling..."
                  :ringing "Incoming call..."
                  :connected "Call connected"
                  "Call")]]
              [:> rn/View {:style {:flex-direction :row :align-items :center}}
               [:> rn/Text {:style {:font-size 11 :color (:text-tertiary t) :margin-right 8}}
                (subs addr 0 8)]
               [:> rn/TouchableOpacity {:on-press #(rf/dispatch [:voip/end-call])
                                        :style {:background-color (:call-reject t)
                                                :padding-horizontal 12 :padding-vertical 4
                                                :border-radius 12}}
                [:> rn/Text {:style {:color :white :font-size 12 :font-weight :600}} "End"]]]])])))))

(defn incoming-call-overlay [{_t :t}]
  (let [call (rf/subscribe [:voip/active-call])]
    (fn [{:keys [t]}]
      (when-let [c @call]
        (when (= :ringing (:call-state c))
          [:> rn/View {:style {:position :absolute :top 0 :left 0 :right 0 :bottom 0
                               :background-color (:bg-modal-overlay t)
                               :justify-content :center :align-items :center :z-index 100}}
           [:> rn/View {:style {:background-color (:bg t) :border-radius 20
                                :padding 32 :width "85%" :align-items :center}}
            [:> rn/View {:style {:width 64 :height 64 :border-radius 32
                                 :background-color (:call-accept t)
                                 :justify-content :center :align-items :center
                                 :margin-bottom 16}}
             [:> Ionicons {:name "call" :size 32 :color :white}]]
            [:> rn/Text {:style {:font-size 18 :font-weight :bold
                                 :margin-bottom 4 :color (:text-primary t)}}
             "Incoming Call"]
            [:> rn/Text {:style {:font-size 14 :color (:text-secondary t)
                                 :margin-bottom 24}}
             (subs (:remote-addr c) 0 15)]
            [:> rn/View {:style {:flex-direction :row}}
             [:> rn/TouchableOpacity {:on-press #(rf/dispatch [:voip/reject-call])
                                      :style {:background-color (:call-reject t)
                                              :width 64 :height 64 :border-radius 32
                                              :justify-content :center :align-items :center
                                              :margin-horizontal 24}}
              [:> Ionicons {:name "close" :size 32 :color :white}]]
             [:> rn/TouchableOpacity {:on-press #(rf/dispatch [:voip/accept-call])
                                      :style {:background-color (:call-accept t)
                                              :width 64 :height 64 :border-radius 32
                                              :justify-content :center :align-items :center
                                              :margin-horizontal 24}}
              [:> Ionicons {:name "call" :size 32 :color :white}]]]]])))))
