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

(ns sicmutils.ratio-test
  (:require [clojure.test :refer [is deftest testing]]
            [clojure.test.check.generators :as gen]
            #?(:cljs [cljs.reader :refer [read-string]])
            [com.gfredericks.test.chuck.clojure-test :refer [checking]
             #?@(:cljs [:include-macros true])]
            [same :refer [ish?]]
            [sicmutils.ratio :as r]
            [sicmutils.util :as u]
            [sicmutils.generic :as g]
            [sicmutils.generic-test :as gt]
            [sicmutils.generators :as sg]
            [sicmutils.laws :as l]
            [sicmutils.value :as v]
            [sicmutils.numbers :as n]))

(deftest ratio-value-implementation
  (testing "v/freeze"
    (is (= '(/ 1 2) (v/freeze #sicm/ratio 1/2)))
    (is (= 2 (v/freeze #sicm/ratio 10/5))
        "Numbers pass through")
    (is (= 2 (v/freeze #sicm/ratio "10/5"))))

  (checking "v/exact? is always true for ratios, v/kind works"
            100
            [r sg/big-ratio]
            (is (v/exact? r))
            (let [k (v/kind r)]
              (is (or (= k r/ratiotype)
                      (= k u/biginttype))
                  "The kind is either ratio, or bigint if the denominator was
                  1."))))

(deftest ratio-laws
  ;; Rational numbers form a field!
  (l/field 100 sg/big-ratio "Ratio"))

(deftest ratio-literal
  (testing "r/parse-ratio can round-trip Ratio instances in clj or cljs. "
    #?(:clj
       (is (= #sicm/ratio "10/3"
              #sicm/ratio "+10/3"
              #sicm/ratio 10/3
              (read-string {:readers {'sicm/ratio r/parse-ratio}}
                           (pr-str #sicm/ratio 10/3)))
           "Ratio parses from numbers and strings.")
       :cljs (is (= `(r/rationalize
                      (u/bigint "10")
                      (u/bigint "3"))
                    (read-string {:readers {'sicm/ratio r/parse-ratio}}
                                 (pr-str #sicm/ratio 10/3)))
                 "Ratio parses from numbers into a code form."))
    (is (= #?(:clj #sicm/ratio "1/999999999999999999999999"
              :cljs `(r/rationalize
                      (u/bigint "1")
                      (u/bigint "999999999999999999999999")))
           (read-string {:readers {'sicm/ratio r/parse-ratio}}
                        (pr-str #sicm/ratio "1/999999999999999999999999")))
        "Parsing #sicm/ratio works with big strings too.")))

(deftest rationalize-test
  (testing "r/rationalize promotes to bigint if evenly divisible"
    (is (not (r/ratio? (r/rationalize 10 2))))
    (is (r/ratio? (r/rationalize 10 3))))

  (checking "r/rationalize round-trips all integrals"
            100
            [x (gen/one-of [sg/any-integral sg/big-ratio])]
            (is (= x (r/rationalize x))))

  (checking "r/rationalize reduces inputs"
            100
            [n sg/any-integral
             d sg/bigint
             :when (and (not (v/nullity? d))
                        (not (v/unity? d)))]
            (is (= n (g/mul d (r/rationalize n d)))
                "multiplying by denominator recovers numerator")
            (let [r      (r/rationalize n d)
                  factor (g/gcd n d)]
              (when-not (r/ratio? r)
                (is (= (g/abs d)
                       (g/abs factor))
                    "If rationalize doesn't return ratio the denominator must
                    have been the gcd.")

                (is (= (g/abs n)
                       (g/abs (g/mul factor r)))
                    "Recover the original n by multiplying the return value by
                    the factor."))

              (when (r/ratio? r)
                (is (= (g/abs d)
                       (g/abs (g/mul factor (r/denominator r))))
                    "denominator scales down by gcd")
                (is (= (g/abs n)
                       (g/abs (g/mul factor (r/numerator r))))
                    "numerator scales down by gcd")))))

(deftest ratio-generics
  (testing "rational generics"
    (gt/integral-tests r/rationalize)
    (gt/integral-a->b-tests r/rationalize identity)
    (gt/floating-point-tests
     r/rationalize :eq #(= (r/rationalize %1)
                           (r/rationalize %2))))

  (testing "ratio exponent"
    (is (= (-> #sicm/ratio 1/2 (g/expt 3))
           #sicm/ratio 1/8)
        "integral exponents stay exact")

    (is (= (-> #sicm/ratio 1/2 (g/expt (u/long 3)))
           (-> #sicm/ratio 1/2 (g/expt (u/bigint 3)))
           (-> #sicm/ratio 1/2 (g/expt (u/int 3)))
           #sicm/ratio 1/8)
        "different types work")

    (is (ish? (-> #sicm/ratio 1/2 (g/expt 0.5))
              (g/invert (g/sqrt 2)))
        "A non-integral exponent forces to floating point")

    (is (ish? (-> #sicm/ratio 1/2 (g/expt #sicm/ratio 1/2))
              (g/invert (g/sqrt 2)))
        "Same with rational exponents!")

    (is (ish? (g/expt 2 #sicm/ratio 1/2)
              (g/sqrt 2))
        "a rational exponent on an integer will drop precision.")

    (is (ish? (g/expt 0.5 #sicm/ratio 1/2)
              (g/invert (g/sqrt 2)))
        "a rational exponent on a float will drop precision."))

  (testing "ratio-operations"
    (is (= #sicm/ratio 13/40
           (g/add #sicm/ratio 1/5
                  #sicm/ratio 1/8)))

    (is (= #sicm/ratio 1/8
           (g/sub #sicm/ratio 3/8
                  #sicm/ratio 1/4)))

    (is (= #sicm/ratio 5/4 (g/div 5 4)))

    (is (= 25 (g/exact-divide #sicm/ratio 10/2
                              #sicm/ratio 2/10)))
    (is (= 1 (g/exact-divide #sicm/ratio 2/10
                             #sicm/ratio 2/10)))

    (is (= #sicm/ratio 1/2 (g/div 1 2)))
    (is (= #sicm/ratio 1/4 (reduce g/div [1 2 2])))
    (is (= #sicm/ratio 1/8 (reduce g/div [1 2 2 2])))
    (is (= #sicm/ratio 1/8 (g/invert 8)))))

(deftest with-ratio-literals
  (is (= #sicm/ratio 13/40 (g/+ #sicm/ratio 1/5
                                #sicm/ratio 1/8)))
  (is (= #sicm/ratio 1/8 (g/- #sicm/ratio 3/8
                              #sicm/ratio 1/4)))
  (is (= #sicm/ratio 5/4 (g/divide 5 4)))
  (is (= #sicm/ratio 1/2 (g/divide 1 2)))
  (is (= #sicm/ratio 1/4 (g/divide 1 2 2)))
  (is (= #sicm/ratio 1/8 (g/divide 1 2 2 2))))
