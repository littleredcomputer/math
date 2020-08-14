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

(ns sicmutils.calculus.derivative-test
  (:refer-clojure :exclude [+ - * / ref])
  (:require [clojure.test :refer :all]
            [sicmutils
             [function :refer :all]
             [generic :as g :refer :all]
             [complex :refer [complex]]
             [value :as v]
             [numbers]
             [simplify :refer [hermetic-simplify-fixture]]
             [infix :refer [->infix]]
             [structure :refer :all]
             [matrix :as matrix]]
            [sicmutils.calculus.derivative :refer :all])
  (:import (sicmutils.calculus.derivative Differential)))

(use-fixtures :once hermetic-simplify-fixture)

(def ^:private q
  (up (literal-function 'x)
      (literal-function 'y)
      (literal-function 'z)))

(defn ^:private δ
  [η]
  (fn [f]
    ;; Define g(ε) as in Eq. 1.22; then δ_η f[q] = Dg(0)
    (fn [q]
      (let [g (fn [ε]
                (f (+ q (* ε η))))]
        ((D g) 0)))))

(deftest differentials
  (testing "add, mul differentials"
    (let [zero-differential (make-differential [])
          dx (make-differential {[0] 1})
          -dx (make-differential {[0] -1})
          dy (make-differential {[1] 1})
          dz (make-differential {[2] 1})
          dx-plus-dx (make-differential {[0] 2})
          dxdy (make-differential {[0 1] 1})
          dxdydz (make-differential {[0 1 2] 1})
          dx-plus-dy (make-differential {[0] 1 [1] 1})
          dx-plus-dz (make-differential {[0] 1 [2] 1})
          ]
      (is (= dx-plus-dy (dx+dy dx dy)))
      (is (= dx-plus-dy (dx+dy dy dx)))
      (is (= dx-plus-dz (dx+dy dx dz)))
      (is (= dx-plus-dz (dx+dy dz dx)))
      (is (= dx-plus-dx (dx+dy dx dx)))
      (is (= (make-differential {[0] 3 [1] 2 [2] 3})
             (reduce dx+dy 0 [dx dy dz dy dz dx dz dx])))
      (is (= (make-differential {[] 1 [0] 1}) (dx+dy dx 1)))
      (is (= (make-differential {[] 'k [0] 1}) (dx+dy dx 'k)))
      (is (= zero-differential (dx+dy dx -dx)))
      (is (= zero-differential (dx+dy -dx dx)))
      (is (= zero-differential (dx*dy dx 0)))
      (let [b (dx+dy 0 (dx*dy dx 0))
            c (dx*dy 0 dx)]
        (is (= zero-differential b))
        (is (= zero-differential c))
        (is (= zero-differential (dx+dy b c))))
      (is (= dxdy (dx*dy dx dy)))
      (is (= dxdydz (dx*dy (dx*dy dx dy) dz)))
      (is (= dxdydz (dx*dy (dx*dy dz dx) dy)))
      (is (= dxdydz (dx*dy (dx*dy dy dz) dx)))
      (is (= zero-differential (dx*dy dx dx)))
      (is (= zero-differential (dx*dy dz (dx*dy dy dz))))
      (is (= 0 (* dx dx)))))
  (testing "more terms"
    (let [d-expr (fn [^Differential dx]
                   (->> dx .terms (filter (fn [[tags coef]] (= tags [0]))) first second))
          d-simplify #(-> % d-expr simplify)]
      (is (= '(* 3 (expt x 2))
             (d-simplify (expt (+ 'x (make-differential {[0] 1})) 3))))
      (is (= '(* 4 (expt x 3))
             (d-simplify (expt (+ 'x (make-differential {[0] 1})) 4))))
      (let [dx (make-differential {[0] 1})
            x+dx (+ 'x dx)
            f (fn [x] (* x x x x))]
        (is (= '(* 4 (expt x 3))
               (d-simplify (* x+dx x+dx x+dx x+dx))))
        (is (= '(* 12 (expt x 2))
               (d-simplify (+ (* (+ (* (+ x+dx x+dx) x+dx) (* x+dx x+dx)) x+dx) (* x+dx x+dx x+dx)))))
        (is (= '(* 24 x) (d-simplify (+
                                      (* (+ (* 2 x+dx) x+dx x+dx x+dx x+dx) x+dx)
                                      (* (+ x+dx x+dx) x+dx)
                                      (* x+dx x+dx)
                                      (* (+ x+dx x+dx) x+dx)
                                      (* x+dx x+dx)))))
        (is (= 24 (d-expr (+ (* 6 x+dx)
                             (* 2 x+dx)
                             x+dx x+dx x+dx x+dx
                             (* 2 x+dx)
                             x+dx x+dx x+dx x+dx
                             (* 2 x+dx)
                             x+dx x+dx x+dx x+dx))))
        (is (= '(* 4 (expt x 3))
               (d-simplify (f x+dx))))))))

(deftest diff-test-1
  (testing "some simple functions"
    (is (= 2 ((D #(* 2 %)) 1)))
    (is (= 2 ((D #(* 2 %)) 'w)))
    (is (= (+ 'z 'z) ((D #(* % %)) 'z)))
    (is (= (* 3 (expt 'y 2))
           ((D #(expt % 3)) 'y)))
    (is (= (* (cos (* 2 'u)) 2) ((D #(sin (* 2 %))) 'u)))
    (is (= (/ 1 (expt (cos 'x) 2)) ((D tan) 'x)))
    (is (= (up 2 (+ 't 't)) ((D #(up (* 2 %) (* % %))) 't)))
    (is (= (up (- (sin 't)) (cos 't)) ((D #(up (cos %) (sin %))) 't)))
    (is (= '(/ 1 (sqrt (+ (* -1 (expt x 2)) 1))) (simplify ((D asin) 'x))))
    (is (= '(/ -1 (sqrt (+ (* -1 (expt x 2)) 1))) (simplify ((D acos) 'x)))))
  (testing "chain rule"
    (let [s (fn [t] (sqrt t))
          u (fn [t] (expt (- (* 3 (s t)) 1) 2/3))
          y (fn [t] (/ (+ (u t) 2) (- (u t) 1)))]
      (is ((v/within 1e-6) (/ -1 18.) ((D y) 9)))))
  (testing "structural-functions"
    (is (= '(up (cos t) (* -1 (sin t))) (simplify ((D (up sin cos)) 't))))))

(deftest partial-diff-test
  (testing "partial derivatives"
    (let [f (fn [x y] (+ (* 'a x x) (* 'b x y) (* 'c y y)))]
      (is (= '(+ (* 4 a) (* 3 b)) (simplify (((∂ 0) f) 2 3))))
      (is (= '(+ (* 2 b) (* 6 c)) (simplify (((∂ 1) f) 2 3))))
      (is (= '(+ (* 2 a x) (* b y)) (simplify (((∂ 0) f) 'x 'y))))
      (is (= '(+ (* b x) (* 2 c y)) (simplify (((∂ 1) f) 'x 'y))))
      ;; matrix of 2nd partials
      (is (= '[[(* 2 a) b]
               [b (* 2 c)]]
             (for [i (range 2)]
               (for [j (range 2)]
                 (simplify (((* (∂ i) (∂ j)) f) 'x 'y))))))
      (is (= '[[(* 2 a) b]
               [b (* 2 c)]]
             (for [i (range 2)]
               (for [j (range 2)]
                 (simplify (((compose (∂ i) (∂ j)) f) 'x 'y)))))))
    (let [F (fn [a b]
              (fn [[x y]]
                (up (* a x) (* b y))))]
      (is (= (up 'x 'y) ((F 1 1) (up 'x 'y))))
      (is (= (up (* 2 'x) (* 3 'y)) ((F 2 3) (up 'x 'y))))
      (is (= (up 'x 0)  ((((∂ 0) F) 1 1) (up 'x 'y))))
      (is (= (up 0 'y)  ((((∂ 1) F) 1 1) (up 'x 'y))))
      (is (= (down (up 'x 0) (up 0 'y)) (((D F) 1 1) (up 'x 'y)))))))

(deftest derivative-of-matrix
  (let [M (matrix/by-rows [(literal-function 'f) (literal-function 'g)]
                          [(literal-function 'h) (literal-function 'k)])]
    (is (= '(matrix-by-rows [(f t) (g t)]
                            [(h t) (k t)])
           (simplify (M 't))))
    (is (= '(matrix-by-rows [((D f) t) ((D g) t)]
                            [((D h) t) ((D k) t)])
           (simplify ((D M) 't))))
    (is (= '(matrix-by-rows
             [(+ (expt (f t) 2) (expt (h t) 2))
              (+ (* (f t) (g t)) (* (h t) (k t)))]
             [(+ (* (f t) (g t)) (* (h t) (k t)))
              (+ (expt (g t) 2) (expt (k t) 2))])
           (simplify ((* (transpose M) M) 't))))
    (is (= '(matrix-by-rows [(+ (* 2 ((D f) t) (f t)) (* 2 (h t) ((D h) t)))
                             (+ (* ((D f) t) (g t)) (* (f t) ((D g) t)) (* (h t) ((D k) t)) (* (k t) ((D h) t)))]
                            [(+ (* ((D f) t) (g t)) (* (f t) ((D g) t)) (* (h t) ((D k) t)) (* (k t) ((D h) t)))
                             (+ (* 2 (g t) ((D g) t)) (* 2 (k t) ((D k) t)))])
           (simplify ((D (* (transpose M) M)) 't))))))

(deftest amazing-bug
  (testing "1"
    (let [f (fn [x]
              (fn [g]
                (fn [y]
                  (g (+ x y)))))
          f-hat ((D f) 3)
          f-hat-inexact ((D f) 3.0)]

      ;; exact precision is maintained for exact arguments.
      (is (= (exp 8) ((f-hat exp) 5)))

      ;; with a float instead of an int, evaluation's forced.
      (is ((v/within 1e-6) 2980.957987 ((f-hat-inexact exp) 5)))

      ;; TODO: this is the amazing bug: bbb == 0 is wrong.
      #_(is (= 'bbb ((f-hat (f-hat exp)) 5))))))

(deftest diff-test-2
  (testing "delta-eta-test"
    (with-literal-functions [η q f g]
      (let [I (fn [q] (fn [t] (q t)))
            F (fn [q] (fn [t] (f (q t))))
            G (fn [q] (fn [t] (g (q t))))
            q+εη (+ q (* 'ε η))
            g (fn [ε] (+ q (* ε η)))
            δη (δ η)
            δηI (δη I)
            δηIq (δηI q)
            δηFq ((δη F) q)
            φ (fn [f] (fn [q] (fn [t] ((literal-function 'φ) ((f q) t)))))]
        (is (= '((D f) t) (simplify ((D f) 't))))
        (is (= '(+ (* ε (η t)) (q t)) (simplify (q+εη 't))))
        (is (= '(+ (* ε (η t)) (q t)) (simplify ((g 'ε) 't))))
        (is (= '(η a) (simplify (((D g) 'dt) 'a))))
        (is (= '(η t) (simplify (δηIq 't))))
        (is (= '(f (q t)) (simplify ((F q) 't))))
        (is (= '(* (η t) ((D f) (q t))) (simplify (δηFq 't))))
        ;; sum rule for variation: δ(F+G) = δF + δG
        (is (= '(+ (* (η t) ((D f) (q t)))
                   (* (η t) ((D g) (q t))))
               (simplify (((δη (+ F G)) q) 't))))
        ;; scalar product rule for variation: δ(cF) = cδF
        (is (= '(* c (η t) ((D f) (q t))) (simplify (((δη (* 'c F)) q) 't))))
        ;; product rule for variation: δ(FG) = δF G + F δG
        (is (= (simplify (+ (* (((δη F) q) 't) ((G q) 't))
                            (* ((F q) 't) (((δη G) q) 't))))
               (simplify (((δη (* F G)) q) 't))))
        ;; path-independent chain rule for variation
        (is (= '(φ (f (q t))) (simplify (((φ F) q) 't))))
        (is (= '(* (η t) ((D f) (q t)) ((D φ) (f (q t)))) (simplify (((δη (φ F)) q) 't))))))))

(deftest derivatives-as-values
  (let [cs0 (fn [x] (sin (cos x)))
        cs1 (compose sin cos)
        cs2 (comp sin cos)
        y0 (D cs0)
        y1 (D cs1)
        y2 (D cs2)]
    (is (= '(sin (cos x)) (simplify (cs0 'x))))
    (is (= '(sin (cos x)) (simplify (cs1 'x))))
    (is (= '(sin (cos x)) (simplify (cs2 'x))))
    (is (= '(* -1 (sin x) (cos (cos x))) (simplify ((D cs0) 'x))))
    (is (= '(* -1 (sin x) (cos (cos x))) (simplify ((D cs1) 'x))))
    (is (= '(* -1 (sin x) (cos (cos x))) (simplify ((D cs2) 'x))))
    (is (= '(* -1 (sin x) (cos (cos x))) (simplify (y0 'x))))
    (is (= '(* -1 (sin x) (cos (cos x))) (simplify (y1 'x))))
    (is (= '(* -1 (sin x) (cos (cos x))) (simplify (y2 'x)))))
  (let [unity (reduce + (map square [sin cos]))
        dU (D unity)]
    (is (= 1 (simplify (unity 'x))))
    (is (= 0 (simplify (dU 'x)))))
  (let [odear (fn [z] ((D (compose sin cos)) z))]
    (is (= '(* -1 (sin x) (cos (cos x))) (simplify (odear 'x))))))

(deftest exponentiation-and-composition
  (let [ff (fn [x y z] (+ (* x x y) (* y y z)(* z z x)))
        ]
    (is (= '(down
             (down (* 2 y) (* 2 x) (* 2 z))
             (down (* 2 x) (* 2 z) (* 2 y))
             (down (* 2 z) (* 2 y) (* 2 x)))
           (simplify (((expt D 2) ff) 'x 'y 'z))))
    (is (= (((* D D) ff) 'x 'y 'z) (((expt D 2) ff) 'x 'y 'z)))
    (is (= (((compose D D) ff) 'x 'y 'z) (((expt D 2) ff) 'x 'y 'z)))
    (is (= (((* D D D) ff) 'x 'y 'z) (((expt D 3) ff) 'x 'y 'z)))
    (is (= (((compose D D D) ff) 'x 'y 'z) (((expt D 3) ff) 'x 'y 'z))))
  (testing "issue #9 regression"
    (let [g (fn [z] (* z z z z))
          f4 (fn [x] (+ (* x x x) (* x x x)))]
      (is (= '(expt t 4) (simplify (g 't))))
      (is (= '(* 4 (expt t 3)) (simplify ((D g) 't))))
      (is (= '(* 12 (expt t 2)) (simplify ((D (D g)) 't))))
      (is (= '(* 24 t) (simplify ((D (D (D g))) 't))))
      (is (= '(* 24 z) (simplify (((expt D 3) g) 'z))))
      (is (= '(* 2 (expt s 3)) (simplify (f4 's))))
      (is (= '(* 6 (expt s 2)) (simplify ((D f4) 's))))
      (is (= '(* 12 s) (simplify ((D (D f4)) 's))))
      (is (= 12 (simplify ((D (D (D f4))) 's))))
      (is (= 12 (simplify (((* D D D) f4) 's))))
      (is (= 12 (simplify (((compose D D D) f4) 's))))
      (is (= 12 (simplify (((expt D 3) f4) 's)))))
    (let [fff (fn [x y z] (+ (* x x y)(* y y y z)(* z z z z x)))]
      (is (= '(+ (* x (expt z 4)) (* (expt y 3) z) (* (expt x 2) y))
             (simplify (((expt D 0) fff) 'x 'y 'z))))
      (is (= '(down
               (+ (expt z 4) (* 2 x y))
               (+ (* 3 (expt y 2) z) (expt x 2))
               (+ (* 4 x (expt z 3)) (expt y 3)))
             (simplify (((expt D 1) fff) 'x 'y 'z))))
      (is (= '(down
               (down (* 2 y) (* 2 x) (* 4 (expt z 3)))
               (down (* 2 x) (* 6 y z) (* 3 (expt y 2)))
               (down (* 4 (expt z 3)) (* 3 (expt y 2)) (* 12 x (expt z 2))))
             (simplify (((expt D 2) fff) 'x 'y 'z))))
      (is (= '(down
               (down (down 0 2 0) (down 2 0 0) (down 0 0 (* 12 (expt z 2))))
               (down (down 2 0 0) (down 0 (* 6 z) (* 6 y)) (down 0 (* 6 y) 0))
               (down
                (down 0 0 (* 12 (expt z 2)))
                (down 0 (* 6 y) 0)
                (down (* 12 (expt z 2)) 0 (* 24 x z))))
             (simplify (((expt D 3) fff) 'x 'y 'z)))))
    (is (= 0 ((D (fn [x] 0)) 'x)))
    (is (= 0 ((D (fn [& xs] 0)) 'x)))))

(deftest literal-functions
  (with-literal-functions [f [g [0 0] 0]]
    (testing "R -> R"
      (is (= '((D f) x) (simplify ((D f) 'x))))
      (is (= '((D f) (+ x y)) (simplify ((D f) (+ 'x 'y))))))
    (testing "R^2 -> R"
      (is (= '(((∂ 0) g) x y) (simplify (((∂ 0) g) 'x 'y))))
      (is (= '(((∂ 1) g) x y) (simplify (((∂ 1) g) 'x 'y))))
      (is (= '(down (((∂ 0) g) x y) (((∂ 1) g) x y))
             (simplify ((D g) 'x 'y)))))
    (testing "zero-like"
      (is (= 0 ((v/zero-like f) 'x)))
      (is (= 0 ((D (v/zero-like f)) 'x))))))

(deftest complex-derivatives
  (let [i (complex 0 1)
        f (fn [z] (* i (sin (* i z))))]
    (is (= '(* -1 (cosh z))
           (simplify ((D f) 'z))))))

(deftest fun-with-operators
  (let [f #(expt % 3)]
    (is (= '(+ (* (expt t 3) (cos t)) (* 3 (expt t 2) (sin t)))
           (simplify (((* D sin) f) 't))))
    (is (= '(* 3 (expt t 2) (sin t) )
           (simplify (((* sin D) f) 't))))))

(deftest vector-calculus
  (let [f (up identity sin cos)
        divergence #(fn [t] (reduce + ((D %) t)))
        laplacian #(* (D %) ((transpose D) %))]
    (is (= '(up 1 (cos t) (* -1 (sin t))) (simplify ((D f) 't))))
    (is (= '(down 1 (cos t) (* -1 (sin t))) (simplify (((transpose D) f) 't))))
    (is (= 2 (simplify (* ((D f) 't) (((transpose D) f) 't)))))
    (is (= 2 (simplify ((laplacian (up identity sin cos)) 't))))
    (is (= '(+ (cos t) (* -1 (sin t)) 1) (simplify ((divergence f) 't))))))

(deftest exp-and-log
  (is (= '(/ 1 x) (simplify ((D log) 'x)))))

(deftest more-trig
  (let [cot (/ cos sin)
        sec (/ cos)
        csc (/ sin)]
    (is (= '(/ (cos x) (sin x)) (simplify (cot 'x))))
    (is (= '(/ -1 (expt (sin x) 2)) (simplify ((D cot) 'x))))
    (is (= '(/ -1 (expt (sin x) 2)) (simplify ((D (/ tan)) 'x))))
    (is (= '(/ (* -1 (cos x)) (expt (sin x) 2)) (simplify ((D csc) 'x))))
    (is (= '(/ (sin x) (expt (cos x) 2)) (simplify ((D sec) 'x))))
    (is (= '(/ 1 (+ (expt x 2) 1)) (simplify ((D atan) 'x))))
    (is (= '(down (/ x (+ (expt x 2) (expt y 2))) (/ (* -1 y) (+ (expt x 2) (expt y 2))))
           (simplify ((D atan) 'y 'x))))))

(deftest alexgian-examples
  (testing "space"
    (let [g (literal-function 'g [0 0] 0)
          h (literal-function 'h [0 0] 0)]
      (is (= '(+ (((∂ 0) g) x y) (((∂ 0) h) x y))
             (simplify (((∂ 0) (+ g h)) 'x 'y))))
      (is (= '(+ (* (((∂ 0) g) x y) (h x y)) (* (((∂ 0) h) x y) (g x y)))
             (simplify (((∂ 0) (* g h)) 'x 'y))))
      (is (= '(+ (* (((∂ 0) g) x y) (h x y) (expt (g x y) (+ (h x y) -1)))
                 (* (((∂ 0) h) x y) (log (g x y)) (expt (g x y) (h x y))))
             (simplify (((∂ 0) (expt g h)) 'x 'y))))))
  (testing "operators"
    (is (= '(down 1 1 1 1 1 1 1 1 1 1)
           (simplify ((D +) 'a 'b 'c 'd 'e 'f 'g 'h 'i 'j))))
    (is (= '(down
             (* b c d e f g h i j)
             (* a c d e f g h i j)
             (* a b d e f g h i j)
             (* a b c e f g h i j)
             (* a b c d f g h i j)
             (* a b c d e g h i j)
             (* a b c d e f h i j)
             (* a b c d e f g i j)
             (* a b c d e f g h j)
             (* a b c d e f g h i))
           (simplify ((D *) 'a 'b 'c 'd 'e 'f 'g 'h 'i 'j))))
    (is (= '(down (* y (expt x (+ y -1)))
                  (* (log x) (expt x y)))
           (simplify ((D expt) 'x 'y))))
    (is (= '(* y (expt x (+ y -1)))
           (simplify (((∂ 0) expt) 'x 'y))))
    (is (= 2
           (simplify (((∂ 0) expt) 1 2))))
    (let [pow (fn [x y] (apply * (repeat y x)))]
      (is (= 8 (pow 2 3)))
      (is (= '(expt x 8) (simplify (pow 'x 8))))))
  (testing "formatting"
    (let [f2 (fn [x y] (* (sin x) (log y)))
          f3 (fn [x y] (* (tan x) (log y)))
          f4 (fn [x y] (* (tan x) (sin y)))
          f5 (fn [x y] (/ (tan x) (sin y)))]
      (is (= '(down (* (cos x) (log y))
                    (/ (sin x) y))
             (simplify ((D f2) 'x 'y))))
      (is (= '(down (/ (log y) (expt (cos x) 2))
                    (/ (tan x) y))
             (simplify ((D f3) 'x 'y))))
      (is (= '(down (/ (sin y) (expt (cos x) 2))
                    (/ (* (sin x) (cos y)) (cos x)))
             (simplify ((D f4) 'x 'y))))
      (is (= '(down
                (/ 1 (* (expt (cos x) 2) (sin y)))
                (/ (* -1 (sin x) (cos y)) (* (cos x) (expt (sin y) 2))))
             (simplify ((D f5) 'x 'y))))))
  (testing "arity"
    (let [f100dd (fn [x ct acc]
                   (if (v/nullity? ct)
                     acc
                     (recur x (dec ct) (sin (+ x acc)))))
          f100d (fn [x] (f100dd x 100 x))
          f100e (fn f100e
                  ([x] (f100e x 100 x))
                  ([x ct acc] (if (v/nullity? ct) acc (recur x (dec ct) (sin (+ x acc))))))
          f100ea (with-meta f100e {:arity [:exactly 1]})]
      (is ((v/within 1e-6) 0.51603111348625 ((D f100d) 6)))
      (is (thrown? IllegalArgumentException ((v/within 1e-6) 0.51603111348625 ((D f100e) 6))))
      (is ((v/within 1e-6) 0.51603111348625 ((D (with-meta f100e {:arity [:exactly 1]})) 6))))))

(deftest deep-partials
  (let [f (fn [x y] (+ (square x) (square (square y))))]
    (is (= '((* 2 x)
             (* 2 y)
             (+ (* 4 (expt w 3)) (* 4 w (expt z 2)))
             (+ (* 4 (expt w 2) z) (* 4 (expt z 3))))
           (map simplify
                (for [i (range 2)
                      j (range 2)]
                  (((∂ i j) f) (up 'x 'y) (up 'w 'z))))))
    (is (thrown? IllegalArgumentException (((∂ 0 1) f) 'x 'y)))))

(deftest derivative-as-operator
  (let [f (literal-function 'f [0 0] 0)
        g (literal-function 'g (up 0 0) 0)
        dX (up 'dx 'dy)]
    (is (= '(f x y) (simplify (f 'x 'y))))
    (is (= '(g (up (* 3 x) (* 3 y))) (simplify (g (* 3 (up 'x 'y))))))
    (is (= '(down (down (((∂ 0) ((∂ 0) f)) x y)
                        (((∂ 1) ((∂ 0) f)) x y))
                  (down (((∂ 1) ((∂ 0) f)) x y)
                        (((∂ 1) ((∂ 1) f)) x y)))
           (simplify (((expt D 2) f) 'x 'y))))
    (is (= '(down (((∂ 0) f) x y) (((∂ 1) f) x y))
           (simplify ((D f) 'x 'y))))
    (is (= '(+ (* dx (((∂ 0) f) x y)) (* dy (((∂ 1) f) x y)))
           (simplify (* ((D f) 'x 'y) dX))))
    (is (= '(+ (* (expt dx 2) (((∂ 0) ((∂ 0) f)) x y))
               (* 2 dx dy (((∂ 1) ((∂ 0) f)) x y))
               (* (expt dy 2) (((∂ 1) ((∂ 1) f)) x y)))
           (simplify (* dX (((expt D 2) f) 'x 'y) dX))))
    (is (= "1/2 dx² ∂₀(∂₀f)(x, y) + dx dy ∂₁(∂₀f)(x, y) + 1/2 dy² ∂₁(∂₁f)(x, y) + dx ∂₀f(x, y) + dy ∂₁f(x, y) + f(x, y)"
           (->infix (simplify (+ (f 'x 'y)
                                 (* ((D f) 'x 'y) dX)
                                 (* 1/2 (((expt D 2) f) 'x 'y) dX dX))))))))

(deftest taylor
  (is (= '(+ (* 1/6 (expt dx 3) (((∂ 0) ((∂ 0) ((∂ 0) f))) (up x y)))
             (* 1/6 (expt dx 2) dy (((∂ 0) ((∂ 0) ((∂ 1) f))) (up x y)))
             (* 1/3 (expt dx 2) dy (((∂ 1) ((∂ 0) ((∂ 0) f))) (up x y)))
             (* 1/3 dx (expt dy 2) (((∂ 1) ((∂ 0) ((∂ 1) f))) (up x y)))
             (* 1/6 dx (expt dy 2) (((∂ 1) ((∂ 1) ((∂ 0) f))) (up x y)))
             (* 1/6 (expt dy 3) (((∂ 1) ((∂ 1) ((∂ 1) f))) (up x y)))
             (* 1/2 (expt dx 2) (((∂ 0) ((∂ 0) f)) (up x y)))
             (* dx dy (((∂ 1) ((∂ 0) f)) (up x y)))
             (* 1/2 (expt dy 2) (((∂ 1) ((∂ 1) f)) (up x y)))
             (* dx (((∂ 0) f) (up x y)))
             (* dy (((∂ 1) f) (up x y)))
             (f (up x y)))
         (simplify
          (reduce +
                  (take 4 (taylor-series-terms
                           (literal-function 'f (up 0 0) 0)
                           (up 'x 'y)
                           (up 'dx 'dy)))))))
  (testing "eq. 5.291"
    (let [V (fn [[xi eta]] (sqrt (+ (square (+ xi 'R_0)) (square eta))))]
      (is (= '[R_0 xi (/ (expt eta 2) (* 2 R_0))]
             (take 3 (simplify (taylor-series-terms V (up 0 0) (up 'xi 'eta)))))))))

(deftest moved-from-structure-and-matrix
  (let [vs (up
            (up 'vx1 'vy1)
            (up 'vx2 'vy2))
        L1 (fn [[v1 v2]]
             (+ (* 1/2 'm1 (g/square v1))
                (* 1/2 'm2 (g/square v2))))]
    (is (= '(down
             (down (down (down m1 0) (down 0 0))
                   (down (down 0 m1) (down 0 0)))
             (down (down (down 0 0) (down m2 0))
                   (down (down 0 0) (down 0 m2))))
           (g/simplify (((g/expt D 2) L1) vs))))

    (testing "identical test in matrix form"
      (is (= '(matrix-by-rows [m1 0 0 0]
                              [0 m1 0 0]
                              [0 0 m2 0]
                              [0 0 0 m2])
             (g/simplify
              (matrix/s->m vs (((g/expt D 2) L1) vs) vs)))))))

(deftest moved-from-matrix
  (testing "s->m->s"
    (let [as-matrix (fn [F]
                      (fn [s]
                        (let [v (F s)]
                          (matrix/s->m (compatible-shape (g/* v s)) v s))))
          C-general (literal-function
                     'C '(-> (UP Real
                                 (UP Real Real)
                                 (DOWN Real Real))
                             (UP Real
                                 (UP Real Real)
                                 (DOWN Real Real))))
          s (up 't (up 'x 'y) (down 'px 'py))]
      (is (= '(matrix-by-rows
               [(((∂ 0) C↑0) (up t (up x y) (down px py)))
                (((∂ 1 0) C↑0) (up t (up x y) (down px py)))
                (((∂ 1 1) C↑0) (up t (up x y) (down px py)))
                (((∂ 2 0) C↑0) (up t (up x y) (down px py)))
                (((∂ 2 1) C↑0) (up t (up x y) (down px py)))]
               [(((∂ 0) C↑1↑0) (up t (up x y) (down px py)))
                (((∂ 1 0) C↑1↑0) (up t (up x y) (down px py)))
                (((∂ 1 1) C↑1↑0) (up t (up x y) (down px py)))
                (((∂ 2 0) C↑1↑0) (up t (up x y) (down px py)))
                (((∂ 2 1) C↑1↑0) (up t (up x y) (down px py)))]
               [(((∂ 0) C↑1↑1) (up t (up x y) (down px py)))
                (((∂ 1 0) C↑1↑1) (up t (up x y) (down px py)))
                (((∂ 1 1) C↑1↑1) (up t (up x y) (down px py)))
                (((∂ 2 0) C↑1↑1) (up t (up x y) (down px py)))
                (((∂ 2 1) C↑1↑1) (up t (up x y) (down px py)))]
               [(((∂ 0) C↑2_0) (up t (up x y) (down px py)))
                (((∂ 1 0) C↑2_0) (up t (up x y) (down px py)))
                (((∂ 1 1) C↑2_0) (up t (up x y) (down px py)))
                (((∂ 2 0) C↑2_0) (up t (up x y) (down px py)))
                (((∂ 2 1) C↑2_0) (up t (up x y) (down px py)))]
               [(((∂ 0) C↑2_1) (up t (up x y) (down px py)))
                (((∂ 1 0) C↑2_1) (up t (up x y) (down px py)))
                (((∂ 1 1) C↑2_1) (up t (up x y) (down px py)))
                (((∂ 2 0) C↑2_1) (up t (up x y) (down px py)))
                (((∂ 2 1) C↑2_1) (up t (up x y) (down px py)))])
             (g/simplify ((as-matrix (D C-general)) s)))))))
