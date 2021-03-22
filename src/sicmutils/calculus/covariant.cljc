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

(ns sicmutils.calculus.covariant
  (:require [sicmutils.calculus.basis :as b]
            [sicmutils.calculus.form-field :as ff]
            [sicmutils.calculus.vector-field :as vf]
            [sicmutils.function :as f]
            [sicmutils.generic :as g]
            [sicmutils.operator :as o]
            [sicmutils.structure :as s]
            [sicmutils.util :as u]
            [sicmutils.util.aggregate :as ua]
            [sicmutils.value :as v]))

;; This comes from `Lie.scm`.

(defn- vector-field-Lie-derivative [X]
  (o/make-operator
   (fn [Y]
     (cond (f/function? Y)      (X Y)
           (vf/vector-field? Y) (o/commutator X Y)
           (ff/form-field? Y)
           (let [k (ff/get-rank Y)
                 op (fn [& vectors]
                      (assert (= k (count vectors))
                              `(~'≠ ~k ~(count vectors)
                                ~@vectors
                                ~@(map meta vectors)))
                      (g/- ((g/Lie-derivative X) (apply Y vectors))
                           (ua/generic-sum
                            (fn [i]
                              (apply Y (map-indexed (fn [j v]
                                                      (if (= j i)
                                                        ((g/Lie-derivative X) v)
                                                        v))
                                                    vectors)))
                            0 k)))
                 name `((~'Lie-derivative ~(v/freeze X))
                        ~(v/freeze Y))]
             (ff/procedure->nform-field op k name))
           (s/structure? Y)
	         (s/mapr (vector-field-Lie-derivative X) Y)

           :else (u/unsupported "Can't take the Lie derivative of that yet")))
   `(~'Lie-derivative ~(v/freeze X))))

(defmethod g/Lie-derivative [::vf/vector-field] [V]
  (vector-field-Lie-derivative V))

;; ## Interior Product, from interior-product.scm

(defn interior-product [V]
  (assert (vf/vector-field? V))
  (fn [omega]
    (assert (ff/form-field? omega))
    (let [k (ff/get-rank omega)]
      (ff/procedure->nform-field
       (fn [& vectors]
         (assert (= (dec k) (count vectors)))
         (apply omega V vectors))
       (dec k)
       `((~'interior-product ~(v/freeze V)) ~(v/freeze omega))))))

;; ## Covariant Derivative, from covariant-derivative.scm

(defn make-Christoffel
  [symbols basis]
  {:type ::Christoffel
   :symbols symbols
   :basis basis})

(def Christoffel->basis :basis)
(def Christoffel->symbols :symbols)

(defn make-Cartan
  [forms basis]
  {:type ::Cartan
   :forms forms
   :basis basis})

(def Cartan->basis :basis)
(def Cartan->forms :forms)

(defn Christoffel->Cartan
  [Christoffel]
  (assert (= (:type Christoffel) ::Christoffel))
  (let [basis (Christoffel->basis Christoffel)
        Christoffel-symbols (Christoffel->symbols Christoffel)]
    (make-Cartan (g/* Christoffel-symbols (b/basis->oneform-basis basis))
                 basis)))

(defn Cartan-transform
  [cartan basis-prime]
  (let [basis (Cartan->basis cartan) ;; tuple of basis vectors
        forms (Cartan->forms cartan)
        prime-dual-basis (b/basis->oneform-basis basis-prime)
        prime-vector-basis (b/basis->vector-basis basis-prime)
        vector-basis (b/basis->vector-basis basis)
        oneform-basis (b/basis->oneform-basis basis)
        J-inv (s/mapr oneform-basis prime-vector-basis)
        J (s/mapr prime-dual-basis vector-basis)
        omega-prime-forms (ff/procedure->oneform-field
                           (fn [u]
                             (g/+ (g/* J (u J-inv))
                                  (g/* J (g/* (forms u) J-inv))))
                           'omega-prime-forms)]
    (make-Cartan omega-prime-forms basis-prime)))


(defn ^:private covariant-derivative-vector
  [Cartan]
  (let [basis (Cartan->basis Cartan)
        Cartan-forms (Cartan->forms Cartan)
        vector-basis (b/basis->vector-basis basis)
        oneform-basis (b/basis->oneform-basis basis)]
    (fn [V]
      (let [CV (Cartan-forms V)]
        (fn [U]
          (let [u-components (oneform-basis U)
                deriv-components (g/+ (V u-components)
                                      (g/* CV u-components))]
            (vf/procedure->vector-field
             #(g/* (vector-basis %) deriv-components)
             `((~'nabla ~(v/freeze V)) ~(v/freeze U)))))))))

(defn ^:private covariant-derivative-form
  [Cartan]
  (fn [V]
    (fn [tau]
      (let [k (ff/get-rank tau)
            nabla_V ((covariant-derivative-vector Cartan) V)]
        (ff/procedure->nform-field
         (fn [& vectors]
           (assert (= k (count vectors)))
           (g/- (V (apply tau vectors))
                (reduce g/+ (for [i (range k)]
                              (apply tau (map-indexed (fn [j v]
                                                        (if (= j i)
                                                          (nabla_V v)
                                                          v))
                                                      vectors))))))
         k
         `((~'nabla ~(v/freeze V)) ~(v/freeze tau)))))))

(defn ^:private covariant-derivative-function
  [Cartan]
  (fn [X]
    (fn [f]
      (fn [& args]
        (u/unsupported "Covariant derivative of a function (need to analyze type)")))))

(defn ^:private covariant-derivative-ordinary
  [Cartan]
  (assert (= (:type Cartan) ::Cartan))
  (fn [X]
    (o/make-operator
     (fn nabla_X [V]
       (cond (vf/vector-field? V)
             (((covariant-derivative-vector Cartan) X) V)

             (ff/form-field? V)
             (((covariant-derivative-form Cartan) X) V)

             (s/structure? V)
             (s/mapr nabla_X V)

             (fn? V)
             (((covariant-derivative-function Cartan) X) V)

             :else
             (u/unsupported
              (str "Can't do this kind of covariant derivative yet " (v/freeze X) " @ " (v/freeze V)))))
     `(~'nabla ~(v/freeze X)))
    ))


(defn covariant-derivative
  ([Cartan]
   (covariant-derivative-ordinary Cartan))
  ([Cartan map]
   (u/unsupported "Can't compute covariant derivatives over maps yet")))
