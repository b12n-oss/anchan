(ns anchan.color
  "Colours as the engines want them: a \"#rrggbb\" string as red, green and
  blue between 0 and 1."
  (:require [clojure.string :as str]))

(defn rgb
  "\"#rrggbb\" (or \"rrggbb\", any case) as [red green blue], each from 0.0
  to 1.0. Throws on anything else."
  [s]
  (let [hex (some-> s str str/trim (str/replace #"^#" ""))]
    (when-not (and hex (re-matches #"[0-9a-fA-F]{6}" hex))
      (throw (ex-info (str "anchan: not a #rrggbb colour: " (pr-str s)) {:color s})))
    (mapv #(/ (Integer/parseInt (subs hex % (+ % 2)) 16) 255.0) [0 2 4])))

(defn palette-problem
  "Why palette can't be the ANSI palette, or nil: it is left out, or holds
  exactly 16 colours."
  [palette]
  (cond
    (nil? palette) nil
    (not (sequential? palette)) "the palette isn't a list of colours"
    (not= 16 (count palette)) (str "the palette has " (count palette) " colours, and needs 16")))
