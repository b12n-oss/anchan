(ns anchan.core-test
  (:require [anchan.color :as color]
            [anchan.core :as anchan]
            [anchan.limits :as limits]
            [clojure.test :refer [deftest is testing]]))

(defn- fake-backend
  "A backend that records what it is asked, in log, and has no engine."
  [log]
  (let [note (fn [k] (fn [impl & args] (swap! log conj (into [k impl] args)) nil))]
    {:name :fake
     :problem (constantly nil) :available? (constantly true)
     :make (fn [opts] (swap! log conj [:make opts]) :impl)
     :widget (fn [_] :the-widget)
     :start! (note :start!) :feed! (note :feed!)
     :text (fn [_] nil) :size (fn [_] {:columns 80 :rows 24})
     :pid (fn [_] (:pid (peek (filterv map? @log))))
     :busy? (fn [_] nil)
     :copy! (note :copy!) :paste! (note :paste!)
     :set-colors! (note :set-colors!) :set-font! (note :set-font!)
     :stop! (note :stop!) :dispose! (note :dispose!)}))

(deftest exit-info-test
  (is (= {:exit 0} (anchan/exit-info 0)))
  (is (= {:exit 3} (anchan/exit-info (bit-shift-left 3 8))) "a wait status holds the exit code in its second byte")
  (is (= {:signal 9} (anchan/exit-info 9)))
  (is (= {:signal 1} (anchan/exit-info 1)))
  (is (= {:signal 11} (anchan/exit-info 11))))

(deftest a-terminal-passes-each-call-to-its-backend-test
  (let [log (atom [])
        t (anchan/terminal (fake-backend log) {:scrollback 100})]
    (is (= [[:make {:scrollback 100}]] @log))
    (is (= :the-widget (anchan/widget t)))
    (is (= "" (anchan/text t)) "no text is the empty string")
    (is (= {:columns 80 :rows 24} (anchan/size t)))
    (is (false? (anchan/running? t)))
    (is (false? (anchan/busy? t)) "a backend that can't tell answers not busy")
    (anchan/start! t {:argv ["/bin/sh"] :dir "/tmp"})
    (anchan/feed! t "ls\n")
    (anchan/set-font! t "Menlo 13")
    (anchan/stop! t)
    (anchan/dispose! t)
    (is (= [[:start! :impl {:argv ["/bin/sh"] :dir "/tmp"}] [:feed! :impl "ls\n"]
            [:set-font! :impl "Menlo 13"] [:stop! :impl] [:dispose! :impl]]
           (subvec @log 1)))))

(deftest bad-arguments-are-refused-before-the-backend-test
  (let [log (atom [])
        t (anchan/terminal (fake-backend log))]
    (doseq [bad [nil [] "sh" [:sh] ["sh" 3]]]
      (is (thrown? Exception (anchan/start! t {:argv bad})) (pr-str bad)))
    (is (thrown? Exception (anchan/set-font! t " ")))
    (is (= [[:make {}]] @log) "nothing reached the backend")))

(deftest an-unusable-backend-says-why-test
  (let [b (assoc (fake-backend (atom [])) :problem (constantly "no engine here") :available? (constantly false))]
    (is (false? (anchan/available? b)))
    (is (= "no engine here" (anchan/problem b)))
    (is (thrown-with-msg? Exception #"no engine here" (anchan/terminal b)))))

(deftest color-test
  (is (= [1.0 0.0 0.0] (color/rgb "#ff0000")))
  (is (= [0.0 1.0 0.0] (color/rgb "00FF00")))
  (is (= [(/ 0x1b 255.0) (/ 0x1c 255.0) (/ 0x3a 255.0)] (color/rgb " #1b1c3a ")))
  (doseq [bad [nil "" "#fff" "#gggggg" "red" "#ff000080"]]
    (is (thrown? Exception (color/rgb bad)) (pr-str bad)))
  (testing "the palette is left out or has 16 colours"
    (is (nil? (color/palette-problem nil)))
    (is (nil? (color/palette-problem (repeat 16 "#000000"))))
    (is (= "the palette has 8 colours, and needs 16" (color/palette-problem (repeat 8 "#000000"))))
    (is (some? (color/palette-problem "#000000")))))

(deftest open-files-ceiling-test
  (is (nil? (limits/capped 256)))
  (is (nil? (limits/capped limits/ceiling)) "a limit at the ceiling stays")
  (is (= limits/ceiling (limits/capped (inc limits/ceiling))))
  (is (= limits/ceiling (limits/capped 1048576)) "the limit that crashed VTE's child"))
