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

(ns sicmutils.operator-test
  (:refer-clojure :exclude [+ - * /  partial])
  (:require [clojure.test :refer [is deftest testing use-fixtures]]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]
             #?@(:cljs [:include-macros true])]
            [same :refer [ish?]]
            [sicmutils.abstract.function :as f]
            [sicmutils.function :refer [I arity]]
            [sicmutils.calculus.derivative :refer [D partial]]
            [sicmutils.generators :as sg]
            [sicmutils.generic :as g :refer [+ - * /]]
            [sicmutils.operator :as o]
            [sicmutils.simplify :refer [hermetic-simplify-fixture]]
            [sicmutils.structure :refer [up down]]
            [sicmutils.value :as v]))

(use-fixtures :each hermetic-simplify-fixture)

(def f (f/literal-function 'f))
(def g (f/literal-function 'g))
(def ff (f/literal-function 'ff [0 0] 0))
(def gg (f/literal-function 'gg [0 0] 0))

(deftest value-protocol-tests
  (let [x2 (-> (fn [f] (fn [x] (* 2 (f x))))
               (o/make-operator 'double))]
    (let [f ((v/zero-like x2) g/sin)]
      (checking " zero-like" 100 [n sg/real]
                (is (v/zero? (f n)))))

    (let [f ((v/one-like x2) g/sin)]
      (checking " one-like" 100 [n sg/real]
                (is (= (g/sin n) (f n))
                    "operator one-like is identity")))

    (let [f ((v/identity-like x2) g/sin)]
      (checking " identity-like" 100 [n sg/real]
                (is (= (g/sin n) (f n)))))

    (testing "one? zero? identity? always return false (for now!)"
      (is (not (v/zero? (v/zero-like x2))))
      (is (not (v/one? (v/one-like x2))))
      (is (not (v/identity? (v/identity-like x2)))))

    (testing "v/numerical?"
      (is (not (v/numerical? x2))))

    (testing "v/freeze"
      (is (= 'double (v/freeze x2))))

    (testing "v/kind"
      (is (= ::o/operator (v/kind x2))))

    (testing "f/arity"
      (is (= [:exactly 2]
             (arity g/mul)
             (arity (o/make-operator g/mul 'mul)))
          "Operator arity reflects the arity of the wrapped function"))

    (testing "metadata support"
      (is (= (meta o/identity)
             (meta (with-meta o/identity nil)))
          "with-meta with nil does nothing")

      (checking "with-meta works" 100
                [m (gen/map gen/keyword gen/any)]
                (is (= m (meta
                          (with-meta o/identity m))))))))

(deftest custom-getter-tests
  (checking "I == identity" 100 [x gen/any-equatable]
            (is (= x (o/identity x)))
            (is (= (I x) (o/identity x))))

  (testing "get, get-in return operators"
    (is (o/operator? (get o/identity 'x)))
    (is (o/operator? (get-in o/identity ['x 'y]))))

  (testing "get names"
    (is (= '(compose (component x) identity)
           (v/freeze (get o/identity 'x)))
        "The name of the operator returned by `get` reflects the (sort of
        awkward) composition that `get` induces."))

  (testing "not-found arity of get throws"
    (is (thrown? #?(:clj IllegalArgumentException :cljs js/Error)
                 (get o/identity :k 0))
        "Because Operator implements the ILookup protocol it can't support the
        not-found arity; internally, `get-in` relies on eager results to check
        if it should return the not-found value, and that's not possible with
        this functional implementation."))

  (checking "get for operators" 100
            [m (gen/map gen/keyword gen/any-equatable)
             k  gen/keyword
             v  gen/any-equatable]
            (is (= (get m k)
                   ((get o/identity k) m))
                "get works on operators")

            (let [op (o/make-operator #(dissoc % k) 'dissoc)]
              (is (nil? ((get op k) m))
                  "always return nil if the key is explicitly missing."))

            (let [op (o/make-operator #(assoc % k v) 'assoc)]
              (is (= v ((get op k) m))
                  "always return v if it's present")))

  (let [inner-gen (gen/map gen/keyword gen/any-equatable
                           {:max-elements 10})]
    (checking "get-in with 2-deep maps" 100
              [m (gen/map gen/keyword inner-gen
                          {:max-elements 4})
               k1  gen/keyword
               k2  gen/keyword
               v   gen/any-equatable]
              (is (= (get-in m [k1 k2])
                     ((get-in o/identity [k1 k2])
                      m))
                  "get-in works on operators")

              (let [op (o/make-operator #(update % k1 dissoc k2) 'dissoc)]
                (is (nil? ((get-in op [k1 k2]) m))
                    "always return nil if the key is explicitly missing from the
                inner map."))

              (let [op (o/make-operator #(assoc-in % [k1 k2] v) 'assoc)]
                (is (= v ((get-in op [k1 k2]) m))
                    "always return v if it's present")))))

(deftest operators-from-fn-tests
  (let [f (fn [x] (+ x 5))
        double (fn [f] (fn [x] (* 2 (f x))))
        double-op (o/make-operator double 'double)]
    (is (= 12 ((double f) 1)))
    (is (= 24 ((double (double f)) 1)))
    (is (= 12 ((double-op f) 1)))
    (is (= 24 ((double-op (double-op f)) 1)))
    (is (= 24 (((g/* double-op double-op) f) 1))) ;; * for operators is composition
    (is (= 144 (((g/* double double) f) 1)))      ;; * for functions is pointwise multiply
    (is (= 2 ((double-op I) 1)))
    (is (= 6 (((g/expt double-op 0) f) 1)))
    (is (= 12 (((g/expt double-op 1) f) 1)))
    (is (= 24 (((g/expt double-op 2) f) 1)))
    (is (= 18 (((g/+ double-op double-op double-op) I) 3)))
    (is (= 16 (((g/+ double-op 4 double-op) I) 3)))))

;; Test operations with Operators
(deftest Operator-tests
  (testing "that our known Operators work with basic arithmetic"
    (is (every? o/operator? [(+ D 1)
                             (+ 2 D)
                             (- D 3)
                             (- 4 D)
                             (* 5 D)
                             (* D 6)]))

    (is (every? o/operator? [(+ (partial 0) 1)
                             (+ 2 (partial 0))
                             (- (partial 0) 3)
                             (- 4 (partial 0))
                             (* 5 (partial 0))
                             (* (partial 0) 6)])))

  (testing "metadata does NOT survive operations on operators"
    (is (nil?
         (meta
          (* (with-meta D {:a "b"})
             (with-meta D {:c "d"}))))))

  (testing "that they compose with other Operators"
    (is (every? o/operator?
                [(* D D)
                 (* D (partial 0))
                 (* (partial 0) D)
                 (* (partial 0) (partial 1))])))

  (testing "that their arithmetic operations compose correctly, as per SICM -  'Our Notation'"
    (is (v/= '(+ (((expt D 2) f) x) (* -1 (f x)))
             (g/simplify
              (((* (+ D I) (- D I)) f) 'x)))))

  (testing "that Operators compose correctly with functions"
    (is (v/= '(+ (* -1 (((expt D 2) f) x) ((D g) (+ ((D f) x) (f x))))
                 (* -1 ((D f) x) ((D g) (+ ((D f) x) (f x))))
                 (((expt D 2) f) x)
                 (((expt D 3) f) x))
             (g/simplify ((D ((* (- D g) (+ D I)) f)) 'x)))))

  (testing "that basic arithmetic operations work on multivariate literal functions"
    (is (= '(down (* 2 (((partial 0) ff) x y))
                  (* 2 (((partial 1) ff) x y)))
           (v/freeze
            (g/simplify
             (((+ D D) ff) 'x 'y)))))

    (is (= (down 0 0)
           (g/simplify (((- D D) ff) 'x 'y))))

    (is (= (((* D D) ff) 'x 'y)
           (down
            (down (((partial 0) ((partial 0) ff)) 'x 'y) (((partial 0) ((partial 1) ff)) 'x 'y))
            (down (((partial 1) ((partial 0) ff)) 'x 'y) (((partial 1) ((partial 1) ff)) 'x 'y)))))

    (is (= (((* (partial 1) (partial 0)) ff) 'x 'y)
           (((partial 1) ((partial 0) ff)) 'x 'y))))

  (testing "operator derivative shape"
    (is (= [:exactly 1] (arity o/identity)))
    (is (= [:exactly 1] (arity D)))
    (is (= [:exactly 1] (arity (* D o/identity))))
    (is (= [:exactly 1] (arity (* 'e D))))
    (is (= [:exactly 1] (arity (* D 'e))))
    (is (= [:exactly 1] (arity g/sin)))
    (is (= [:exactly 1] (arity (o/identity g/sin))))
    (is (= (g/sin 'x) (g/simplify ((o/identity g/sin) 'x))))
    (is (= (g/cos 'x) (g/simplify (((* D o/identity) g/sin) 'x))))
    (is (= (g/cos 'x) (g/simplify (((* o/identity D) g/sin) 'x)))))

  (testing "exponentiation"
    (is (= '((f t)
             (* ε ((D f) t))
             (* (/ 1 2) (expt ε 2) (((expt D 2) f) t))
             (* (/ 1 6) (expt ε 3) (((expt D 3) f) t))
             (* (/ 1 24) (expt ε 4) (((expt D 4) f) t))
             (* (/ 1 120) (expt ε 5) (((expt D 5) f) t)))
           (v/freeze
            (g/simplify (take 6 (seq (((g/exp (* 'ε D)) (f/literal-function 'f)) 't)))))))

    (is (ish? '(0
                ε
                0
                (* (/ -1 6) (expt ε 3))
                0
                (* (/ 1 120) (expt ε 5))
                0
                (* (/ -1 5040) (expt ε 7))
                0
                (* (/ 1 362880) (expt ε 9))
                0
                (* (/ -1 39916800) (expt ε 11)))
              (v/freeze
               (g/simplify (take 12 (seq (((g/exp (* 'ε D)) g/sin) 0)))))))

    (is (ish? '(1
                0
                (* (/ -1 2) (expt ε 2))
                0
                (* (/ 1 24) (expt ε 4))
                0
                (* (/ -1 720) (expt ε 6))
                0
                (* (/ 1 40320) (expt ε 8))
                0
                (* (/ -1 3628800) (expt ε 10))
                0)
              (v/freeze
               (g/simplify (take 12 (seq (((g/exp (* 'ε D)) g/cos) 0)))))))

    (is (= '(1
             (* (/ 1 2) ε)
             (* (/ -1 8) (expt ε 2))
             (* (/ 1 16) (expt ε 3))
             (* (/ -5 128) (expt ε 4))
             (* (/ 7 256) (expt ε 5)))
           (v/freeze
            (g/simplify (take 6 (seq (((g/exp (* 'ε D)) #(g/sqrt (+ % 1))) 0)))))))

    (is (= '(+
             (* (/ 1 5040) (expt n 7) (expt ε 7))
             (* (/ -1 240) (expt n 6) (expt ε 7))
             (* (/ 5 144) (expt n 5) (expt ε 7))
             (* (/ -7 48) (expt n 4) (expt ε 7))
             (* (/ 29 90) (expt n 3) (expt ε 7))
             (* (/ -7 20) (expt n 2) (expt ε 7))
             (* (/ 1 7) n (expt ε 7)))
           (v/freeze
            (g/simplify (nth (seq (((g/exp (* 'ε D)) #(g/expt (+ 1 %) 'n)) 0)) 7))))))

  (testing "mixed types don't combine"
    (derive ::x ::o/operator)
    (derive ::y ::o/operator)
    (let [o (o/make-operator identity 'o {:subtype ::x})
          p (o/make-operator identity 'p {:subtype ::y})
          q (o/make-operator identity 'q {:subtype ::x :color :blue})
          r (o/make-operator identity 'r {:subtype ::x :color :green})]
      (is (thrown? #?(:clj IllegalArgumentException :cljs js/Error)
                   ((+ o p) inc)))
      (is (= {:subtype ::y} (o/context (* o p))))
      (is (= 2 (((+ o o) inc) 0)))
      (is (= 1 (((* o o) inc) 0)))
      (is (= {:subtype ::x} (o/context (+ o o))))
      (is (= {:subtype ::y} (o/context (* p p))))
      (is (thrown? #?(:clj IllegalArgumentException :cljs js/Error)
                   ((+ q r) inc)))
      (is (= {:subtype ::x :color :blue} (o/context (+ q o))))
      (is (thrown? #?(:clj IllegalArgumentException :cljs js/Error)
                   (+ q p))))))

    ;;; more testing to come as we implement multivariate literal functions that rely on operations on structures....
