;
; Copyright © 2017 Colin Smith.
; This work is based on the Scmutils system of MIT/GNU Scheme:
; Copyright © 2002 Massachusetts Institute of Technology
;
; This is free software;  you can redistribute it and/or modify
; it under the terms of the GNU General Public License as published by
; the Free Software Foundation; either version 3 of the License, or (at
; your option) any later version.
;
; This software is distributed in the hope that it will be useful, but
; WITHOUT ANY WARRANTY; without even the implied warranty of
; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
; General Public License for more details.
;
; You should have received a copy of the GNU General Public License
; along with this code; if not, see <http://www.gnu.org/licenses/>.
;

(ns sicmutils.value
  (:refer-clojure :rename {zero? core-zero?
                           number? core-number?}
                  #?@(:cljs [:exclude [zero? number?]]))
  (:require [sicmutils.util :as u]
            #?@(:cljs
                [[goog.math.Long]
                 [goog.math.Integer]]))
  #?(:clj
     (:import (clojure.lang BigInt PersistentVector Sequential Symbol))))

(defprotocol Value
  (numerical? [this])
  (zero? [this])
  (one? [this])
  (zero-like [this])
  (one-like [this])
  (exact? [this])
  (freeze [this]
    "Freezing an expression means removing wrappers and other metadata from
  subexpressions, so that the result is basically a pure S-expression with the
  same structure as the input. Doing this will rob an expression of useful
  information for further computation; so this is intended to be done just
  before simplification and printing, to simplify those processes.")
  (kind [this]))

(def argument-kind #(mapv kind %&))

(def object-name-map (atom {}))

(def seqtype #?(:clj Sequential :cljs ::seq))

;; Allows multimethod dispatch to seqs in CLJS.
#?(:cljs
   (do
     (derive IndexedSeq ::seq)
     (derive PersistentVector ::seq)
     (derive LazySeq ::seq)))

;; Smaller inheritance tree to enabled shared implementations between numeric
;; types that represent mathematical integers.

(derive ::native-integral ::integral)
(derive ::integral ::real)
(derive ::floating-point ::real)
(derive ::real ::number)

(defn native-integral?
  "Returns true if x is an integral number that Clojure's math operations work
  with, false otherwise."
  [x]
  (isa? (kind x) ::native-integral))

(defn integral?
  "Returns true if x is an integral number, false otherwise."
  [x]
  (isa? (kind x) ::integral))

(defn real?
  "Returns true if `x` is either an integral number or a floating point number (ie,
  in the numeric tower but not complex), false otherwise."
  [x]
  (isa? (kind x) ::real))

(defn number?
  "Returns true if `x` is any number type in the numeric tower:

  - integral
  - floating point
  - complex

  false otherwise."
  [x]
  (isa? (kind x) ::number))

;; `::scalar` is a thing that symbolic expressions AND actual numbers both
;; derive from.
(derive ::number ::scalar)

(defn scalar?
  "Returns true for anything that derives from `::scalar`, ie, any numeric type in
  the numeric tower that responds true to [[number?]], plus symbolic expressions
  generated [[sicmutils.abstract.number/literal-number]],

  false otherwise."
  [x]
  (isa? (kind x) ::scalar))

#?(:clj
   (do
     (derive Number ::real)
     (derive Double ::floating-point)
     (derive Float ::floating-point)
     (derive BigDecimal ::floating-point)
     (derive Integer ::native-integral)
     (derive Long ::native-integral)
     (derive BigInt ::native-integral)
     (derive BigInteger ::native-integral))

   :cljs
   (do (derive js/Number ::real)
       (derive js/BigInt ::integral)
       (derive goog.math.Integer ::integral)
       (derive goog.math.Long ::integral)))

(extend-protocol Value
  #?(:clj Number :cljs number)
  (zero? [x] (core-zero? x))
  (one? [x] (== 1 x))
  (zero-like [_] 0)
  (one-like [_] 1)
  (freeze [x] x)
  (exact? [x] (or (integer? x) #?(:clj (ratio? x))))
  (numerical? [_] true)
  (kind [x] #?(:clj (type x)
               :cljs (if (exact? x)
                       ::native-integral
                       ::floating-point)))

  #?(:clj Boolean :cljs boolean)
  (zero? [x] false)
  (one? [x] false)
  (zero-like [_] 0)
  (one-like [_] 1)
  (freeze [x] x)
  (exact? [x] false)
  (numerical? [_] false)
  (kind [x] (type x))

  #?@(:clj
      [java.lang.Double
       (zero? [x] (core-zero? x))
       (one? [x] (== 1 x))
       (zero-like [_] 0.0)
       (one-like [_] 1.0)
       (freeze [x] x)
       (exact? [x] false)
       (numerical? [_] true)
       (kind [x] (type x))

       java.lang.Float
       (zero? [x] (core-zero? x))
       (one? [x] (== 1 x))
       (zero-like [_] 0.0)
       (one-like [_] 1.0)
       (freeze [x] x)
       (exact? [x] false)
       (numerical? [_] true)
       (kind [x] (type x))])

  nil
  (zero? [_] true)
  (zero-like [o] (u/unsupported "nil doesn't support zero-like."))
  (one?[_] false)
  (one-like [o] (u/unsupported "nil doesn't support one-like."))
  (numerical? [_] false)
  (freeze [_] nil)
  (kind [_] nil)

  PersistentVector
  (zero? [v] (every? zero? v))
  (one? [_] false)
  (zero-like [v] (mapv zero-like v))
  (one-like [o] (u/unsupported (str "one-like: " o)))
  (exact? [v] (every? exact? v))
  (numerical? [_] false)
  (freeze [v] (mapv freeze v))
  (kind [v] (type v))

  #?(:clj Object :cljs default)
  (zero? [o] false)
  (numerical? [_] false)
  (one? [o] false)
  (exact? [o] false)
  (zero-like [o] (u/unsupported (str "zero-like: " o)))
  (one-like [o] (u/unsupported (str "one-like: " o)))
  (freeze [o] (if (sequential? o)
                (map freeze o)
                (get @object-name-map o o)))
  (kind [o] (:type o (type o))))

;; Override equiv for numbers.
(defmulti eq argument-kind)

;; These two constitute the default cases.
(defmethod eq [::number ::number] [l r]
  #?(:clj  (= l r)
     :cljs (identical? l r)))

(defmethod eq :default [l r]
  (if (or (isa? (kind l) ::number)
          (isa? (kind r) ::number))
    false
    (= l r)))

#?(:cljs
   ;; These definitions are required for the protocol implementation below.
   (do
     (defmethod eq [::native-integral js/BigInt] [l r]
       (js*  "~{} == ~{}" l r))

     (defmethod eq [js/BigInt ::native-integral] [l r]
       (js*  "~{} == ~{}" l r))

     (doseq [[from to f] [[goog.math.Long goog.math.Integer u/int]
                          [::native-integral goog.math.Integer u/int]
                          [::native-integral goog.math.Long u/long]
                          [goog.math.Long js/BigInt u/bigint]
                          [goog.math.Integer js/BigInt u/bigint]]]
       (defmethod eq [from to] [l r] (= (f l) r))
       (defmethod eq [to from] [l r] (= l (f r))))

     (extend-protocol IEquiv
       number
       (-equiv [this other]
         (cond (core-number? other) (identical? this other)
               (numerical? other)   (eq this other)
               :else false))

       goog.math.Integer
       (-equiv [this other]
         (if (= goog.math.Integer (type other))
           (.equals this other)
           (eq this other)))

       goog.math.Long
       (-equiv [this other]
         (if (= goog.math.Long (type other))
           (.equals this other)
           (eq this other))))))

#?(:cljs
   (extend-type js/BigInt
     IEquiv
     (-equiv [this other]
       (if (= js/BigInt (type other))
         (js*  "~{} == ~{}" this other)
         (eq this other)))

     IPrintWithWriter
     (-pr-writer [x writer opts]
       (let [rep (if (<= x (.-MAX_SAFE_INTEGER js/Number))
                   (str x)
                   (str "\"" x "\""))]
         (write-all writer "#sicm/bigint " rep)))))

#?(:cljs
   (extend-protocol IComparable
     goog.math.Integer
     (-compare [this other]
       (if (core-number? other)
         (.compare this (u/int other))
         (.compare this other)))

     goog.math.Long
     (-compare [this other]
       (if (core-number? other)
         (.compare this (u/long other))
         (.compare this other)))))

#?(:cljs
   ;; Clojurescript-specific implementations of Value.
   (let [big-zero (js/BigInt 0)
         big-one (js/BigInt 1)]

     (extend-protocol Value
       js/BigInt
       (zero? [x] (js*  "~{} == ~{}" big-zero x))
       (one? [x] (js*  "~{} == ~{}" big-one x))
       (zero-like [_] big-zero)
       (one-like [_] big-one)
       (freeze [x]
         ;; Bigint freezes into a non-bigint if it can be represented as a
         ;; number; otherwise, it turns into its own literal.
         (if (<= x (.-MAX_SAFE_INTEGER js/Number))
           (js/Number x)
           x))
       (exact? [_] true)
       (numerical? [_] true)
       (kind [_] js/BigInt)

       goog.math.Integer
       (zero? [x] (.isZero x))
       (one? [x] (= (.-ONE goog.math.Integer) x))
       (zero-like [_] (.-ZERO goog.math.Integer))
       (one-like [_] (.-ONE goog.math.Integer))
       (freeze [x] x)
       (exact? [_] true)
       (numerical? [_] true)
       (kind [_] goog.math.Integer)

       goog.math.Long
       (zero? [x] (.isZero x))
       (one? [x] (= (.getOne goog.math.Long) x))
       (zero-like [_] (.getZero goog.math.Long))
       (one-like [_] (.getOne goog.math.Long))
       (freeze [x] x)
       (exact? [x] true)
       (numerical? [_] true)
       (kind [_] goog.math.Long))))

(defn add-object-symbols!
  [o->syms]
  (swap! object-name-map into o->syms))

(def machine-epsilon
  (loop [e 1.0]
    (if (= 1.0 (+ e 1.0))
      (* e 2.0)
      (recur (/ e 2.0)))))

(def sqrt-machine-epsilon
  (Math/sqrt machine-epsilon))

(defn within
  "Returns a function that tests whether two values are within ε of each other."
  [^double ε]
  (fn [^double x ^double y] (< (Math/abs (- x y)) ε)))

(def twopi (* 2 Math/PI))

(defn principal-value
  [cuthigh]
  (let [cutlow (- cuthigh twopi)]
    (fn [x]
      (if (and (<= cutlow x) (< x cuthigh))
        x
        (let [y (- x (* twopi (Math/floor (/ x twopi))))]
          (if (< y cuthigh)
            y
            (- y twopi)))))))
