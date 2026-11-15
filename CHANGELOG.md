# Changelog

Each release is a git tag; `jolt release:notes vX.Y.Z` prints its section,
and the release workflow uses that section as the GitHub Release's notes.
New work goes under "Unreleased" until it's tagged.

## Unreleased

## v0.1.0

The first release: a terminal in a GTK 4 window from Jolt, with VTE as its
engine.

- `anchan.core`, the interface: `terminal`, `widget`, `start!`, `feed!`,
  `text`, `size`, `pid`, `running?`, `busy?`, `copy!`, `paste!`,
  `set-colors!`, `set-font!`, `stop!`, `dispose!`, `available?` and
  `problem`. It names no engine; a backend is passed in.
- `anchan.vte/backend`, over VTE's GTK 4 build. VTE is loaded on first use,
  so a machine without it can still require the library and ask what's
  missing.
- `anchan.limits` lowers the soft open-files limit to 65536 before a program
  starts, because VTE's child overflows its stack on macOS at 1048576.
- Works on an Apple Silicon Mac with VTE from Homebrew. The unit tests run
  on Linux in CI, but terminals there are untested.
