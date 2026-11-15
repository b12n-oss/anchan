<p align="center"><img src="docs/images/logo.svg" alt="Anchan logo: a butterfly pea flower drawn as a fan of layered indigo petals over a green calyx" width="128"></p>

<h1 align="center">Anchan</h1>

A terminal you can put in a GTK 4 window from
[Jolt](https://github.com/jolt-lang/jolt), Clojure on Chez Scheme. Anchan
(อัญชัน) is the Thai name for the butterfly pea, a vine that wraps around
whatever holds it up.

**Status: early.** It works on an Apple Silicon Mac with VTE from Homebrew.
Linux is untested, and so is typing into it from inside another app's key
handling.

```clojure
(require '[anchan.core :as anchan]
         '[anchan.vte :as vte])

(def t (anchan/terminal vte/backend))
(gtk-window-set-child window (anchan/widget t))   ; your own GTK bindings
(anchan/start! t {:argv ["/bin/zsh"] :dir "/path/to/project"
                  :on-start (fn [{:keys [pid error]}] ...)
                  :on-exit (fn [{:keys [exit signal]}] ...)})
```

## What it does

`anchan.core` is the whole interface, and it names no terminal engine. You
pass it a backend. `anchan.vte/backend` wraps
[VTE](https://gitlab.gnome.org/GNOME/vte), the widget GNOME Terminal uses,
in its GTK 4 build. Another engine, such as
[libghostty-vt](https://lib.ghostty.org/vt/), can be a second backend
behind the same functions.

| Function | Does |
|---|---|
| `terminal` | Makes a terminal from a backend |
| `widget` | The GTK widget to put in your window |
| `start!` | Starts a program in a folder; `:on-start` and `:on-exit` report back |
| `feed!` | Sends text to the program as if typed |
| `text`, `size` | What the terminal shows, and its size in characters |
| `pid`, `running?` | The program's process, while it runs |
| `copy!`, `paste!` | The selection to the clipboard, and back |
| `set-colors!`, `set-font!` | `"#rrggbb"` colours with an optional 16-colour palette, and a font such as `"Menlo 13"` |
| `stop!` | Hangs up the program |
| `dispose!` | Stops the program and lets go of the terminal |
| `available?`, `problem` | Whether a backend can run here, and why not |

Call them on the GTK thread.

[Jonquil](https://github.com/b12n-oss/jonquil) is the first program that
will use it.

## Trying it

You need Jolt, GTK 4 and VTE's GTK 4 build (`brew install vte3` on a Mac).

```sh
jolt shell      # your shell in a window; it closes when the shell exits
jolt test       # unit tests, no display or VTE needed
jolt selftest   # opens a window and drives real terminals through VTE
```

`jolt selftest` starts shells, reads what they print, checks exit codes,
stops one while the app runs and checks its process is gone.

## One thing VTE can't take

With a very high open-files limit, VTE overflows its stack while starting a
program on macOS, and the program dies at once. A limit of 262144 works and
1048576 doesn't. Anchan lowers this process's soft limit to 65536 before it
starts a program, and leaves a lower limit and the hard limit alone
(`anchan.limits`).

## License

Eclipse Public License 2.0, see [LICENSE](LICENSE). VTE has its own
license.
