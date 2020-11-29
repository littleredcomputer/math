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

(ns sicmutils.expression.analyze-test
  (:require [clojure.test :refer [is deftest testing]]
            [clojure.string :as cs]
            [sicmutils.expression.analyze :as a]))

(deftest symbol-generator
  (let [gen (a/monotonic-symbol-generator "cake")
        symbols (repeatedly 1000 gen)]
    (is (= symbols (sort symbols))
        "Generated symbols sort into the same order in which they were
        generated.")

    (is (every? #(cs/starts-with? (str %) "cake")
                symbols)
        "The prefix gets prepended to every generated symbol.")))
