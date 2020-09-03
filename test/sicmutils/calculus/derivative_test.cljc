;;
;; Copyright © 2017 Colin Smith.
;; This work is based on the Scmutils system of MIT/GNU Scheme:
;; Copyright © 2002 Massachusetts Institute of Technology
;;
;; This is free software;  you can redistribute it and/or modify
;;; it under the terms of the GNU General Public License as published by
;; the Free Software Foundation; either version 3 of the License, or (at
;; your option) any later version.
;;
;;; This software is distributed in the hope that it will be useful, but
;; WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
;; General Public License for more details.
;;
;; You should have received a copy of the GNU General Public License
;; along with this code; if not, see <http://www.gnu.org/licenses/>.
;;

(ns sicmutils.calculus.derivative-test
  (:refer-clojure :exclude [+ - * / ref partial])
  (:require [clojure.test :refer [is deftest testing use-fixtures]]
            [sicmutils.calculus.derivative :as d :refer [D partial]]
            [sicmutils.complex :refer [complex]]
            #?(:clj  [sicmutils.function :as f :refer [with-literal-functions]]
               :cljs [sicmutils.function :as f :refer-macros [with-literal-functions]])
            [sicmutils.generic :as g :refer [acos asin atan cos sin tan log exp expt + - * /]]
            [sicmutils.matrix :as matrix]
            [sicmutils.series :as series]
            [sicmutils.simplify :refer [hermetic-simplify-fixture]]
            [sicmutils.structure :as s]
            [sicmutils.value :as v]))

(use-fixtures :once hermetic-simplify-fixture)

(def ^:private q
  (s/up (f/literal-function 'x)
        (f/literal-function 'y)
        (f/literal-function 'z)))

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
    (let [zero-differential (d/make-differential [])
          dx (d/make-differential {[0] 1})
          -dx (d/make-differential {[0] -1})
          dy (d/make-differential {[1] 1})
          dz (d/make-differential {[2] 1})
          dx-plus-dx (d/make-differential {[0] 2})
          dxdy (d/make-differential {[0 1] 1})
          dxdydz (d/make-differential {[0 1 2] 1})
          dx-plus-dy (d/make-differential {[0] 1 [1] 1})
          dx-plus-dz (d/make-differential {[0] 1 [2] 1})]
      (is (= dx-plus-dy (d/dx+dy dx dy)))
      (is (= dx-plus-dy (d/dx+dy dy dx)))
      (is (= dx-plus-dz (d/dx+dy dx dz)))
      (is (= dx-plus-dz (d/dx+dy dz dx)))
      (is (= dx-plus-dx (d/dx+dy dx dx)))
      (is (= (d/make-differential {[0] 3 [1] 2 [2] 3})
             (reduce d/dx+dy 0 [dx dy dz dy dz dx dz dx])))
      (is (= (d/make-differential {[] 1 [0] 1}) (d/dx+dy dx 1)))
      (is (= (d/make-differential {[] 'k [0] 1}) (d/dx+dy dx 'k)))
      (is (= zero-differential (d/dx+dy dx -dx)))
      (is (= zero-differential (d/dx+dy -dx dx)))
      (is (= zero-differential (d/dx*dy dx 0)))
      (let [b (d/dx+dy 0 (d/dx*dy dx 0))
            c (d/dx*dy 0 dx)]
        (is (= zero-differential b))
        (is (= zero-differential c))
        (is (= zero-differential (d/dx+dy b c))))
      (is (= dxdy (d/dx*dy dx dy)))
      (is (= dxdydz (d/dx*dy (d/dx*dy dx dy) dz)))
      (is (= dxdydz (d/dx*dy (d/dx*dy dz dx) dy)))
      (is (= dxdydz (d/dx*dy (d/dx*dy dy dz) dx)))
      (is (= zero-differential (d/dx*dy dx dx)))
      (is (= zero-differential (d/dx*dy dz (d/dx*dy dy dz))))
      (is (= 0 (* dx dx)))))

  (testing "more terms"
    (let [d-expr (fn [dx]
                   (->> dx .-terms (filter (fn [[tags coef]] (= tags [0]))) first second))
          d-simplify #(-> % d-expr g/simplify)]
      (is (= '(* 3 (expt x 2))
             (d-simplify (g/expt (+ 'x (d/make-differential {[0] 1})) 3))))
      (is (= '(* 4 (expt x 3))
             (d-simplify (g/expt (+ 'x (d/make-differential {[0] 1})) 4))))
      (let [dx (d/make-differential {[0] 1})
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
    (is (= (* 3 (g/expt 'y 2))
           ((D #(g/expt % 3)) 'y)))
    (is (= (* (cos (* 2 'u)) 2) ((D #(sin (* 2 %))) 'u)))
    (is (= (/ 1 (g/expt (cos 'x) 2)) ((D tan) 'x)))
    (is (= (s/up 2 (+ 't 't)) ((D #(s/up (* 2 %) (* % %))) 't)))
    (is (= (s/up (- (sin 't)) (cos 't)) ((D #(s/up (cos %) (sin %))) 't)))
    (is (= '(/ 1 (sqrt (+ (* -1 (expt x 2)) 1))) (g/simplify ((D asin) 'x))))
    (is (= '(/ -1 (sqrt (+ (* -1 (expt x 2)) 1))) (g/simplify ((D acos) 'x)))))

  (testing "chain rule"
    (let [s (fn [t] (g/sqrt t))
          u (fn [t] (g/expt (- (* 3 (s t)) 1) (/ 2 3)))
          y (fn [t] (/ (+ (u t) 2) (- (u t) 1)))]
      (is ((v/within 1e-6) (/ -1 18.) ((D y) 9)))))

  (testing "structural-functions"
    (is (= '(up (cos t) (* -1 (sin t))) (g/simplify ((D (s/up sin cos)) 't))))))

(deftest partial-diff-test
  (testing "partial derivatives"
    (let [f (fn [x y] (+ (* 'a x x) (* 'b x y) (* 'c y y)))]
      (is (= '(+ (* 4 a) (* 3 b)) (g/simplify (((partial 0) f) 2 3))))
      (is (= '(+ (* 2 b) (* 6 c)) (g/simplify (((partial 1) f) 2 3))))
      (is (= '(+ (* 2 a x) (* b y)) (g/simplify (((partial 0) f) 'x 'y))))
      (is (= '(+ (* b x) (* 2 c y)) (g/simplify (((partial 1) f) 'x 'y))))
      ;; matrix of 2nd partials
      (is (= '[[(* 2 a) b]
               [b (* 2 c)]]
             (for [i (range 2)]
               (for [j (range 2)]
                 (g/simplify (((* (partial i) (partial j)) f) 'x 'y))))))
      (is (= '[[(* 2 a) b]
               [b (* 2 c)]]
             (for [i (range 2)]
               (for [j (range 2)]
                 (g/simplify (((f/compose (partial i) (partial j)) f) 'x 'y)))))))
    (let [F (fn [a b]
              (fn [[x y]]
                (s/up (* a x) (* b y))))]
      (is (= (s/up 'x 'y) ((F 1 1) (s/up 'x 'y))))
      (is (= (s/up (* 2 'x) (* 3 'y)) ((F 2 3) (s/up 'x 'y))))
      (is (= (s/up 'x 0)  ((((partial 0) F) 1 1) (s/up 'x 'y))))
      (is (= (s/up 0 'y)  ((((partial 1) F) 1 1) (s/up 'x 'y))))
      (is (= (s/down (s/up 'x 0) (s/up 0 'y)) (((D F) 1 1) (s/up 'x 'y)))))))

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
            φ (fn [f] (fn [q] (fn [t] ((f/literal-function 'φ) ((f q) t)))))]
        (is (= '((D f) t) (g/simplify ((D f) 't))))
        (is (= '(+ (* ε (η t)) (q t)) (g/simplify (q+εη 't))))
        (is (= '(+ (* ε (η t)) (q t)) (g/simplify ((g 'ε) 't))))
        (is (= '(η a) (g/simplify (((D g) 'dt) 'a))))
        (is (= '(η t) (g/simplify (δηIq 't))))
        (is (= '(f (q t)) (g/simplify ((F q) 't))))
        (is (= '(* (η t) ((D f) (q t))) (g/simplify (δηFq 't))))
        ;; sum rule for variation: δ(F+G) = δF + δG
        (is (= '(+ (* (η t) ((D f) (q t)))
                   (* (η t) ((D g) (q t))))
               (g/simplify (((δη (+ F G)) q) 't))))
        ;; scalar product rule for variation: δ(cF) = cδF
        (is (= '(* c (η t) ((D f) (q t))) (g/simplify (((δη (* 'c F)) q) 't))))
        ;; product rule for variation: δ(FG) = δF G + F δG
        (is (= (g/simplify (+ (* (((δη F) q) 't) ((G q) 't))
                              (* ((F q) 't) (((δη G) q) 't))))
               (g/simplify (((δη (* F G)) q) 't))))
        ;; path-independent chain rule for variation
        (is (= '(φ (f (q t))) (g/simplify (((φ F) q) 't))))
        (is (= '(* (η t) ((D f) (q t)) ((D φ) (f (q t)))) (g/simplify (((δη (φ F)) q) 't))))))))

(deftest exponentiation-and-composition
  (let [ff (fn [x y z] (+ (* x x y) (* y y z)(* z z x)))
        ]
    (is (= '(down
             (down (* 2 y) (* 2 x) (* 2 z))
             (down (* 2 x) (* 2 z) (* 2 y))
             (down (* 2 z) (* 2 y) (* 2 x)))
           (g/simplify (((g/expt D 2) ff) 'x 'y 'z))))
    (is (= (((* D D) ff) 'x 'y 'z) (((g/expt D 2) ff) 'x 'y 'z)))
    (is (= (((f/compose D D) ff) 'x 'y 'z) (((g/expt D 2) ff) 'x 'y 'z)))
    (is (= (((* D D D) ff) 'x 'y 'z) (((g/expt D 3) ff) 'x 'y 'z)))
    (is (= (((f/compose D D D) ff) 'x 'y 'z) (((g/expt D 3) ff) 'x 'y 'z))))
  (testing "issue #9 regression"
    (let [g (fn [z] (* z z z z))
          f4 (fn [x] (+ (* x x x) (* x x x)))]
      (is (= '(expt t 4) (g/simplify (g 't))))
      (is (= '(* 4 (expt t 3)) (g/simplify ((D g) 't))))
      (is (= '(* 12 (expt t 2)) (g/simplify ((D (D g)) 't))))
      (is (= '(* 24 t) (g/simplify ((D (D (D g))) 't))))
      (is (= '(* 24 z) (g/simplify (((g/expt D 3) g) 'z))))
      (is (= '(* 2 (expt s 3)) (g/simplify (f4 's))))
      (is (= '(* 6 (expt s 2)) (g/simplify ((D f4) 's))))
      (is (= '(* 12 s) (g/simplify ((D (D f4)) 's))))
      (is (= 12 (g/simplify ((D (D (D f4))) 's))))
      (is (= 12 (g/simplify (((* D D D) f4) 's))))
      (is (= 12 (g/simplify (((f/compose D D D) f4) 's))))
      (is (= 12 (g/simplify (((g/expt D 3) f4) 's)))))
    (let [fff (fn [x y z] (+ (* x x y)(* y y y z)(* z z z z x)))]
      (is (= '(+ (* x (expt z 4)) (* (expt y 3) z) (* (expt x 2) y))
             (g/simplify (((g/expt D 0) fff) 'x 'y 'z))))
      (is (= '(down
               (+ (expt z 4) (* 2 x y))
               (+ (* 3 (expt y 2) z) (expt x 2))
               (+ (* 4 x (expt z 3)) (expt y 3)))
             (g/simplify (((g/expt D 1) fff) 'x 'y 'z))))
      (is (= '(down
               (down (* 2 y) (* 2 x) (* 4 (expt z 3)))
               (down (* 2 x) (* 6 y z) (* 3 (expt y 2)))
               (down (* 4 (expt z 3)) (* 3 (expt y 2)) (* 12 x (expt z 2))))
             (g/simplify (((g/expt D 2) fff) 'x 'y 'z))))
      (is (= '(down
               (down (down 0 2 0) (down 2 0 0) (down 0 0 (* 12 (expt z 2))))
               (down (down 2 0 0) (down 0 (* 6 z) (* 6 y)) (down 0 (* 6 y) 0))
               (down
                (down 0 0 (* 12 (expt z 2)))
                (down 0 (* 6 y) 0)
                (down (* 12 (expt z 2)) 0 (* 24 x z))))
             (g/simplify (((g/expt D 3) fff) 'x 'y 'z)))))
    (is (= 0 ((D (fn [x] 0)) 'x)))
    (is (= 0 ((D (fn [& xs] 0)) 'x)))))

(deftest literal-functions
  (with-literal-functions [f [g [0 0] 0]]
    (testing "R -> R"
      (is (= '((D f) x) (g/simplify ((D f) 'x))))
      (is (= '((D f) (+ x y)) (g/simplify ((D f) (+ 'x 'y))))))

    (testing "R^2 -> R"
      (is (= '(((partial 0) g) x y) (g/simplify (((partial 0) g) 'x 'y))))
      (is (= '(((partial 1) g) x y) (g/simplify (((partial 1) g) 'x 'y))))
      (is (= '(down (((partial 0) g) x y) (((partial 1) g) x y))
             (g/simplify ((D g) 'x 'y)))))

    (testing "zero-like"
      (is (= 0 ((v/zero-like f) 'x)))
      (is (= 0 ((D (v/zero-like f)) 'x))))))

(deftest complex-derivatives
  (let [i (complex 0 1)
        f (fn [z] (* i (sin (* i z))))]
    (is (= '(* -1 (cosh z))
           (g/simplify ((D f) 'z))))))

(deftest fun-with-operators
  (let [f #(g/expt % 3)]
    (is (= '(+ (* (expt t 3) (cos t)) (* 3 (expt t 2) (sin t)))
           (g/simplify (((* D sin) f) 't))))
    (is (= '(* 3 (expt t 2) (sin t) )
           (g/simplify (((* sin D) f) 't))))))

(deftest vector-calculus
  (let [f (s/up identity sin cos)
        divergence #(fn [t] (reduce + ((D %) t)))
        laplacian #(* (D %) ((g/transpose D) %))]
    (is (= '(up 1 (cos t) (* -1 (sin t))) (g/simplify ((D f) 't))))
    (is (= '(down 1 (cos t) (* -1 (sin t))) (g/simplify (((g/transpose D) f) 't))))
    (is (= 2 (g/simplify (* ((D f) 't) (((g/transpose D) f) 't)))))
    (is (= 2 (g/simplify ((laplacian (s/up identity sin cos)) 't))))
    (is (= '(+ (cos t) (* -1 (sin t)) 1) (g/simplify ((divergence f) 't))))))

(deftest exp-and-log
  (is (= '(/ 1 x) (g/simplify ((D log) 'x)))))

(deftest more-trig
  (let [cot (/ cos sin)
        sec (/ cos)
        csc (/ sin)]
    (is (= '(/ (cos x) (sin x)) (g/simplify (cot 'x))))
    (is (= '(/ -1 (expt (sin x) 2)) (g/simplify ((D cot) 'x))))
    (is (= '(/ -1 (expt (sin x) 2)) (g/simplify ((D (/ tan)) 'x))))
    (is (= '(/ (* -1 (cos x)) (expt (sin x) 2)) (g/simplify ((D csc) 'x))))
    (is (= '(/ (sin x) (expt (cos x) 2)) (g/simplify ((D sec) 'x))))
    (is (= '(/ 1 (+ (expt x 2) 1)) (g/simplify ((D atan) 'x))))
    (is (= '(down (/ x (+ (expt x 2) (expt y 2))) (/ (* -1 y) (+ (expt x 2) (expt y 2))))
           (g/simplify ((D atan) 'y 'x))))))

(deftest alexgian-examples
  (testing "space"
    (let [g (f/literal-function 'g [0 0] 0)
          h (f/literal-function 'h [0 0] 0)]
      (is (= '(+ (((partial 0) g) x y) (((partial 0) h) x y))
             (g/simplify (((partial 0) (+ g h)) 'x 'y))))
      (is (= '(+ (* (((partial 0) g) x y) (h x y)) (* (((partial 0) h) x y) (g x y)))
             (g/simplify (((partial 0) (* g h)) 'x 'y))))
      (is (= '(+ (* (((partial 0) g) x y) (h x y) (expt (g x y) (+ (h x y) -1)))
                 (* (((partial 0) h) x y) (log (g x y)) (expt (g x y) (h x y))))
             (g/simplify (((partial 0) (g/expt g h)) 'x 'y))))))

  (testing "operators"
    (is (= '(down 1 1 1 1 1 1 1 1 1 1)
           (g/simplify ((D +) 'a 'b 'c 'd 'e 'f 'g 'h 'i 'j))))
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
           (g/simplify ((D *) 'a 'b 'c 'd 'e 'f 'g 'h 'i 'j))))
    (is (= '(down (* y (expt x (+ y -1)))
                  (* (log x) (expt x y)))
           (g/simplify ((D expt) 'x 'y))))
    (is (= '(* y (expt x (+ y -1)))
           (g/simplify (((partial 0) expt) 'x 'y))))
    (is (= 2
           (g/simplify (((partial 0) expt) 1 2))))
    (let [pow (fn [x y] (apply * (repeat y x)))]
      (is (= 8 (pow 2 3)))
      (is (= '(expt x 8) (g/simplify (pow 'x 8))))))

  (testing "formatting"
    (let [f2 (fn [x y] (* (sin x) (log y)))
          f3 (fn [x y] (* (tan x) (log y)))
          f4 (fn [x y] (* (tan x) (sin y)))
          f5 (fn [x y] (/ (tan x) (sin y)))]
      (is (= '(down (* (cos x) (log y))
                    (/ (sin x) y))
             (g/simplify ((D f2) 'x 'y))))
      (is (= '(down (/ (log y) (expt (cos x) 2))
                    (/ (tan x) y))
             (g/simplify ((D f3) 'x 'y))))
      (is (= '(down (/ (sin y) (expt (cos x) 2))
                    (/ (* (sin x) (cos y)) (cos x)))
             (g/simplify ((D f4) 'x 'y))))
      (is (= '(down
               (/ 1 (* (expt (cos x) 2) (sin y)))
               (/ (* -1 (sin x) (cos y)) (* (cos x) (expt (sin y) 2))))
             (g/simplify ((D f5) 'x 'y))))))

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
      (let [run (fn [] ((v/within 1e-6) 0.51603111348625 ((D f100e) 6)))]
        #?(:clj
           (is (thrown? IllegalArgumentException (run)))
           :cljs
           ;; The CLJS implementation doesn't have trouble here.
           (is (run))))
      (is ((v/within 1e-6) 0.51603111348625
           ((D (with-meta f100e {:arity [:exactly 1]})) 6))))))

(deftest deep-partials
  (let [f (fn [x y] (+ (g/square x) (g/square (g/square y))))]
    (is (= '((* 2 x)
             (* 2 y)
             (+ (* 4 (expt w 3)) (* 4 w (expt z 2)))
             (+ (* 4 (expt w 2) z) (* 4 (expt z 3))))
           (map g/simplify
                (for [i (range 2)
                      j (range 2)]
                  (((partial i j) f) (s/up 'x 'y) (s/up 'w 'z))))))
    (is (thrown? #?(:clj IllegalArgumentException :cljs js/Error)
                 (((partial 0 1) f) 'x 'y)))))

(deftest derivative-as-operator
  (let [f (f/literal-function 'f [0 0] 0)
        g (f/literal-function 'g (s/up 0 0) 0)
        dX (s/up 'dx 'dy)]
    (is (= '(f x y) (g/simplify (f 'x 'y))))
    (is (= '(g (up (* 3 x) (* 3 y))) (g/simplify (g (* 3 (s/up 'x 'y))))))
    (is (= '(down (down (((partial 0) ((partial 0) f)) x y)
                        (((partial 1) ((partial 0) f)) x y))
                  (down (((partial 1) ((partial 0) f)) x y)
                        (((partial 1) ((partial 1) f)) x y)))
           (g/simplify (((g/expt D 2) f) 'x 'y))))
    (is (= '(down (((partial 0) f) x y) (((partial 1) f) x y))
           (g/simplify ((D f) 'x 'y))))
    (is (= '(+ (* dx (((partial 0) f) x y)) (* dy (((partial 1) f) x y)))
           (g/simplify (* ((D f) 'x 'y) dX))))
    (is (= '(+ (* (expt dx 2) (((partial 0) ((partial 0) f)) x y))
               (* 2 dx dy (((partial 1) ((partial 0) f)) x y))
               (* (expt dy 2) (((partial 1) ((partial 1) f)) x y)))
           (g/simplify (* dX (((g/expt D 2) f) 'x 'y) dX))))))


(deftest taylor
  (is (= '(+ (* (/ 1 6)
                (expt dx 3) (((partial 0) ((partial 0) ((partial 0) f))) (up x y)))
             (* (/ 1 6)
                (expt dx 2) dy (((partial 0) ((partial 0) ((partial 1) f))) (up x y)))
             (* (/ 1 3)
                (expt dx 2) dy (((partial 1) ((partial 0) ((partial 0) f))) (up x y)))
             (* (/ 1 3)
                dx (expt dy 2) (((partial 1) ((partial 0) ((partial 1) f))) (up x y)))
             (* (/ 1 6)
                dx (expt dy 2) (((partial 1) ((partial 1) ((partial 0) f))) (up x y)))
             (* (/ 1 6)
                (expt dy 3) (((partial 1) ((partial 1) ((partial 1) f))) (up x y)))
             (* (/ 1 2)
                (expt dx 2) (((partial 0) ((partial 0) f)) (up x y)))
             (* dx dy (((partial 1) ((partial 0) f)) (up x y)))
             (* (/ 1 2)
                (expt dy 2) (((partial 1) ((partial 1) f)) (up x y)))
             (* dx (((partial 0) f) (up x y)))
             (* dy (((partial 1) f) (up x y)))
             (f (up x y)))
         (->> (d/taylor-series-terms
               (f/literal-function 'f (s/up 0 0) 0)
               (s/up 'x 'y)
               (s/up 'dx 'dy))
              (take 4)
              (reduce +)
              (g/simplify)
              (v/freeze))))

  (testing "eq. 5.291"
    (let [V (fn [[xi eta]] (g/sqrt (+ (g/square (+ xi 'R_0)) (g/square eta))))]
      (is (= '[R_0 xi (/ (expt eta 2) (* 2 R_0))]
             (take 3 (g/simplify (d/taylor-series-terms V (s/up 0 0) (s/up 'xi 'eta)))))))))

(deftest moved-from-structure-and-matrix
  (let [vs (s/up
            (s/up 'vx1 'vy1)
            (s/up 'vx2 'vy2))
        L1 (fn [[v1 v2]]
             (+ (* (/ 1 2) 'm1 (g/square v1))
                (* (/ 1 2) 'm2 (g/square v2))))]
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
                          (matrix/s->m (s/compatible-shape (* v s)) v s))))
          C-general (f/literal-function
                     'C '(-> (UP Real
                                 (UP Real Real)
                                 (DOWN Real Real))
                             (UP Real
                                 (UP Real Real)
                                 (DOWN Real Real))))
          s (s/up 't (s/up 'x 'y) (s/down 'px 'py))]
      (is (= '(matrix-by-rows
               [(((partial 0) C↑0) (up t (up x y) (down px py)))
                (((partial 1 0) C↑0) (up t (up x y) (down px py)))
                (((partial 1 1) C↑0) (up t (up x y) (down px py)))
                (((partial 2 0) C↑0) (up t (up x y) (down px py)))
                (((partial 2 1) C↑0) (up t (up x y) (down px py)))]
               [(((partial 0) C↑1↑0) (up t (up x y) (down px py)))
                (((partial 1 0) C↑1↑0) (up t (up x y) (down px py)))
                (((partial 1 1) C↑1↑0) (up t (up x y) (down px py)))
                (((partial 2 0) C↑1↑0) (up t (up x y) (down px py)))
                (((partial 2 1) C↑1↑0) (up t (up x y) (down px py)))]
               [(((partial 0) C↑1↑1) (up t (up x y) (down px py)))
                (((partial 1 0) C↑1↑1) (up t (up x y) (down px py)))
                (((partial 1 1) C↑1↑1) (up t (up x y) (down px py)))
                (((partial 2 0) C↑1↑1) (up t (up x y) (down px py)))
                (((partial 2 1) C↑1↑1) (up t (up x y) (down px py)))]
               [(((partial 0) C↑2_0) (up t (up x y) (down px py)))
                (((partial 1 0) C↑2_0) (up t (up x y) (down px py)))
                (((partial 1 1) C↑2_0) (up t (up x y) (down px py)))
                (((partial 2 0) C↑2_0) (up t (up x y) (down px py)))
                (((partial 2 1) C↑2_0) (up t (up x y) (down px py)))]
               [(((partial 0) C↑2_1) (up t (up x y) (down px py)))
                (((partial 1 0) C↑2_1) (up t (up x y) (down px py)))
                (((partial 1 1) C↑2_1) (up t (up x y) (down px py)))
                (((partial 2 0) C↑2_1) (up t (up x y) (down px py)))
                (((partial 2 1) C↑2_1) (up t (up x y) (down px py)))])
             (g/simplify ((as-matrix (D C-general)) s)))))))

(deftest taylor-moved-from-series
  (let [taylor-series-expander (fn [f x h]
                                 (((g/exp (* h D)) f) x))]
    (is (= '(+ (* (/ 1 24) (expt dx 4) (sin x))
               (* (/ -1 6) (expt dx 3) (cos x))
               (* (/ -1 2) (expt dx 2) (sin x))
               (* dx (cos x))
               (sin x))
           (v/freeze
            (g/simplify
             (reduce + (series/take 5 (taylor-series-expander g/sin 'x 'dx)))))))
    (is (= '(1
             (* (/ 1 2) dx)
             (* (/ -1 8) (expt dx 2))
             (* (/ 1 16) (expt dx 3))
             (* (/ -5 128) (expt dx 4))
             (* (/ 7 256) (expt dx 5)))
           (v/freeze
            (g/simplify
             (series/take 6 (taylor-series-expander
                             (fn [x] (g/sqrt (+ (v/one-like x) x)))
                             0 'dx))))))))

(deftest derivative-of-matrix
  (let [M (matrix/by-rows [(f/literal-function 'f) (f/literal-function 'g)]
                          [(f/literal-function 'h) (f/literal-function 'k)])]
    (is (= '(matrix-by-rows [(f t) (g t)]
                            [(h t) (k t)])
           (g/simplify (M 't))))
    (is (= '(matrix-by-rows [((D f) t) ((D g) t)]
                            [((D h) t) ((D k) t)])
           (g/simplify ((D M) 't))))
    (is (= '(matrix-by-rows
             [(+ (expt (f t) 2) (expt (h t) 2))
              (+ (* (f t) (g t)) (* (h t) (k t)))]
             [(+ (* (f t) (g t)) (* (h t) (k t)))
              (+ (expt (g t) 2) (expt (k t) 2))])
           (g/simplify ((* (g/transpose M) M) 't))))
    (is (= '(matrix-by-rows [(+ (* 2 ((D f) t) (f t)) (* 2 (h t) ((D h) t)))
                             (+ (* ((D f) t) (g t)) (* (f t) ((D g) t)) (* (h t) ((D k) t)) (* (k t) ((D h) t)))]
                            [(+ (* ((D f) t) (g t)) (* (f t) ((D g) t)) (* (h t) ((D k) t)) (* (k t) ((D h) t)))
                             (+ (* 2 (g t) ((D g) t)) (* 2 (k t) ((D k) t)))])
           (g/simplify ((D (* (g/transpose M) M)) 't))))))

(deftest derivatives-as-values
  (let [cs0 (fn [x] (sin (cos x)))
        cs1 (f/compose sin cos)
        cs2 (comp sin cos)
        y0 (D cs0)
        y1 (D cs1)
        y2 (D cs2)]
    (is (= '(sin (cos x)) (g/simplify (cs0 'x))))
    (is (= '(sin (cos x)) (g/simplify (cs1 'x))))
    (is (= '(sin (cos x)) (g/simplify (cs2 'x))))
    (is (= '(* -1 (sin x) (cos (cos x))) (g/simplify ((D cs0) 'x))))
    (is (= '(* -1 (sin x) (cos (cos x))) (g/simplify ((D cs1) 'x))))
    (is (= '(* -1 (sin x) (cos (cos x))) (g/simplify ((D cs2) 'x))))
    (is (= '(* -1 (sin x) (cos (cos x))) (g/simplify (y0 'x))))
    (is (= '(* -1 (sin x) (cos (cos x))) (g/simplify (y1 'x))))
    (is (= '(* -1 (sin x) (cos (cos x))) (g/simplify (y2 'x)))))
  (let [unity (reduce + (map g/square [sin cos]))
        dU (D unity)]
    (is (= 1 (g/simplify (unity 'x))))
    (is (= 0 (g/simplify (dU 'x)))))
  (let [odear (fn [z] ((D (f/compose sin cos)) z))]
    (is (= '(* -1 (sin x) (cos (cos x))) (g/simplify (odear 'x))))))
