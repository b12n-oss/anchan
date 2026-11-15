# Getting started

## What you need

- [Jolt](https://github.com/jolt-lang/jolt) 0.8.27 or later.
- GTK 4 and VTE's GTK 4 build. On a Mac that's `brew install vte3`, which
  brings GTK 4 with it. On Linux, look for your distribution's
  `libvte-2.91-gtk4` package.

Anchan loads VTE the first time you make a terminal, not when you require
it. So a program can require `anchan.vte` on a machine without VTE and ask
what's missing before it tries anything:

```clojure
(anchan/problem vte/backend)
;; => nil when VTE loads, or a sentence such as
;;    "VTE's GTK 4 library isn't installed (looked for [...]). Try: brew install vte3"
```

VTE is looked for in Homebrew's folders on a Mac and on the library path on
Linux. Set `ANCHAN_VTE_LIB` to a full path to use another copy, or reset
`anchan.vte/library-paths` before the first terminal when your app bundles
its own.

## Adding it to a project

Anchan is a git dependency. Pin a release tag and the commit it names:

```clojure
{:deps {io.github.b12n-oss/anchan
        {:git/url "https://github.com/b12n-oss/anchan.git"
         :git/tag "v0.1.0" :git/sha "<the tag's commit>"}}}
```

`git ls-remote https://github.com/b12n-oss/anchan refs/tags/v0.1.0` prints
that commit. Before the first release, pin a commit from `main` with
`:git/sha` alone, which is how Jonquil does it today.

## A shell in a window

This is the example in `examples/shell.clj`, cut down. The `gtk/` functions
come from that example's own small binding, `dev/anchan_dev/gtk.clj`.
Anchan doesn't bind GTK for you, since your app already has its own
window code.

```clojure
(gtk/run-app!
 "net.b12n.anchan.example.shell"
 (fn [app]
   (let [win (gtk/gtk-application-window-new app)
         t (anchan/terminal vte/backend)]
     (gtk/gtk-window-set-child win (anchan/widget t))
     (anchan/set-colors! t {:foreground "#e6e6f0" :background "#1b1c3a"})
     (gtk/gtk-window-present win)
     (anchan/start! t {:argv [(or (System/getenv "SHELL") "/bin/sh")]
                       :dir (System/getProperty "user.dir")
                       :on-exit (fn [_]
                                  (anchan/dispose! t)
                                  (gtk/g-application-quit app))}))))
```

From a checkout you can run the full version, or the tests:

```sh
jolt shell      # your shell in a window; it closes when the shell exits
jolt test       # unit tests, no display or VTE needed
jolt selftest   # opens a window and drives real terminals through VTE
```

Every call into Anchan happens on the GTK thread, the same as GTK itself.
