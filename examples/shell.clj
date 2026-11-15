(ns shell
  "Your login shell in a window, in the folder you ran this from. The window
  closes when the shell exits. Run: jolt shell"
  (:require [anchan.core :as anchan]
            [anchan.vte :as vte]
            [anchan-dev.gtk :as gtk]))

(defn -main [& _]
  (when-let [why (anchan/problem vte/backend)]
    (println why)
    (System/exit 1))
  (gtk/run-app!
   "net.b12n.anchan.example.shell"
   (fn [app]
     (let [win (gtk/gtk-application-window-new app)
           t (anchan/terminal vte/backend)
           shell (or (System/getenv "SHELL") "/bin/sh")]
       (gtk/gtk-window-set-title win "Anchan")
       (gtk/gtk-window-set-default-size win 820 480)
       (gtk/gtk-window-set-child win (anchan/widget t))
       (anchan/set-colors! t {:foreground "#e6e6f0" :background "#1b1c3a"})
       (gtk/gtk-window-present win)
       (gtk/gtk-widget-grab-focus (anchan/widget t))
       (anchan/start! t {:argv [shell] :dir (System/getProperty "user.dir")
                         :on-start (fn [{:keys [error]}]
                                     (when error (println "couldn't start" shell ":" error)
                                           (gtk/g-application-quit app)))
                         :on-exit (fn [_] (anchan/dispose! t) (gtk/g-application-quit app))}))))
  (System/exit 0))
