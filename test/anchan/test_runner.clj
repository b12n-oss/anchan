(ns anchan.test-runner
  "Runs the unit tests, which need no display and no VTE: jolt test"
  (:require [anchan.core-test]
            [clojure.test :as t]))

(defn -main [& _]
  (let [{:keys [test pass fail error]} (t/run-tests 'anchan.core-test)]
    (println (str "\nSUMMARY: " test " tests, " (+ pass fail error) " assertions, "
                  fail " failures, " error " errors"))
    (System/exit (if (zero? (+ fail error)) 0 1))))
