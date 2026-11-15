(ns anchan.vte
  "The VTE backend: GNOME's terminal widget, in its GTK 4 build
  (libvte-2.91-gtk4). VTE owns the pty, the drawing, the scrollback and the
  selection; this namespace starts programs in it and reports back.

  The library is loaded the first time it is needed, from library-paths, so
  requiring this namespace costs nothing on a machine without VTE, and
  `anchan.core/problem` says what is missing. Loading VTE is enough for the
  GLib, GTK, Pango and libc calls below to resolve, since VTE links against
  them."
  (:require [anchan.color :as color]
            [anchan.core :as core]
            [anchan.limits :as limits]
            [jolt.ffi :as ffi]))

(def library-paths
  "Where VTE's GTK 4 library is looked for, in order. Env ANCHAN_VTE_LIB,
  when set, is tried first. An app that bundles the library resets this
  before it makes a terminal."
  (atom {:darwin ["/opt/homebrew/lib/libvte-2.91-gtk4.0.dylib"
                  "/usr/local/lib/libvte-2.91-gtk4.0.dylib"
                  "libvte-2.91-gtk4.0.dylib"]
         :linux ["libvte-2.91-gtk4.so.0" "libvte-2.91-gtk4.so"]}))

(defn- system [] (if (= "Mac OS X" (System/getProperty "os.name")) :darwin :linux))

(defn- load!
  "Loads the first of the paths that loads: nil, or the sentence saying
  none did."
  []
  (let [sys (system)
        paths (concat (when-let [p (System/getenv "ANCHAN_VTE_LIB")] [p]) (get @library-paths sys))
        loads? (fn [path] (try (ffi/load-library {sys path}) true (catch Throwable _ false)))]
    (when-not (some loads? paths)
      (str "VTE's GTK 4 library isn't installed (looked for " (pr-str (vec paths)) "). "
           (if (= :darwin sys) "Try: brew install vte3" "Install your system's libvte-2.91-gtk4 package")))))

(defonce ^:private loaded (delay (load!)))

;; --- C functions, all fixed arity ------------------------------------------

;; vte/vteterminal.h
(ffi/defcfn vte-terminal-new "vte_terminal_new" [] :pointer)
;; void vte_terminal_spawn_async(VteTerminal*, VtePtyFlags, const char *working_directory,
;;   char **argv, char **envv, GSpawnFlags, GSpawnChildSetupFunc, gpointer child_setup_data,
;;   GDestroyNotify, int timeout, GCancellable*, VteTerminalSpawnAsyncCallback, gpointer user_data)
(ffi/defcfn vte-terminal-spawn-async "vte_terminal_spawn_async"
  [:pointer :int :string :pointer :pointer :int :pointer :pointer :pointer :int :pointer :pointer :pointer] :void)
(ffi/defcfn vte-terminal-feed-child "vte_terminal_feed_child" [:pointer :string :long] :void)
;; char *vte_terminal_get_text_format(VteTerminal*, VteFormat), freed with g_free
(ffi/defcfn vte-terminal-get-text-format "vte_terminal_get_text_format" [:pointer :int] :pointer)
(ffi/defcfn vte-terminal-get-column-count "vte_terminal_get_column_count" [:pointer] :long)
(ffi/defcfn vte-terminal-get-row-count "vte_terminal_get_row_count" [:pointer] :long)
(ffi/defcfn vte-terminal-copy-clipboard-format "vte_terminal_copy_clipboard_format" [:pointer :int] :void)
(ffi/defcfn vte-terminal-paste-clipboard "vte_terminal_paste_clipboard" [:pointer] :void)
;; void vte_terminal_set_colors(VteTerminal*, const GdkRGBA *fg, const GdkRGBA *bg,
;;   const GdkRGBA *palette, gsize palette_size)
(ffi/defcfn vte-terminal-set-colors "vte_terminal_set_colors" [:pointer :pointer :pointer :pointer :size_t] :void)
(ffi/defcfn vte-terminal-set-font "vte_terminal_set_font" [:pointer :pointer] :void)
;; pango/pango-font.h
(ffi/defcfn pango-font-description-from-string "pango_font_description_from_string" [:string] :pointer)
(ffi/defcfn pango-font-description-free "pango_font_description_free" [:pointer] :void)
;; glib and gobject
(ffi/defcfn g-free "g_free" [:pointer] :void)
(ffi/defcfn g-signal-connect-data "g_signal_connect_data"
  [:pointer :string :pointer :pointer :pointer :int] :ulong)
;; VtePty *vte_terminal_get_pty(VteTerminal*); int vte_pty_get_fd(VtePty*)
(ffi/defcfn vte-terminal-get-pty "vte_terminal_get_pty" [:pointer] :pointer)
(ffi/defcfn vte-pty-get-fd "vte_pty_get_fd" [:pointer] :int)
;; unistd.h: pid_t tcgetpgrp(int fd), the terminal's foreground process group
(ffi/defcfn c-tcgetpgrp "tcgetpgrp" [:int] :int)
;; signal.h: int kill(pid_t, int)
(ffi/defcfn c-kill "kill" [:int :int] :int)

(def ^:private FORMAT-TEXT 1)
(def ^:private SIGHUP 1)

;; --- terminals by widget ----------------------------------------------------

;; {terminal pointer -> the terminal's state atom}. The two callbacks below
;; are made once for the process and find their terminal here, so no
;; terminal owns a callback that would have to be freed with it.
(defonce ^:private terminals (atom {}))

(defn- error-text
  "A GError's message: struct { GQuark domain; gint code; gchar *message }."
  [err]
  (or (ffi/ptr->string (ffi/read err :pointer 8)) "the program didn't start"))

(defn- free-argv! [state]
  (when-let [{:keys [array strings]} (:argv @state)]
    (doseq [s strings] (ffi/free s))
    (ffi/free array)
    (swap! state dissoc :argv)))

(defn- contained
  "f as a fn that never throws into C: a throw is reported on stderr."
  [f]
  (fn [& args]
    (try (apply f args)
         (catch Throwable e
           (binding [*out* *err*] (println "anchan.vte: a callback threw:" (ex-message e)))))
    nil))

(defonce ^:private spawn-callback
  (delay
    (ffi/foreign-callable
     (contained
      (fn [term pid err _data]
        (when-let [state (get @terminals term)]
          (free-argv! state)
          (let [failed? (not (ffi/null? err))
                on-start (:on-start @state)]
            (when-not failed? (swap! state assoc :pid pid))
            (when on-start
              (on-start (if failed? {:error (error-text err)} {:pid pid})))))))
     [:pointer :int :pointer :pointer] :void :collect-safe)))

(defonce ^:private child-exited-callback
  (delay
    (ffi/foreign-callable
     (contained
      (fn [term status _data]
        (when-let [state (get @terminals term)]
          (let [on-exit (:on-exit @state)]
            (swap! state assoc :pid nil)
            (when on-exit (on-exit (core/exit-info status)))))))
     [:pointer :int :pointer] :void :collect-safe)))

;; --- the operations ---------------------------------------------------------

(defn- make [_opts]
  (let [term (vte-terminal-new)
        state (atom {:term term :pid nil})]
    (swap! terminals assoc term state)
    (g-signal-connect-data term "child-exited" @child-exited-callback ffi/null ffi/null 0)
    state))

(defn- argv-array
  "argv as a NULL-terminated C array, {:array p :strings [p ...]}. VTE reads
  it after the call returns, so it is freed once the spawn has answered."
  [argv]
  (let [width (ffi/sizeof :pointer)
        strings (mapv ffi/string->ptr argv)
        array (ffi/alloc (* (inc (count strings)) width))]
    (doseq [[i s] (map-indexed vector strings)]
      (ffi/write array :pointer s (* i width)))
    (ffi/write array :pointer ffi/null (* (count strings) width))
    {:array array :strings strings}))

(defn- start! [state {:keys [argv dir on-start on-exit]}]
  (when (:pid @state)
    (throw (ex-info "anchan: this terminal's program is still running" {:pid (:pid @state)})))
  ;; see anchan.limits: above the ceiling, VTE's forked child overflows its stack
  (limits/cap-open-files!)
  (free-argv! state)
  (let [built (argv-array argv)]
    (swap! state assoc :argv built :on-start on-start :on-exit on-exit)
    (vte-terminal-spawn-async (:term @state) 0 dir (:array built) ffi/null 0 ffi/null ffi/null ffi/null
                              -1 ffi/null @spawn-callback ffi/null)))

(defn- screen-text [state]
  (let [p (vte-terminal-get-text-format (:term @state) FORMAT-TEXT)]
    (when-not (ffi/null? p)
      (try (ffi/ptr->string p) (finally (g-free p))))))

(defn- rgba!
  "Writes colour c as a GdkRGBA (four floats) at p plus offset."
  [p offset c]
  (let [[r g b] (color/rgb c)]
    (ffi/write p :float (float r) offset)
    (ffi/write p :float (float g) (+ offset 4))
    (ffi/write p :float (float b) (+ offset 8))
    (ffi/write p :float (float 1.0) (+ offset 12))))

(defn- set-colors! [state {:keys [foreground background palette]}]
  (when-let [why (color/palette-problem palette)]
    (throw (ex-info (str "anchan: " why) {:palette palette})))
  (let [n (count palette)
        p (ffi/alloc (* 16 (+ 2 n)))]
    (try
      (rgba! p 0 foreground)
      (rgba! p 16 background)
      (doseq [[i c] (map-indexed vector palette)]
        (rgba! p (* 16 (+ 2 i)) c))
      (vte-terminal-set-colors (:term @state) p (ffi/slice p 16)
                               (if (pos? n) (ffi/slice p 32) ffi/null) n)
      (finally (ffi/free p)))))

(defn- set-font! [state description]
  (let [font (pango-font-description-from-string description)]
    (try (vte-terminal-set-font (:term @state) font)
         (finally (pango-font-description-free font)))))

(defn- busy?
  "True when the pty's foreground process group isn't the program's own:
  the program (a shell) is a session leader whose group is its pid, and a
  command it runs in the foreground gets a group of its own."
  [state]
  (when-let [pid (:pid @state)]
    (let [pty (vte-terminal-get-pty (:term @state))]
      (when-not (ffi/null? pty)
        (let [group (c-tcgetpgrp (vte-pty-get-fd pty))]
          (and (pos? group) (not= group pid)))))))

(defn- stop! [state]
  (when-let [pid (:pid @state)]
    ;; VTE makes the program a session leader, so its process group is its
    ;; pid: hang up the whole group, and the program itself if that fails
    (when-not (zero? (c-kill (- pid) SIGHUP))
      (c-kill pid SIGHUP))))

(defn- dispose! [state]
  (stop! state)
  (free-argv! state)
  (swap! terminals dissoc (:term @state)))

(def backend
  "The VTE backend, for anchan.core/terminal."
  {:name :vte
   :problem (fn [] @loaded)
   :available? (fn [] (nil? @loaded))
   :make make
   :widget (fn [state] (:term @state))
   :start! start!
   :feed! (fn [state text] (vte-terminal-feed-child (:term @state) text -1))
   :text screen-text
   :size (fn [state] {:columns (vte-terminal-get-column-count (:term @state))
                      :rows (vte-terminal-get-row-count (:term @state))})
   :pid (fn [state] (:pid @state))
   :busy? busy?
   :copy! (fn [state] (vte-terminal-copy-clipboard-format (:term @state) FORMAT-TEXT))
   :paste! (fn [state] (vte-terminal-paste-clipboard (:term @state)))
   :set-colors! set-colors!
   :set-font! set-font!
   :stop! stop!
   :dispose! dispose!})
