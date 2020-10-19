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

(ns sicmutils.numerical.quadrature.trapezoid-test
  (:require [clojure.test :refer [is deftest testing]]
            [same :refer [ish?]]
            [sicmutils.numerical.interpolate.richardson :as ir]
            [sicmutils.numerical.quadrature.riemann :as qr]
            [sicmutils.numerical.quadrature.trapezoid :as qt]
            [sicmutils.numsymb]
            [sicmutils.util :as u]
            [sicmutils.value :as v]
            [sicmutils.util.stream :as us]))

;; The tests on Pi estimation come from Sussman's ["Abstraction in Numerical
;; Methods"](https://dspace.mit.edu/bitstream/handle/1721.1/6060/AIM-997.pdf?sequence=2).

(defn- pi-estimator-sequence [n0]
  (map @#'qt/pi-estimator
       (us/powers 2 n0)))

(deftest trapezoid-tests
  (testing "triangles work!"
    (= (* 0.5 10 10)
       ((qt/trapezoid-sum identity 0.0 10.0) 10)))

  (testing "the trapezoid method is identical to the average of the left and
  right riemann sums"
    (let [points  (take 5 (iterate inc 1))
          average (fn [l r]
                    (/ (+ l r) 2))
          f       (fn [x] (/ 4 (+ 1 (* x x))))
          [a b]   [0 1]
          left-estimates  (qr/left-sequence f a b {:n points})
          right-estimates (qr/right-sequence f a b {:n points})]
      (ish? (qt/trapezoid-sequence f a b {:n points})
            (map average
                 left-estimates
                 right-estimates))))

  (testing "Convergence to Pi, from Sussman's paper"
    (is
     (ish? [3.1399259889071587
            3.1415926529697855
            3.1415926536207928
            3.141592653589793
            3.141592653589794
            3.141592653589793
            3.141592653589794
            3.1415926535897927
            3.1415926535897927
            3.1415926535897927]
           (-> (take 10 (pi-estimator-sequence 10))
               (ir/richardson-sequence 2 2 2)))
     "The sequence converges fairly quickly, even without acceleration.")

    (testing "explicit Pi convergence tests"
      (let [f (fn [x] (/ 4 (+ 1 (* x x))))]
        (is (ish? {:converged? true
                   :terms-checked 13
                   :result 3.141592643655686}
                  (qt/integral f 0 1)))

        (is (ish? {:converged? true
                   :terms-checked 6
                   :result 3.141592653638244}
                  (qt/integral f 0 1 {:accelerate? true}))
            "Acceleration speeds up convergence to the default tolerance.")

        (is (ish?
             {:converged? true
              :terms-checked 9
              :result Math/PI}
             (qt/integral f 0 1 {:accelerate? true
                                 :tolerance v/machine-epsilon}))
            "With acceleration we hit machine epsilon in 9 iterations.")

        (testing "incremental trapezoid method is more efficient, even with
  arbitrary sequences"
          (let [[counter1 f1] (u/counted f)
                [counter2 f2] (u/counted f)
                n-seq (take 12 (interleave
                                (iterate (fn [x] (* 2 x)) 2)
                                (iterate (fn [x] (* 2 x)) 3)))]
            (is (ish?
                 (qt/trapezoid-sequence f1 0 1 {:n n-seq})
                 (map (qt/trapezoid-sum f2 0 1) n-seq))
                "The incremental and non-incremental versions produce ~identical
       results.")

            (is (= [162 327] [@counter1 @counter2])
                "The incremental method requires many fewer evaluations.")))))))
