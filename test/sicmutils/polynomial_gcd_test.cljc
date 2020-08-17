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

(ns sicmutils.polynomial-gcd-test
  (:require #?(:clj  [clojure.test :refer :all]
               :cljs [cljs.test :as t :refer-macros [is deftest testing]])
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]
            [sicmutils.analyze :as a]
            [sicmutils.expression :refer [variables-in]]
            [sicmutils.generic :as g]
            [sicmutils.polynomial :as p]
            [sicmutils.polynomial-gcd :as pg]
            [sicmutils.polynomial-test :as p-test]
            [sicmutils.util.stopwatch :as us]
            [sicmutils.value :as v]
            [taoensso.timbre :as log]))

(deftest poly-gcd
  (let [X (p/make 2 [[[1 0] 1]]) ;; some polynomials of arity 2
        Y (p/make 2 [[[0 1] 1]])]
    (testing "inexact coefficients"
      (is (= (p/make [1]) (g/gcd (p/make [0.2 0.4 0.6]) (p/make [0.4 0.6 0.8])))))

    (testing "GCD: arity 1 case"
      (let [x+1 (p/make [1 1])
            x+2 (p/make [2 1])
            x+3 (p/make [3 1])
            x+4 (p/make [4 1])
            U (g/mul x+1 (g/mul x+1 (g/mul x+2 x+4)))
            V (g/mul x+1 (g/mul x+2 x+3))]
        (is (= (p/make [2 3 1]) (g/gcd U V)))
        (is (= (p/make [4]) (g/gcd (p/make [8]) (p/make [4]))))
        (is (= (p/make [1]) (g/gcd (p/make [7]) (p/make [11]))))
        (is (= (p/make [11]) (g/gcd (p/make []) (p/make [11])))))
      (let [x+4 (p/make [4 1])
            x+3 (p/make [3 1])
            x-2 (p/make [-2 1])
            x+1 (p/make [1 1])
            U (reduce g/mul [x+4 x+4 x+3 x+3 x-2 x-2 x-2 x-2 x-2 x+1])
            V (reduce g/mul [x+4 x+3 x+3 x+3 x-2 x-2 x+1 x+1])
            W (reduce g/mul [x+4 x+3 x+3 x-2 x-2 x+1])
            Z (p/make [])]
        (is (= W (g/gcd U V)))
        (is (= W (g/gcd V U)))
        (is (= U (g/gcd U U)))
        (is (= V (g/gcd V V)))
        (is (= W (g/gcd W W)))
        (is (= U (g/gcd U Z)))
        (is (= U (g/gcd Z U)))
        (is (= V (g/gcd V Z)))
        (is (= V (g/gcd Z V)))))

    (testing "divide constant arity 2"
      (is (= [(p/make 2 []) X] (p/divide X Y)))
      (is (= [(p/make 2 []) Y] (p/divide Y X))))

    (testing "GCD: arity 2 case"
      (let [I (p/make 2 [[[0 0] 1]])
            X (p/make 2 [[[1 0] 1]])
            Y (p/make 2 [[[0 1] 1]])
            X+Y (g/add X Y)
            X+1 (g/add X I)
            Y+1 (g/add Y I)
            X+Y_2 (g/mul X+Y X+Y)
            X+Y_3 (g/mul X+Y_2 X+Y)
            U (reduce g/mul [(g/expt X+1 3) (g/expt X+Y 2) (g/expt Y+1 4)])
            V (reduce g/mul [(g/expt X+1 2) (g/expt X+Y 5) (g/expt Y+1 3)])
            G (reduce g/mul [(g/expt X+1 2) (g/expt X+Y 2) (g/expt Y+1 3)])]
        (is (= X+Y_2 (g/gcd X+Y_2 X+Y_3)))
        (is (= X+Y_3 (g/gcd X+Y_3 X+Y_3)))
        (is (= G (g/gcd U V)))))

    (testing "GCD: arity 3 case"
      (binding [pg/*poly-gcd-time-limit* #?(:clj  [2 :seconds]
                                            :cljs [6 :seconds])]
        (let [I (p/make 3 [[[0 0 0] 1]])
              X (p/make 3 [[[1 0 0] 1]])
              Y (p/make 3 [[[0 1 0] 1]])
              Z (p/make 3 [[[0 0 1] 1]])
              X+Y (g/add X Y)
              X+Z (g/add X Z)
              Y+Z (g/add Y Z)
              X+Y+Z (g/add X+Y Z)
              X+1 (g/add X I)
              Y+1 (g/add Y I)
              Z+1 (g/add Z I)
              U (reduce g/mul [(g/expt X+1 3) (g/expt X+Y 2) (g/expt Y+Z 5) (g/expt X+Y+Z 4) (g/expt Y+1 4) (g/expt Z+1 3)])
              V (reduce g/mul [(g/expt X+1 2)  (g/expt X+Y 5) (g/expt Y+Z 3) (g/expt X+Y+Z 5) (g/expt Y+1 2) (g/expt Z+1 1) X+1])
              G (reduce g/mul [(g/expt X+1 3) (g/expt X+Y 2) (g/expt Y+Z 3) (g/expt X+Y+Z 4) (g/expt Y+1 2) Z+1])]
          (is (= [(reduce g/mul [(g/expt Y+Z 2) (g/expt Y+1 2) (g/expt Z+1 2)]) (p/make 3 [])] (p/divide U G)))
          (is (= [(reduce g/mul [(g/expt X+Y 3) X+Y+Z]) (p/make 3 [])] (p/divide V G)))
          (is (= X+Z (g/gcd (g/mul X+Y X+Z) (g/mul Y+Z X+Z))))
          (is (= (g/mul X+Z X+Y+Z) (g/gcd (reduce g/mul [X+Y X+Z X+Y+Z]) (reduce g/mul [X+Z X+Y+Z Y+Z]))))
          (is (= (g/mul X+Z (g/mul X+Z X+Y)) (g/gcd (reduce g/mul [X+Z X+Z X+Y X+Y+Z Y+1]) (reduce g/mul [X+Z X+Z X+Y X+1 Z+1 X+Z]))))
          (is (= G (g/gcd U V))))))
    ;; this was sort of cool, but prevented us from using native BigInteger GCD.
    ;; it could come back, but in order to do this right, we would need a way
    ;; to specify the coefficient field when we create a polynomial so that we
    ;; can efficiently dispatch to a GCD routine tailored to that field.
    #_(testing "modular polynomial reduction"
        (let [A (p/make [-360 -171 145 25 1])
              B (p/make [-15 -14 -1 15 14 1])
              Z5 #(modular/make % 5)
              A:Z5 (map-coefficients Z5 A)
              B:Z5 (map-coefficients Z5 B)
              G5 (g/gcd A:Z5 B:Z5)]
          (is (= (p/make [(Z5 0) (Z5 -1) (Z5 0) (Z5 0) (Z5 1)]) A:Z5))
          (is (= (p/make [(Z5 0) (Z5 1) (Z5 -1) (Z5 0) (Z5 -1) (Z5 1)]) B:Z5))
          (is (= (p/make [(Z5 0) (Z5 -1) (Z5 0) (Z5 0) (Z5 1)]) G5))))))

(deftest simple-gcd-3
  (testing "GCD: arity 3 case"
    (let [I (p/make 3 [[[0 0 0] 1]])
          II (g/add I I)
          X (p/make 3 [[[1 0 0] 1]])
          Y (p/make 3 [[[0 1 0] 1]])
          Z (p/make 3 [[[0 0 1] 1]])
          X+Y (g/add X Y)
          X+Z (g/add X Z)
          Y+Z (g/add Y Z)]
      (is (= X+Z (g/gcd (g/mul X+Y X+Z) (g/mul Y+Z X+Z))))
      (is (= II (g/gcd II (g/add Z Z))))
      (is (= II (g/gcd (g/add Z Z) II)))
      (is (= II (g/gcd II (g/add (g/add X X) (g/add Z Z))))))))

(def ^:private poly-analyzer (p/->PolynomialAnalyzer))
(defn ^:private ->poly [x] (a/expression-> poly-analyzer x (fn [p _] p)))

(defn ^:private gcd-test [name dx fx gx]
  (let [d (->poly dx)
        f (->poly fx)
        g (->poly gx)
        df (g/mul d f)
        dg (g/mul d g)
        sw (us/stopwatch)
        a (binding [pg/*poly-gcd-time-limit* [10 :seconds]
                    pg/*poly-gcd-cache-enable* false]
            (is (= d (pg/gcd df dg))))]
    (log/info "gcd-test" name (us/repr sw))))

(deftest ^:long ^:benchmark gjs
  (testing "GJS cases (see sparse-gcd.scm:666)"
    (let [d1 '(+ (expt x1 2) x1 3)
          f1 '(+ (* 2 (expt x1 2)) (* 2 x1) 1)
          g1 '(+ (expt x1 2) (* 2 x1) 2)

          d2 '(+ (* 2 (expt x1 2) (expt x2 2))
                 (* x1 x2)
                 (* 2 x1))
          f2 '(+ (expt x2 2)
                 (* 2 (expt x1 2) x2)
                 (expt x1 2)
                 1)
          g2 '(+ (* (expt x1 2) (expt x2 2))
                 (* (expt x1 2) x2)
                 (* x1 x2)
                 (expt x1 2)
                 x1)

          d3 '(+ (* x2 x2 x3 x3)
                 (* x2 x2 x3)
                 (* 2 x1 x1 x2 x3)
                 (* x1 x3))
          f3 '(+ (* x3 x3)
                 (* x2 x2 x3)
                 (* x1 x1 x2 x3)
                 (* x1 x3)
                 (* x1 x1 x2 x2))
          g3 '(+ (* x2 x3)
                 (* 2 x1 x3)
                 x3
                 x1)

          d4 '(+ (* x1 x1 x4 x4)
                 (* x2 x2 x3 x4)
                 (* x1 x1 x2 x4)
                 (* x2 x4)
                 (* x1 x1 x2 x3))
          f4 '(+ (* x1 x2 x3 x3 x4 x4)
                 (* x1 x3 x3 x4 x4)
                 (* x1 x4 x4)
                 (* x4 x4)
                 (* x1 x3 x4))
          g4 '(+ (* x1 x3 x3 x4 x4)
                 (* x3 x3 x4 x4)
                 (* x4 x4)
                 (* x1 x2 x2 x3 x4)
                 (* x1 x2 x2))

          d5 '(+ (* x1 x1 x1 x2 x2 x3 x3 x4 x5 x5)
                 (* x1 x2 x2 x5 x5)
                 (* x1 x1 x1 x3 x4 x4 x5)
                 (* x1 x1 x1 x2 x3 x3 x4 x5)
                 (* x1 x1 x2 x3 x3 x4 x4))
          f5 '(+ (* x1 x2 x2 x5 x5)
                 (* x1 x2 x3 x3 x4 x5)
                 (* x1 x2 x3 x3 x4 x4)
                 (* x1 x2 x2 x4 x4)
                 1)
          g5 '(+ (* x1 x3 x3 x4 x5 x5)
                 (* x2 x5 x5)
                 (* x1 x2 x4 x5)
                 (* x2 x5)
                 (* x1 x2 x3 x4 x4))
          d6 '(+ (* x1 x2 x4 x4 x5 x5 x6 x6)
                 (* x1 x2 x2 x3 x3 x4 x5 x5 x6 x6)
                 (* x1 x1 x3 x6 x6)
                 (* x1 x1 x2 x3 x3 x4 x5 x5 x6)
                 (* x1 x1 x3 x5 x6))
          f6 '(+ (* x1 x1 x2 x4 x5 x5 x6 x6)
                 (* x1 x3 x5 x5 x6 x6)
                 (* x1 x2 x2 x6 x6)
                 (* x1 x1 x2 x2 x3 x3 x5 x6)
                 (* x1 x3 x3 x4 x5))
          g6 '(+ (* x2 x2 x3 x3 x4 x5 x5 x6)
                 (* x1 x4 x4 x5 x6)
                 (* x2 x2 x3 x3 x4 x5 x6)
                 (* x1 x2 x2 x3 x4 x4 x6)
                 (* x1 x1 x3 x5 x5))
          d7 '(+ (* x1 x2 x2 x4 x4 x6 x6 x7 x7)
                 (* x1 x1 x3 x4 x6 x6 x7 x7)
                 (* x3 x3 x4 x4 x7 x7)
                 (* x1 x1 x2 x4 x4 x6)
                 (* x3 x4 x5 x5))
          f7 '(+ (* x1 x1 x2 x4 x4 x5 x6 x6 x7 x7)
                 (* x1 x2 x3 x6 x7)
                 (* x3 x4 x4 x5 x5 x7)
                 (* x1 x1 x2 x3 x4 x4 x5 x6))
          g7 '(+ (* x1 x3 x5 x6 x6 x7 x7)
                 (* x2 x2 x3 x3 x4 x4 x5 x6 x7 x7)
                 (* x4 x6 x7 x7)
                 (* x1 x1 x2 x3 x5 x6 x7)
                 (* x1 x1 x3 x3 x4 x5 x5))]

      (is (= (->poly d2) (-> d2 ->poly p/lower-arity p/raise-arity)))
      (is (= (->poly d3) (-> d3 ->poly p/lower-arity p/raise-arity)))
      (is (= (->poly d4) (-> d4 ->poly p/lower-arity p/raise-arity)))

      (is (= (p/make [0
                      (p/make [2 1])
                      (p/make [0 0 2])])
             (-> d2 ->poly p/lower-arity)))

      (is (= (p/make [0
                      (p/make [2 1 2 1])
                      (p/make [0 0 2 0 2])
                      (p/make [2 5 2])
                      (p/make [0 0 2 4])])
             (p/lower-arity (g/mul (->poly d2) (->poly f2)))))

      (is (= (p/make [0
                      0
                      (p/make [4 4 5 4 1])
                      (p/make [0 0 8 4 8 4])
                      (p/make [4 12 9 2 4 0 4])
                      (p/make [0 0 8 20 8])
                      (p/make [0 0 0 0 4 8])])
             (g/mul (p/lower-arity (->poly d2))
                    (p/lower-arity (g/mul (->poly d2) (->poly f2))))))

      (gcd-test "D1" d1 f1 g1)
      (gcd-test "D2" d2 f2 g2)
      (gcd-test "D3" d3 f3 g3)
      (gcd-test "D4" d4 f4 g4)
      (gcd-test "D5" d5 f5 g5)
      ;; the following are too big for our naive algorithm.
      ;;(gcd-test "D6" d6 f6 g6)
      #_(gcd-test "D7" d7 f7 g7)
      (pg/gcd-stats))))

(deftest big-gcd
  (let [u (p/make 10 [[[0 0 1 0 0 0 1 1 0 1] 1]
                      [[0 1 1 0 0 0 1 1 1 0] 2]
                      [[0 0 1 2 0 1 0 1 0 1] 1]
                      [[0 0 1 2 1 0 0 1 0 1] -1]
                      [[1 1 0 1 0 1 0 2 0 0] 1]
                      [[1 1 0 1 1 0 0 2 0 0] -1]
                      [[2 0 1 0 0 1 0 1 0 1] -1]
                      [[2 0 1 0 1 0 0 1 0 1] 1]
                      [[0 1 1 2 0 1 0 1 1 0] -2]
                      [[1 0 2 1 0 1 0 0 1 1] 2]
                      [[1 0 2 1 1 0 0 0 1 1] -2]])
        v (p/make 10 [[[0 0 1 4 1 1 0 0 0 0] 1]
                      [[2 0 1 2 1 1 0 0 0 0] 2]
                      [[4 0 1 0 1 1 0 0 0 0] 1]])]
    (let [t (fn []
              (let [g (g/gcd u v)]
                (pg/gcd-stats)
                g))]
      (is (= (p/make 10 [[[0 0 0 0 0 0 0 0 0 0] 1]]) (t)))
      ;; for profiling
      (binding [pg/*poly-gcd-cache-enable* false]
        (dotimes [_ 0] (t))))))

(deftest ^:long troublesome-gcd
  (let [u (p/make 10 [[[0 1 1 2 1 0 1 1 0 1] -1]
                      [[2 1 1 0 0 1 1 1 0 1] -1]
                      [[0 2 1 2 1 0 1 1 1 0] -2]
                      [[1 1 2 1 0 1 1 0 1 1] 1]
                      [[1 1 2 1 1 0 1 0 1 1] -1]
                      [[2 2 1 0 0 1 1 1 1 0] -2]
                      [[0 1 1 4 1 1 0 1 0 1] -1]
                      [[0 1 1 4 2 0 0 1 0 1] 1]
                      [[1 2 0 3 1 1 0 2 0 0] -1]
                      [[1 2 0 3 2 0 0 2 0 0] 1]
                      [[1 2 2 1 0 1 1 0 2 0] 1]
                      [[1 2 2 1 1 0 1 0 2 0] -1]
                      [[2 1 1 2 0 2 0 1 0 1] 1]
                      [[2 1 1 2 1 1 0 1 0 1] -2]
                      [[2 1 1 2 2 0 0 1 0 1] 1]
                      [[3 2 0 1 0 2 0 2 0 0] -1]
                      [[3 2 0 1 1 1 0 2 0 0] 1]
                      [[4 1 1 0 0 2 0 1 0 1] 1]
                      [[4 1 1 0 1 1 0 1 0 1] -1]
                      [[0 2 1 4 1 1 0 1 1 0] 2]
                      [[1 1 2 3 0 2 0 0 1 1] -1]
                      [[1 1 2 3 2 0 0 0 1 1] 1]
                      [[2 2 1 2 0 2 0 1 1 0] 2]
                      [[2 2 1 2 2 0 0 1 1 0] 2]
                      [[3 1 2 1 0 2 0 0 1 1] -1]
                      [[3 1 2 1 2 0 0 0 1 1] 1]
                      [[4 2 1 0 1 1 0 1 1 0] 2]
                      [[1 2 2 3 0 2 0 0 2 0] -1]
                      [[1 2 2 3 1 1 0 0 2 0] 1]
                      [[3 2 2 1 1 1 0 0 2 0] -1]
                      [[3 2 2 1 2 0 0 0 2 0] 1]])
        v (p/make 10 [[[0 0 1 4 1 1 0 0 0 0] 1]
                      [[2 0 1 2 1 1 0 0 0 0] 2]
                      [[4 0 1 0 1 1 0 0 0 0] 1]])]
    (is (= (p/make-constant 10 1) (g/gcd u v)))))

(deftest ^:benchmark kuniaki-tsuji-examples
  ;; (only have a few of these, will add more)
  ;; http://www.sciencedirect.com/science/article/pii/S0747717108001016
  (testing "ex1"
    (let [d '(+ (* x x) (* 2 (expt y 23) (expt z 24)))
          p '(+ (* x x) (* (expt y 23) (expt z 22)))
          q '(+ (* y z (expt x 3))
                (* 2 (expt z 35) (expt y 41) (expt x 2))
                (* (expt z 3) (expt y 5) x)
                525)]
      (gcd-test "K1" d p q)))

  (testing "ex2"
    ;; a hack, using (expt z 0) to make the arities equal
    (let [d '(+ (expt x 3) (* 2 (expt z 0) (+ (expt y 34) (expt y 75)) (expt x 2)) (expt y 84))
          p '(+ (expt x 2) (* (expt y 53) (expt z 62)))
          q '(+ (* y (expt x 8))
                (* 2 (expt y 47) (expt x 6))
                (* (+ (expt y 20) (expt y 14)) (expt x 4))
                (* (expt y 69) (expt x 3))
                (* (expt y 55) (expt x 2))
                (expt y 99)
                (* (expt y 45) x)
                54
                (expt z 0))]
      (gcd-test "K2" d p q))))

(deftest ^:benchmark some-interesting-small-examples
  "Clojure.test.check's awesome problem-shrinking feature found some
  small examples of polynomials whose GCD is difficult to compute with
  this code (at the time of this writing). Recording them here as they
  should provide excellent examples for performance experiments."
  (testing "ex1"
    (let [u (p/make 3 {[0 0 0] -1, [0 0 3] 1})
          v (p/make 3 {[0 0 0] 1, [2 3 0] 2, [0 8 1] 1, [7 0 5] -1})]
      (gcd-test "S1" (p/make 3 {[0 0 0] 1}) u v)))

  (testing "ex2"
    (let [u (p/make 2 {[0 0] -1, [0 7] -1})
          v (p/make 2 {[0 0] 1, [0 1] -1, [0 4] 1, [3 3] -11, [1 9] 8, [8 5] -9, [12 1] 1})]
      (gcd-test "S2" (p/make 2 {[0 0] 1}) u v))))


;; Currently we only do GCD testing of univariate polynomials, because
;; we find that unfortunately clojure.test.check is very good at finding
;; polynomials even of arity 2 that will exceed the time allotment for
;; findinig GCDs. Hopefully we can fix that, but for the  present this
;; explains why we draw arities from the singleton set [1].

(def ^:private num-tests 20)

(defspec ^:long g-divides-u-and-v num-tests
  (gen/let [arity (gen/elements [1])]
    (prop/for-all [u (p-test/generate-poly arity)
                   v (p-test/generate-poly arity)]
                  (let [g (g/gcd u v)]
                    (or (and (v/nullity? u)
                             (v/nullity? v)
                             (v/nullity? g))
                        (and (g/exact-divide u g)
                             (g/exact-divide v g)))))))

(defspec ^:long d-divides-gcd-ud-vd num-tests
  (gen/let [arity (gen/elements [1])]
    (prop/for-all [u (p-test/generate-nonzero-poly arity)
                   v (p-test/generate-nonzero-poly arity)
                   d (p-test/generate-nonzero-poly arity)]
                  (let [ud (g/mul u d)
                        vd (g/mul v d)
                        g (g/gcd ud vd)]
                    (and
                     (g/exact-divide ud g)
                     (g/exact-divide vd g)
                     (g/exact-divide g d))))))
