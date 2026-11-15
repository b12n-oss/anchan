(ns anchan.core
  "A terminal to put in a GTK 4 window. This namespace is the whole public
  interface, and it names no terminal engine: an engine is a backend, a map
  of functions, passed to `terminal`. anchan.vte/backend is the first one.

      (require '[anchan.core :as anchan] '[anchan.vte :as vte])
      (def t (anchan/terminal vte/backend))
      (gtk-window-set-child win (anchan/widget t))
      (anchan/start! t {:argv [\"/bin/zsh\"] :dir \"/tmp\"
                        :on-exit (fn [{:keys [exit signal]}] ...)})

  A backend is {:name kw :available? (fn []) :problem (fn []) :make (fn
  [opts]) and the operations below, each taking the value :make answered}.
  Everything here runs on the GTK thread, as GTK itself asks."
  (:require [clojure.string :as str]))

(defn exit-info
  "A wait status as {:exit code} for a program that ended on its own, or
  {:signal n} for one a signal ended."
  [status]
  (let [sig (bit-and status 0x7f)]
    (if (zero? sig)
      {:exit (bit-and (bit-shift-right status 8) 0xff)}
      {:signal sig})))

(defn available?
  "True when backend's engine can be used on this machine."
  [backend]
  (boolean ((:available? backend))))

(defn problem
  "Why backend can't be used here, as a sentence, or nil when it can."
  [backend]
  ((:problem backend)))

(defn terminal
  "A new terminal from backend: {:backend b :impl x}. opts is the backend's
  own, and may be left out. Throws, with problem's sentence, when the
  backend can't be used here."
  ([backend] (terminal backend {}))
  ([backend opts]
   (when-let [why (problem backend)]
     (throw (ex-info why {:backend (:name backend)})))
   {:backend backend :impl ((:make backend) opts)}))

(defn- op [t k] (get (:backend t) k))

(defn widget
  "The GTK widget to put in a window: a pointer."
  [t]
  ((op t :widget) (:impl t)))

(defn start!
  "Starts a program in the terminal. opts is {:argv [program arg ...] :dir
  folder :on-start (fn [{:pid n}] or [{:error text}]) :on-exit (fn [{:exit
  code} or {:signal n}])}. :argv is required and the rest may be left out;
  the program gets this process's environment. Answers nil at once: the
  program starts a moment later, and :on-start says how it went."
  [t {:keys [argv] :as opts}]
  (when-not (and (sequential? argv) (seq argv) (every? string? argv))
    (throw (ex-info "anchan: :argv must be a program and its arguments, as strings" {:argv argv})))
  ((op t :start!) (:impl t) opts)
  nil)

(defn feed!
  "Sends text to the program as if it were typed."
  [t text]
  ((op t :feed!) (:impl t) (str text))
  nil)

(defn text
  "What the terminal shows now, as text."
  [t]
  (or ((op t :text) (:impl t)) ""))

(defn size
  "The terminal's size in characters, {:columns n :rows n}."
  [t]
  ((op t :size) (:impl t)))

(defn pid
  "The process id of the program, or nil before it starts and after it
  ends."
  [t]
  ((op t :pid) (:impl t)))

(defn running?
  "True while the program started with start! hasn't ended."
  [t]
  (some? (pid t)))

(defn busy?
  "True while the program is running something of its own in the
  foreground: a shell that has started a command, as against one waiting
  at its prompt. False when nothing runs, and when the backend can't tell."
  [t]
  (boolean ((op t :busy?) (:impl t))))

(defn copy!
  "Copies the terminal's selection to the clipboard."
  [t]
  ((op t :copy!) (:impl t))
  nil)

(defn paste!
  "Sends the clipboard's text to the program."
  [t]
  ((op t :paste!) (:impl t))
  nil)

(defn set-colors!
  "Sets the colours: {:foreground c :background c :palette [c ...]}, each c
  a \"#rrggbb\" string. :palette holds the 16 ANSI colours, or is left out
  for the engine's own."
  [t colors]
  ((op t :set-colors!) (:impl t) colors)
  nil)

(defn set-font!
  "Sets the font from a description such as \"Menlo 13\"."
  [t description]
  (when (str/blank? description)
    (throw (ex-info "anchan: a font description can't be blank" {})))
  ((op t :set-font!) (:impl t) description)
  nil)

(defn stop!
  "Ends the program, if one is running, by hanging up its terminal. The
  program's :on-exit still runs when it has gone."
  [t]
  ((op t :stop!) (:impl t))
  nil)

(defn dispose!
  "Stops the program and lets go of what the terminal holds. The widget is
  the caller's to remove from its window. A disposed terminal isn't used
  again."
  [t]
  ((op t :dispose!) (:impl t))
  nil)
