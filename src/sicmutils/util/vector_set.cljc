;;
;; Copyright © 2020 Sam Ritchie.
;; This work is based on the Scmutils system of MIT/GNU Scheme:
;; Copyright © 2002 Massachusetts Institute of Technology
;;
;; This is free software;  you can redistribute it and/or modify
;; it under the terms of the GNU General Public License as published by
;; the Free Software Foundation; either version 3 of the License, or (at
;; your option) any later version.
;;
;; This software is distributed in the hope that it will be useful, but
;; WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
;; General Public License for more details.
;;
;; You should have received a copy of the GNU General Public License
;; along with this code; if not, see <http://www.gnu.org/licenses/>.
;;

(ns sicmutils.util.vector-set
  "Contains an implementation and API for an 'ordered set' data structure backed
  by a persistent vector."
  (:refer-clojure :rename {conj core-conj}
                  :exclude [contains? disj #?(:cljs conj)])
  (:require [sicmutils.util :as u]))

(def empty-set [])

(defn make
  "Returns a new `vector-set`, ie, a vector with the distinct elements of the
  supplied sequence `xs` stored in sorted order."
  [xs]
  (into [] (dedupe) (sort xs)))

(defn union
  "Returns a vector-set containing all elements present in either sequence `x` and
  `y`."
  [x y]
  (loop [i (long 0)
         j (long 0)
         r (transient [])]
    (let [xi (nth x i nil)
          yj (nth y j nil)]
      (cond (and (not xi) (not yj)) (persistent! r)
            (not xi)  (into (persistent! r) (subvec y j))
            (not yj)  (into (persistent! r) (subvec x i))
            (< xi yj) (recur (inc i) j (conj! r xi))
            (> xi yj) (recur i (inc j) (conj! r yj))
            :else     (recur (inc i) (inc j) (conj! r xi))))))

(defn intersection
  "Returns a vector-set that contains all elements present in BOTH vector-sets `x`
  and `y`.

  `x` and `y` must be vector sets, ie, sorted and containing only distinct
  entries."
  [x y]
  (loop [i (long 0)
         j (long 0)
         r (transient [])]
    (let [xi (nth x i nil)
          yj (nth y j nil)]
      (cond (not (and xi yj)) (persistent! r)
            (< xi yj) (recur (inc i) j r)
            (> xi yj) (recur i (inc j) r)
            :else     (recur (inc i) (inc j) (conj! r xi))))))

(defn difference
  "Returns a vector-set that contains all elements present in vector-set `x` and
  NOT in vector-set `y`.

  `x` and `y` must be vector sets, ie, sorted and containing only distinct
  entries."
  [x y]
  (loop [i (long 0)
         j (long 0)
         r (transient [])]
    (let [xi (nth x i nil)
          yj (nth y j nil)]
      (cond (not xi) (persistent! r)
            (not yj) (into (persistent! r) (subvec x i))
            (< xi yj) (recur (inc i) j (conj! r xi))
            (> xi yj) (recur i (inc j) r)
            :else     (recur (inc i) (inc j) r)))))

(defn symmetric-difference
  "Returns a vector-set that contains all elements present in vector-set `x` and
  vector-set `y`, but not in both.

  `x` and `y` must be vector sets, ie, sorted and containing only distinct
  entries."
  [x y]
  (loop [i (long 0)
         j (long 0)
         r (transient [])]
    (let [xi (nth x i nil)
          yj (nth y j nil)]
      (cond (not xi) (into (persistent! r) (subvec y j))
            (not yj) (into (persistent! r) (subvec x i))
            (< xi yj) (recur (inc i) j (conj! r xi))
            (> xi yj) (recur i (inc j) (conj! r yj))
            :else     (recur (inc i) (inc j) r)))))

(defn contains?
  "Return true if the element `x` is present in the supplied vector `vset`, false
  otherwise."
  [vset x]
  (boolean
   (seq (intersection vset [x]))))

(defn conj
  "Returns a vector-set generated by inserting `x` into the appropriate position
  in the sorted, distinct vector-set `vset`. The invariant is that if `vset` is
  sorted and contains distinct elements, the return value will contain `x` and
  also be sorted.

  Attempting to insert an element `x` already contained in `vset` throws an
  exception."
  [vset x]
  (cond (empty? vset)      [x]
        (< x (nth vset 0)) (into [x] vset)
        (> x (peek vset))  (core-conj vset x)
        :else
        (loop [i (long 0)
               r (transient [])]
          (let [xi (nth vset i nil)]
            (cond (not xi) (persistent! (conj! r x))
                  (< x xi) (into (persistent! (conj! r x))
                                 (subvec vset i))
                  (= x xi)
                  (u/illegal (str "elem " x "already present in " vset))
                  :else (recur (inc i) (conj! r xi)))))))

(defn disj
  "Returns a vector-set generated by dropping `x` from the supplied vector-set
  `vset`. If `x` is not present in `vset`, acts as identity."
  [vset x]
  (cond (empty? vset) empty-set
        (or (< x (nth vset 0))
            (> x (peek vset)))
        vset
        :else (loop [i (long 0)
                     r (transient [])]
                (let [xi (nth vset i nil)]
                  (cond (or (not xi) (< x xi))
                        (persistent! r)
                        (= x xi) (into (persistent! r)
                                       (subvec vset (inc i)))
                        :else (recur (inc i) (conj! r xi)))))))
