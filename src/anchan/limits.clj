(ns anchan.limits
  "The open-files limit, which VTE can't take when it is very high. Before
  it starts a program VTE forks, and the forked child walks every possible
  file descriptor with a buffer on its stack sized by the limit. On macOS
  with a soft limit of 1048576 that overflows the stack and the program
  dies at once (measured 2026-11-15: 262144 works, 1048576 doesn't). So
  the soft limit is lowered to a ceiling before a program starts. A limit
  already under the ceiling is left alone, and the hard limit never moves."
  (:require [jolt.ffi :as ffi]))

(def ceiling
  "The highest soft open-files limit Anchan leaves in place."
  65536)

(defn capped
  "The soft limit to set for a current soft limit, or nil to leave it: only
  a limit above ceiling is lowered, to ceiling."
  [soft]
  (when (> soft ceiling) ceiling))

;; sys/resource.h: RLIMIT_NOFILE is 8 on macOS and 7 on Linux.
(defn- nofile [] (if (= "Mac OS X" (System/getProperty "os.name")) 8 7))

;; int getrlimit(int, struct rlimit *) and setrlimit(int, const struct rlimit *):
;; struct rlimit is two rlim_t, soft then hard, 64 bits each on both systems.
(ffi/defcfn c-getrlimit "getrlimit" [:int :pointer] :int)
(ffi/defcfn c-setrlimit "setrlimit" [:int :pointer] :int)

(defn open-files
  "This process's open-files limits, {:soft n :hard n}, or nil when they
  can't be read."
  []
  (let [p (ffi/alloc 16)]
    (try
      (when (zero? (c-getrlimit (nofile) p))
        {:soft (ffi/read p :uint64 0) :hard (ffi/read p :uint64 8)})
      (finally (ffi/free p)))))

(defn cap-open-files!
  "Lowers this process's soft open-files limit to ceiling when it is above
  it. Answers {:was n :now n} when it lowered it, else nil. Never throws."
  []
  (try
    (when-let [{:keys [soft hard]} (open-files)]
      (when-let [to (capped soft)]
        (let [p (ffi/alloc 16)]
          (try
            (ffi/write p :uint64 to 0)
            (ffi/write p :uint64 hard 8)
            (when (zero? (c-setrlimit (nofile) p))
              {:was soft :now to})
            (finally (ffi/free p))))))
    (catch Throwable _ nil)))
