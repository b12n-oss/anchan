<p align="center"><img src="docs/images/logo.svg" alt="Anchan logo: a butterfly pea flower drawn as a fan of layered indigo petals over a green calyx" width="128"></p>

<h1 align="center">Anchan</h1>

A terminal you can put in a GTK 4 window from
[Jolt](https://github.com/jolt-lang/jolt), Clojure on Chez Scheme. Anchan
(อัญชัน) is the Thai name for the butterfly pea, a vine that wraps around
whatever holds it up.

**Status: a spike.** Nothing here is a library yet. `spike/vte_spike.clj`
shows that VTE's GTK 4 terminal widget draws, runs a shell and reports back
through Jolt's FFI on an Apple Silicon Mac.

## The plan

Anchan will have a small interface that names no terminal engine: make a
terminal and get a GTK widget, start a program in a folder, feed it text,
copy and paste, set its colours and font, and hear when the program ends.
The first engine behind it is [VTE](https://gitlab.gnome.org/GNOME/vte),
the widget GNOME Terminal uses. Another engine, such as
[libghostty-vt](https://lib.ghostty.org/vt/), can sit behind the same
interface later.

[Jonquil](https://github.com/b12n-oss/jonquil) is the first program that
will use it.

## Running the spike

You need Jolt, GTK 4, VTE's GTK 4 build (`brew install vte3` on a Mac) and
a checkout of Jonquil, whose GTK bindings the spike borrows.

```sh
cd ../jonquil
VTE_SPIKE_MODE=exit jolt -e '(load-file "../anchan/spike/vte_spike.clj")'
```

It opens a window, runs `zsh` in `/tmp`, types one `echo` into it, prints
what it saw as `SPIKE` lines, tells the shell to exit, and quits.

If your shell allows about a million open files (`ulimit -n` prints
1048576), lower it first with `ulimit -n 4096`. Above that, VTE overflows
its stack while starting the shell and the shell dies at once. Anchan will
cap the limit itself.

## License

Eclipse Public License 2.0, see [LICENSE](LICENSE). VTE has its own
license.
