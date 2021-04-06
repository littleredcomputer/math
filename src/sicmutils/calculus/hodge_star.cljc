;;
;; Copyright © 2021 Sam Ritchie.
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

(ns sicmutils.calculus.hodge-star
  (:refer-clojure :exclude [+ - * /])
  (:require [sicmutils.calculus.basis :as b]
            [sicmutils.calculus.form-field :as ff]
            [sicmutils.function :as f]
            [sicmutils.generic :as g :refer [+ - * /]]
            [sicmutils.matrix :as matrix]
            [sicmutils.structure :as s]
            [sicmutils.util.permute :as permute]))

;; ## Hodge-star dual
;;
;; This namespace holds functions from hodge-star.scm and gram-schmidt.scm in
;; scmutils.
;;
;; ## Gram-Schmidt orthonormalization process

(defn Gram-Schmidt [vector-basis metric]
  (letfn [(make-positive [x]
            (g/sqrt (g/square x)))
          (normalize [v]
            (* (/ 1 (g/sqrt (make-positive (metric v v))))
               v))]
    (let [vects (flatten vector-basis)
          e0    (normalize (first vects))]
      (loop [ins (rest vects)
             outs [e0]]
        (if (empty? ins)
          (s/down* outs)
          (recur (rest ins)
                 (conj outs
                       (normalize
                        (- (first ins)
                           (apply + (map (fn [outv]
                                           (* (metric (first ins) outv)
                                              outv))
                                         outs)))))))))))

(defn orthonormalize [basis metric coordinate-system]
  (let [ovb (Gram-Schmidt (b/basis->vector-basis basis) metric)]
    (b/make-basis ovb (b/vector-basis->dual ovb coordinate-system))))

(defn- list-difference
  "Returns a new list containing all elements in `l1` not present in `l2`.
  Duplicates are allowed in the return value."
  [l1 l2]
  (remove (into #{} l2) l1))

(defn Hodge-star
  "Takes a `metric` and a `spec` and returns the [Hodge star
  operator](https://en.wikipedia.org/wiki/Hodge_star_operator) (actually just a
  function, but I suspect this should be a proper operator!)

  `spec` may be:

  - a coordinate system with an orthonormal basis
  - an orthonormal basis
  - a basis

  if the spec is a basis that needs to be orthonormalized, the optional
  `:orthonormalize?` keyword argument must be a coordinate system."
  [metric spec & {:keys [orthonormalize?]
                  :or {orthonormalize? false}}]
  (let [basis (if (b/basis? spec)
                (if orthonormalize?
                  ;; orthonormalize? must be a coordinate system...
                  (orthonormalize spec metric orthonormalize?)
                  spec)
                ;; spec must be a coordinate system if it's not a basis.
                (if orthonormalize?
                  (orthonormalize (b/coordinate-system->basis spec)
                                  metric
                                  spec)
                  (b/coordinate-system->basis spec)))
        on-vector-basis  (flatten (b/basis->vector-basis basis))
        on-oneform-basis (flatten (b/basis->oneform-basis basis))
        basis-check (matrix/by-rows*
                     (map (fn [ei]
                            (map (fn [ej]
                                   (metric ei ej))
                                 on-vector-basis))
                          on-vector-basis))
        bsigns (matrix/diagonal basis-check)]
    (fn the-star [pform-field]
      (assert (or (f/function? pform-field)
                  (ff/form-field? pform-field)))
      (let [p (ff/get-rank pform-field)]
        (if (zero? p)
          (* pform-field (apply ff/wedge on-oneform-basis))
          (let [pvect-basis-lists (permute/combinations on-vector-basis p)
                coeffs (map (fn [pvect]
                              (apply pform-field pvect))
                            pvect-basis-lists)
                pform-basis-lists (permute/combinations on-oneform-basis p)
                n-p:form-basis-lists (map (fn [onefbl]
                                            (list-difference on-oneform-basis onefbl))
                                          pform-basis-lists)
                n-p:basis (map (fn [n-p:basis-list]
                                 (apply ff/wedge n-p:basis-list))
                               n-p:form-basis-lists)
                signs (map (fn [bsign-list p:basis-list n-p:basis-list]
                             (* (apply * bsign-list)
                                (permute/permutation-parity
                                 (concat p:basis-list n-p:basis-list)
                                 on-oneform-basis)))
                           (permute/combinations bsigns p)
                           pform-basis-lists
                           n-p:form-basis-lists)]
            (apply + (map * signs coeffs n-p:basis))))))))
