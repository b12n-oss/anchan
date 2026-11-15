# The open-files limit

With a very high open-files limit, VTE overflows its stack while starting a
program on macOS, and the program dies the moment it starts. It's easier
to hit than it sounds: the Mac Anchan was written on gives every shell a
soft limit of 1048576.

Before it forks, VTE's child process walks every file descriptor it could
have, using a buffer on its stack sized by that limit. Measured on
2026-11-15, a soft limit of 262144 worked and 1048576 didn't.

So Anchan lowers this process's soft limit to 65536 (`anchan.limits/ceiling`)
before it starts a program. A soft limit already below that is left alone,
and so is the hard limit. You can see the current values from a REPL:

```clojure
(require '[anchan.limits :as limits])
(limits/open-files)   ;; => {:soft 1048576, :hard 9223372036854775807} on that Mac
```

The cap applies to your whole process, not only the terminal, because the
soft limit belongs to the process. If your app needs more than 65536 open
files at once, open them before you start a terminal, or start terminals
from a helper process.

For a hand run outside Anchan, `ulimit -n 4096` first does the same job.
