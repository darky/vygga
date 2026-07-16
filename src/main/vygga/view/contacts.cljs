(ns vygga.view.contacts
  (:require [vygga.subs]
            [vygga.theme :as theme]
            [vygga.view.yggstatus :refer [status-indicator]]
            ["expo-status-bar" :refer [StatusBar]]
            [re-frame.core :as rf]
            ["react-native" :as rn]
            [reagent.core :as r]
            ["@expo/vector-icons/Ionicons" :default Ionicons]))

(defn contact-item-render [^js props contact-id {:keys [address last-message unread-count name]} t on-options]
  (let [preview (if last-message (:text last-message) "")
        has-name (boolean (seq name))
        letter (if has-name (-> name .charAt (.toUpperCase)) (-> address .charAt (.toUpperCase)))]
    [:> rn/View {:style {:flex-direction :row :align-items :center
                         :padding 14 :border-bottom-width 1
                         :border-bottom-color (:border-light t)}}
     [:> rn/TouchableOpacity
      {:style {:flex-direction :row :align-items :center :flex 1}
       :on-press #(do
                    (rf/dispatch [:contacts/set-current-contact contact-id])
                    (-> props .-navigation (.navigate "Chat")))}
      [:> rn/View {:style {:width 44 :height 44 :border-radius 22
                           :background-color (:accent t) :justify-content :center
                           :align-items :center :margin-right 12}}
       [:> rn/Text {:style {:color (:text-inverse t) :font-size 18 :font-weight :bold}}
        letter]
       (when (and unread-count (pos? unread-count))
         [:> rn/View {:style {:position :absolute :top -4 :right -4
                              :min-width 20 :height 20 :border-radius 10
                              :background-color (:error t)
                              :justify-content :center :align-items :center
                              :padding-horizontal 4 :z-index 1}}
          [:> rn/Text {:style {:color :white :font-size 11 :font-weight :bold}}
           (if (> unread-count 99) "99+" (str unread-count))]])]
      [:> rn/View {:style {:flex 1}}
       (if has-name
         [:<>]
         [:> rn/Text {:style {:font-size 16 :font-weight :600 :color (:text-primary t) :margin-bottom 0}}
          address])
       (when has-name
         [:> rn/Text {:style {:font-size 16 :font-weight :600 :color (:text-primary t)}}
          name])
       (when has-name
         [:> rn/Text {:style {:font-size 12 :color (:text-tertiary t) :margin-top 1}}
          address])
       (when (seq preview)
         [:> rn/Text {:style {:font-size 13 :color (:text-tertiary t) :margin-top (if has-name 4 2)}
                      :number-of-lines 1} preview])]]
     [:> rn/TouchableOpacity
      {:style {:padding 8 :margin-left 8}
       :on-press #(on-options contact-id)}
      [:> Ionicons {:name "ellipsis-vertical" :size 20 :color (:text-tertiary t)}]]]))

(defn contact-item [^js props contact-id contact on-options]
  (r/with-let [pref (rf/subscribe [:theme/preferred-scheme])
               t (theme/use-theme @pref)]
    (contact-item-render props contact-id contact t on-options)))

(defn contacts [props]
  (r/with-let [sorted-contacts (rf/subscribe [:contacts/sorted])
               raw-contacts (rf/subscribe [:contacts/list])
               *show-add (r/atom false)
               *new-addr (r/atom "")
               *addr-ref (r/atom nil)
               *options-contact (r/atom nil)
               *editing-contact (r/atom nil)
               *edit-name-val (r/atom "")
               *name-ref (r/atom nil)
               *confirm-remove (r/atom nil)
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
             ^{:key cid} [contact-item props cid c #(reset! *options-contact %)]))))]
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
                                            (rf/dispatch [:contacts/add-contact
                                                          {:address addr}]))
                                          (reset! *new-addr "")
                                          (reset! *show-add false)
                                          (when-let [r @*addr-ref] (.clear r))))}
           [:> rn/Text {:style {:color (:text-inverse t) :font-weight :600}} "Add"]]]]])
     (when-let [cid @*options-contact]
       (let [contact (get @raw-contacts cid)
             display-name (or (:name contact) (:address contact))]
         [:> rn/View {:style {:position :absolute :top 0 :left 0 :right 0 :bottom 0
                              :background-color (:bg-modal-overlay t)
                              :justify-content :center :align-items :center}
                      :on-press #(reset! *options-contact nil)}
          [:> rn/View {:style {:background-color (:bg t) :border-radius 16
                               :padding 24 :width "85%"}}
           [:> rn/Text {:style {:font-size 20 :font-weight :bold :margin-bottom 4 :color (:text-primary t)}}
            display-name]
           [:> rn/Text {:style {:font-size 13 :color (:text-tertiary t) :margin-bottom 20}}
            (:address contact)]
           [:> rn/Pressable {:style {:background-color (:bg-card t) :padding-vertical 14
                                     :padding-horizontal 16 :border-radius 10 :margin-bottom 8}
                             :on-press (fn []
                                         (reset! *edit-name-val (or (:name contact) ""))
                                         (reset! *editing-contact cid)
                                         (reset! *options-contact nil))}
            [:> rn/Text {:style {:font-size 16 :color (:text-primary t)}} "Edit Name"]]
           [:> rn/Pressable {:style {:background-color (:bg-card t) :padding-vertical 14
                                     :padding-horizontal 16 :border-radius 10 :margin-bottom 12}
                             :on-press (fn []
                                         (reset! *confirm-remove cid)
                                         (reset! *options-contact nil))}
            [:> rn/Text {:style {:font-size 16 :color (:error t)}} "Remove Contact"]]
           [:> rn/Pressable {:on-press #(reset! *options-contact nil)
                             :style {:align-items :center :padding-vertical 10}}
            [:> rn/Text {:style {:font-size 15 :color (:cancel-text t)}} "Cancel"]]]]))
     (when-let [cid @*editing-contact]
       [:> rn/View {:style {:position :absolute :top 0 :left 0 :right 0 :bottom 0
                            :background-color (:bg-modal-overlay t)
                            :justify-content :center :align-items :center}}
        [:> rn/View {:style {:background-color (:bg t) :border-radius 16
                             :padding 24 :width "85%"}}
         [:> rn/Text {:style {:font-size 20 :font-weight :bold :margin-bottom 16 :color (:text-primary t)}}
          "Edit Name"]
         [:> rn/TextInput {:key "edit-name-input"
                           :style {:border-width 1 :border-color (:border-input-alt t)
                                   :border-radius 8 :padding 12 :font-size 15
                                   :margin-bottom 20 :color (:text-primary t)}
                           :placeholder "Contact name"
                           :placeholder-text-color (:text-tertiary t)
                           :default-value @*edit-name-val
                           :ref #(reset! *name-ref %)
                           :on-change-text #(reset! *edit-name-val %)}]
         [:> rn/View {:style {:flex-direction :row :justify-content :flex-end}}
          [:> rn/Pressable {:on-press (fn []
                                        (reset! *edit-name-val "")
                                        (reset! *editing-contact nil))
                            :style {:padding-horizontal 16 :padding-vertical 10
                                    :margin-right 12}}
           [:> rn/Text {:style {:font-size 15 :color (:cancel-text t)}} "Cancel"]]
          [:> rn/Pressable {:style {:background-color (:accent t) :padding-horizontal 20
                                    :padding-vertical 10 :border-radius 8}
                            :on-press (fn []
                                        (let [n @*edit-name-val]
                                          (rf/dispatch [:contacts/update-contact-name
                                                        cid (when (seq n) n)])
                                          (reset! *edit-name-val "")
                                          (reset! *editing-contact nil)
                                          (when-let [r @*name-ref] (.clear r))))}
           [:> rn/Text {:style {:color (:text-inverse t) :font-weight :600}} "Save"]]]]])
     (when-let [cid @*confirm-remove]
       (let [contact (get @raw-contacts cid)
             display-name (or (:name contact) (:address contact))]
         [:> rn/View {:style {:position :absolute :top 0 :left 0 :right 0 :bottom 0
                              :background-color (:bg-modal-overlay t)
                              :justify-content :center :align-items :center}}
          [:> rn/View {:style {:background-color (:bg t) :border-radius 16
                               :padding 24 :width "85%"}}
           [:> rn/Text {:style {:font-size 20 :font-weight :bold :margin-bottom 12 :color (:text-primary t)}}
            "Remove Contact"]
           [:> rn/Text {:style {:font-size 15 :color (:text-secondary t) :margin-bottom 20
                                :line-height 22}}
            "Are you sure you want to remove "
            [:> rn/Text {:style {:font-weight :600}} display-name]
            "?"]
           [:> rn/View {:style {:flex-direction :row :justify-content :flex-end}}
            [:> rn/Pressable {:on-press #(reset! *confirm-remove nil)
                              :style {:padding-horizontal 16 :padding-vertical 10
                                      :margin-right 12}}
             [:> rn/Text {:style {:font-size 15 :color (:cancel-text t)}} "Cancel"]]
            [:> rn/Pressable {:style {:background-color (:error t) :padding-horizontal 20
                                      :padding-vertical 10 :border-radius 8}
                              :on-press (fn []
                                          (rf/dispatch [:contacts/remove-contact cid])
                                          (reset! *confirm-remove nil))}
             [:> rn/Text {:style {:color :white :font-weight :600}} "Remove"]]]]]))
     [:> StatusBar {:style "auto"}]]))
