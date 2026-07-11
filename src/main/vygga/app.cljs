(ns vygga.app
  (:require [vygga.events]
            [vygga.subs]
            [vygga.network]
            [vygga.view :as view]
            [expo.root :as expo-root]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(defn start
  {:dev/after-load true}
  []
  (expo-root/render-root (r/as-element [view/root])))

(defn init []
  (rf/dispatch-sync [:initialize-db])
  (start))
