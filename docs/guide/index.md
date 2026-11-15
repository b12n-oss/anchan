# Anchan Guide

**Anchan** is a terminal you can put in a GTK 4 window from
[Jolt](https://github.com/jolt-lang/jolt), Clojure on Chez Scheme. You give
it a window, it gives you back a widget with a running program inside, and
it tells you when that program starts and when it ends.

Its interface, `anchan.core`, never names a terminal engine. The engine is
a backend you pass in, and the one that ships is
[VTE](https://gitlab.gnome.org/GNOME/vte), the widget GNOME Terminal is
built on, in its GTK 4 build.

```clojure
(require '[anchan.core :as anchan]
         '[anchan.vte :as vte])

(def t (anchan/terminal vte/backend))
(gtk-window-set-child window (anchan/widget t))   ; your own GTK bindings
(anchan/start! t {:argv ["/bin/zsh"] :dir "/path/to/project"
                  :on-start (fn [{:keys [pid error]}] ...)
                  :on-exit (fn [{:keys [exit signal]}] ...)})
```

Anchan is early. It works on an Apple Silicon Mac with VTE from Homebrew,
and its unit tests pass on Linux, but nobody has opened a terminal there
yet.

## Pages

- [Getting started](getting-started.md): what to install, the `deps.edn`
  line, and a shell in a window.
- [The interface](interface.md): every function in `anchan.core`, the
  options `start!` takes, and what a backend has to provide.
- [The open-files limit](open-files.md): the one thing VTE can't take, and
  what Anchan does about it.
- [Contributing](contributing.md): the tasks, what CI checks, and how a
  release is cut.

## Where it's used

[Jonquil](https://github.com/b12n-oss/jonquil), a Clojure editor on Jolt
and GTK 4, is the first program to use it.

Anchan is under the Eclipse Public License 2.0. VTE has its own license.
