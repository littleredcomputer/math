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

(ns sicmutils.sicm.ch3-test
  (:refer-clojure :exclude [+ - * / zero? partial])
  (:require [clojure.test :refer [is deftest testing use-fixtures]]
            [sicmutils.env :as e
             :refer [+ - * / D zero? partial simplify
                     up down
                     literal-function]
             #?@(:cljs [:include-macros true])]
            [sicmutils.mechanics.lagrange :as L]
            [sicmutils.mechanics.hamilton :as H]
            [sicmutils.simplify :refer [hermetic-simplify-fixture]]
            [sicmutils.examples.driven-pendulum :as driven]
            [sicmutils.examples.top :as top]))

(use-fixtures :each hermetic-simplify-fixture)

(deftest section-3-1
  (testing "p.189"
    (is (= '(up 0
                (up (/ (+ (* m ((D x) t)) (* -1 (p_x t))) m)
                    (/ (+ (* m ((D y) t)) (* -1 (p_y t))) m))
                (down (+ ((D p_x) t) (((partial 0) V) (x t) (y t)))
                      (+ ((D p_y) t) (((partial 1) V) (x t) (y t)))))
           (simplify (((e/Hamilton-equations
                        (H/H-rectangular
                         'm
                         (literal-function 'V (-> (X Real Real) Real))))
                       (up (literal-function 'x) (literal-function 'y))
                       (down (literal-function 'p_x) (literal-function 'p_y)))
                      't)))))
  (testing "p.198"
    (is (= '(/ (+ (* 2 m (V x y)) (expt p_x 2) (expt p_y 2))
               (* 2 m))
           (simplify ((e/Lagrangian->Hamiltonian
                       (L/L-rectangular
                        'm (literal-function 'V (-> (X Real Real) Real))))
                      (up 't (up 'x 'y) (down 'p_x 'p_y))))))))

(deftest section-3-2
  (testing "p.205"
    (let [F (literal-function 'F (-> (UP Real (UP Real Real) (DOWN Real Real)) Real))
          G (literal-function 'G (-> (UP Real (UP Real Real) (DOWN Real Real)) Real))
          H (literal-function 'H (-> (UP Real (UP Real Real) (DOWN Real Real)) Real))]
      (is (zero? (simplify ((+ (e/Poisson-bracket F (e/Poisson-bracket G H))
                               (e/Poisson-bracket G (e/Poisson-bracket H F))
                               (e/Poisson-bracket H (e/Poisson-bracket F G)))
                            (up 't (up 'x 'y) (down 'px 'py)))))))))

(deftest section-3-4
  (testing "p.212"
    (is (= '(/ (+ (* 2 m (expt r 2) (V r)) (* (expt p_r 2) (expt r 2)) (expt p_phi 2)) (* 2 m (expt r 2)))
           (simplify ((e/Lagrangian->Hamiltonian
                       (L/L-central-polar 'm (literal-function 'V)))
                      (up 't (up 'r 'phi) (down 'p_r 'p_phi))))))
    (is (= '(up 0
                (up (/ (+ (* m ((D r) t))
                          (* -1 (p_r t)))
                       m)
                    (/ (+ (* m (expt (r t) 2) ((D phi) t))
                          (* -1 (p_phi t)))
                       (* m (expt (r t) 2))))
                (down (/ (+ (* m (expt (r t) 3) ((D p_r) t))
                            (* m (expt (r t) 3) ((D V) (r t)))
                            (* -1 (expt (p_phi t) 2)))
                         (* m (expt (r t) 3)))
                      ((D p_phi) t)))
           (simplify (((e/Hamilton-equations
                        (e/Lagrangian->Hamiltonian
                         (L/L-central-polar 'm (literal-function 'V))))
                       (up (literal-function 'r)
                           (literal-function 'phi))
                       (down (literal-function 'p_r)
                             (literal-function 'p_phi)))
                      't)))))
  (testing "p.213"
    (is (= '(/ (+ (* 2 A C gMR (expt (sin theta) 2) (cos theta))
                  (* A (expt p_psi 2) (expt (sin theta) 2))
                  (* C (expt p_psi 2) (expt (cos theta) 2))
                  (* C (expt p_theta 2) (expt (sin theta) 2))
                  (* -2 C p_phi p_psi (cos theta))
                  (* C (expt p_phi 2)))
               (* 2 A C (expt (sin theta) 2)))
           (simplify ((e/Lagrangian->Hamiltonian (top/L-axisymmetric 'A 'C 'gMR))
                      (up 't
                          (up 'theta 'phi 'psi)
                          (down 'p_theta 'p_phi 'p_psi)))))))
  (testing "p.214"
    (let [top-state (up 't
                        (up 'theta 'phi 'psi)
                        (down 'p_theta 'p_phi 'p_psi))
          H (e/Lagrangian->Hamiltonian
             (top/L-axisymmetric 'A 'C 'gMR))
          sysder (e/Hamiltonian->state-derivative H)]
      (is (= '(/ (+ (* 2 A C gMR (expt (sin theta) 2) (cos theta))
                    (* A (expt p_psi 2) (expt (sin theta) 2))
                    (* C (expt p_psi 2) (expt (cos theta) 2))
                    (* C (expt p_theta 2) (expt (sin theta) 2))
                    (* -2 C p_phi p_psi (cos theta))
                    (* C (expt p_phi 2)))
                 (* 2 A C (expt (sin theta) 2)))
             (simplify (H top-state))))

      (is (= '(up 1
                  (up (/ p_theta A)
                      (/ (+ (* -1 p_psi (cos theta)) p_phi) (* A (expt (sin theta) 2)))
                      (/ (+ (* A p_psi (expt (sin theta) 2)) (* C p_psi (expt (cos theta) 2)) (* -1 C p_phi (cos theta)))
                         (* A C (expt (sin theta) 2))))
                  (down (/ (+ (* A gMR (expt (cos theta) 4))
                              (* -2 A gMR (expt (cos theta) 2))
                              (* -1 p_phi p_psi (expt (cos theta) 2))
                              (* (expt p_phi 2) (cos theta))
                              (* (expt p_psi 2) (cos theta))
                              (* A gMR)
                              (* -1 p_phi p_psi))
                           (* A (expt (sin theta) 3)))
                        0
                        0))
             (simplify (sysder top-state))))
      (is (= (str "function(A, C, gMR, p_phi, p_psi, p_theta, theta) {\n"
                  "  var _0001 = Math.cos(theta);\n"
                  "  var _0004 = Math.sin(theta);\n"
                  "  var _0005 = Math.pow(_0001, 2);\n"
                  "  var _0006 = Math.pow(_0004, 2);\n"
                  "  return [1, [p_theta / A, (- p_psi * _0001 + p_phi) / (A * _0006), (A * p_psi * _0006 + C * p_psi * _0005 - C * p_phi * _0001) / (A * C * _0006)], [(A * gMR * Math.pow(_0001, 4) -2 * A * gMR * _0005 - p_phi * p_psi * _0005 + Math.pow(p_phi, 2) * _0001 + Math.pow(p_psi, 2) * _0001 + A * gMR - p_phi * p_psi) / (A * Math.pow(_0004, 3)), 0, 0]];\n"
                  "}")
             (-> top-state sysder simplify (e/->JavaScript :deterministic? true)))))))

(deftest section-3-5
  (testing "p.221"
    (let [H ((e/Lagrangian->Hamiltonian
              (driven/L 'm 'l 'g 'a 'omega))
             (up 't 'theta 'p_theta))]
      (is (= '(/ (+ (* -1 (expt a 2) (expt l 2) (expt m 2) (expt omega 2) (expt (sin (* omega t)) 2) (expt (cos theta) 2))
                    (* 2 a g (expt l 2) (expt m 2) (cos (* omega t)))
                    (* 2 a l m omega p_theta (sin (* omega t)) (sin theta))
                    (* -2 g (expt l 3) (expt m 2) (cos theta))
                    (expt p_theta 2))
                 (* 2 (expt l 2) m))
             (simplify H))))
    (let [sysder (simplify
                  ((e/Hamiltonian->state-derivative
                    (e/Lagrangian->Hamiltonian
                     (driven/L 'm 'l 'g 'a 'omega)))
                   (up 't 'theta 'p_theta)))]
      (is (= '(up 1
                  (/ (+ (* a l m omega (sin (* omega t)) (sin theta)) p_theta)
                     (* (expt l 2) m))
                  (/ (+ (* -1 (expt a 2) l m (expt omega 2) (expt (sin (* omega t)) 2) (sin theta) (cos theta))
                        (* -1 a omega p_theta (sin (* omega t)) (cos theta))
                        (* -1 g (expt l 2) m (sin theta)))
                     l))
             sysder))

      ;; ah, we observe that _1 is omega*t, and we have a few examples of the
      ;; sine (of that. So our algorithm is a little on the naive side o_o
      (is (= (str "function(a, g, l, m, omega, p_theta, t, theta) {\n"
                  "  var _0001 = omega * t;\n"
                  "  var _0002 = Math.cos(theta);\n"
                  "  var _0003 = Math.pow(l, 2);\n"
                  "  var _0005 = Math.sin(theta);\n"
                  "  var _0006 = Math.sin(_0001);\n"
                  "  return [1, (a * l * m * omega * _0006 * _0005 + p_theta) / (_0003 * m), (- Math.pow(a, 2) * l * m * Math.pow(omega, 2) * Math.pow(_0006, 2) * _0005 * _0002 - a * omega * p_theta * _0006 * _0002 - g * _0003 * m * _0005) / l];\n"
                  "}")
             (e/->JavaScript sysder :deterministic? true))))))
