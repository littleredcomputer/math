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
  (:refer-clojure :exclude [+ - * / partial])
  (:require [clojure.test :refer [is deftest testing use-fixtures]]
            [same :refer [ish? with-comparator]
             #?@(:cljs [:include-macros true])]
            [sicmutils.abstract.function :as af #?@(:cljs [:include-macros true])]
            [sicmutils.calculus.derivative :as d :refer [D partial]]
            [sicmutils.complex :as c]
            [sicmutils.differential :as sd]
            [sicmutils.function :as f]
            [sicmutils.generic :as g :refer [acos asin atan cos sin tan
                                             cot sec csc
                                             log exp expt + - * /]]
            [sicmutils.matrix :as matrix]
            [sicmutils.operator :as o]
            [sicmutils.series :as series]
            [sicmutils.simplify :refer [hermetic-simplify-fixture]]
            [sicmutils.structure :as s]
            [sicmutils.util :as u]
            [sicmutils.value :as v]))

(use-fixtures :each hermetic-simplify-fixture)

(deftest fn-iperturbed-tests
  (testing "tag-active? responds appropriately"
    (let [tag 0
          f   (fn [x] (fn [g] (g x)))
          Df  (-> (f (sd/bundle-element 'x 1 tag))
                  (sd/extract-tangent tag))]
      (is (not (sd/tag-active? tag))
          "Outside the context of a derivative, the tag is not marked as
          active.")

      (is (= '(* 3 (expt x 2))
             (g/simplify
              (Df (fn [x]
                    (is (sd/tag-active? tag)
                        "Df is a function looking to extract `tag`, so inside a
                        call to Df the `tag` IS active.")
                    (g/cube x)))))))))

(deftest basic-D-tests
  (testing "D of linear returns slope"
    (is (= 2 ((D #(* 2 %)) 1)))
    (is (= 2 ((D #(* 2 %)) 'w))))

  (testing "square, cube"
    (is (= (+ 'z 'z)
           ((D g/square) 'z)))
    (is (= (* 3 (g/expt 'y 2))
           ((D #(g/expt % 3)) 'y))))

  (is (= (/ 1 (g/expt (cos 'x) 2))
         ((D tan) 'x)))

  (testing "D of a fn returning a structure returns the componentwise
            derivative"
    (is (= (s/up 2 (+ 't 't))
           ((D #(s/up (* 2 %) (* % %))) 't)))

    (is (= (s/up (- (sin 't)) (cos 't))
           ((D #(s/up (cos %) (sin %))) 't))))

  (testing "trig derivatives"
    (is (= '(/ 1 (sqrt (+ (* -1 (expt x 2)) 1)))
           (g/simplify ((D asin) 'x))))

    (is (= '(/ -1 (sqrt (+ (* -1 (expt x 2)) 1)))
           (g/simplify ((D acos) 'x)))))

  (testing "log"
    (is (= '(/ 1 x)
           (g/simplify ((D log) 'x)))))

  (testing "chain rule"
    (is (= (* (cos (* 2 'u)) 2)
           ((D #(sin (* 2 %))) 'u)))

    (let [s (fn [t] (g/sqrt t))
          u (fn [t] (g/expt (- (* 3 (s t)) 1) (/ 2 3)))
          y (fn [t] (/ (+ (u t) 2) (- (u t) 1)))]
      (is (ish? (/ -1 18)
                ((D y) 9)))))

  (testing "structural-functions"
    (is (= '(up (cos t) (* -1 (sin t)))
           (g/simplify ((D (s/up sin cos)) 't)))))

  (testing "structure / x works"
    (letfn [(f [x]
              (g// (s/up 1 2 3) x))]
      (is (= '(up (/ -1 (expt x 2))
                  (/ -2 (expt x 2))
                  (/ -3 (expt x 2)))
             (g/simplify
              ((D f) 'x)))))))

(deftest derivative-return-tests
  (testing "Series, PowerSeries"
    (let [series-D ((D (fn [x] (series/exp-series x))) 'x)]
      (is (series/series? series-D)
          "we get a proper series back out")

      (is (not (series/power-series? series-D))
          "the result is NOT a power series! It's already been applied.")

      (is (= '(0 1 x (* (/ 1 2) (expt x 2)))
             (v/freeze
              (g/simplify (take 4 series-D))))
          "which is why the first element is 0.")

      (is (series/power-series? (D series/exp-series))
          "Derivative of a [[series/PowerSeries]] returns a
        new [[series/PowerSeries]].")

      (is (= (take 10 series/exp-series)
             (take 10 (D series/exp-series)))
          "derivative of exp matches (and you can get it via the `D`
        operator!)")))

  (testing "Clojure Maps"
    (letfn [(f [x]
              {:x x
               :square (g/square x)
               :cube   (g/cube x)})]
      (is (= {:x 1
              :square '(* 2 x)
              :cube '(* 3 (expt x 2))}
             (g/simplify ((D f) 'x)))
          "derivative of a fn returning a map returns the derivative for each
          value")))

  (testing "Operator"
    (letfn [(f [x]
              (o/make-operator (g/* x g/sin) 'D-op))]
      (is (o/operator? ((D f) 'x))
          "if f returns an operator, (D f) does too.")
      (is (= '(sin y)
             (v/freeze
              (((D f) 'x) 'y)))
          "derivative pushes into the operator's fn.."))))

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
                 (g/simplify
                  (((* (partial i) (partial j)) f) 'x 'y)))))
          "generate matrix of second partials through operator *")

      (is (= '[[(* 2 a) b]
               [b (* 2 c)]]
             (for [i (range 2)]
               (for [j (range 2)]
                 (g/simplify
                  (((f/compose (partial i) (partial j)) f) 'x 'y)))))
          "matrix of second partials through explicit operator composition"))

    (let [F (fn [a b]
              (fn [[x y]]
                (s/up (* a x) (* b y))))]
      (is (= (s/up 'x 'y)
             ((F 1 1) (s/up 'x 'y))))

      (is (= (s/up (* 2 'x) (* 3 'y))
             ((F 2 3) (s/up 'x 'y))))

      (is (= (s/up 'x 0)
             ((((partial 0) F) 1 1) (s/up 'x 'y))))

      (is (= (s/up 0 'y)
             ((((partial 1) F) 1 1) (s/up 'x 'y))))

      (is (= (s/down (s/up 'x 0) (s/up 0 'y))
             (((D F) 1 1) (s/up 'x 'y)))))))

(defn- δ [η]
  (fn [f]
    ;; Define g(ε) as in Eq. 1.22; then δ_η f[q] = Dg(0)
    (fn [q]
      (let [g (fn [ε]
                (f (+ q (* ε η))))]
        ((D g) 0)))))

(deftest delta-eta-tests
  (af/with-literal-functions [η q f g]
    (let [I (fn [q] (fn [t] (q t)))
          F (fn [q] (fn [t] (f (q t))))
          G (fn [q] (fn [t] (g (q t))))
          q+εη (+ q (* 'ε η))
          g (fn [ε] (+ q (* ε η)))
          δη (δ η)
          δηI (δη I)
          δηIq (δηI q)
          δηFq ((δη F) q)
          φ (fn [f] (fn [q] (fn [t] ((af/literal-function 'φ) ((f q) t)))))]
      (is (= '((D f) t) (g/simplify ((D f) 't))))
      (is (= '(+ (* ε (η t)) (q t)) (g/simplify (q+εη 't))))
      (is (= '(+ (* ε (η t)) (q t)) (g/simplify ((g 'ε) 't))))
      (is (= '(η a) (g/simplify (((D g) 'dt) 'a))))
      (is (= '(η t) (g/simplify (δηIq 't))))
      (is (= '(f (q t)) (g/simplify ((F q) 't))))
      (is (= '(* (η t) ((D f) (q t))) (g/simplify (δηFq 't))))

      (testing "sum rule for variation: δ(F+G) = δF + δG"
        (is (= '(+ (* (η t) ((D f) (q t)))
                   (* (η t) ((D g) (q t))))
               (g/simplify (((δη (+ F G)) q) 't)))))


      (testing "scalar product rule for variation: δ(cF) = cδF"
        (is (= '(* c (η t) ((D f) (q t))) (g/simplify (((δη (* 'c F)) q) 't)))))


      (testing "product rule for variation: δ(FG) = δF G + F δG"
        (is (= (g/simplify (+ (* (((δη F) q) 't) ((G q) 't))
                              (* ((F q) 't) (((δη G) q) 't))))
               (g/simplify (((δη (* F G)) q) 't)))))

      (testing "path-independent chain rule for variation"
        (is (= '(φ (f (q t)))
               (g/simplify (((φ F) q) 't))))
        (is (= '(* (η t) ((D f) (q t)) ((D φ) (f (q t))))
               (g/simplify (((δη (φ F)) q) 't))))))))

(deftest exponentiation-and-composition
  (let [ff (fn [x y z]
             (+ (* x x y)
                (* y y z)
                (* z z x)))]
    (is (= '(down
             (down (* 2 y) (* 2 x) (* 2 z))
             (down (* 2 x) (* 2 z) (* 2 y))
             (down (* 2 z) (* 2 y) (* 2 x)))
           (g/simplify (((g/expt D 2) ff) 'x 'y 'z)))
        "second derivative via nesting of D")

    (is (= (((* D D) ff) 'x 'y 'z)
           (((g/expt D 2) ff) 'x 'y 'z))
        "expt matches * behavior")

    (testing "D is an operator, so expt == compose"
      (is (= (((f/compose D D) ff) 'x 'y 'z)
             (((g/expt D 2) ff) 'x 'y 'z)))

      (is (= (((* D D D) ff) 'x 'y 'z)
             (((g/expt D 3) ff) 'x 'y 'z)))

      (is (= (((f/compose D D D) ff) 'x 'y 'z)
             (((g/expt D 3) ff) 'x 'y 'z)))))

  (testing "issue #9 regression. These check that composition and exponentiation
  work properly on various function arities."
    (let [g (fn [z] (* z z z z))]
      (is (= '(expt t 4) (g/simplify (g 't))))
      (is (= '(* 4 (expt t 3)) (g/simplify ((D g) 't))))
      (is (= '(* 12 (expt t 2)) (g/simplify ((D (D g)) 't))))

      (testing "expt matches explicit compose"
        (is (= '(* 24 t) (g/simplify ((D (D (D g))) 't))))
        (is (= '(* 24 z) (g/simplify (((g/expt D 3) g) 'z))))))

    (testing "another test of explicit composition vs expt, *"
      (let [f4 (fn [x]
                 (+ (* x x x)
                    (* x x x)))]
        (is (= '(* 2 (expt s 3)) (g/simplify (f4 's))))
        (is (= '(* 6 (expt s 2)) (g/simplify ((D f4) 's))))
        (is (= '(* 12 s) (g/simplify ((D (D f4)) 's))))
        (is (= 12 (g/simplify ((D (D (D f4))) 's))))

        (testing "*, compose, expt match"
          (is (= 12 (g/simplify (((* D D D) f4) 's))))
          (is (= 12 (g/simplify (((f/compose D D D) f4) 's))))
          (is (= 12 (g/simplify (((g/expt D 3) f4) 's)))))))

    (let [fff (fn [x y z]
                (+ (* x x y)
                   (* y y y z)
                   (* z z z z x)))]
      (testing "identity, first, second, third multivariate derivatives via expt"
        (is (= '(+ (* x (expt z 4))
                   (* (expt y 3) z)
                   (* (expt x 2) y))
               (g/simplify
                (((g/expt D 0) fff) 'x 'y 'z))))

        (is (= '(down
                 (+ (expt z 4) (* 2 x y))
                 (+ (* 3 (expt y 2) z) (expt x 2))
                 (+ (* 4 x (expt z 3)) (expt y 3)))
               (g/simplify
                (((g/expt D 1) fff) 'x 'y 'z))))

        (is (= '(down
                 (down (* 2 y) (* 2 x) (* 4 (expt z 3)))
                 (down (* 2 x) (* 6 y z) (* 3 (expt y 2)))
                 (down (* 4 (expt z 3))
                       (* 3 (expt y 2))
                       (* 12 x (expt z 2))))
               (g/simplify
                (((g/expt D 2) fff) 'x 'y 'z))))

        (is (= '(down
                 (down (down 0 2 0) (down 2 0 0) (down 0 0 (* 12 (expt z 2))))
                 (down (down 2 0 0) (down 0 (* 6 z) (* 6 y)) (down 0 (* 6 y) 0))
                 (down
                  (down 0 0 (* 12 (expt z 2)))
                  (down 0 (* 6 y) 0)
                  (down (* 12 (expt z 2)) 0 (* 24 x z))))
               (g/simplify (((g/expt D 3) fff) 'x 'y 'z))))))

    (testing "derivative of constant == 0 whatever the arity."
      (is (= 0 ((D (fn [x] 0)) 'x)))
      (is (= 0 ((D (fn [& xs] 0)) 'x))))))

(deftest literal-function-tests
  (af/with-literal-functions [f [g [0 0] 0]]
    (testing "R -> R"
      (is (= '((D f) x) (g/simplify ((D f) 'x))))
      (is (= '((D f) (+ x y)) (g/simplify ((D f) (+ 'x 'y))))))

    (testing "R^2 -> R"
      (is (= '(((partial 0) g) x y)
             (g/simplify (((partial 0) g) 'x 'y))))

      (is (= '(((partial 1) g) x y)
             (g/simplify (((partial 1) g) 'x 'y))))

      (is (= '(down (((partial 0) g) x y)
                    (((partial 1) g) x y))
             (g/simplify ((D g) 'x 'y)))))

    (testing "D of zero-like"
      (is (= 0 ((v/zero-like f) 'x)))
      (is (= 0 ((D (v/zero-like f)) 'x))))))

(deftest complex-derivatives
  (let [f (fn [z] (* c/I (sin (* c/I z))))]
    (is (= '(* -1 (cosh z))
           (g/simplify ((D f) 'z))))))

(deftest operator-tests
  (testing "operator multiplication by fn == "
    (is (= '(+ (* (expt t 3) (cos t))
               (* 3 (expt t 2) (sin t)))
           (g/simplify (((* D sin) g/cube) 't)))
        "D * fn == multiplies before D"))

  (is (= '(* 3 (expt t 2) (sin t))
         (g/simplify (((* sin D) g/cube) 't)))
      "fn * D == multiplies after D"))

(deftest vector-calculus
  (let [f (fn [[x y z]]
            (s/up (identity x) (sin y) (cos z)))
        xyz (s/up 'x 'y 'z)]
    (is (= '(down
             (up 1 0 0)
             (up 0 (cos y) 0)
             (up 0 0 (* -1 (sin z))))
           (g/simplify ((D f) xyz))))
    (is (= '(up
             (up 1 0 0)
             (up 0 (cos y) 0)
             (up 0 0 (* -1 (sin z))))
           (g/simplify ((d/Grad f) xyz))))

    (is (= '(up 0 (* -1 (sin y)) (* -1 (cos z)))
           (g/simplify ((d/Lap f) xyz))))

    (is (= '(+ (cos y) (* -1 (sin z)) 1)
           (g/simplify ((d/Div f) (s/up 'x 'y 'z)))))))

(deftest vector-operator-tests
  (testing "symbolic representations of Div, Curl, Grad, Lap are correct"
    (let [F (af/literal-function 'F '(-> (UP Real Real Real) Real))
          A (af/literal-function 'A '(-> (UP Real Real Real)
                                         (UP Real Real Real)))]
      (is (= '(up (((partial 0) F) (up x y z))
                  (((partial 1) F) (up x y z))
                  (((partial 2) F) (up x y z)))
             (g/simplify
              ((d/Grad F) (s/up 'x 'y 'z)))))

      (is (= '(+ (((partial 0) A↑0) (up x y z))
                 (((partial 1) A↑1) (up x y z))
                 (((partial 2) A↑2) (up x y z)))
             (g/simplify
              ((d/Div A) (s/up 'x 'y 'z)))))

      (is (= '(up (+ (((partial 1) A↑2) (up x y z))
                     (* -1 (((partial 2) A↑1) (up x y z))))
                  (+ (((partial 2) A↑0) (up x y z))
                     (* -1 (((partial 0) A↑2) (up x y z))))
                  (+ (((partial 0) A↑1) (up x y z))
                     (* -1 (((partial 1) A↑0) (up x y z)))))
             (g/simplify
              ((d/Curl A) (s/up 'x 'y 'z)))))

      (is (= '(+ (((partial 0) ((partial 0) F)) (up x y z))
                 (((partial 1) ((partial 1) F)) (up x y z))
                 (((partial 2) ((partial 2) F)) (up x y z)))
             (g/simplify
              ((d/Lap F) (s/up 'x 'y 'z)))))))

  (testing "Div, Curl, Grad, Lap identities"
    (let [F (af/literal-function 'F '(-> (UP Real Real Real) Real))
          G (af/literal-function 'G '(-> (UP Real Real Real) Real))
          A (af/literal-function 'A '(-> (UP Real Real Real)
                                         (UP Real Real Real)))]
      (is (= '(up 0 0 0)
             (g/simplify
              ((d/Curl (d/Grad F)) (s/up 'x 'y 'z))))
          "Curl of the gradient is zero!")

      (is (= 0 (g/simplify
                ((d/Div (d/Curl A)) (s/up 'x 'y 'z))))
          "divergence of curl is 0.")

      (is (= 0 (g/simplify
                ((- (d/Div (d/Grad F))
                    (d/Lap F))
                 (s/up 'x 'y 'z))))
          "The Laplacian of a scalar field is the div of its gradient.")

      (is (= '(up 0 0 0)
             (g/simplify
              ((- (d/Curl (d/Curl A))
                  (- (d/Grad (d/Div A)) (d/Lap A)))
               (s/up 'x 'y 'z)))))

      (is (= 0 (g/simplify
                ((- (d/Div (* F (d/Grad G)))
                    (+ (* F (d/Lap G))
                       (g/dot-product (d/Grad F)
                                      (d/Grad G))))
                 (s/up 'x 'y 'z))))))))

(deftest more-trig-tests
  (testing "cotangent"
    (is (= '(/ (cos x) (sin x))
           (g/simplify (cot 'x))))

    (is (= '(/ -1 (expt (sin x) 2))
           (g/simplify ((D cot) 'x))))

    (is (= '(/ -1 (expt (sin x) 2))
           (g/simplify ((D (/ tan)) 'x)))
        "cotangent defined as inverse tangent"))

  (testing "secant"
    (is (= '(/ (sin x) (expt (cos x) 2))
           (g/simplify ((D sec) 'x)))))

  (testing "cosecant"
    (is (= '(/ (* -1 (cos x)) (expt (sin x) 2))
           (g/simplify ((D csc) 'x)))))

  (testing "arctangent"
    (is (= '(/ 1 (+ (expt x 2) 1))
           (g/simplify ((D atan) 'x))))

    (is (= '(down (/ x (+ (expt x 2) (expt y 2)))
                  (/ (* -1 y) (+ (expt x 2) (expt y 2))))
           (g/simplify ((D atan) 'y 'x)))))

  (testing "hyperbolic trig"
    (is (= '(cosh x) (g/simplify ((D g/sinh) 'x))))
    (is (= '(sinh x) (g/simplify ((D g/cosh) 'x))))

    (is (= '(sinh x)
           (g/simplify (((g/square D) g/sinh) 'x)))
        "sinh round trips after two derivatives")

    (testing "tanh"
      (is (= '(/ (+ (expt (cosh x) 2)
                    (* -1 (expt (sinh x) 2)))
                 (expt (cosh x) 2))
             (g/simplify ((D g/tanh) 'x))))

      (let [l (D g/tanh)
            r (- 1 (g/square g/tanh))]
        (is (zero? (g/simplify ((- l r) 'x)))
            "This style uses function arithmetic and applies 'x at the
            end.")))))

(deftest alexgian-examples
  (testing "space"
    (let [g (af/literal-function 'g [0 0] 0)
          h (af/literal-function 'h [0 0] 0)]
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
      (is (= '(down (* (log y) (cos x))
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

  (testing "D can handle functions of varying arities"
    (let [f100dd (fn [x n acc]
                   (if (v/zero? n)
                     acc
                     (recur x (dec n) (sin (+ x acc)))))
          f100d  (fn [x] (f100dd x 100 x))
          f100e  (fn f100e
                   ([x] (f100e x 100 x))
                   ([x n acc]
                    (if (v/zero? n)
                      acc
                      (recur x (dec n) (sin (+ x acc))))))
          f100ea (f/with-arity f100e [:exactly 1])
          expected 0.51603111348625]
      (is (ish? expected ((D f100d) 6)))
      (is (ish? expected ((D f100e) 6)))
      (is (ish? expected ((D f100ea) 6))))))

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
  (let [f (af/literal-function 'f [0 0] 0)
        g (af/literal-function 'g (s/up 0 0) 0)
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
         (->> (d/taylor-series
               (af/literal-function 'f (s/up 0 0) 0)
               (s/up 'x 'y)
               (s/up 'dx 'dy))
              (take 4)
              (reduce +)
              (g/simplify)
              (v/freeze))))

  (testing "eq. 5.291"
    (let [V  (fn [[xi eta]]
               (g/sqrt (+ (g/square (+ xi 'R_0))
                          (g/square eta))))
          x  (s/up 0 0)
          dx (s/up 'xi 'eta)]
      (is (= '[R_0 xi (/ (expt eta 2) (* 2 R_0))]
             (->> (d/taylor-series V x dx)
                  (g/simplify)
                  (take 3)))))))

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
          C-general (af/literal-function
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
  (let [simp4 (fn [x] (g/simplify (take 4 x)))
        V (series/series g/sin g/cos g/tan)]

    (testing "derivatives"
      (is (= '[(sin t) (cos t) (tan t) 0]
             (simp4 (V 't))))
      (is (= '[(cos t) (* -1 (sin t)) (/ 1 (expt (cos t) 2)) 0]
             (simp4 ((D V) 't)))))

    (testing "f -> Series"
      (let [F (fn [k] (series/series
                       (fn [t] (g/* k t))
                       (fn [t] (g/* k k t))))]
        (is (= '((* q z) (* (expt q 2) z) 0 0) (simp4 ((F 'q) 'z))))
        (is (= '(z (* 2 q z) 0 0) (simp4 (((D F) 'q) 'z)))))))

  (is (= '(+ (* (/ 1 24) (expt dx 4) (sin x))
             (* (/ -1 6) (expt dx 3) (cos x))
             (* (/ -1 2) (expt dx 2) (sin x))
             (* dx (cos x))
             (sin x))
         (v/freeze
          (g/simplify
           (-> (d/taylor-series g/sin 'x 'dx)
               (series/sum 4))))))
  (is (= '(1
           (* (/ 1 2) dx)
           (* (/ -1 8) (expt dx 2))
           (* (/ 1 16) (expt dx 3))
           (* (/ -5 128) (expt dx 4))
           (* (/ 7 256) (expt dx 5)))
         (v/freeze
          (g/simplify
           (take 6 (d/taylor-series
                    (fn [x] (g/sqrt (+ (v/one-like x) x)))
                    0 'dx)))))))

(deftest derivative-of-matrix
  (let [M (matrix/by-rows [(af/literal-function 'f) (af/literal-function 'g)]
                          [(af/literal-function 'h) (af/literal-function 'k)])]
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
    (is (= '(matrix-by-rows
             [(+ (* 2 (f t) ((D f) t)) (* 2 (h t) ((D h) t)))
              (+ (* (f t) ((D g) t)) (* (g t) ((D f) t)) (* (h t) ((D k) t)) (* (k t) ((D h) t)))]
             [(+ (* (f t) ((D g) t)) (* (g t) ((D f) t)) (* (h t) ((D k) t)) (* (k t) ((D h) t)))
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
    (is (= '(* -1 (cos (cos x)) (sin x)) (g/simplify ((D cs0) 'x))))
    (is (= '(* -1 (cos (cos x)) (sin x)) (g/simplify ((D cs1) 'x))))
    (is (= '(* -1 (cos (cos x)) (sin x)) (g/simplify ((D cs2) 'x))))
    (is (= '(* -1 (cos (cos x)) (sin x)) (g/simplify (y0 'x))))
    (is (= '(* -1 (cos (cos x)) (sin x)) (g/simplify (y1 'x))))
    (is (= '(* -1 (cos (cos x)) (sin x)) (g/simplify (y2 'x)))))
  (let [unity (reduce + (map g/square [sin cos]))
        dU (D unity)]
    (is (= 1 (g/simplify (unity 'x))))
    (is (= 0 (g/simplify (dU 'x)))))
  (let [odear (fn [z] ((D (f/compose sin cos)) z))]
    (is (= '(* -1 (cos (cos x)) (sin x)) (g/simplify (odear 'x))))))


;; Tests from the refman that came about while implementing various derivative
;; and operator functions.

(deftest refman-tests
  (testing "o/expn expansion of `D`"
    (let [f     (af/literal-function 'f)
          ->seq (comp v/freeze g/simplify #(take 10 %))]
      (is (= (->seq ((series/->function (((o/exp D) f) 'x)) 'dx))
             (->seq (((o/exp (g/* 'dx D)) f) 'x)))
          "Multiplying (* dx D) is identical to NOT doing that, and then
          promoting the series back to a power series in `dx` after the fact.")

      (let [expected '((f x)
                       0
                       (* (expt dx 2) ((D f) x))
                       0
                       (* (/ 1 2) (expt dx 4) (((expt D 2) f) x))
                       0
                       (* (/ 1 6) (expt dx 6) (((expt D 3) f) x))
                       0
                       (* (/ 1 24) (expt dx 8) (((expt D 4) f) x))
                       0)]
        (is (= expected
               (->seq (((o/expn (g/* (g/square 'dx) D) 2) f) 'x)))
            "power series of f at x with increment dx, expanded as `x^2`. If you
            call `expn` with an exponent, you also have to exponentiate `'dx`.
            This is confusing! See the next test for another example.")

        (let [expanded   (((o/expn D 2) f) 'x)
              f-expanded (series/->function expanded)]
          (is (= expected (->seq (f-expanded 'dx)))
              "`(o/expn D 2)` returns an expanded taylor series; if AFTER
              application to 'x, you turn it back into a function, it becomes a
              function of `(g/square dx)` (compare to previous test.)")))))

  (testing "d/taylor-series"
    (is (ish? (take 8 series/cos-series)
              (take 8 (d/taylor-series g/cos 0 1)))
        "The taylor series expansion of g/cos around x=0 with dx=1 should be the
        original cos series."))

  (testing "taylor series approximations"
    (let [cos-approx (into [] (comp (take 10)
                                    (map u/double))
                           (series/partial-sums
                            (d/taylor-series g/cos 0 1)))]
      (is (ish? [1.0 1.0
                 0.5 0.5
                 0.5416666666666667 0.5416666666666667
                 0.5402777777777777 0.5402777777777777
                 0.5403025793650794 0.5403025793650794]
                cos-approx)
          "The estimates should approach cos(1)")

      (with-comparator (v/within 1e-6)
        (letfn [(via-series [x]
                  (u/double
                   (-> (d/taylor-series g/cos 0 x)
                       (series/sum 10))))]
          (testing "The taylor series approximation of `g/cos` around 1 returns
          fairly accurate estimates of Math/cos at a few points."
            (is (ish? (Math/cos 1) (via-series 1)))
            (is (ish? (Math/cos 0.6) (via-series 0.6)))))))))

(deftest higher-order-fn-tests
  (letfn [(f [x]
            (fn [y z]
              (g/* x y z)))]
    (is (= 12
           (((D f) 2) 3 4)
           (((d/partial 0) g/*) 2 3 4))
        "D should be able to handle curried functions. This is curried
        multiplication."))

  (is (= '(* -1 (sin t))
         (g/simplify
          (((D (fn [eps]
                 (fn [t]
	                 ((d/D (g/* g/cos eps)) t))))
            'e)
           't)))
      "another nesting test from deriv.scm."))

#?(:clj
   ;; NOTE these are disabled for cljs because they force themselves into ratio
   ;; arithmetic!
   (deftest newton-raphson-sqrt
     (testing "autodiff works through arbitrary optimization loops!"
       (with-comparator (v/within 1e-8)
         (letfn [(nr-sqrt [x]
                   (loop [y (- (+ x 1.0) x)]
                     (let [y' (- y (/ (- (g/square y) x)
                                      (+ y y)))]
                       (if (neg? (compare (g/abs (- y y')) 1e-8))
                         y
                         (recur y')))))]
           (testing "sqrt works (switch to generative tests!)"
             (is (ish? 2 (nr-sqrt 4)))
             (is (ish? 3 (nr-sqrt 9)))
             (is (ish? 4 (nr-sqrt 16))))

           (testing "D(sqrt(x)) == 1 / {2 * sqrt(x)}"
             (is (ish? (/ 1 4) ((D nr-sqrt) 4)))
             (is (ish? (/ 1 6) ((D nr-sqrt) 9)))
             (is (ish? (/ 1 8) ((D nr-sqrt) 16))))

           (testing "D(D(sqrt(x))) == (-1 / {4 * sqrt(x)^3})"
             (is (ish? (/ -1 32) (((g/square D) nr-sqrt) 4)))
             (is (ish? (/ -1 108) (((g/square D) nr-sqrt) 9)))
             (is (ish? (/ -1 256) (((g/square D) nr-sqrt) 16)))))))))

(deftest amazing-bug
  (testing "alexey's amazing bug"
    (let [shift (fn [offset]
                  (fn [g]
                    (fn [a]
                      (g (+ a offset)))))
          f-hat ((D shift) 3)]
      ;; This is the example that triggered [Manzyuk et al.
      ;; 2019](https://arxiv.org/pdf/1211.4892.pdf) and the research that led to
      ;; the current (0.15.0) SICMUtils implementation of [[D]].
      ;;
      ;; [[D]] does its work by generating a function that lifts its input into
      ;; a tangent space tagged with a unique `tag`. Multiple, nested calls
      ;; to [[D]] generate unique tags; but if calling `(D f)` produces ANOTHER
      ;; function that captures its input, you can trick a non-careful
      ;; implementation into confusing tangents that are supposed to be
      ;; associated with distinct tags.
      ;;
      ;; Forgetting how it works, note that the derivative of a higher-order
      ;; function `shift`:
      ;;
      ;; (D shift) == (D (fn [offset] (fn [g] (fn [a] (g (+ a offset))))))
      ;;
      ;; should act like the partial derivative of the first argument of the
      ;; equivalent multi-argument function:
      ;;
      ;; (D shift) == ((partial 0) (fn [offset g a] (g (+ a offset))))
      ;;           == (fn [offset g a] ((D g) (+ a offset)))
      ;;
      ;; Of course Alexey chose the example to work out nicely after applying
      ;; the chain rule, by passing in `x` == 3 and `g` == `exp` ( `(D exp)` ==
      ;; `exp` ):
      ;;
      ;; (fn [a] (exp (+ a 3)))
      ;;
      ;; And indeed it is:
      (is (= (exp 8) ((f-hat exp) 5))
          "Nothing tough expected in this case.")

      ;; What is the Amazing Bug?
      (comment
        (is (= 0 ((f-hat (f-hat exp)) 5))
            "IN the bug version, this passed!"))

      ;; The correct answer is `(exp 11)`; each nested invocation should simply
      ;; add `3` to the argument. Why would we see a return of 0?
      ;;
      ;; The problem is that `(D f)` is a function that takes an argument, and
      ;; RETURNS a function with a captured tag (say, `0`). The buggy approach
      ;; to a functional return value was to re-wrap the returned function in a
      ;; new function that extracted the original tag. That code
      ;; lived [here](https://github.com/sicmutils/sicmutils/blob/9b4f9c5983cd0f9b209ef27036bc2dbb8f7ffb1c/src/sicmutils/calculus/derivative.cljc#L233).
      ;;
      ;; The reason this is a bug is that both instances of `f-hat` attempted to
      ;; extract the same tag `0`. The outer call saw no tangent associated with
      ;; tag `0` (it was already extracted!), and returned the default value of
      ;; `0.`
      ;;
      ;; The fixed implementation for a functional return from `(D f)`
      ;; responsible for extracting `tag` remaps any instance of `tag` in the
      ;; function's input to some fresh tag before extracting `tag`, then back
      ;; from `fresh` to `tag` after extraction. This leaves tag `0` available
      ;; for _both_ nested calls, but prevents them from getting confused inside
      ;; the body of the other `f-hat` call:

      (is (= (exp 11) ((f-hat (f-hat exp)) 5))
          "This case is susceptible to tag confusion, if `f-hat` uses the same
          tag for every invocation. The inner `f-hat` extracts the tangent of
          that tag; when the outer call tries to extract it it finds only
          `0`.")

      ;; I'll spell this out and point out where the tags get confused. Remember
      ;; that `f-hat` == `((D f) 3)` is:
      ;;
      ;; (fn [g] (fn [a] (g (+ a 3))))
      ;;
      ;; So
      ;;
      ;; ((f-hat (f-hat exp)) 5)
      ;;
      ;; expands out in steps like this:
      (comment
        (let [f-hat   (fn [g]
                        ;; tags `g` with `0`, extracts at the end.
                        (fn [a] (g (+ a 3))))
              inner-f (f-hat exp)
              outer-f (f-hat inner-f)]
          (outer-f 5))

        ;; sub in `f-hat` definition everywhere:
        (let [inner-f ((fn [g1]
                         ;; tags g1 with `0`
                         (fn [a] (g1 (+ a 3)))) exp)
              outer-f ((fn [g2]
                         ;; tags g2 with `0`
                         (fn [b] (g2 (+ b 3)))) inner-f)]
          (outer-f 5))

        ;; sub `exp` into `inner-f`:
        (let [inner-f (fn [a]
                        ;; tags `a` with `0` (in the old implementation!)
                        (exp (+ a 3)))
              outer-f ((fn [g2]
                         ;; tags g2 with `0`
                         (fn [b] (g2 (+ b 3)))) inner-f)]
          (outer-f 5))

        ;; sub `inner-f` into `outer-f`:
        (let [outer-f (fn [a]
                        ;; tags `a` with `0`
                        (let [inner-f (fn [b]
                                        ;; tags `b` with `0` (pushed down from `g2`)
                                        (exp (+ b 3)))]
                          (inner-f (+ a 3))))]
          (outer-f 5))

        ;; This is the point where the tag confusion comes in! Sub in `5` for
        ;; `a` (I'll write the perturbed argument using a vector of the
        ;; form [primal, (tangent,tag)], like `[5, (1,0)]`):
        (let [inner-f (fn [b]
                        ;; tags `b` with `0`
                        (exp (+ b 3)))]
          ;; extract `0` tag on the way out
          (inner-f (+ [5, (1,0)] 3)))

        ;; final substitution (remember that when you "tag" a dual number
        ;; like [5, (1,0)] with the same tag, you're adding 5 + 1*tag + 1*tag ==
        ;; 5 + 2*tag)

        ;; extract `0` tag on the way out
        ;; extract `0` tag on the way out
        (exp (+ [(+ 5 3), (2, 0)] 3))

        ;; At this point it doesn't even matter what's going on inside the
        ;; function. the double "extract 0" is the problem here, and also the
        ;; fact that the two tangent pieces added together. The tag gets killed
        ;; on the inner extraction, so the outer one finds `0` for the tangent
        ;; component of the now-missing tag.

        ;; If the tags had been distinct, this would have been the final step:
        (let [inner-f (fn [b]
                        ;; tags `b` with `1`
                        (exp (+ b 3)))]
          ;; extract `0` tag on the way out
          (inner-f (+ [(+ 5 3), (1,0)] 3)))

        ;; extract `0` tag on the way out
        ;; extract `1` tag on the way out
        (exp (+ [[(+ 5 3), (1,0)], (1, 1)] 3))

        ;; group the perturbations:

        ;; extract `0` tag on the way out
        ;; extract `1` tag on the way out
        (exp (+ [(+ 5 3) (+ (1,0) (1, 1))] 3))

        ;; add:

        ;; extract `0` tag on the way out
        ;; extract `1` tag on the way out
        (exp [(+ 5 3 3) (+ (1,0) (1, 1))])

        ;; derivative of `(exp x)` is, conveniently, `(exp x)`:

        ;; extract `0` tag on the way out
        ;; extract `1` tag on the way out
        [(exp 11) (+ ((exp 11), 0) ((exp 11), 1))]

        ;; The outer function now gives back the expected `(exp 11)` when we
        ;; extract the tag associated with `0`.

        ;; The solution lives in `derivative.cljc` and is roughly what you'd
        ;; expect. If a derivative returns a function, instead of implementing
        ;; `extract-tangent` by simply pushing the original tag down, you return
        ;; a function like this:
        (comment
          (fn [& args]
            ;; make a NEW fresh tag that gets used for any new arguments; this
            ;; way every new invocation of this returned fn will generate new
            ;; tags. `new-tag` was either `0` or `1` depending on the call in
            ;; the second example. Let's track the outer call through
            (let [fresh (d/fresh-tag)

                  ;; replace any occurrence of the original tag in the new fn's
                  ;; arguments with `fresh`. This is exactly the case where the
                  ;; outer `f-exp` in `(f-exp (f-exp 3))` got an argument that
                  ;; was tracking the original `tag` already.
                  new-args (map (fn [arg] (d/replace-tag arg tag fresh))
                                args)]

              ;; apply the function to the new arguments. (This is the whole
              ;; machine we described above, now with non-clashing tags).
              (-> (apply returned-f new-args)

                  ;; go extract the original tag that we were looking for in the
                  ;; first place.
                  (d/extract-tangent tag)

                  ;; Sub `tag` back in so that any wrapping fn can extract it.
                  ;;
                  ;; NOTE: This case is actually NOT checked by the amazing
                  ;; bug... and I can't seem to cook up a case where I can force
                  ;; it to matter! Keep trying.
                  (d/replace-tag fresh tag)))))

        ;; Before the fix, every call to `D` generated (and froze, closed over)
        ;; a new tag. Now every invocation generates a new tag.
        )))

  (testing "more subtle amazing bug!"
    ;; Here's an example of a variant on the bug above that shows why the
    ;; substitution above is not QUITE sufficient.
    ;;
    ;; Let's say you pass some fn `f` as an argument to the `extract-tangent` fn
    ;; laid out above. It's going to hit this line:
    ;;
    ;; `(d/replace-tag f tag fresh)`
    ;;
    ;; `replace-tag` is only supposed to replace tags on the way OUT of some
    ;; function call. The seemingly obvious implementation will replace tags on
    ;; the way in, too:
    (comment
      (fn [& args]
        (-> (apply f (map #(d/replace-tag % old fresh) args))
            (d/replace-tag fresh old))))

    ;; Why does this matter? If you cook up a situation where some function has
    ;; already captured the `new-tag` internally, then replacing `old` with
    ;; `fresh` on the way in will clobber `fresh` perturbations inside the fn.
    ;;
    ;; here's an example. With the suggested implementation this will return 0:

    (let [v (fn [u]
              (fn [f1]
                (fn [f2]
                  (fn [x]
                    ((f1 f2) (+ x u))))))
          v-hat ((D v) 0)]
      (is (= (exp 1)
             (((v-hat (v-hat identity)) exp) 1))))

    ;; The reason for this is that `f1`and `f2` both capture the the same tag
    ;; from the definition of `v-hat` == `(D v)` internally; so when `f2` gets
    ;; passed on to `f1`, `f1` needs to never pass this same tag back down. (It
    ;; does need to respect requests to `replace-tag` by replacing its outputs.)
    ;;
    ;; The solution is to only substitute `new-tag` for `old` in the output, and
    ;; to use a temporary tag on the arguments to protect them from tag
    ;; substitution, and render them "unique" in the eyes of the fn. Here is the
    ;; correct implementation:

    (comment
      (fn [& args]
        (let [fresh (d/fresh-tag)]
          (-> (apply f (map #(d/replace-tag % old fresh) args))
              (d/replace-tag old new-tag)
              (d/replace-tag fresh old)))))

    ;; The `old -> fresh` swap only applies to the result; any `old` that goes
    ;; in will stay tagged as `old` if it happens to leak out of this level, and
    ;; stay tagged as `fresh` internally so it can never get confused if someone
    ;; ELSE passes `old` in.
    )

  (testing "church box example"
    ;; According to [Manzyuk et al.
    ;; 2019](https://www.cambridge.org/core/journals/journal-of-functional-programming/article/perturbation-confusion-in-forward-automatic-differentiation-of-higherorder-functions/A808189A3875A2EDAC6E0D62CF2AD262),
    ;; GJS suggested a different fix: why not just do NO tag substitution at all
    ;; for functions? Then you can't clobber any tags internally, right? Just
    ;; let the function itself be transparent!
    ;;
    ;; I haven't done the good work of staring at WHY this example exposes the
    ;; problem, but if you work it out I'm sure it has to do with this problem
    ;; of perturbation capture. I'll leave it as an exercise ;) but the test
    ;; does fail if you swap the implementation of `replace-tags` to `identity`
    ;; for functions.
    (letfn [;; R-> (box R)
            (box    [x] (fn [m] (m x)))

            ;; (box R)->R
            (unbox  [x] (x (fn [x] x)))

            ;; (R->R) -> ((box R)->(box R))
            (wrap   [f] (fn [x] (box (f (unbox x)))))

            ;; ((box R)->(box R)) -> (R -> R)
            (unwrap [f] (fn [x] (unbox (f (box x)))))

            ;; ((R->R) -> (R->R))
            ;;   -> (((box R) -> (box R)) -> ((box R) -> (box R)))
            (wrap2 [f]
              (fn [g]
                (fn [x]
                  (box ((f (unwrap g)) (unbox x))))))

            ;; (R -> ((R->R) -> (R->R)))
            ;;   -> (R-> (((box R) -> (box R)) -> ((box R)->(box R))))
            (wrap2-result [f]
              (fn [x] (wrap2 (f x))))]

      (let [
            ;; R -> ((R->R) -> (R->R)))
            s (fn [x]
                (fn [g]
                  (fn [y]
                    (g (+ x y)))))
            ;; R-> (((box R) -> (box R)) -> ((box R)->(box R)))
            wrapped-d-hat ((D (wrap2-result s)) 0)]
        (is (= (exp 1)
               ((unwrap
                 (wrapped-d-hat
                  (wrapped-d-hat (wrap exp)))) 1)))))))

(deftest sams-amazing-bug
  ;; This test shows a potential pitfall that might bite you if you're not
  ;; careful about tracking the scope introduced by each call to [[d/D]]. This
  ;; gets tricky with derivatives of higher order functions.

  (testing "D on a higher-order function responds differently to evaluations
  inside the 'body' of the multi-argument version of the function, vs outside."
    (let [
          ;; Make some literal function that takes TWO inputs:
          a (af/literal-function 'a '(-> (X Real Real) Real))

          ;; (D f) returns a function that takes a continuation that received
          ;; two functions, `f1` and `f2`:
          ;;
          ;; - `f1` passes `x` (the arg that `D` is with respect to) and `y`,
          ;;    its argument, into the literal function
          ;; - `f2` passes its captive `x` into its argument `g`, a function
          f (fn [x]
              (fn [cont]
                (cont (fn f1 [y] (a x y))
                      (fn f2 [g] (g x)))))]

      ;; Calling f1 or f2 separately work as expected:
      (is (= '(((partial 0) a) t t)
             (v/freeze
              (((D f) 't) (fn [f1 _] (f1 't)))))
          "`a` received `x` as its first arg, so we see `(partial 0)`")

      (let [g (af/literal-function 'g)]
        (is (= '((D g) t)
               (v/freeze
                (((D f) 't) (fn [_ f2] (f2 g)))))
            "derivative of (g x) == ((D g) x)."))

      ;; If you pass a continuation that feeds `f1` into `f2`, then `(f2 f1)` is
      ;; called _inside_ the scope introduced by `(D f)`. The `x` instances
      ;; captured by `f1` and `f2` are both still "live" and their tangent
      ;; components can interact.
      ;;
      ;; The result is a sum of partials:
      (is (= '(+ (((partial 0) a) t t)
                 (((partial 1) a) t t))

             (g/simplify
              ((D (fn [t] (a t t))) 't))

             (g/simplify
              (((D f) 't)
               (fn [f1 f2] (f2 f1)))))
          "All three cases identically sub `x` into the body of `f1` before
          taking the derivative.")

      ;; If instead you pass `list` as a continuation and _remove_ `f1` and `f2`
      ;; from context associated with _x_ before you call `(f2 f1)`, then you
      ;; instead see a "mixed partial" result:
      (let [[f1 f2] (((D f) 't) list)]
        (is (= '(((partial 1) ((partial 0) a)) t t)
               (g/simplify
                (f2 f1)))
            "If you first get `f1` and `f2` out and THEN call (f2 f1), you see a
            mixed partial instead."))

      ;; You see this because the `list` continuation triggers
      ;; an `(extract-tangent ,,, tag)` call on each component of the `list`
      ;; separately, so the returned `f1`and `f2` are both composed with
      ;; `extract-tangent` calls for the tangent associated with the scope
      ;; introduced by `(D f)`.
      ;;
      ;; This means that the tangents of the `x` instances captured by `f1` and
      ;; `f2` can no longer interact. There is no context waiting to bind them
      ;; together!
      )))

(deftest dvl-bug-examples
  ;; These tests and comments all come from Alexey Radul's
  ;; https://github.com/axch/dysvunctional-language. Thanks, Alexey!

  (testing "amazing bug two"
    ;; What should happen if we differentiate a function that returns a pair of
    ;; functions? And then tries to confuse their perturbations with each other
    ;; like the amazing-bug trick? They should refuse to confuse, and be
    ;; separate.
    (letfn [(f [x]
              [(fn [y] (sin (* x y)))
               (fn [g]
                 (fn [z] (g (+ x z))))])]
      (is (ish? ((fn [y]
                   (- (cos (* 3 y))
                      (* 3 y (sin (* 3 y)))))
                 (+ Math/PI 3))
                (let [[g-hat f-hat] ((D f) 3)]
                  ((f-hat g-hat) Math/PI))))))

  (testing "amazing bug three, from Alexey"
    ;; Here we have the same program as in amazing-bug-2.dvl, but using a
    ;; Church-encoded pair rather than a normal one. Should the answer be the
    ;; same?

    ;; Arguably not.  Consider that under the normal definition of
    ;; addition on functions and pairs, Church-encoded pairs add
    ;; differently from normal ones:
    ;; (fn [cont] (cont x1 y1)) + (fn [cont] (cont x2 y2)) =
    ;; (fn [cont] (+ (cont x1 y1) (cont x2 y2))) !=
    ;; (fn [cont] (cont (+ x1 x2) (+ y1 y2)))
    (letfn [(f [x]
              (fn [recipient]
                (recipient
                 (fn [y] (sin (* x y)))
                 (fn [g]
                   (fn [z] (g (+ x z)))))))]
      (is (ish? ((fn [y] (+ (* 3 (cos (* 3 y)))
                           (* y (cos (* 3 y)))))
                 (+ 3 Math/PI))
                (((D f) 3)
                 (fn [g-hat f-hat]
                   ((f-hat g-hat) Math/PI))))))

    ;; These are only different if the CONT procedure is non-linear. The
    ;; interpretation is that in the Church-encoded case, the encoding respects
    ;; the non-linearity in the CONT procedure, whereas in the pair case, adding
    ;; pairs does not respect the non-linearity of the result. (In fact, the
    ;; same is true of ordinary addition of numbers). Since differentiation is
    ;; supposed to expose linear structure, it makes sense that it would expose
    ;; different things in these two cases.
    ;;
    ;; NOTE from @sritchie: I think the linear vs nonlinear comment is not the
    ;; right point of focus. What causes the difference is whether or not you
    ;; break the scope introduced by `((D f) 3)`. That call sets up a
    ;; unique "tangent space" that follows `3` around, and when you leave that
    ;; scope, the tangent associated with that space gets dropped down to primal
    ;; space.
    ;;
    ;; Doing work inside a continuation means you're actually working
    ;; with [[sicmutils.differential/Differential]] instances whose tangents can
    ;; interact. Once you break out of the continuation, as in "bug two", the
    ;; two components separately drop their tangents, so they can't talk
    ;; anymore.
    ;;
    ;; The "linear" comment matters because if you only combine the dropped-down
    ;; pieces linearly, then their tangents wouldn't have interacted anyway, so
    ;; you can't tell that there are different cases here.
    )

  (testing "amazing bug 4"
    ;; The same as amazing-bug-3.dvl, but supplies the arguments to f in the
    ;; opposite order. It is clear that the answers should be identical, and
    ;; makes it easier to check the correctness of the answer.
    (letfn [(f [recipient]
              (fn [x]
                (recipient
                 (fn [y] (sin (* x y)))
                 (fn [g]
                   (fn [z] (g (+ x z)))))))
            (recip [g-hat f-hat]
              ((f-hat g-hat) Math/PI))]
      (is (ish? ((fn [y] (* (cos (* 3 y)) (+ 3 y)))
                 (+ 3 Math/PI))
                ((D (f recip)) 3)))))

  (testing "amazing bug 5"
    (letfn [(church-output [f]
              (fn [x]
                (fn [recipient]
                  (recipient (f x)))))
            (continue [x]
              (* x x))
            (flip [f]
              (fn [x] (fn [y] ((f y) x))))]
      (is (= (* (cos 1) (cos 1))
             (continue ((D sin) 1))))

      (is (= (* 2 (sin 1) (cos 1))
             (((D (church-output sin)) 1) continue)))

      (is (= ((D ((flip (church-output sin)) continue)) 1)
             (((D       (church-output sin)) 1) continue))
          "(((D f) x) y) === ((D ((flip f) y)) x)")

      (is (= (* 2 (sin 1) (cos 1))
             ((D (fn [x] (* (sin x) (sin x)))) 1))))))

(deftest confusion-tests
  ;; More tests from dvl stressing perturbation confusion.
  (testing "don't confuse perturbations, from dvl"
    (letfn [(one [x]
              ((D (fn [y] (+ x y))) 3))]
      (is (= 0 ((D one) 'x)))
      (is (= 1 ((D (fn [x] (* x (one x)))) 'x)))
      (is (= 1 ((D (fn [x] (* x (one (* 2 x))))) 'x)))
      (is (= '(* 12 x)
             (g/simplify
              ((D (fn [y]
                    ((D (fn [x] (* x (* x y))))
                     (* y 3))))
               'x)))))))
