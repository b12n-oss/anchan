;; A spike, not library code: does VTE's GTK 4 widget draw, take a shell and
;; report back through Jolt's FFI on this machine?
;;
;; Run from the Jonquil checkout, which supplies the GTK bindings:
;;   cd ~/dev/b12n-oss/jonquil
;;   VTE_SPIKE_MODE=exit  jolt -e '(load-file "../anchan/spike/vte_spike.clj")'
;;   VTE_SPIKE_MODE=close jolt -e '(load-file "../anchan/spike/vte_spike.clj")'
;;
;; exit:  the shell is told to exit, and the app quits on child-exited.
;; close: the window is closed with the shell still running, to see whether
;;        the shell dies with it.
;; Each run prints "SPIKE key value" lines, and writes a screenshot.
(ns vte-spike
  (:require [clojure.string :as str]
            [jolt.ffi :as ffi]
            [jolt.process :as p]
            [jonquil.gtk.callbacks :refer [callable]]
            [jonquil.gtk.ffi :as g]))

(def lib (ffi/load-library {:darwin "/opt/homebrew/lib/libvte-2.91-gtk4.0.dylib"
                            :linux "libvte-2.91-gtk4.so.0"}))

;; vte/vteterminal.h (0.84): all fixed arity.
(ffi/defcfn vte-terminal-new "vte_terminal_new" [] :pointer)
;; void vte_terminal_spawn_async(VteTerminal*, VtePtyFlags, const char *working_directory,
;;   char **argv, char **envv, GSpawnFlags, GSpawnChildSetupFunc, gpointer child_setup_data,
;;   GDestroyNotify, int timeout, GCancellable*, VteTerminalSpawnAsyncCallback, gpointer user_data)
(ffi/defcfn vte-terminal-spawn-async "vte_terminal_spawn_async"
  [:pointer :int :string :pointer :pointer :int :pointer :pointer :pointer :int :pointer :pointer :pointer] :void)
(ffi/defcfn vte-terminal-feed-child "vte_terminal_feed_child" [:pointer :string :long] :void)
;; char *vte_terminal_get_text_format(VteTerminal*, VteFormat): VTE_FORMAT_TEXT is 1
(ffi/defcfn vte-terminal-get-text-format "vte_terminal_get_text_format" [:pointer :int] :pointer)
(ffi/defcfn vte-terminal-get-column-count "vte_terminal_get_column_count" [:pointer] :long)
(ffi/defcfn vte-terminal-get-row-count "vte_terminal_get_row_count" [:pointer] :long)
(ffi/defcfn vte-get-major-version "vte_get_major_version" [] :uint)
(ffi/defcfn vte-get-minor-version "vte_get_minor_version" [] :uint)

(def mode (or (System/getenv "VTE_SPIKE_MODE") "exit"))
(def shot (str (or (System/getenv "VTE_SPIKE_OUT") "/tmp") "/vte-spike-" mode ".png"))
(defn say [k & vs] (println "SPIKE" k (str/join " " vs)) (flush))

(def state (atom {:pid nil :exited nil}))

(defn after! [ms f]
  (g/g-timeout-add ms (callable (fn [_] (f) 0) [:pointer] :int) ffi/null))

(defn text-of [term]
  (let [ptr (vte-terminal-get-text-format term 1)]
    (if (ffi/null? ptr) "" (or (ffi/ptr->string ptr) ""))))

(defn alive? [pid]
  (zero? (:exit (p/sh "kill" "-0" (str pid)))))

(defn on-activate [app]
  (let [win (g/gtk-application-window-new app)
        term (vte-terminal-new)
        spawned (callable (fn [_term pid err _data]
                            (swap! state assoc :pid pid)
                            (say :spawned :pid pid :error? (not (ffi/null? err)))
                            nil)
                          [:pointer :int :pointer :pointer] :void)
        exited (callable (fn [_term status _data]
                           (swap! state assoc :exited status)
                           (say :child-exited :status status)
                           (g/g-application-quit app)
                           nil)
                         [:pointer :int :pointer] :void)]
    (say :vte (vte-get-major-version) (vte-get-minor-version))
    (g/gtk-window-set-title win (str "VTE spike (" mode ")"))
    (g/gtk-window-set-default-size win 760 420)
    (g/gtk-window-set-child win term)
    (g/g-signal-connect-data term "child-exited" exited ffi/null ffi/null g/CONNECT-DEFAULT)
    (g/gtk-window-present win)
    ;; a NULL-terminated argv, kept alive on purpose: spawn_async is
    ;; asynchronous, and the first run (argv freed when the call returned)
    ;; ended with the child gone at once, status 11
    (let [argv (ffi/alloc (* 3 (ffi/sizeof :pointer)))
          shell (or (System/getenv "VTE_SPIKE_SHELL") "/bin/zsh")]
      (ffi/write argv :pointer (ffi/string->ptr shell) 0)
      (ffi/write argv :pointer (ffi/string->ptr "-f") (ffi/sizeof :pointer))
      (ffi/write argv :pointer ffi/null (* 2 (ffi/sizeof :pointer)))
      (vte-terminal-spawn-async term 0 "/tmp" argv ffi/null 0 ffi/null ffi/null ffi/null -1
                                ffi/null spawned ffi/null))
    (after! 1500 #(vte-terminal-feed-child term "echo anchan-$((6*7)) in $(pwd)\n" -1))
    (after! 3000 (fn []
                   (let [text (text-of term)]
                     (say :size (vte-terminal-get-column-count term) "x" (vte-terminal-get-row-count term))
                     (say :echo-seen (str/includes? text "anchan-42"))
                     (say :cwd-seen (boolean (re-find #"anchan-42 in (/private)?/tmp" text)))
                     (say :pid-alive (some-> (:pid @state) alive?)))
                   (let [pid (.pid (java.lang.ProcessHandle/current))]
                     (say :screenshot (:exit (p/sh "cgevent" "screenshot" shot "--pid" (str pid))) shot))))
    (after! 4500 (fn []
                   (if (= "close" mode)
                     (do (say :closing-window) (g/gtk-window-close win))
                     (vte-terminal-feed-child term "exit\n" -1))))))

(let [app (g/gtk-application-new "net.b12n.anchan.spike" 0)
      activate (callable (fn [a _] (on-activate a) nil) [:pointer :pointer] :void)]
  (g/g-signal-connect-data app "activate" activate ffi/null ffi/null g/CONNECT-DEFAULT)
  (say :run-exit (g/g-application-run app 0 ffi/null))
  (say :exited-status (:exited @state))
  (when-let [pid (:pid @state)]
    (Thread/sleep 500)
    (say :pid-alive-after-app (alive? pid) :pid pid)))
