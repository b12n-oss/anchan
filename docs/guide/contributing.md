# Contributing

Anchan is small, and the aim is to keep it that way. `anchan.core` should
only grow by things every terminal engine can do. Anything that's VTE's
alone belongs in `anchan.vte`.

## The tasks

Every task runs through Jolt, which reads them from `deps.edn`.
`jolt info` lists them all.

```sh
jolt test           # unit tests: no display, no VTE
jolt selftest       # real terminals in a window (needs a display and VTE)
jolt verify         # what CI checks: lint:strict, format:check, metrics:gate, test
jolt lint           # clj-kondo over src, test, dev, examples and ci
jolt format         # reformat with clojure-lsp (format:check is the dry run)
jolt metrics        # jolt-metrics' report; metrics:gate is the version that fails
jolt site:serve     # this guide, built and served at /anchan/ on a free port
```

`lint` needs [clj-kondo](https://github.com/clj-kondo/clj-kondo) and
`format` needs [clojure-lsp](https://clojure-lsp.io). The site tasks look
for a checkout of
[jlt-commons/docs-engine](https://github.com/jlt-commons/docs-engine) in
`~/dev/jlt-commons/docs-engine`, or wherever `$JLT_DOCS_ENGINE` points.

`jolt selftest` opens a window, so on a Mac with a very high open-files
limit, start it from a shell where you've run `ulimit -n 4096`.

## What CI checks

`.github/workflows/ci.yml` runs on every push to `main` and every pull
request. Each lane is one of the tasks above, so a red lane reproduces with
the same command on your machine.

| Lane | Runs |
|---|---|
| clj-kondo and format check | `jolt lint:strict`, `jolt format:check` |
| jolt-metrics gate | `jolt metrics:gate`, with the report in the job summary |
| unit tests | `jolt test` on Linux and on macOS |
| no AI attribution | `ci/attributioncheck.sh` over the new commits and the pull request text |

The last job, `ci ok`, passes only when all of them did. `metrics:gate`
fails on any critical jolt-metrics finding, and on unused code that
`ci/metrics-allow.edn` doesn't list with a reason it stays. `copy!` and
`paste!` are on that list because nothing in this repo calls them.

`.github/workflows/site.yml` builds this guide on every pull request, checks
it with `docs/check-site.sh`, and publishes it from `main`.

## Releasing

A release is a tag. Programs pin it in `deps.edn`, so there's nothing to
build or upload.

1. Move the notes under `## Unreleased` in `CHANGELOG.md` into a new
   `## v0.2.0` section, say, and merge that to `main`.
2. Check the notes read the way you want: `jolt release:notes v0.2.0`.
3. Tag the merged commit and push the tag:

   ```sh
   git tag -a v0.2.0 -m "Anchan v0.2.0"
   git push origin v0.2.0
   ```

`.github/workflows/release.yml` then runs CI against the tagged commit,
checks that the tag is on `main`, and makes the GitHub Release with that
`CHANGELOG.md` section as its notes. When a check fails, no release is
made. Fix it on `main` and tag the next version, rather than moving a tag
someone may already have pinned.
