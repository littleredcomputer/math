;;
;; Copyright © 2017 Colin Smith.
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

(ns sicmutils.simplify
  (:require [clojure.pprint :as pp]
            [clojure.set :as set]
            [pattern.rule :as rule]
            [sicmutils.expression.analyze :as a]
            [sicmutils.expression :as x]
            [sicmutils.generic :as g]
            [sicmutils.polynomial :as poly]
            [sicmutils.polynomial.factor :as factor]
            [sicmutils.rational-function :as rf]
            [sicmutils.simplify.rules :as rules]
            [sicmutils.value :as v]
            [taoensso.timbre :as log])
  #?(:clj
     (:import [java.util.concurrent TimeoutException])))

(defn ^:private unless-timeout
  "Returns a function that invokes f, but catches TimeoutException;
  if that exception is caught, then x is returned in lieu of (f x)."
  [f]
  (fn [x]
    (try (f x)
         (catch #?(:clj TimeoutException :cljs js/Error) _
           (log/warn (str "simplifier timed out: must have been a complicated expression"))
           x))))

(defn ^:private poly-analyzer
  "An analyzer capable of simplifying sums and products, but unable to
  cancel across the fraction bar"
  []
  (a/make-analyzer (poly/->PolynomialAnalyzer)
                   (a/monotonic-symbol-generator "-s-")))

(defn ^:private rational-function-analyzer
  "An analyzer capable of simplifying expressions built out of rational
  functions."
  []
  (a/make-analyzer (rf/->RationalFunctionAnalyzer (poly/->PolynomialAnalyzer))
                   (a/monotonic-symbol-generator "-r-")))

(def ^:dynamic *rf-analyzer*
  (memoize (unless-timeout (rational-function-analyzer))))

(def ^:dynamic *poly-analyzer*
  (memoize (poly-analyzer)))

(defn hermetic-simplify-fixture
  [f]
  (binding [*rf-analyzer* (rational-function-analyzer)
            *poly-analyzer* (poly-analyzer)]
    (f)))

(def ^:private
  simplify-and-flatten
  (comp #'*poly-analyzer*
        #'*rf-analyzer*))

(defn ^:private simplify-until-stable
  [rule-simplify canonicalize]
  (fn [expression]
    (let [new-expression (rule-simplify expression)]
      (if (= expression new-expression)
        expression
        (let [canonicalized-expression (canonicalize new-expression)]
          (cond (= canonicalized-expression expression) expression
                (v/zero?
                 (*poly-analyzer*
                  `(~'- ~expression ~canonicalized-expression))) canonicalized-expression
                :else (recur canonicalized-expression)))))))

(defn ^:private simplify-and-canonicalize
  [rule-simplify canonicalize]
  (fn [expression]
    (let [new-expression (rule-simplify expression)]
      (if (v/= expression new-expression)
        expression
        (canonicalize new-expression)))))

(def ^:private sin-sq->cos-sq-simplifier
  (-> rules/sin-sq->cos-sq
      (simplify-and-canonicalize simplify-and-flatten)))

(def ^:private sincos-simplifier
  (-> (rules/sincos-flush-ones *rf-analyzer*)
      (simplify-and-canonicalize simplify-and-flatten)))

(def ^:private square-root-simplifier
  (-> (rules/simplify-square-roots *rf-analyzer*)
      (simplify-and-canonicalize simplify-and-flatten)))

;; looks like we might have the modules inverted: rulesets will need some functions from the
;; simplification library, so this one has to go here. Not ideal the way we have split things
;; up, but at least things are beginning to simplify adequately.

(def ^:private simplifies-to-zero?
  #(-> % *poly-analyzer* v/zero?))

(def ^:private simplifies-to-one?
  #(-> % *rf-analyzer* v/one?))

;; TODO move to rules!!
(def trig-cleanup
  "This finds things like a - a cos^2 x and replaces them with a sin^2 x"
  (let [at-least-two? #(and (number? %) (>= % 2))]
    (simplify-until-stable
     (rule/rule-simplifier
      (rule/ruleset
       ;;  ... + a + ... + cos^n x + ...   if a + cos^(n-2) x = 0: a sin^2 x
       (+ ??a1 ?a ??a2 (expt (cos ?x) (:? ?n at-least-two?)) ??a3)
       #(simplifies-to-zero?
         `(~'+ (~'expt (~'cos ~(% '?x)) ~(- (% '?n) 2)) ~(% '?a)))
       (+ ??a1 ??a2 ??a3 (* ?a (expt (sin ?x) 2)))

       (+ ??a1 (expt (cos ?x) (:? ?n at-least-two?)) ??a2 ?a ??a3)
       #(simplifies-to-zero?
         `(~'+ (~'expt (~'cos ~(% '?x)) ~(- (% '?n) 2)) ~(% '?a)))
       (+ ??a1 ??a2 ??a3 (* ?a (expt (sin ?x) 2)))

       (+ ??a1 ?a ??a2 (* ??b1 (expt (cos ?x) (:? ?n at-least-two?)) ??b2) ??a3)
       #(simplifies-to-zero?
         `(~'+ (~'* ~@(% '??b1) ~@(% '??b2) (~'expt (~'cos ~(% '?x)) ~(- (% '?n) 2))) ~(% '?a)))
       (+ ??a1 ??a2 ??a3 (* ?a (expt (sin ?x) 2)))

       (+ ??a1 (* ??b1 (expt (cos ?x) (:? ?n at-least-two?)) ??b2) ??a2 ?a ??a3)
       #(simplifies-to-zero?
         `(~'+ (~'* ~@(% '??b1) ~@(% '??b2) (~'expt (~'cos ~(% '?x)) ~(- (% '?n) 2))) ~(% '?a)))
       (+ ??a1 ??a2 ??a3 (* ?a (expt (sin ?x) 2)))

       ;; TODO - the original pushes rcf:simplify inside this block.
       (atan ?y ?x)
       (fn [m]
         (let [xy-gcd (*rf-analyzer* `(~'gcd ~(m '?x) ~(m '?y)))]
           (when-not (v/one? xy-gcd)
             {'?gcd xy-gcd})))
       (atan (/ ?y ?gcd) (/ ?x ?gcd))))
     simplify-and-flatten)))

;; TODO add guards.

(def clear-square-roots-of-perfect-squares
  (-> (comp (rules/universal-reductions *rf-analyzer*)
            factor/root-out-squares)
      (simplify-and-canonicalize simplify-and-flatten)))

(defn ^:private simplify-expression-1
  "this is a chain of rule-simplifiers (i.e., each entry in the chain
  passes the expression through after the simplification of the step
  stabilizes.)"
  [x]
  (let [sqrt-contract (rules/sqrt-contract *rf-analyzer*)
        sqrt-expand (rules/sqrt-expand *rf-analyzer*)]
    (-> x
        (rules/canonicalize-partials)
        (rules/trig->sincos)
        (simplify-and-flatten)
        (rules/complex-trig)
        (sincos-simplifier)
        (sin-sq->cos-sq-simplifier)
        (trig-cleanup)
        (rules/sincos->trig)
        (sqrt-expand)
        (simplify-and-flatten)
        (sqrt-contract)
        (square-root-simplifier)
        (clear-square-roots-of-perfect-squares)
        (simplify-and-flatten))))

(def simplify-expression
  (simplify-until-stable simplify-expression-1 simplify-and-flatten))

(defn only-if
  "returns a function that will apply f to its argument x if `bool`, else returns x."
  [bool f]
  (if bool
    f
    identity))

(let [universal-reductions (rules/universal-reductions *rf-analyzer*)
      sqrt-contract (rules/sqrt-contract *rf-analyzer*)
      sqrt-expand   (rules/sqrt-expand *rf-analyzer*)
      log-contract (rules/log-contract *rf-analyzer*)
      sincos-random (rules/sincos-random *rf-analyzer*)
      sincos-flush-ones (rules/sincos-flush-ones *rf-analyzer*)]
  (defn new-simplify
    [expr]
    (let [syms (x/variables-in expr)
          sqrt? (rules/occurs-in? #{'sqrt} syms)
          full-sqrt? (and rules/*sqrt-factor-simplify?*
                          (rules/occurs-in? #{'sqrt} syms))
          logexp? (rules/occurs-in? #{'log 'exp} syms)
          sincos? (rules/occurs-in? #{'sin 'cos} syms)
          partials? (rules/occurs-in? #{'partial} syms)
          simplified-expr
          ((comp (only-if rules/*divide-numbers-through-simplify?*
                          rules/divide-numbers-through)
                 (only-if
                  sqrt?
                  clear-square-roots-of-perfect-squares)

                 (only-if
                  full-sqrt?
                  (comp
                   (simplify-until-stable (comp universal-reductions
                                                sqrt-expand)
                                          simplify-and-flatten)
                   clear-square-roots-of-perfect-squares
                   (simplify-until-stable sqrt-contract
                                          simplify-and-flatten)))
                 (only-if
                  sincos?
                  (comp (simplify-and-canonicalize
                         (comp universal-reductions rules/sincos->trig)
                         simplify-and-flatten)
                        (simplify-and-canonicalize rules/angular-parity simplify-and-flatten)
                        (simplify-until-stable sincos-random simplify-and-flatten)
                        (simplify-and-canonicalize rules/sin-sq->cos-sq simplify-and-flatten)
                        (simplify-and-canonicalize sincos-flush-ones simplify-and-flatten)
                        (if rules/*trig-product-to-sum-simplify?*
                          (simplify-and-canonicalize rules/trig-product-to-sum simplify-and-flatten)
                          identity)
                        (simplify-and-canonicalize universal-reductions simplify-and-flatten)
                        (simplify-until-stable sincos-random simplify-and-flatten)
                        (simplify-and-canonicalize rules/sin-sq->cos-sq simplify-and-flatten)
                        (simplify-and-canonicalize sincos-flush-ones simplify-and-flatten)))

                 (only-if
                  logexp?
                  (comp (simplify-and-canonicalize universal-reductions simplify-and-flatten)
                        (simplify-until-stable (comp rules/log-expand rules/exp-expand) simplify-and-flatten)
                        (simplify-until-stable (comp log-contract rules/exp-contract) simplify-and-flatten)))


                 (-> (comp universal-reductions
                           (only-if logexp? (comp rules/log-expand rules/exp-expand))
                           (only-if sqrt? sqrt-expand))
                     (simplify-until-stable simplify-and-flatten))

                 (only-if sincos?
                          (simplify-and-canonicalize rules/angular-parity simplify-and-flatten))

                 (simplify-and-canonicalize rules/trig->sincos simplify-and-flatten)
                 (only-if partials?
                          (simplify-and-canonicalize rules/canonicalize-partials
                                                     simplify-and-flatten))
                 simplify-expression
                 simplify-and-flatten)
           expr)]
      simplified-expr)))

(defn expression->stream
  "Renders an expression through the simplifier and onto the stream."
  ([expr stream]
   (-> (v/freeze
        (g/simplify expr))
       (pp/write :stream stream)))
  ([expr stream options]
   (let [opt-seq (->> (assoc options :stream stream)
                      (apply concat))
         simple  (v/freeze
                  (g/simplify expr))]
     (apply pp/write simple opt-seq))))

(defn expression->string
  "Renders an expression through the simplifier and into a string,
  which is returned."
  [expr]
  (with-out-str
    (expression->stream expr true)))

(defn print-expression [expr]
  (pp/pprint
   (v/freeze
    (g/simplify expr))))

(def pe print-expression)
