(ns anchan-dev.gtk
  "The few GTK calls the example and the self-test need to put a terminal
  in a window: an application, a window and a timer. Not part of the
  library, whose users bring their own GTK bindings. The symbols resolve
  once anchan.vte has loaded VTE, which links against GTK."
  (:require [jolt.ffi :as ffi]))

(ffi/defcfn gtk-application-new "gtk_application_new" [:string :uint] :pointer)
(ffi/defcfn g-application-run "g_application_run" [:pointer :int :pointer] :int :blocking)
(ffi/defcfn g-application-quit "g_application_quit" [:pointer] :void)
(ffi/defcfn gtk-application-window-new "gtk_application_window_new" [:pointer] :pointer)
(ffi/defcfn gtk-window-set-title "gtk_window_set_title" [:pointer :string] :void)
(ffi/defcfn gtk-window-set-default-size "gtk_window_set_default_size" [:pointer :int :int] :void)
(ffi/defcfn gtk-window-set-child "gtk_window_set_child" [:pointer :pointer] :void)
(ffi/defcfn gtk-window-present "gtk_window_present" [:pointer] :void)
(ffi/defcfn gtk-window-close "gtk_window_close" [:pointer] :void)
(ffi/defcfn gtk-widget-grab-focus "gtk_widget_grab_focus" [:pointer] :int)
(ffi/defcfn g-timeout-add "g_timeout_add" [:uint :pointer :pointer] :uint)
(ffi/defcfn g-signal-connect-data "g_signal_connect_data"
  [:pointer :string :pointer :pointer :pointer :int] :ulong)

;; every callback made here, kept for the life of the process
(defonce retained (atom []))

(defmacro callable
  "f as a C callback with signature sig and return ret, kept alive for the
  process and safe to call from GTK's main loop."
  [f sig ret]
  `(let [c# (ffi/foreign-callable ~f ~sig ~ret :collect-safe)]
     (swap! retained conj c#)
     c#))

(defn every!
  "Calls f every ms milliseconds on the GTK thread until it answers false."
  [ms f]
  (g-timeout-add ms (callable (fn [_data] (if (try (f) (catch Throwable _ false)) 1 0))
                              [:pointer] :int)
                 ffi/null))

(defn run-app!
  "Runs a GTK application: on-activate (fn [app]) builds its windows.
  Answers when the application has quit."
  [id on-activate]
  (let [app (gtk-application-new id 0)]
    (g-signal-connect-data app "activate"
                           (callable (fn [a _data] (on-activate a) nil) [:pointer :pointer] :void)
                           ffi/null ffi/null 0)
    (g-application-run app 0 ffi/null)))
