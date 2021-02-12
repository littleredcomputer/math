;;
;; Copyright © 2017 Colin Smith.
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

(ns sicmutils.demo
  (:refer-clojure :exclude [+ - * / zero? ref compare])
  (:require [sicmutils.env :refer :all]
            [sicmutils.mechanics.lagrange :as l]))

(def q
  ;; See p. 17
  (with-literal-functions [x y z]
    (up x y z)))

(def literal-q (literal-function 'q))

(defn test-path
  "See p. 20"
  [t]
  (up (+ (* 4 t) 7)
      (+ (* 3 t) 5)
      (+ (* 2 t) 1)))

(defn make-η
  "See p. 21"
  [ν t1 t2]
  (fn [t]
    (* (- t t1) (- t t2) (ν t))))

(defn varied-free-particle-action
  "See p. 21"
  [mass q ν t1 t2]
  (fn [ε]
    (let [η (make-η ν t1 t2)]
      (Lagrangian-action (l/L-free-particle mass)
                         (+ q (* ε η)) t1 t2))))

(Lagrangian-action (l/L-free-particle 3.0) test-path 0.0 10.0)

((varied-free-particle-action 3.0 test-path (up sin cos square) 0.0 10.0) 0.001)

(minimize (varied-free-particle-action 3.0 test-path (up sin cos square) 0.0 10.0) -2 1)

(defn F
  "A generic path function."
  [q]
  (fn [t]
    ((literal-function 'f) (q t))))

(defn G
  "Another generic path function."
  [q]
  (fn [t]
    ((literal-function 'g) (q t))))

(defn φ
  "A path transformation function"
  [f]
  (fn [q]
    (fn [t]
      ((literal-function 'φ) ((f q) t)))))

(defn δ
  "The variation operator (p. 28)."
  [η]
  (fn [f]
    ;; Define g(ε) as in Eq. 1.22; then δ_η f[q] = Dg(0)
    (fn [q]
      (let [g (fn [ε]
                (f (+ q (* ε η))))]
        ((D g) 0)))))

;; Exercise 1.7
(def δ_η (δ (literal-function 'eta)))
(((δ_η   identity) literal-q) 't)
(((δ_η          F) literal-q) 't)
(((δ_η          G) literal-q) 't)
(((δ_η   (* 'c G)) literal-q) 't) ; scalar multiplication of variation
(((δ_η    (* F G)) literal-q) 't) ; product rule for variation
(((δ_η    (+ F G)) literal-q) 't) ; sum rule for variation
(((δ_η      (φ F)) literal-q) 't) ; composition rule for variation
;; p. 34
(((Lagrange-equations (l/L-free-particle 'm)) (literal-function 'q)) 't)
;; p. 35
(((Lagrange-equations (l/L-free-particle 'm)) test-path) 't)
;; p.36
(defn proposed-solution [t]
  (* 'a (cos (+ (* 'omega t) 'φ))))
(((Lagrange-equations (l/L-harmonic 'm 'k)) proposed-solution) 't)
;; p. 40
(((Lagrange-equations (l/L-uniform-acceleration 'm 'g)) (up (literal-function 'x) (literal-function 'y))) 't)
;; p. 41
(((Lagrange-equations (l/L-central-rectangular 'm (literal-function 'U)))
  (up (literal-function 'x)
      (literal-function 'y)))
 't)

;; p. 43
(prn "central polar")
(((Lagrange-equations (l/L-central-polar 'm (literal-function 'U)))
  (up (literal-function 'r)
      (literal-function 'φ)))
 't)

;; Coordinate transformation (p. 47)
(velocity ((F->C p->r)
           (->local 't (up 'r 'φ) (up 'rdot 'φdot))))

(defn L-alternate-central-polar
  [m U]
  (comp (l/L-central-rectangular m U) (F->C p->r)))

(println "alternate central polar Lagrangian")

((L-alternate-central-polar 'm (literal-function 'U))
  (->local 't (up 'r 'φ) (up 'rdot 'φdot)))

(println "alternate central polar Lagrange equations")

(((Lagrange-equations (L-alternate-central-polar 'm (literal-function 'U)))
   (up (literal-function 'r)
       (literal-function 'φ)))
  't)

(println "The Simple Pendulum")

(defn T-pend
  [m l _ ys]
  (fn [local]
    (let [[t theta thetadot] local
          vys (D ys)]
      (* 1/2 m
         (+ (square (* l thetadot))
            (square (vys t))
            (* 2 l (vys t) thetadot (sin theta)))))))
(defn V-pend
  [m l g ys]
  (fn [local]
    (let [[t theta _] local]
      (* m g (- (ys t) (* l (cos theta)))))))

(def L-pend (- T-pend V-pend))

(def θ (literal-function 'θ))
(def y_s (literal-function 'y_s))

(((Lagrange-equations (L-pend 'm 'l 'g y_s)) θ) 't)
(->infix (simplify (((Lagrange-equations (L-pend 'm 'l 'g y_s)) θ) 't)))

; p. 61
(defn Lf [m g]
  (fn [[_ [_ y] v]]
    (- (* 1/2 m (square v)) (* m g y))))

(defn dp-coordinates [l y_s]
  (fn [[t θ]]
    (let [x (* l (sin θ))
          y (- (y_s t) (* l (cos θ)))]
      (up x y))))

(defn L-pend2 [m l g y_s]
  (comp (Lf m g)
        (F->C (dp-coordinates l y_s))))

((L-pend2 'm 'l 'g y_s) (->local 't 'θ 'θdot))
(->infix (simplify ((L-pend2 'm 'l 'g y_s) (->local 't 'θ 'θdot))))

; skipping ahead to p. 81

(def V (literal-function 'V))
(def spherical-state (up 't
                         (up 'r 'θ 'φ)
                         (up 'rdot 'θdot 'φdot)))
(defn T3-spherical [m]
  (fn [[_ [r θ _] [rdot θdot φdot]]]
    (* 1/2 m (+ (square rdot)
                (square (* r θdot))
                (square (* r (sin θ) φdot))))))

(defn L3-central [m Vr]
  (let [Vs (fn [[_ [r]]] (Vr r))]
    (- (T3-spherical m) Vs)))

(((partial 1) (L3-central 'm V)) spherical-state)

(((partial 2) (L3-central 'm V)) spherical-state)
