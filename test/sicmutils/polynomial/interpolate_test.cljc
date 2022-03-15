#_
"Copyright © 2020 Sam Ritchie.
This work is based on the Scmutils system of MIT/GNU Scheme:
Copyright © 2002 Massachusetts Institute of Technology

This is free software;  you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 3 of the License, or (at
your option) any later version.

This software is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with this code; if not, see <http://www.gnu.org/licenses/>."

(ns sicmutils.polynomial.interpolate-test
  (:require [clojure.test :refer [is deftest testing use-fixtures]]
            [same :refer [ish?]
             #?@(:cljs [:include-macros true])]
            [sicmutils.generic :as g]
            [sicmutils.numsymb]
            [sicmutils.polynomial.gcd :as pg]
            [sicmutils.polynomial.interpolate :as pi]
            [sicmutils.simplify :as s :refer [hermetic-simplify-fixture]]
            [sicmutils.value :as v]))

(use-fixtures :each hermetic-simplify-fixture)

(deftest symbolic-tests
  (letfn [(lagrange-incremental [points x]
            (let [n (count points)]
              (map (fn [i]
                     (pi/lagrange (take i points) x))
                   (range 1 (inc n)))))
          (diff [l r]
            (g/simplify (g/- l r)))]

    (testing "Neville and Lagrange interpolation are equivalent"
      (let [points [['x_1 'y_1] ['x_2 'y_2]]]
        (is (v/zero?
             (diff (pi/lagrange points 'x)
                   (pi/neville-recursive points 'x))))))

    (testing "points ordering doesn't matter for the final value. (Should test
    all permutations...)"
      (is (v/zero?
           (diff
            (pi/lagrange [['x_1 'y_1] ['x_2 'y_2] ['x_3 'y_3]] 'x)
            (pi/lagrange [['x_2 'y_2] ['x_1 'y_1] ['x_3 'y_3]] 'x))))

      (is (v/zero?
           (diff
            (pi/lagrange [['x_2 'y_2] ['x_1 'y_1] ['x_3 'y_3]] 'x)
            (pi/lagrange [['x_3 'y_3] ['x_2 'y_2] ['x_1 'y_1]] 'x)))))

    ;; This was giving cljs some trouble on CI, so here we are.
    (binding [pg/*poly-gcd-time-limit* #?(:clj  [2 :seconds]
                                          :cljs [6 :seconds])]
      (testing "symbolic incremental methods should be identical to the full
  lagrange method at each point prefix."
        (let [points [['x_1 'y_1] ['x_2 'y_2] ['x_3 'y_3] ['x_4 'y_4]]
              diffs  (map diff
                          (lagrange-incremental points 'x)
                          (pi/neville-incremental points 'x))]
          (is (v/= [0 0 0 0] diffs)))))))

(deftest performance-tests
  (let [points [[0 1] [2 1] [5 2] [8 10]]
        expected [1 1.0 0.9359999999999999 1.0829333333333333]]
    (testing "each function returns a sequence of successive approximations. The
  approximation around 1.2 gets better the more points we add in."

      (is (ish? (last expected) (pi/lagrange points 1.2))
          "Lagrange only returns the final value.")

      (is (ish? (last expected) (pi/neville-recursive points 1.2))
          "Non-incremental neville.")

      (is (ish? expected (pi/neville-incremental points 1.2))
          "This is the initial, unabstracted version.")

      (is (ish? expected (pi/neville points 1.2))
          "incremental calculation via full Neville's algorithm")

      (is (ish? expected (pi/modified-neville points 1.2))
          "incremental calculation via modified Neville"))

    (testing "folding points should match the best estimate received through
    column-wise processing."
      (is (ish? (last expected) ((pi/neville-sum 1.2) points)))
      (is (ish? (last expected) ((pi/modified-neville-sum 1.2) points))))

    (testing "the diagonal of the tableau processed with a fold should match the
    first row of column-wise processing. (Scan produces the diagonal by
    returning a sequence of the final values in each row.)"
      (is (ish? expected ((pi/neville-scan 1.2) points)))
      (is (ish? expected ((pi/modified-neville-scan 1.2) points))))

    (testing "the tableau processed with a fold should match the first row of
    column-wise processing."
      (is (ish? ((pi/neville-sum 1.2) points)
                (last ((pi/neville-scan 1.2) points))))

      (is (ish?  ((pi/modified-neville-sum 1.2) points)
                 (last ((pi/modified-neville-scan 1.2) points)))))))
