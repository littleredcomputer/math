(ns sicmutils.generators
  "test.check generators for the various types in the sicmutils project."
  (:refer-clojure :rename {bigint core-bigint
                           biginteger core-biginteger
                           double core-double
                           long core-long}
                  #?@(:cljs [:exclude [bigint double long]]))
  (:require [clojure.test.check.generators :as gen]
            [same.ish :as si]
            [sicmutils.complex :as c]
            [sicmutils.generic :as g]
            [sicmutils.ratio :as r]
            [sicmutils.util :as u]
            [sicmutils.value :as v])
  #?(:clj
     (:import [org.apache.commons.math3.complex Complex])))

(def bigint
  "js/BigInt in cljs, clojure.lang.BigInt in clj."
  #?(:cljs
     (gen/fmap u/bigint gen/large-integer)
     :clj
     gen/size-bounded-bigint))

#?(:clj
   (def biginteger
     (gen/fmap u/biginteger bigint)))

(def native-integral
  "non-floating-point integers on cljs, Long on clj."
  gen/large-integer)

(def long
  "goog.math.Long in cljs,
  java.lang.Long in clj."
  #?(:clj gen/large-integer
     :cljs (gen/fmap u/long gen/large-integer)))

(def integer
  "goog.math.Integer in cljs, java.lang.Integer in clj."
  (gen/fmap u/int gen/small-integer))

(defn reasonable-double [& {:keys [min max]
                            :or {min -10e5
                                 max 10e5}}]
  (let [[excluded-lower excluded-upper] [-1e-4 1e-4]]
    (gen/one-of [(gen/double* {:infinite? false
                               :NaN? false
                               :min min
                               :max excluded-lower})
                 (gen/double* {:infinite? false
                               :NaN? false
                               :min excluded-upper
                               :max max})])))

(def any-integral
  (gen/one-of [native-integral
               bigint
               long
               integer]))

(def complex
  (gen/let [r (reasonable-double)
            i (reasonable-double)]
    (c/complex r i)))

(def ratio
  "Generates a small ratio (or integer) using gen/small-integer. Shrinks
  toward simpler ratios, which may be larger or smaller."
  (gen/fmap
   (fn [[a b]] (r/rationalize a b))
   (gen/tuple gen/small-integer (gen/fmap inc gen/nat))))

(def big-ratio
  (gen/let [n bigint
            d bigint]
    (let [d (if (v/nullity? d)
              (u/bigint 1)
              d)]
      (r/rationalize n d))))

(def ^:dynamic *complex-tolerance* 1e-12)

(extend-protocol si/Approximate
  #?@(:cljs
      [js/BigInt
       (ish [this that]
            (= this that))])

  #?(:cljs c/complextype :clj Complex)
  (ish [this that]
    (< (g/abs (g/- this that))
       *complex-tolerance*)))
