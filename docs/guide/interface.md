# The interface

Everything a program calls is in `anchan.core`. Each function takes the
terminal that `terminal` made, and runs on the GTK thread.

## Making and placing a terminal

| Function | Does |
|---|---|
| `(terminal backend)`, `(terminal backend opts)` | Makes a terminal. Throws, with `problem`'s sentence, when the backend can't run here |
| `(widget t)` | The GTK widget to put in your window, as a pointer |
| `(available? backend)` | True when the backend's engine can be used on this machine |
| `(problem backend)` | Why it can't, as a sentence, or nil |

## Running a program

`start!` takes a map:

```clojure
(anchan/start! t {:argv ["/bin/zsh" "-l"]      ; required: the program and its arguments
                  :dir "/path/to/project"      ; the folder it starts in
                  :on-start (fn [{:keys [pid error]}] ...)
                  :on-exit (fn [{:keys [exit signal]}] ...)})
```

It returns nil straight away, because the program starts a moment later.
`:on-start` then gets `{:pid n}`, or `{:error text}` when it couldn't
start. `:on-exit` gets `{:exit code}` for a program that ended on its own
and `{:signal n}` for one a signal ended. The program inherits this
process's environment.

| Function | Does |
|---|---|
| `(feed! t text)` | Sends text to the program as if it were typed |
| `(pid t)` | The program's process id, or nil before it starts and after it ends |
| `(running? t)` | True while the program hasn't ended |
| `(busy? t)` | True while a shell is running a command, false while it waits at its prompt or when the backend can't tell |
| `(stop! t)` | Hangs up the terminal, which ends the program. `:on-exit` still runs |
| `(dispose! t)` | Stops the program and lets go of the terminal. Removing the widget from its window is up to you |

## Reading and styling it

| Function | Does |
|---|---|
| `(text t)` | What the terminal shows now, as a string |
| `(size t)` | `{:columns n :rows n}` |
| `(copy! t)`, `(paste! t)` | The selection to the clipboard, and the clipboard to the program |
| `(set-colors! t colors)` | `{:foreground c :background c :palette [c ...]}`, each `c` a `"#rrggbb"` string. `:palette` is the 16 ANSI colours, or left out for the engine's own |
| `(set-font! t description)` | A Pango font description such as `"Menlo 13"` |

## Writing a backend

A backend is a plain map, so a second engine such as
[libghostty-vt](https://lib.ghostty.org/vt/) can sit behind the same
functions. `anchan.vte/backend` is the one to read alongside this table.

| Key | Called as | Answers |
|---|---|---|
| `:name` | | A keyword, such as `:vte` |
| `:available?` | `(f)` | Truthy when the engine can run here |
| `:problem` | `(f)` | A sentence saying why it can't, or nil |
| `:make` | `(f opts)` | The backend's own value for one terminal, passed to every key below |
| `:widget` | `(f impl)` | The GTK widget pointer |
| `:start!` | `(f impl opts)` | Starts the program; reports through `opts`' callbacks |
| `:feed!` | `(f impl text)` | |
| `:text` | `(f impl)` | A string, or nil for empty |
| `:size` | `(f impl)` | `{:columns n :rows n}` |
| `:pid` | `(f impl)` | The process id, or nil |
| `:busy?` | `(f impl)` | Truthy while a foreground job runs, nil when it can't tell |
| `:copy!`, `:paste!` | `(f impl)` | |
| `:set-colors!` | `(f impl colors)` | |
| `:set-font!` | `(f impl description)` | |
| `:stop!`, `:dispose!` | `(f impl)` | |

`anchan.core` checks what it can before it calls a backend: `start!`
refuses an `:argv` that isn't a list of strings, and `set-font!` refuses a
blank description. A backend doesn't need to check those again.

Keep anything only one engine can do out of this map. If VTE has a
feature worth exposing, it goes in `anchan.vte` as its own function, so
another backend never has to fake it.
