(ns anchan-dev.selftest
  "Drives real terminals through VTE in a window and checks what they did:
  jolt selftest. It needs a display and VTE. Each row prints PASS or FAIL
  with what it saw, then a SUMMARY line; the exit status is 0 only when
  every row passed.

  A row is a function of the app's context that starts something and
  answers a function to poll: that one answers nil while it is waiting and
  [ok? detail] when it knows. A row that hasn't answered in TIMEOUT-MS
  fails."
  (:require [anchan.core :as anchan]
            [anchan.limits :as limits]
            [anchan.vte :as vte]
            [anchan-dev.gtk :as gtk]
            [clojure.string :as str]
            [jolt.process :as p]))

(def TIMEOUT-MS 8000)

(def results (atom []))

(defn- alive? [pid]
  (zero? (:exit (p/sh "kill" "-0" (str pid)))))

(defn- terminal-in! [win]
  (let [t (anchan/terminal vte/backend)]
    (gtk/gtk-window-set-child win (anchan/widget t))
    t))

(defn- row-shell-runs-and-shows-output
  "A shell started in /tmp echoes what it is fed, in that folder, and the
  terminal has a size."
  [{:keys [win]}]
  (let [t (terminal-in! win)
        started (atom nil)]
    (anchan/start! t {:argv ["/bin/sh"] :dir "/tmp" :on-start #(reset! started %)})
    (fn []
      (when (and (:pid @started) (not (:fed @started)))
        (swap! started assoc :fed true)
        (anchan/feed! t "echo anchan-$((6*7)) in $(pwd)\n"))
      (when (re-find #"anchan-42 in (/private)?/tmp" (anchan/text t))
        (let [{:keys [columns rows]} (anchan/size t)
              pid (anchan/pid t)]
          (anchan/dispose! t)
          [(and (pos? columns) (pos? rows) (pos? pid))
           {:pid pid :columns columns :rows rows}])))))

(defn- row-exit-code-is-reported
  "A program that exits with 3 is reported as {:exit 3}, and the terminal
  no longer says it is running."
  [{:keys [win]}]
  (let [t (terminal-in! win)
        exit (atom nil)]
    (anchan/start! t {:argv ["/bin/sh" "-c" "exit 3"] :on-exit #(reset! exit %)})
    (fn []
      (when @exit
        (let [running (anchan/running? t)]
          (anchan/dispose! t)
          [(and (= {:exit 3} @exit) (not running)) {:exit @exit :running running}])))))

(defn- row-a-missing-program-is-an-error
  "Starting a program that doesn't exist answers :on-start with an error,
  not a pid."
  [{:keys [win]}]
  (let [t (terminal-in! win)
        started (atom nil)]
    (anchan/start! t {:argv ["/no/such/anchan-program"] :on-start #(reset! started %)})
    (fn []
      (when @started
        (anchan/dispose! t)
        [(and (string? (:error @started)) (nil? (:pid @started))) @started]))))

(defn- row-stop-ends-the-program-while-the-app-runs
  "stop! hangs up a running shell: its process is gone and :on-exit says a
  signal ended it, with the application still running."
  [{:keys [win]}]
  (let [t (terminal-in! win)
        seen (atom {})]
    (anchan/start! t {:argv ["/bin/sh"] :dir "/tmp"
                      :on-start #(swap! seen assoc :pid (:pid %))
                      :on-exit #(swap! seen assoc :exit %)})
    (fn []
      (when (and (:pid @seen) (not (:stopped @seen)))
        (swap! seen assoc :stopped true :alive-before (alive? (:pid @seen)))
        (anchan/stop! t))
      (when (and (:exit @seen) (not (alive? (:pid @seen))))
        (anchan/dispose! t)
        [(and (:alive-before @seen) (contains? (:exit @seen) :signal))
         (select-keys @seen [:pid :alive-before :exit])]))))

(defn- row-colors-and-font-are-taken
  "Setting colours (with and without a palette) and a font doesn't throw,
  and a short palette is refused."
  [{:keys [win]}]
  (let [t (terminal-in! win)
        palette (vec (take 16 (cycle ["#000000" "#cc3333" "#33aa33" "#ccaa33"
                                      "#3355cc" "#aa33aa" "#33aaaa" "#dddddd"])))
        outcome (try
                  (anchan/set-colors! t {:foreground "#e6e6f0" :background "#1b1c3a"})
                  (anchan/set-colors! t {:foreground "#e6e6f0" :background "#1b1c3a" :palette palette})
                  (anchan/set-font! t "Menlo 13")
                  {:short-palette-refused
                   (try (anchan/set-colors! t {:foreground "#ffffff" :background "#000000"
                                               :palette ["#000000"]})
                        false
                        (catch Exception _ true))}
                  (catch Throwable e {:threw (ex-message e)}))]
    (anchan/dispose! t)
    (fn [] [(= {:short-palette-refused true} outcome) outcome])))

(defn- row-open-files-limit-is-under-the-ceiling
  "After a program has been started, this process's soft open-files limit
  is at or under the ceiling, whatever it was launched with."
  [_ctx]
  (let [soft (:soft (limits/open-files))]
    (fn [] [(<= soft limits/ceiling) {:soft soft :ceiling limits/ceiling}])))

(def rows
  [[:shell-runs-and-shows-output row-shell-runs-and-shows-output]
   [:exit-code-is-reported row-exit-code-is-reported]
   [:a-missing-program-is-an-error row-a-missing-program-is-an-error]
   [:stop-ends-the-program-while-the-app-runs row-stop-ends-the-program-while-the-app-runs]
   [:colors-and-font-are-taken row-colors-and-font-are-taken]
   [:open-files-limit-is-under-the-ceiling row-open-files-limit-is-under-the-ceiling]])

(defn- record! [id ok? detail]
  (swap! results conj [id ok?])
  (println (str "[anchan-selftest] " (name id) " " (if ok? "PASS" "FAIL") " " (pr-str detail)))
  (flush))

(defn- run-rows!
  "Runs the rows one after the other on the GTK thread, then calls done."
  [ctx done]
  (let [left (atom rows)
        current (atom nil)]
    (gtk/every!
     50
     (fn []
       (when (and (nil? @current) (seq @left))
         (let [[id f] (first @left)]
           (swap! left rest)
           (reset! current
                   (try {:id id :poll (f ctx) :since (System/currentTimeMillis)}
                        (catch Throwable e (record! id false {:threw (ex-message e)}) nil)))))
       (when-let [{:keys [id poll since]} @current]
         (let [answer (try (poll) (catch Throwable e [false {:threw (ex-message e)}]))]
           (cond
             answer (do (record! id (boolean (first answer)) (second answer)) (reset! current nil))
             (> (- (System/currentTimeMillis) since) TIMEOUT-MS)
             (do (record! id false {:timed-out-after-ms TIMEOUT-MS}) (reset! current nil)))))
       (if (and (nil? @current) (empty? @left))
         (do (done) false)
         true)))))

(defn -main [& _]
  (when-let [why (anchan/problem vte/backend)]
    (println "[anchan-selftest]" why)
    (System/exit 2))
  (gtk/run-app!
   "net.b12n.anchan.selftest"
   (fn [app]
     (let [win (gtk/gtk-application-window-new app)]
       (gtk/gtk-window-set-title win "Anchan self-test")
       (gtk/gtk-window-set-default-size win 760 420)
       (gtk/gtk-window-present win)
       (run-rows! {:app app :win win} #(gtk/g-application-quit app)))))
  (let [passed (count (filter second @results))]
    (println (str "SUMMARY " passed " of " (count rows) " passed"
                  (when (not= (count @results) (count rows))
                    (str " (" (count @results) " ran)"))))
    (System/exit (if (= passed (count rows)) 0 1))))
