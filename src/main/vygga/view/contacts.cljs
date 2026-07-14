(ns vygga.view.contacts
  (:require [vygga.subs]
            [vygga.theme :as theme]
            [vygga.view.yggstatus :refer [status-indicator]]
            ["expo-status-bar" :refer [StatusBar]]
            [re-frame.core :as rf]
            ["react-native" :as rn]
            [reagent.core :as r]))

(defn contact-item-render [^js props contact-id {:keys [address last-message unread-count]} t]
  (let [preview (if last-message (:text last-message) "")]
    [:> rn/TouchableOpacity
     {:key contact-id
      :style {:flex-direction :row :align-items :center
              :padding 14 :border-bottom-width 1
              :border-bottom-color (:border-light t)}
      :on-press #(do
                   (rf/dispatch [:messenger/set-current-contact contact-id])
                   (-> props .-navigation (.navigate "Chat")))}
     [:> rn/View {:style {:width 44 :height 44 :border-radius 22
                          :background-color (:accent t) :justify-content :center
                          :align-items :center :margin-right 12}}
      [:> rn/Text {:style {:color (:text-inverse t) :font-size 18 :font-weight :bold}}
       (-> address .charAt (.toUpperCase))]
      (when (and unread-count (pos? unread-count))
        [:> rn/View {:style {:position :absolute :top -4 :right -4
                             :min-width 20 :height 20 :border-radius 10
                             :background-color (:error t)
                             :justify-content :center :align-items :center
                             :padding-horizontal 4 :z-index 1}}
         [:> rn/Text {:style {:color :white :font-size 11 :font-weight :bold}}
          (if (> unread-count 99) "99+" (str unread-count))]])]
     [:> rn/View {:style {:flex 1}}
      [:> rn/Text {:style {:font-size 16 :font-weight :600 :color (:text-primary t)}}
       address]
      (when (seq preview)
        [:> rn/Text {:style {:font-size 13 :color (:text-tertiary t) :margin-top 2}
                     :number-of-lines 1} preview])]]))

(defn contact-item [^js props contact-id {:keys [address last-message unread-count]}]
  (r/with-let [pref (rf/subscribe [:theme/preferred-scheme])
               t (theme/use-theme @pref)]
    (contact-item-render props contact-id {:address address :last-message last-message :unread-count unread-count} t)))

(defn contacts [props]
  (r/with-let [sorted-contacts (rf/subscribe [:messenger/sorted-contacts])
               *show-add (r/atom false)
               *new-addr (r/atom "")
               *addr-ref (r/atom nil)
               pref (rf/subscribe [:theme/preferred-scheme])
               t (theme/use-theme @pref)]
    [:> rn/View {:style {:flex 1 :background-color (:bg t)}}
     [status-indicator props]
     [:> rn/ScrollView {:style {:flex 1 :padding-bottom 90}}
      (let [sorted @sorted-contacts]
        (if (empty? sorted)
          [:> rn/View {:style {:padding 40 :align-items :center}}
           [:> rn/Text {:style {:font-size 16 :color (:empty-text t)}}
            "No contacts yet"]]
          (doall
           (for [[cid c] sorted]
             ^{:key cid} [contact-item props cid c]))))]
     [:> rn/Pressable {:style {:position :absolute :bottom 90 :right 20
                               :width 56 :height 56 :border-radius 28
                               :background-color (:accent t) :justify-content :center
                               :align-items :center :elevation 4}
                       :on-press #(reset! *show-add true)}
      [:> rn/Text {:style {:color (:text-inverse t) :font-size 28}} "+"]]
     (when @*show-add
       [:> rn/View {:style {:position :absolute :top 0 :left 0 :right 0 :bottom 0
                            :background-color (:bg-modal-overlay t)
                            :justify-content :center :align-items :center}}
        [:> rn/View {:style {:background-color (:bg t) :border-radius 16
                             :padding 24 :width "85%"}}
         [:> rn/Text {:style {:font-size 20 :font-weight :bold :margin-bottom 16 :color (:text-primary t)}}
          "Add Contact"]
         [:> rn/TextInput {:key "add-contact-input"
                           :style {:border-width 1 :border-color (:border-input-alt t)
                                   :border-radius 8 :padding 12 :font-size 15
                                   :margin-bottom 20 :color (:text-primary t)}
                           :placeholder "Yggdrasil IPv6 (e.g. 201:1234::1)"
                           :placeholder-text-color (:text-tertiary t)
                           :default-value ""
                           :ref #(reset! *addr-ref %)
                           :on-change-text #(reset! *new-addr %)}]
         [:> rn/View {:style {:flex-direction :row :justify-content :flex-end}}
          [:> rn/Pressable {:on-press #(reset! *show-add false)
                            :style {:padding-horizontal 16 :padding-vertical 10
                                    :margin-right 12}}
           [:> rn/Text {:style {:font-size 15 :color (:cancel-text t)}} "Cancel"]]
          [:> rn/Pressable {:style {:background-color (:accent t) :padding-horizontal 20
                                    :padding-vertical 10 :border-radius 8}
                            :on-press (fn []
                                        (let [addr @*new-addr]
                                          (when (seq addr)
                                            (rf/dispatch [:messenger/add-contact
                                                          {:address addr}]))
                                          (reset! *new-addr "")
                                          (reset! *show-add false)
                                          (when-let [r @*addr-ref] (.clear r))))}
           [:> rn/Text {:style {:color (:text-inverse t) :font-weight :600}} "Add"]]]]])
     [:> StatusBar {:style "auto"}]]))
