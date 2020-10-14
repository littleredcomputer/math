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

(ns sicmutils.numerical.quadrature.milne-test
  (:refer-clojure :exclude [+ - * /])
  (:require [clojure.test :refer [is deftest testing use-fixtures]]
            [same :refer [ish?]]
            [sicmutils.numerical.quadrature.midpoint :as mid]
            [sicmutils.numerical.quadrature.milne :as qm]
            [sicmutils.function :as f #?@(:cljs [:include-macros true])]
            [sicmutils.generic :as g :refer [+ - * /]]
            [sicmutils.numsymb]
            [sicmutils.simplify :as s :refer [hermetic-simplify-fixture]]
            [sicmutils.util :as u]))

(use-fixtures :once hermetic-simplify-fixture)

(defn milne-step
  "Implements a single step of Milne's method, as laid out in the [Wikipedia
  entry on open Newton Cotes
  formulas](https://en.wikipedia.org/wiki/Newton%E2%80%93Cotes_formulas#Open_Newton%E2%80%93Cotes_formulas)."
  [f a b]
  (let [h     (/ (- b a) 4)
        mid   (/ (+ a b) 2)
        l-mid (/ (+ a mid) 2)
        r-mid (/ (+ mid b) 2)]
    (* (/ (* 4 h) 3)
       (+ (* 2 (f l-mid))
          (- (f mid))
          (* 2 (f r-mid))))))

(deftest milne-tests
  (testing "Milne's Method is equivalent to one step of Richardson extrapolation
  on the Midpoint method, when the midpoint increases by 2x slices each time."
    (f/with-literal-functions [f]
      (let [a 'a
            b 'b
            mid   (/ (+ a b) 2)
            m1 (mid/single-midpoint f a b)
            m2 (+ (mid/single-midpoint f a mid)
                  (mid/single-midpoint f mid b))

            ;; Richardson extrapolation step with t=2, p=2,4,6... since the
            ;; error series of the Midpoint rule = the even naturals.
            richardson-step (fn [p a b]
                              (let [t**p (g/expt 2 p)]
                                (/ (- (* t**p b) a)
                                   (- t**p 1))))]
        (is (zero?
             (g/simplify
              (- (richardson-step 2 m1 m2)
                 (milne-step f a b))))
            "A Milne step is equivalent to a single Richardson step with p=2,
            applied to successive terms of the midpoint method."))))

  (testing "Milne's rule converges, and the interface works properly. (Milne
  never evaluates the endpoints!)"
    (let [pi-test (fn [x]
                    (condp = x
                      0 (u/illegal "Zero!!")
                      1 (u/illegal "One!")
                      (/ 4 (+ 1 (* x x)))))]
      (is (ish? {:converged? true
                 :terms-checked 5
                 :result 3.141592653625595}
                (qm/integral pi-test 0 1)))

      (is (ish? {:converged? false
                 :terms-checked 3
                 :result 3.141592799990937}
                (qm/integral pi-test 0 1 {:maxterms 3}))
          "options get passed through."))))
