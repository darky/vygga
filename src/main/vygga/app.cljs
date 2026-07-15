(ns vygga.app
  (:require [vygga.events.app]
            [vygga.events.yggstack]
            [vygga.events.messenger]
            [vygga.events.voip]
            [vygga.events.debug]
            [vygga.subs]
            [vygga.network]
            [vygga.config :as config]
            [vygga.logging :as logging]
            [vygga.view.navigation :refer [root]]
            [expo.root :as expo-root]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(defn start
  {:dev/after-load true}
  []
  (expo-root/render-root (r/as-element [root])))

(defn init []
  (rf/dispatch-sync [:initialize-db])
  (when config/log-enabled
    (logging/patch-console!)
    (logging/patch-re-frame!))
  (start))
