(ns vygga.widgets
  (:require [vygga.theme :as theme]
            [reagent.core :as r]
            ["react-native" :as rn]))

(defn button [{:keys [style text-style on-press
                      disabled? disabled-style disabled-text-style]
               :or {on-press #()}} text]
  (r/with-let [t (theme/use-theme)]
    [:> rn/Pressable {:style (cond-> {:font-weight      :bold
                                      :font-size        18
                                      :padding          6
                                      :background-color (:accent t)
                                      :border-radius    999
                                      :margin-bottom    20}
                               :always (merge style)
                               disabled? (merge {:background-color (:disabled t)}
                                                disabled-style))
                      :on-press on-press
                      :disabled disabled?}
     [:> rn/Text {:style (cond-> {:padding-left  12
                                  :padding-right 12
                                  :font-weight   :bold
                                  :font-size     18
                                  :color         (:text-inverse t)}
                           :always (merge text-style)
                           disabled? (merge {:color (:text-inverse t)}
                                            disabled-text-style))}
      text]]))