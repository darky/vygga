(ns vygga.view.navigation
  (:require [vygga.events]
            [vygga.subs]
            [vygga.config :as config]
            [vygga.theme :as theme]
            [vygga.view.settings :refer [settings]]
            [vygga.view.debug :refer [debug-screen]]
            [vygga.view.contacts :refer [contacts]]
            [vygga.view.chat :refer [chat]]
            [vygga.view.calls :refer [incoming-call-overlay active-call-bar]]
            [re-frame.core :as rf]
            ["react-native" :as rn]
            [reagent.core :as r]
            ["@react-navigation/native" :as rnn]
            ["@react-navigation/native-stack" :as rnn-stack]
            ["react-native-safe-area-context" :refer [SafeAreaProvider]]))

(defonce Stack (rnn-stack/createNativeStackNavigator))

(defn root []
  (r/with-let [pref (rf/subscribe [:theme/preferred-scheme])
               t (theme/use-theme @pref)
               !root-state (rf/subscribe [:navigation/root-state])
               save-root-state! (fn [^js state]
                                  (rf/dispatch [:navigation/set-root-state state]))
               add-listener! (fn [^js navigation-ref]
                               (when navigation-ref
                                 (.addListener navigation-ref "state" save-root-state!)))]
    [:> SafeAreaProvider {:style {:flex 1}}
     [:> rnn/NavigationContainer {:ref add-listener!
                                  :initialState (when @!root-state (-> @!root-state .-data .-state))}
      [:> Stack.Navigator
       {:screenOptions {:headerStyle {:background-color (:header-bg t)}
                        :headerTintColor (:header-text t)
                        :headerTitleStyle {:color (:header-text t)}}}
       [:> Stack.Screen {:name "Contacts"
                         :component (fn [props] (r/as-element [contacts props]))
                         :options {:title "Messenger"}}]
       [:> Stack.Screen {:name "Chat"
                         :component (fn [props] (r/as-element [chat props]))
                         :options {:title "Chat"}}]
       [:> Stack.Screen {:name "Settings"
                         :component (fn [props] (r/as-element [settings props]))
                         :options {:title "Yggdrasil Settings"}}]
       (when config/log-enabled
         [:> Stack.Screen {:name "Debug"
                           :component (fn [_] (r/as-element [debug-screen]))
                           :options {:title "Debug Logs"}}])]]
     [incoming-call-overlay {:t t}]
     [:> rn/View {:style {:position :absolute :left 0 :right 0 :bottom 0 :z-index 50}}
      [active-call-bar {:t t}]]]))
