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

(ns sicmutils.sr.frames
  (:refer-clojure :exclude [+ - * /])
  (:require [sicmutils.calculus.frame :as cf]
            [sicmutils.calculus.manifold :as m]
            [sicmutils.sr.boost :as b]
            [sicmutils.generic :as g :refer [+ - * /]]
            [sicmutils.structure :as s]
            [sicmutils.util :as u]))

;; ## Special-relativity frames
;;
;; A frame is defined by a Poincare transformation from a given background
;; 4-space frame (the "ancestor-frame"). The transformation is specified by a
;; boost magnitude and a unit-vector boost direction, relative to the ancestor
;; frame, and the position of the origin of the frame being defined in the
;; ancestor frame.

;; The events are absolute, in that it is always possible to compare them to
;; determine if two are the same. They will be represented with coordinates
;; relative to some arbitrary absolute frame,
;; "the-ether".
;;
;; To keep us from going nuts, an SR frame has a name, which it uses to label
;; coordinates in its frame.

(defn make-SR-coordinates [frame four-tuple]
  {:pre [(s/up? four-tuple)
         (= (count four-tuple) 4)]}
  (-> four-tuple
      (vary-meta assoc ::SR-coordinates? true)
      (cf/claim frame)))

(defn SR-coordinates? [coords]
  (::SR-coordinates? (meta coords) false))

(defn SR-name [coords]
  (cf/frame-name
   (cf/frame-owner coords)))

;; ### SR frames

(defn- coordinates->event
  [ancestor-frame this-frame
   {:keys [boost-direction vc origin]}]
  {:pre [(= (cf/frame-owner origin) ancestor-frame)]}
  (fn c->e [coords]
    {:pre [(SR-coordinates? coords)]}
    ((m/point ancestor-frame)
     (make-SR-coordinates ancestor-frame
                          (+ ((b/general-boost2 boost-direction vc)
                              coords)
                             origin)))))

(defn- event->coordinates
  [ancestor-frame this-frame
   {:keys [boost-direction vc origin]}]
  {:pre [(= (cf/frame-owner origin) ancestor-frame)]}
  (fn e->c [event]
    {:pre [(cf/event? event)]}
    (let [coords ((b/general-boost2 (- boost-direction) vc)
                  (- ((m/chart ancestor-frame) event)
                     origin))]
      (make-SR-coordinates this-frame coords))))


(let [make (cf/frame-maker coordinates->event event->coordinates)]
  (defn make-SR-frame [name ancestor-frame boost-direction v-over-c origin]
    (make name ancestor-frame
          {:boost-direction boost-direction
           :vc v-over-c
           :origin origin})))

;; ### The background frame

(defn- base-frame-point [ancestor-frame this-frame _]
  (fn [coords]
    {:pre [(SR-coordinates? coords)
           (= this-frame (cf/frame-owner coords))]}
    (cf/make-event coords)))

(defn- base-frame-chart [ancestor-frame this-frame _]
  (fn [event]
    {:pre [(cf/event? event)]}
    (make-SR-coordinates this-frame event)))

(def the-ether
  ((cf/frame-maker base-frame-point base-frame-chart)
   'the-ether 'the-ether))

(defn boost-direction [frame]
  (:boost-direction (cf/params frame)))

(defn v:c [frame]
  (:vc (cf/params frame)))

(defn coordinate-origin [frame]
  (:origin (cf/params frame)))

(defn add-v:cs [v1:c v2:c]
  (/ (+ v1:c v2:c)
     (+ 1 (* v1:c v2:c))))

(defn add-velocities
  "velocities must be in meters/second, since we don't yet have units support."
  [v1 v2]
  (/ (+ v1 v2)
     (+ 1 (* (/ v1 'C)
             (/ v2 'C)))))
