(ns vygga.view.chat
  (:require [vygga.subs]
            [vygga.theme :as theme]
            [vygga.view.calls :refer [call-button incoming-call-overlay]]
            ["expo-status-bar" :refer [StatusBar]]
            [re-frame.core :as rf]
            ["react-native" :as rn]
            [reagent.core :as r]))

(defn message-bubble-render [{:keys [id text from-me status cid]} t]
  [:> rn/View {:style {:align-items (if from-me :flex-end :flex-start)
                       :margin-bottom 8}}
   [:> rn/View {:style {:max-width "75%"
                        :background-color (if from-me (:bg-bubble-sent t) (:bg-bubble-received t))
                        :border-radius 16 :padding 12}}
    [:> rn/Text {:style {:font-size 15
                         :color (if from-me (:text-bubble-sent t) (:text-bubble-received t))}}
     text]
    (when (and from-me (= status :sent))
      [:> rn/Text {:style {:font-size 12 :color (:checkmark t)
                           :text-align :right :margin-top 4}}
       "✓"])]
   (when (and from-me (= status :failed))
     [:> rn/View {:style {:flex-direction :row :align-items :center
                          :margin-top 4}}
      [:> rn/Text {:style {:font-size 12 :color (:error t) :margin-right 8}}
       "! Failed"]
      [:> rn/TouchableOpacity {:on-press #(rf/dispatch [:messenger/resend-message cid id])
                               :style {:padding-horizontal 10 :padding-vertical 4
                                       :border-radius 8 :border-width 1
                                       :border-color (:error t)}}
       [:> rn/Text {:style {:font-size 12 :color (:error t) :font-weight :600}}
        "Resend"]]])])

(defn message-bubble [{:keys [id text from-me status cid]}]
  (r/with-let [pref (rf/subscribe [:theme/preferred-scheme])
               t (theme/use-theme @pref)]
    (message-bubble-render {:id id :text text :from-me from-me :status status :cid cid} t)))

(defn message-input [cid]
  (r/with-let [*text (r/atom "")
               *ref (r/atom nil)
               pref (rf/subscribe [:theme/preferred-scheme])
               t (theme/use-theme @pref)]
    [:> rn/View {:style {:flex-direction :row :align-items :center
                         :padding 12 :border-top-width 1
                         :border-top-color (:border t)
                         :padding-bottom 48}}
     [:> rn/TextInput {:key "message-input"
                       :style {:flex 1 :border-width 1 :border-color (:border-input-alt t)
                               :border-radius 20 :padding-horizontal 16
                               :padding-vertical 14 :font-size 15 :margin-right 8
                               :min-height 44 :max-height 120 :color (:text-primary t)}
                       :placeholder "Type a message..."
                       :placeholder-text-color (:text-tertiary t)
                       :multiline true
                       :default-value ""
                       :ref #(reset! *ref %)
                       :on-change-text #(reset! *text %)
                       :on-submit-editing #(when (seq @*text)
                                             (rf/dispatch [:messenger/send-message cid @*text])
                                             (reset! *text "")
                                             (when-let [r @*ref] (.clear r)))}]
     [:> rn/Pressable {:style {:width 40 :height 40 :border-radius 20
                               :background-color (:accent t) :justify-content :center
                               :align-items :center}
                       :on-press (fn []
                                   (let [t @*text]
                                     (when (seq t)
                                       (rf/dispatch [:messenger/send-message cid t])
                                       (reset! *text "")
                                       (when-let [r @*ref] (.clear r)))))}
      [:> rn/Text {:style {:color (:text-inverse t) :font-size 18}} "➤"]]]))

(defn chat []
  (r/with-let [current-id (rf/subscribe [:contacts/current])
               contacts (rf/subscribe [:contacts/list])
               *flat-ref (r/atom nil)
               *at-bottom (r/atom true)
               pref (rf/subscribe [:theme/preferred-scheme])
               t (theme/use-theme @pref)]
    (let [cid @current-id
          c (get @contacts cid)
          msgs (:messages c [])]
      [:> rn/KeyboardAvoidingView {:style {:flex 1} :behavior "padding" :keyboard-vertical-offset 90}
       [:> rn/View {:style {:flex 1 :background-color (:bg t)}}
        [:> rn/View {:style {:padding-horizontal 16 :padding-vertical 12
                             :border-bottom-width 1 :border-bottom-color (:border t)
                             :flex-direction :row :align-items :center}}
         [:> rn/View {:style {:flex 1}}
          (if (seq (:name c))
            [:<>]
            [:> rn/Text {:style {:font-size 17 :font-weight :600 :color (:text-primary t)}}
             (or (:address c) "Unknown")])
          (when (seq (:name c))
            [:> rn/Text {:style {:font-size 17 :font-weight :600 :color (:text-primary t)}}
             (:name c)])
          (when (seq (:name c))
            [:> rn/Text {:style {:font-size 12 :color (:text-tertiary t) :margin-top 1}}
             (:address c)])]
         [call-button {:contact-id cid :t t}]]
        (if (empty? msgs)
          [:> rn/View {:style {:flex 1 :padding 40 :align-items :center}}
           [:> rn/Text {:style {:font-size 15 :color (:empty-text t)}}
            "No messages yet"]]
          [:> rn/FlatList
           {:data (to-array (rseq msgs))
            :key-extractor (fn [item] (:id item))
            :inverted true
            :ref #(reset! *flat-ref %)
            :style {:flex 1 :padding 12}
            :on-content-size-change (fn []
                                      (when (and @*at-bottom @*flat-ref)
                                        (try (.scrollToEnd ^js @*flat-ref #js {:animated false})
                                             (catch js/Error _))))
            :on-scroll (fn [^js e]
                         (when-let [y (some-> e .-nativeEvent .-contentOffset .-y)]
                           (reset! *at-bottom (< y 50))))
            :initial-num-to-render 20
            :max-to-render-per-batch 20
            :window-size 7
            :render-item (fn [info]
                           (let [item (.-item info)
                                 id (:id item)
                                 text (:text item)
                                 from-me (:from-me item)
                                 status (:status item)]
                             (r/as-element
                              [message-bubble
                               {:id id :text text :from-me from-me
                                :status status :cid cid}])))}])
        [message-input cid]
        [:> StatusBar {:style "auto"}]
        [incoming-call-overlay {:t t}]]])))
