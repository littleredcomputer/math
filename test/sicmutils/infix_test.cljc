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

(ns sicmutils.infix-test
  (:refer-clojure :exclude [+ - * /])
  (:require [clojure.test :refer [is deftest testing use-fixtures]]
            #?(:cljs [goog.string :refer [format]])
            [sicmutils.calculus.derivative :refer [D taylor-series]]
            [sicmutils.generic :as g :refer [expt sin cos + - * /]]
            [sicmutils.infix :as i :refer [->infix ->TeX ->JavaScript]]
            [sicmutils.function :as f
             #?(:clj :refer :cljs :refer-macros) [with-literal-functions]]
            [sicmutils.series :as series]
            [sicmutils.simplify :refer [hermetic-simplify-fixture]]
            [sicmutils.structure :refer [up down]]))

(use-fixtures :once hermetic-simplify-fixture)

(def ^:private s->infix (f/compose ->infix g/simplify))
(def ^:private s->TeX (f/compose ->TeX g/simplify))
(defn ^:private s->JS
  [x & options]
  (apply ->JavaScript (g/simplify x) options))

(deftest basic
  (testing "raw epxressions"
    (is (= "Df(x, y)" (->infix '((D f) x y))))
    (is (= "D(f + g)(x, y)" (->infix '((D (+ f g)) x y))))
    (is (= "D(f g)(x, y)" (->infix '((D (* f g)) x y))))
    (is (= "(sin cos)(t)" (->infix '((* sin cos) t))))
    (is (= "- cos(t ω + φ) a m ω² + cos(t ω + φ) a k"
           (->infix '(+ (* -1 (cos (+ (* t ω) φ)) a m (expt ω 2))
                        (* (cos (+ (* t ω) φ)) a k)))))
    (is (= "(a + b c)(x, y, z)" (->infix '((+ a (* b c)) x y z))))
    (is (= "(a (b + c))(u, v)" (->infix '((* a (+ b c)) u v))))
    (is (= "f(g)(h)(k)(r, s)" (->infix '((((f g) h) k) r s))))
    (is (= "f(r, s)(x, y)" (->infix '((f r s) x y))))
    (is (= "a + b + c f(u)(v)" (->infix '(+ a b (* c ((f u) v))))))
    (is (= "a b (c + (f / g)(v))" (->infix '(* a b (+ c ((/ f g) v))))))
    (is (= "foo bar" (->infix '(* foo bar))))
    (is (= "a b (f / g)(v)" (->infix '(* a b ((/ f g) v)))))
    (is (= "a" (->infix '(* a))))
    (is (= "(- x)³" (->infix '(expt (- x) 3))))
    (is (= "(- x)⁴" (->infix '(expt (- x) 4))))
    (is (= "a" (->infix '(+ a))))
    (is (= "1 / a" (->infix '(/ a))))
    (is (= "- a" (->infix '(- a)))))

  (testing "with-simplifier"
    (with-literal-functions [[f [0 0] 0] h k]
      (is (= "a + b + c" (s->infix (+ 'a 'b 'c))))
      (is (= "a b c" (s->infix (* 'a 'b 'c))))
      (is (= "a b + a c" (s->infix (* 'a (+ 'b 'c)))))
      (is (= "b c + a" (s->infix (+ 'a (* 'b 'c)))))
      (is (= "- a + b - c + d" (s->infix (+ (* -1 'a) 'b (* -1 'c) 'd))))
      (is (= "a - b + c - d" (s->infix (+ 'a (* -1 'b) 'c (* -1 'd)))))
      (is (= "- a" (s->infix (- 'a))))
      (is (= "- a" (s->infix (- 0 'a))))
      (is (= "- a / b" (s->infix (/ (- 'a) 'b))))
      (is (= "1 / (a + b)" (s->infix (/ (+ 'a 'b)))))
      (is (= "x⁴" (s->infix (expt (- 'x) 4))))
      (is (= "- x³" (s->infix (expt (- 'x) 3))))
      (is (= "a - b - c" (s->infix (- 'a 'b 'c))))
      (is (= "a f(b, c)" (s->infix (* 'a (f 'b 'c)))))
      (is (= "a f(2 h + 2 k, c)" (s->infix (* 'a (f (* 2 (+ 'h 'k)) 'c)))))
      (is (= "f(x, y)" (s->infix (f 'x 'y))))
      (is (= "down(∂₀f(x, y), ∂₁f(x, y))" (s->infix ((D f) 'x 'y))))
      (is (= "sin(t) cos(t)" (s->infix ((* sin cos) 't)))))))

(deftest exponents
  (is (= '"x⁴ + 4 x³ + 6 x² + 4 x + 1"
         (s->infix (expt (+ 1 'x) 4))))
  (is (= "x¹²" (s->infix (expt 'x 12))))
  (is (= "y¹⁵ + 3 x⁴ y¹⁰ + 3 x⁸ y⁵ + x¹²"
         (s->infix (expt (+ (expt 'x 4) (expt 'y 5)) 3))))
  (is (= "x² + x^-2" (s->infix '(+ (expt x 2) (expt x -2)))))
  (is (= "sin²(x)" (->infix '((expt sin 2) x))))
  (is (= "(x y)²" (->infix '(expt (* x y) 2))))
  (is (= "sin²(x)" (s->infix ((expt sin 2) 'x))))
  (is (= "(sin(x))^y" (s->infix (expt (sin 'x) 'y))))
  (is (= "(a + b)²" (->infix '(expt (+ a b) 2))))
  (is (= "(a + b)^(x + y)" (->infix '(expt (+ a b) (+ x y)))))
  (is (= "(a + b)^x" (->infix '(expt (+ a b) x))))
  (is (= "a^(x + y)" (->infix '(expt a (+ x y)))))
  (is (= "x^y" (s->infix (expt 'x 'y)))))

(deftest more-with-D
  (with-literal-functions [f g [p [] 0]]
    (is (= "f(s)" (s->infix (f 's))))
    (is (= "(f + g)(x, y)" (->infix '((+ f g) x y))))
    (is (= "f(x) g(x)" (s->infix ((* f g) 'x))))
    (is (= "f(t)" (s->infix (f 't))))
    (is (= "Df(s)" (s->infix ((D f) 's))))
    (is (= "D²f(s)" (s->infix (((expt D 2) f) 's))))))

(deftest structures
  (is (= "down(up(1, 2), up(3, 4))" (->infix (g/simplify (down (up 1 2) (up 3 4)))))))

(deftest variable-subscripts
  (is (= "x₀ + y₁ + z₂" (s->infix (+ 'x_0 'y_1 'z_2)))))

(deftest TeX-easy
  (is (= "a + b" (s->TeX (+ 'a 'b))))
  (is (= "\\lambda + \\mu" (s->TeX (+ 'lambda 'mu))))
  (is (= "x_0 + y_s" (s->TeX (+ 'x_0 'y_s))))
  (is (= "\\frac{1}{x}" (s->TeX (/ 1 'x))))
  (is (= "\\frac{a + b}{c + d}" (s->TeX (/ (+ 'a 'b) (+ 'c 'd)))))
  (is (= "\\frac{a}{b}" (s->TeX (/ 'a 'b)))))

(defn ^:private make-symbol-generator
  [p]
  (let [i (atom 0)]
    (fn [] (symbol (format "%s%d" p (swap! i inc))))))

(deftest JS
  (is (= "function(a, b, theta) {\n  return a + b + Math.sin(theta);\n}"
         (s->JS (+ 'a 'b (sin 'theta)))))
  (is (= "function(j) {\n  return 1 / j;\n}"
         (s->JS (g/invert 'j))))
  (is (= "function(y) {\n  return Math.pow(2.71828, y);\n}"
         (s->JS (expt 2.71828 'y))))
  (is (= "function(a, b, x) {\n  return a * Math.exp(b * Math.log(x));\n}"
         (s->JS (* 'a (g/exp (* 'b (g/log 'x)))))))
  (is (= "function(x) {\n  var _0001 = Math.sin(x);\n  return Math.pow(_0001, 2) + _0001;\n}"
         (s->JS (+ (sin 'x) (expt (sin 'x) 2)))))
  (is (= (str "function(x, dx) {\n"
              "  var t1 = Math.sin(x);\n"
              "  return -1/2 * Math.pow(dx, 2) * t1 + dx * Math.cos(x) + t1;\n"
              "}")
         (s->JS (series/sum (taylor-series sin 'x 'dx) 2)
                :symbol-generator (make-symbol-generator "t")
                :parameter-order '[x dx])))
  (is (= "function(x, y) {\n  return [1, x + y, 2];\n}" (s->JS (up 1 (+ 'x 'y) 2))))
  (is (= "function(a, b) {\n  return [[1, a], [b, 2]];\n}" (s->JS (down (up 1 'a) (up 'b 2))))))

(deftest systematic
  (let [all-formats (juxt s->infix s->JS s->TeX)]
    (is (= ["a + b"
            "function(a, b) {\n  return a + b;\n}"
            "a + b"]
           (all-formats (+ 'a 'b))))
    (is (= ["a b"
            "function(a, b) {\n  return a * b;\n}"
            "a\\,b"]
           (all-formats (* 'a 'b))))
    (is (= ["a / b"
            "function(a, b) {\n  return a / b;\n}"
            "\\frac{a}{b}"]
           (all-formats (/ 'a 'b))))
    (is (= ["a - b"
            "function(a, b) {\n  return a - b;\n}"
            "a - b"]
           (all-formats (- 'a 'b))))
    (is (= ["sin(t)"
            "function(t) {\n  return Math.sin(t);\n}"
            "\\sin\\left(t\\right)"]
           (all-formats (sin 't))))
    (is (= ["sin²(t)"
            "function(t) {\n  return Math.pow(Math.sin(t), 2);\n}"
            "{\\sin}^{2}\\left(t\\right)"]
           (all-formats ((expt sin 2) 't))))
    (is (= ["cos²(tan(t))"
            "function(t) {\n  return Math.pow(Math.cos(Math.tan(t)), 2);\n}"
            "{\\cos}^{2}\\left(\\tan\\left(t\\right)\\right)"]
           (all-formats ((expt cos 2) (g/tan 't)))))
    (is (= ["sin²(q + t)"
            "function(q, t) {\n  return Math.pow(Math.sin(q + t), 2);\n}"
            "{\\sin}^{2}\\left(q + t\\right)"]
           (all-formats ((expt sin 2) (+ 't 'q)))))
    (is (= ["a b + c d"
            "function(a, b, c, d) {\n  return a * b + c * d;\n}"
            "a\\,b + c\\,d"]
           (all-formats (+ (* 'a 'b) (* 'c 'd)))))
    (is (= ["a c + a d + b c + b d"
            "function(a, b, c, d) {\n  return a * c + a * d + b * c + b * d;\n}"
            "a\\,c + a\\,d + b\\,c + b\\,d"]
           (all-formats (* (+ 'a 'b) (+ 'c 'd)))))
    (is (= ["(a + b) / c"
            "function(a, b, c) {\n  return (a + b) / c;\n}"
            "\\frac{a + b}{c}"]
           (all-formats (/ (+ 'a 'b) 'c))))
    (is (= ["a / (b + c)"
            "function(a, b, c) {\n  return a / (b + c);\n}"
            "\\frac{a}{b + c}"]
           (all-formats (/ 'a (+ 'b 'c)))))
    (is (= ["a / (b c)"
            "function(a, b, c) {\n  return a / (b * c);\n}"
            "\\frac{a}{b\\,c}"]
           (all-formats (/ 'a (* 'b 'c)))))
    (is (= ["(b c) / a"
            "function(a, b, c) {\n  return (b * c) / a;\n}"
            "\\frac{b\\,c}{a}"]
           (all-formats (/ (* 'b 'c) 'a))))
    (is (= ["(a b) / (c d)"
            "function(a, b, c, d) {\n  return (a * b) / (c * d);\n}"
            "\\frac{a\\,b}{c\\,d}"]
           (all-formats (/ (* 'a 'b) (* 'c 'd)))))
    (is (= ["- a - b - c"
            "function(a, b, c) {\n  return - a - b - c;\n}"
            "- a - b - c"]
           (all-formats (- (+ 'a 'b 'c)))))
    (is (= ["- a b c"
            "function(a, b, c) {\n  return - a * b * c;\n}"
            "- a\\,b\\,c"]
           (all-formats (- (* 'a 'b 'c)))))
    (let [f (f/literal-function 'f)]
      (is (= ["Df(x)"
              "function(D, f, x) {\n  return D(f)(x);\n}"
              "Df\\left(x\\right)"]
             (all-formats ((D f) 'x))))
      (is (= ["D²f(x)"
              "function(D, f, x) {\n  return Math.pow(D, 2)(f)(x);\n}"
              "{D}^{2}f\\left(x\\right)"]
             (all-formats ((D (D f)) 'x))))

      (let [expr (-> (taylor-series
                      (f/literal-function 'f (up 0 0) 0)
                      (up 'x 'y)
                      (up 'dx 'dy))
                     (series/sum 2))]
        (is (= "1/2 dx² ∂₀(∂₀f)(up(x, y)) + dx dy ∂₁(∂₀f)(up(x, y)) + 1/2 dy² ∂₁(∂₁f)(up(x, y)) + dx ∂₀f(up(x, y)) + dy ∂₁f(up(x, y)) + f(up(x, y))"
               (s->infix expr)))

        (is (= (str "function(dx, dy, f, partial, x, y) {\n"
                    "  var _0003 = partial(0);\n"
                    "  var _0004 = partial(1);\n"
                    "  var _0005 = [x, y];\n"
                    "  var _0006 = _0003(f);\n"
                    "  var _0007 = _0004(f);\n"
                    "  return 1/2 * Math.pow(dx, 2) * _0003(_0006)(_0005) + dx * dy * _0004(_0006)(_0005) + 1/2 * Math.pow(dy, 2) * _0004(_0007)(_0005) + dx * _0006(_0005) + dy * _0007(_0005) + f(_0005);\n}")
               (s->JS expr :deterministic? true)))

        (is (= "\\frac{1}{2}\\,{dx}^{2}\\,\\partial_0\\left(\\partial_0f\\right)\\left(\\begin{pmatrix}\\displaystyle{x} \\cr \\cr \\displaystyle{y}\\end{pmatrix}\\right) + dx\\,dy\\,\\partial_1\\left(\\partial_0f\\right)\\left(\\begin{pmatrix}\\displaystyle{x} \\cr \\cr \\displaystyle{y}\\end{pmatrix}\\right) + \\frac{1}{2}\\,{dy}^{2}\\,\\partial_1\\left(\\partial_1f\\right)\\left(\\begin{pmatrix}\\displaystyle{x} \\cr \\cr \\displaystyle{y}\\end{pmatrix}\\right) + dx\\,\\partial_0f\\left(\\begin{pmatrix}\\displaystyle{x} \\cr \\cr \\displaystyle{y}\\end{pmatrix}\\right) + dy\\,\\partial_1f\\left(\\begin{pmatrix}\\displaystyle{x} \\cr \\cr \\displaystyle{y}\\end{pmatrix}\\right) + f\\left(\\begin{pmatrix}\\displaystyle{x} \\cr \\cr \\displaystyle{y}\\end{pmatrix}\\right)"
               (s->TeX expr)))))))
