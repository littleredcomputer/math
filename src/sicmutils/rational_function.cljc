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

(ns sicmutils.rational-function
  (:require [clojure.set :as set]
            [sicmutils.analyze :as a]
            [sicmutils.euclid :as euclid]
            [sicmutils.expression :as x]
            [sicmutils.generic :as g]
            [sicmutils.numsymb :as sym]
            [sicmutils.polynomial :as p]
            [sicmutils.polynomial-gcd :as poly]
            [sicmutils.ratio :as r]
            [sicmutils.util :as u]
            [sicmutils.value :as v]))

(declare operator-table operators-known)

(deftype RationalFunction [arity u v]
  v/Value
  (nullity? [_] (v/nullity? u))
  (unity? [_] (and (v/unity? u) (v/unity? v)))
  (numerical? [_] false)
  (kind [_] ::rational-function)

  #?@(:clj
      [Object
       (toString [p] (str u " : " v))
       (equals [_ b]
               (and (instance? RationalFunction b)
                    (and (= arity (.-arity b))
                         (= u (.-u b))
                         (= v (.-v b)))))]

      :cljs
      [Object
       (toString [p] (str u " : " v))

       IEquiv
       (-equiv [_ b]
               (and (instance? RationalFunction b)
                    (and (= arity (.-arity b))
                         (= u (.-u b))
                         (= v (.-v b)))))

       IPrintWithWriter
       (-pr-writer
        [x writer _]
        (write-all writer
                   "#object[sicmutils.structure.RationalFunction \""
                   (.toString x)
                   "\"]"))]))

(defn rational-function? [r]
  (instance? RationalFunction r))

(defn make
  "Make the fraction of the two polynomials p and q, after dividing
  out their greatest common divisor."
  [u v]
  {:pre [(p/polynomial? u)
         (p/polynomial? v)
         (= (.-arity u) (.-arity v))]}
  (when (v/nullity? v)
    (u/arithmetic-ex "Can't form rational function with zero denominator"))
  ;; annoying: we are using native operations here for the base coefficients
  ;; of the polynomial. Can we do better? That would involve exposing gcd as
  ;; a generic operation (along with lcm), and binding the euclid implmentation
  ;; in for language supported integral types. Perhaps also generalizing ratio?
  ;; and denominator. TODO.
  ;;
  ;; (note from sritchie: I don't think this is true anymore, as of 8.26.20.
  ;; Should we remove the comment, or is there some more work to do to make this
  ;; more efficient?)
  (let [arity (.-arity u)
        cv (p/coefficients v)
        lcv (last cv)
        cs (into (into #{} cv) (p/coefficients u))
        integerizing-factor (g/*
                             (if (< lcv 0) -1 1)
                             (reduce g/lcm 1 (map r/denominator (filter r/ratio? cs))))
        u' (if (not (v/unity? integerizing-factor)) (p/map-coefficients #(g/* integerizing-factor %) u) u)
        v' (if (not (v/unity? integerizing-factor)) (p/map-coefficients #(g/* integerizing-factor %) v) v)
        g (poly/gcd u' v')
        u'' (p/evenly-divide u' g)
        v'' (p/evenly-divide v' g)]
    (if (v/unity? v'') u''
        (do (when-not (and (p/polynomial? u'')
                           (p/polynomial? v''))
              (u/illegal (str "bad RF" u v u' v' u'' v'')))
            (->RationalFunction arity  u'' v'')))))

(defn ^:private make-reduced
  [arity u v]
  (if (v/unity? v)
    u
    (->RationalFunction arity u v)))

;;
;; Rational arithmetic is from Knuth vol 2 section 4.5.1
;;

(defn add
  "Add the rational functions r and s."
  [r s]
  {:pre [(rational-function? r)
         (rational-function? s)
         (= (.-arity r) (.-arity s))]}
  (let [a (.-arity r)
        u (.-u r)
        u' (.-v r)
        v (.-u s)
        v' (.-v s)
        d1 (poly/gcd u' v')]
    (if (v/unity? d1)
      (make-reduced  a (p/add (p/mul u v') (p/mul u' v)) (p/mul u' v'))
      (let [t (p/add (p/mul u (p/evenly-divide v' d1))
                     (p/mul v (p/evenly-divide u' d1)))
            d2 (poly/gcd t d1)]
        (make-reduced a
                      (p/evenly-divide t d2)
                      (p/mul (p/evenly-divide u' d1)
                             (p/evenly-divide v' d2)))))))

(defn addp
  "Add a rational function to a polynomial."
  [r p]
  (if (v/nullity? p)
    r
    (let [v (.-v r)]
      (make (p/add (.-u r) (p/mul v p)) v))))

(defn subp
  [r p]
  {:pre [(rational-function? r)
         (p/polynomial? p)]}
  (if (v/nullity? p)
    r
    (let [v (.-v r)]
      (make (p/sub (.-u r) (p/mul v p)) v))))

(defn negate
  [r]
  {:pre [(rational-function? r)]}
  (->RationalFunction (.-arity r) (p/negate (.-u r)) (.-v r)))

(defn square [r]
  {:pre [(rational-function? r)]}
  (let [u (.-u r)
        v (.-v r)]
    (->RationalFunction (.-arity r) (p/mul u u) (p/mul v v))))

(defn cube [r]
  {:pre [(rational-function? r)]}
  (let [u (.-u r)
        v (.-v r)]
    (->RationalFunction (.-arity r) (p/mul u (p/mul u u)) (p/mul v (p/mul v v)))))

(defn sub [r s]
  (add r (negate s)))

(defn mul
  [r s]
  {:pre [(rational-function? r)
         (rational-function? s)
         (= (.-arity r) (.-arity s))]}
  (let [a (.-arity r)
        u (.-u r)
        u' (.-v r)
        v (.-u s)
        v' (.-v s)]
    (cond (v/nullity? r) r
          (v/nullity? s) s
          (v/unity? r) s
          (v/unity? s) r
          :else (let [d1 (poly/gcd u v')
                      d2 (poly/gcd u' v)
                      u'' (p/mul (p/evenly-divide u d1) (p/evenly-divide v d2))
                      v'' (p/mul (p/evenly-divide u' d2) (p/evenly-divide v' d1))]
                  (make-reduced a u'' v'')))))

(defn invert
  [r]
  ;; use make so that the - sign will get flipped if needed
  (make (.-v r) (.-u r)))

(defn div
  [r s]
  (g/mul r (invert s)))

(defn expt
  [r n]
  {:pre [(rational-function? r)
         (v/integral? n)]}
  (let [u (.-u r)
        v (.-v r)
        [top bottom e] (if (g/negative? n)
                         [v u (g/negate n)]
                         [u v n])]
    (->RationalFunction (.-arity r) (p/expt top e) (p/expt bottom e))))

(def ^:private operator-table
  {'+ #(reduce g/add %&)
   '- (fn [arg & args]
        (if (some? args) (g/sub arg (reduce g/add args)) (g/negate arg)))
   '* #(reduce g/mul %&)
   '/ (fn [arg & args]
        (if (some? args) (g/div arg (reduce g/mul args)) (g/invert arg)))
   'negate negate
   'invert invert
   'expt g/expt
   'square g/square
   'cube cube
   'gcd g/gcd
   })

(def operators-known (set (keys operator-table)))           ;; XXX

(deftype RationalFunctionAnalyzer [polynomial-analyzer]
  a/ICanonicalize
  (expression-> [this expr cont] (a/expression-> this expr cont compare))
  (expression-> [this expr cont v-compare]
    ;; Convert an expression into Rational Function canonical form. The
    ;; expression should be an unwrapped expression, i.e., not an instance
    ;; of the Expression type, nor should subexpressions contain type
    ;; information. This kind of simplification proceeds purely
    ;; symbolically over the known Rational Function operations;;  other
    ;; operations outside the arithmetic available R(x...) should be
    ;; factored out by an expression analyzer before we get here. The
    ;; result is a RationalFunction object representing the structure of
    ;; the input over the unknowns."
    (let [expression-vars (sort v-compare (set/difference (x/variables-in expr) operators-known))
          arity (count expression-vars)]
      (let [variables (zipmap expression-vars (a/new-variables this arity))]
        (-> expr (x/walk-expression variables operator-table) (cont expression-vars)))))
  (->expression [_ r vars]
    ;; This is the output stage of Rational Function canonical form simplification.
    ;; The input is a RationalFunction, and the output is an expression
    ;; representing the evaluation of that function over the
    ;; indeterminates extracted from the expression at the start of this
    ;; process."
    (cond (rational-function? r)
          (sym/div (a/->expression polynomial-analyzer (.-u r) vars)
                   (a/->expression polynomial-analyzer (.-v r) vars))

          (p/polynomial? r)
          (a/->expression polynomial-analyzer r vars)

          :else r))
  (known-operation? [_ o] (operators-known o))
  (new-variables [_ n] (a/new-variables polynomial-analyzer n)))


(defmethod g/add [::rational-function ::rational-function] [a b] (add a b))

(defmethod g/add [::rational-function ::p/polynomial] [r p] (addp r p))
(defmethod g/add [::p/polynomial ::rational-function] [p r] (addp r p))

(defmethod g/add [::rational-function ::v/number] [a b]
  (addp a (p/make-constant (.-arity a) b)))

(defmethod g/add [::v/number ::rational-function] [b a]
  (addp a (p/make-constant (.-arity a) b)))

(defmethod g/sub [::rational-function ::rational-function] [a b] (sub a b))
(defmethod g/sub [::rational-function ::p/polynomial] [r p] (subp r p))

(defmethod g/sub [::rational-function ::v/integral] [r c]
  (let [u (.-u r)
        v (.-v r)]
    (make (p/sub (g/mul c v) u) v)))

(defmethod g/sub [::rational-function ::p/polynomial] [r p]
  (addp r (g/negate p)))

(defmethod g/sub [::p/polynomial ::rational-function] [p r]
  (addp (g/negate r) p))

(defmethod g/mul [::rational-function ::rational-function] [a b] (mul a b))
(defmethod g/mul [::rational-function ::p/polynomial] [r p]
  "Multiply the rational function r = u/v by the polynomial p"
  (let [u (.-u r)
        v (.-v r)
        a (.-arity r)]
    (cond (v/nullity? p) 0
          (v/unity? p) r
          :else (let [d (poly/gcd v p)]
                  (if (v/unity? d)
                    (make-reduced a (p/mul u p) v)
                    (make-reduced a (p/mul u (p/evenly-divide p d)) (p/evenly-divide v d)))))))

(defmethod g/mul [::p/polynomial ::rational-function] [p r]
  "Multiply the polynomial p by the rational function r = u/v"
  (let [u (.-u r)
        v (.-v r)
        a (.-arity r)]
    (cond (v/nullity? p) 0
          (v/unity? p) r
          :else (let [d (poly/gcd p v) ]
                  (if (v/unity? d)
                    (->RationalFunction a (p/mul p u) v)
                    (->RationalFunction a (p/mul (p/evenly-divide p d) u) (p/evenly-divide v d)))))))

(defmethod g/mul [::v/number ::rational-function] [c r]
  (make (g/mul c (.-u r)) (.-v r)))

(defmethod g/mul [::rational-function ::v/number] [r c]
  (make (g/mul (.-u r) c) (.-v r)))

;; Ratio support for Clojure.
(defmethod g/mul [::rational-function r/ratiotype] [r a]
  (make (g/mul (.-u r) (r/numerator a)) (g/mul (.-v r) (r/denominator a))))

(defmethod g/mul [r/ratiotype ::rational-function] [a r]
  (make (g/mul (r/numerator a) (.-u r)) (g/mul (r/denominator a) (.-v r))))

(defmethod g/div [::rational-function ::rational-function] [a b] (div a b))

(defmethod g/div [::rational-function ::p/polynomial] [r p]
  (make (.-u r) (p/mul (.-v r) p)))

(defmethod g/div [::p/polynomial ::rational-function] [p r]
  (make (p/mul p (.-v r)) (.-u r)))

(defmethod g/div [::p/polynomial ::p/polynomial] [p q]
  (let [g (poly/gcd p q)]
    (make (p/evenly-divide p g) (p/evenly-divide q g))))

(defmethod g/div [::rational-function ::v/integral] [r c]
  (make (.-u r) (g/mul c (.-v r))))

(defmethod g/div [::v/integral ::rational-function] [c r]
  (g/divide (p/make-constant (.-arity r) c) r))

(defmethod g/div [::v/integral ::p/polynomial] [c p]
  (make (p/make-constant (.-arity p) c) p))

(defmethod g/expt [::rational-function ::v/integral] [b x] (expt b x))

(defmethod g/negate [::rational-function] [a] (negate a))

(defmethod g/gcd [::p/polynomial ::p/polynomial] [p q]
  (poly/gcd p q))

(defmethod g/gcd [::p/polynomial ::rational-function] [p u]
  (poly/gcd p (.-u u)))

(defmethod g/gcd [::rational-function ::p/polynomial] [u p]
  (poly/gcd (.-u u) p))

(defmethod g/gcd [::rational-function ::rational-function] [u v]
  (make (poly/gcd (.-u u) (.-u v)) (poly/gcd (.-v u) (.-v v))))

(defmethod g/gcd [::p/polynomial ::v/integral] [p a]
  (poly/primitive-gcd (cons a (p/coefficients p))))

(defmethod g/gcd [::v/integral ::p/polynomial] [a p]
  (poly/primitive-gcd (cons a (p/coefficients p))))

(defmethod g/gcd [::p/polynomial r/ratiotype] [p a]
  (poly/primitive-gcd (cons a (p/coefficients p))))

(defmethod g/gcd [r/ratiotype ::p/polynomial] [a p]
  (poly/primitive-gcd (cons a (p/coefficients p))))
