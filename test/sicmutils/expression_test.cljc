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

(ns sicmutils.expression-test
  (:require [clojure.test :refer [is deftest testing]]
            [sicmutils.abstract.number :as an]
            [sicmutils.expression :as e]
            [sicmutils.generic :as g]
            [sicmutils.value :as v]))

(deftest expressions
  (testing "value protocol impl"
    (is (v/zero? (e/make-literal ::blah 0)))
    (is (v/one? (e/make-literal ::blah 1)))
    (is (not (v/zero? (e/make-literal ::blah 10))))
    (is (not (v/one? (e/make-literal ::blah 10))))
    (is (not (v/exact? (e/make-literal ::blah 10.5))))
    (is (v/exact? (e/make-literal ::blah 10)))

    (is (= '(sin 1 2 3)
           (v/freeze
            (e/literal-apply ::blah 'sin [1 2 3]))))

    (is (e/literal?
         (e/literal-apply ::blah 'sin [1 2 3])))

    (is (e/literal?
         (an/literal-number '(* 4 3))))

    (is (not (e/literal? "face")))
    (is (not (e/literal? 'x)))

    (is (not (e/abstract?
              (e/make-literal ::blah 12))))

    (is (e/abstract?
         (an/literal-number 12))))

  (testing "literal-type"
    (is (= ::e/numeric
           (e/literal-type
            (an/literal-number 12))))

    (is (= ::blah
           (e/literal-type
            (e/make-literal ::blah 12)))))

  (testing "fmap"
    (is (= (e/make-literal ::blah 9)
           (e/fmap g/square (e/make-literal ::blah 3)))))

  (testing "expression-of"
    (is (= 9 (e/expression-of
              (e/fmap g/square (e/make-literal ::blah 3))))
        "e/expression-of returns the wrapped expression from literals.")

    (is (= 'x (e/expression-of 'x))
        "symbols get round-tripped")

    (is (thrown? #?(:clj IllegalArgumentException :cljs js/Error)
                 (e/expression-of 12))
        "Any other type throws"))

  (testing "variables-in"
    (let [expr '(+ x (* 3 y) [a [b 9 c] [3 4 5 d]])]
      (is (= '#{a b c d x y * +}
             (e/variables-in expr)
             (e/variables-in (e/make-literal ::blah expr)))))
    (is (= '#{x} (e/variables-in 'x))))

  (testing "evaluate"
    (is (= 12 (e/evaluate '(+ 3 4 x) {'x 5} {'+ +})))
    (is (= 0 (e/evaluate '(+ 3 (* 4 y) x) {'x 5 'y -2} {'* * '+ +})))
    (is (thrown? #?(:clj Exception :cljs js/Error)
                 (e/evaluate '(+ 3 (* 4 y) x) {'x 5 'y -2} {'+ +}))))

  (testing "substitute"
    (is (= 'x
           (e/substitute '(* (* (* x y) y) y)
                         '(* x y) 'x))
        "Substitutions occur in postwalk order, so the lower-level replacements
        make new potential replacements available higher up.")

    (is (= '(* (* (* 1 2) 2) 2)
           (e/substitute '(* (* (* x y) y) y)
                         {'x 1 'y 2}))
        "substitute works with a dict too")

    (is (= 8 (e/evaluate
              (e/substitute '(* (* (* x y) y) y)
                            {'x 1 'y 2})
              {} {'* * '- -}))
        "of course we can evaluate the results."))

  (testing "compare"
    (testing "empty seq is only equal to itself, less than anything else"
      (is (= -1 (e/compare () 10)))
      (is (= 0 (e/compare () ()))))

    (testing "for types that don't play nice we resort to hashing."
      (is (= -1 (e/compare '(+ x y) #sicm/complex "1+2i")))
      (is (= 1 (e/compare #sicm/complex "1+2i" '(+ x y)))))

    ;; TODO add more tests as we start to explore this function.
    ))

(deftest is-literal-test
  )
