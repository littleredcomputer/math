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

(ns sicmutils.numerical.quadrature.bulirsch-stoer
  (:require [sicmutils.numerical.interpolate.rational :as rat]
            [sicmutils.numerical.quadrature.midpoint :as mid]
            [sicmutils.numerical.quadrature.trapezoid :as trap]
            [sicmutils.generic :as g]
            [sicmutils.util :as u]
            [sicmutils.util.stream :as us]))

(def bulirsch-stoer-steps
  (interleave
   (us/powers 2 2)
   (us/powers 2 3)))

(defn slice-width [a b]
  (let [width (- b a)]
    (fn [n] (/ width n))))

(defn h-sequence
  "Defines the sequence of slice widths, given a sequence of `n` (number of
  slices) in the interval $(a, b)$."
  ([a b] (h-sequence a b bulirsch-stoer-steps))
  ([a b n-seq]
   (map (slice-width a b) n-seq)))

(defn- bs-sequence-fn [integrator-seq]
  (fn call
    ([f a b]
     (call f a b bulirsch-stoer-steps))
    ([f a b n-seq]
     (let [square (fn [x] (* x x))
           xs (map square (h-sequence a b n-seq))
           ys (integrator-seq f a b n-seq)]
       (-> (map vector xs ys)
           (rat/modified-bulirsch-stoer 0))))))

(def ^{:doc "Docstring"}
  open-sequence
  (bs-sequence-fn mid/midpoint-sequence))

(def ^{:doc "Docstring"}
  closed-sequence
  (bs-sequence-fn trap/trapezoid-sequence))

(defn open-integral
  ([f a b] (open-integral f a b {}))
  ([f a b opts]
   (-> (open-sequence f a b)
       (us/seq-limit opts))))

(defn closed-integral
  ([f a b] (closed-integral f a b {}))
  ([f a b opts]
   (-> (closed-sequence f a b)
       (us/seq-limit opts))))