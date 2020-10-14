;;
;; Copyright © 2020 Sam Ritchie.
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

(ns sicmutils.numerical.interpolate.richardson
  "Richardson interpolation is a special case of polynomial interpolation; knowing
  the ratios of successive `x` coordinates in the point sequence allows a more
  efficient calculation."
  (:require [sicmutils.numerical.interpolate.polynomial :as ip]
            [sicmutils.generic :as g]
            [sicmutils.util :as u]
            [sicmutils.util.aggregate :as ua]
            [sicmutils.util.stream :as us]
            [sicmutils.value :as v]))

;; ## Richardson Interpolation
;;
;; This approach (and much of this numerical library!) was inspired by Gerald
;; Sussman's ["Abstraction in Numerical
;; Methods"](https://dspace.mit.edu/bitstream/handle/1721.1/6060/AIM-997.pdf?sequence=2)
;; paper.
;;
;; That paper builds up to Richardson interpolation as a method of ["series
;; acceleration"](https://en.wikipedia.org/wiki/Series_acceleration). The
;; initial example concerns a series of the side lengths of an N-sided polygon
;; inscribed in a unit circle.
;;
;; The paper derives this relationship between the sidelength of an N- and
;; 2N-sided polygon:

(defn- refine-by-doubling
  "`s` is the side length of an N-sided polygon inscribed in the unit circle. The
  return value is the side length of a 2N-sided polygon."
  [s]
  (/ s (g/sqrt (+ 2 (g/sqrt (- 4 (g/square s)))))))

;; If we can increase the number of sides => infinity, we should reach a circle.
;; The "semi-perimeter" of an N-sided polygon is
;;
;; $$P_n = {n \over 2} S_n$$
;;
;; In code:

(defn- semi-perimeter
  "Returns the semi-perimeter length of an `n`-sided regular polygon with side
  length `side-len`."
  [n side-len]
  (* (/ n 2) side-len))

;; so as $n \to \infty$, $P_n$ should approach $\pi$, the half-perimeter of a
;; circle.
;;
;; Let's start with a square, ie, $n = 4$ and $s_4 = \sqrt{2}$. Clojure's
;; `iterate` function will let us create an infinite sequence of side lengths:

(def ^:private side-lengths
  (iterate refine-by-doubling (Math/sqrt 2)))

;; and an infinite sequence of the number of sides:

(def ^:private side-numbers
  (iterate #(* 2 %) 4))

;; Mapping a function across two sequences at once generates a new infinite
;; sequence, of semi-perimeter lengths in this case:

(def ^:private archimedean-pi-sequence
  (map semi-perimeter side-numbers side-lengths))

;; I don't have a nice way of embedding the sequence in a notebook, but the
;; following code will print the first 20 terms:

#_
(us/pprint 20 archimedean-pi-sequence)

;; Unfortunately (for Archimedes, by hand!), as the paper notes, it takes 26
;; iterations to converge to machine precision:

#_
(= (-> archimedean-pi-sequence
       (us/seq-limit {:tolerance v/machine-epsilon}))

   {:converged? true
    :terms-checked 26
    :result 3.1415926535897944})

;; Enter Sussman: "Imagine poor Archimedes doing the arithmetic by hand: square
;; roots without even the benefit of our place value system! He would be
;; interested in knowing that full precision can be reached on the fifth term,
;; by forming linear combinations of the early terms that allow the limit to be
;; seized by extrapolation." (p4, Abstraction in Numerical Methods).
;;
;; Sussman does this by noting that you can also write the side length as:
;;
;; $$S_n = 2 \sin {\pi \over n}$$
;;
;; Then the taylor series expansion for $P_n$ becomes:
;;
;; $$
;;  P_n = {n \over 2} S_n \
;;      = {n \over 2} 2 \sin {\pi \over n} \
;;      = \pi + {A\ over n^2} + B \over n^4 ...
;; $$
;;
;; A couple things to note:
;;
;; - At large N, the $A \over n^2$ term dominates the truncation error.
;; - when we double $n$ by taking $P_n$, that term becomes $A \over {4 n^2}$, 4x
;;   smaller.
;;
;; The big idea is to multiply $P_{2n}$ by 4 and subtract $P_n$ (then divide by
;; 3 to cancel out the extra factor). This will erase the $A \over n^2$ term and
;; leave a /new/ sequence with $B \over n^4$ as the dominant error term.
;;
;; Now keep going and watch the error terms drain away.
;;
;; Before we write code, let's follow the paper's example and imagine instead
;; some general sequence of $R(h), R(h/t), R(h/t^2)...$ (where $t = 2$ in the
;; example above), with a power series expansion that looks like
;;
;; $$R(h) = A + B h^{p_1} + C h^{p_2}...$$
;;
;; where the exponents $p_1, p_2, ...$ are some OTHER series of error
;; growth. (In the example above, because the taylor series expanson of $n \sin
;; n$ only has even factors, the sequence was the even numbers.)
;;
;; In that case, the general way to cancel error between successive terms is:
;;
;; $${R(h/t) - t^{p_1} R(h)} = {t^{p_1} - 1} A + C_1 h^{p_2} + ...$$
;;
;; or:
;;
;; $${R(h/t) - t^{p_1} R(h)} \over {t^{p_1} - 1} = A + C_2 h^{p_2} + ...$$
;;
;; Let's write this in code:

(defn- accelerate-sequence
  "Generates a new sequence by combining each term in the input sequence `xs`
  pairwise according to the rules for richardson acceleration.

  `xs` is a sequence of evaluations of some function of $A$ with its argument
  smaller by a factor of `t` each time:

  $$A(h), A(h/t), ...$$

  `p` is the order of the dominant error term for the sequence."
  [xs t p]
  (let [t**p   (Math/pow t p)
        t**p-1 (dec t**p)]
    (map (fn [ah ah-over-t]
           (/ (- (* t**p ah-over-t) ah)
              t**p-1))
         xs
         (rest xs))))

;; If we start with the original sequence, we can implement Richardson
;; extrapolation by using Clojure's `iterate` with the `accelerate-sequence`
;; function to generate successive columns in the "Richardson Tableau". (This is
;; starting to sound familiar to the scheme for polynomial interpolation, isn't
;; it?)
;;
;; To keep things general, let's take a general sequence `ps`, defaulting to the
;; sequence of natural numbers.

(defn- make-tableau
  "Generates the 'tableau' of succesively accelerated Richardson interpolation
  columns."
  ([xs t] (make-tableau xs t (iterate inc 1)))
  ([xs t ps]
   (->> (iterate (fn [[xs [p & ps]]]
                   [(accelerate-sequence xs t p) ps])
                 [xs ps])
        (map first)
        (take-while seq))))

;; All we really care about are the FIRST terms of each sequence. These
;; approximate the sequence's final value with small and smaller error (see the
;; paper for details).
;;
;; Polynomial interpolation in `polynomial.cljc` has a similar tableau
;; structure (not by coincidence!), so we can use `ip/first-terms` in the
;; implementation below to fetch this first row.
;;
;; Now we can put it all together into a sequence transforming function, with
;; nice docs:

(defn richardson-sequence
  "Takes:

  - `xs`: a (potentially lazy) sequence of points representing function values
  generated by inputs continually decreasing by a factor of `t`. For example:
  `[f(x), f(x/t), f(x/t^2), ...]`
  - `t`: the ratio between successive inputs that generated `xs`.

  And returns a new (lazy) sequence of 'accelerated' using [Richardson
  extrapolation](https://en.wikipedia.org/wiki/Richardson_extrapolation) to
  cancel out error terms in the taylor series expansion of `f(x)` around the
  value the series to which the series is trying to converge.

  Each term in the returned sequence cancels one of the error terms through a
  linear combination of neighboring terms in the sequence.

  ### Custom P Sequence

  The three-arity version takes one more argument:

  - `p-sequence`: the orders of the error terms in the taylor series expansion
  of the function that `xs` is estimating. For example, if `xs` is generated
  from some `f(x)` trying to approximate `A`, then `[p_1, p_2...]` etc are the
  correction terms:

    $$f(x) = A + B x^{p_1} + C x^{p_2}...$$

  The two-arity version uses a default `p-sequence` of `[1, 2, 3, ...]`

  ### Arithmetic Progression

  The FOUR arity version takes `xs` and `t` as before, but instead of
  `p-sequence` makes the assumption that `p-sequence` is an arithmetic
  progression of the form `p + iq`, customized by:

  - `p`: the exponent on the highest-order error term
  - `q`: the step size on the error term exponent for each new seq element

  ## Notes

  Richardson extrapolation is a special case of polynomial extrapolation,
  implemented in `polynomial.cljc`.

  Instead of a sequence of `xs`, if you generate an explicit series of points of
  the form `[x (f x)]` with successively smaller `x` values and
  polynomial-extrapolate it forward to x == 0 (with,
  say, `(polynomial/modified-neville xs 0)`) you'll get the exact same result.

  Richardson extrapolation is more efficient since it can make assumptions about
  the spacing between points and pre-calculate a few quantities. See the
  namespace for more discussion.

  References:

  - Wikipedia: https://en.wikipedia.org/wiki/Richardson_extrapolation
  - GJS, 'Abstraction in Numerical Methods': https://dspace.mit.edu/bitstream/handle/1721.1/6060/AIM-997.pdf?sequence=2"
  ([xs t]
   (ip/first-terms
    (make-tableau xs t)))
  ([xs t p-sequence]
   (ip/first-terms
    (make-tableau xs t p-sequence)))
  ([xs t p q]
   (let [arithmetic-p-q (iterate #(+ q %) p)]
     (richardson-sequence xs t arithmetic-p-q))))

;; We can now call this function, combined with `us/seq-limit` (a
;; general-purpose tool that takes elements from a sequence until they
;; converge), to see how much acceleration we can get:

#_
(= (-> (richardson-sequence archimedean-pi-sequence 2 2 2)
       (us/seq-limit {:tolerance v/machine-epsilon}))

   {:converged? true
    :terms-checked 7
    :result 3.1415926535897936})

;; Much faster!
;;
;; ## Richardson Columns
;;
;; Richardson extrapolation works by cancelling terms in the error terms of a
;; function's taylor expansion about `0`. To cancel the nth error term, the nth
;; derivative has to be defined. Non-smooth functions aren't going to play well
;; with `richardson-sequence` above.
;;
;; The solution is to look at specific /columns/ of the Richardson tableau. Each
;; column is a sequence with one further error term cancelled.
;;
;; `rational.cljc` and `polynomial.cljc` both have this feature in their
;; tableau-based interpolation functions. The feature here requires a different
;; function, because the argument vector is a bit crowded already in
;; `richardson-sequence` above.

(defn richardson-column
  "Function with an identical interface to `richardson-sequence` above, except for
  an additional second argument `col`.

  `richardson-column` will return that /column/ offset the interpolation tableau
  instead of the first row. This will give you a sequence of nth-order
  Richardson accelerations taken between point `i` and the next `n` points.

  As a reminder, this is the shape of the Richardson tableau:

   p0 p01 p012 p0123 p01234
   p1 p12 p123 p1234 .
   p2 p23 p234 .     .
   p3 p34 .    .     .
   p4 .   .    .     .

  So supplying a `column` of `1` gives a single acceleration by combining points
  from column 0; `2` kills two terms from the error sequence, etc.

  NOTE Given a better interface for `richardson-sequence`, this function could
  be merged with that function."
  ([xs col t]
   (nth (make-tableau xs t) col))
  ([xs col t p-seq]
   (nth (make-tableau xs t p-seq) col))
  ([xs col t p q]
   (let [arithmetic-p-q (iterate #(+ q %) p)]
     (richardson-column xs col t arithmetic-p-q))))


;; ## Richardson Extrapolation and Polynomial Extrapolation
;;
;; It turns out that the Richardson extrapolation is a special case of
;; polynomial extrapolation using Neville's algorithm (as described in
;; `polynomial/neville`), evaluated at x == 0.
;;
;; Neville's algorithm looks like this:
;;
;; $$P(x) = [(x - x_r) P_l(x) - (x - x_l) P_r(x)] / [x_l - x_r]$$
;;
;; Where:

;; - $P(x)$ is a polynomial estimate from some sequence of points $(a, b, c,
;;  ...)$ where a point $a$ has the form $(x_a, f(x_a))$
;; - $x_l$ is the coordinate of the LEFTmost point, $x_a$
;; - $x_r$ is the rightmost point, say, $x_c$ in this example
;; - $x$ is the coordinate where we want to evaluate $P(x)$
;; - $P_l(x)$ is the estimate with all points but the first, ie, $P_{bc}(x)$
;; - $P_l(x)$ is the estimate with all points but the LAST, ie, $P_{ab}(x)$
;;
;; Fill in $x = 0 and rearrange$:
;;
;; $$P(0) = [(x_l P_r(0)) - (x_r P_l(x))] \over [x_l - x_r]$$
;;
;; In the richardson extrapolation scheme, one of our parameters was `t`, the
;; ratio between successive elements in the sequence. Now multiply through by $1
;; = {1 \over x_r} \over {1 \over x_r}$ so that our formula contains ratios:
;;
;; $$P(0) = [({x_l \over x_r} P_r(0)) - P_l(x)] \over [{x_l \over x_r} - 1]$$
;;
;; Because the sequence of $x_i$ elements looks like $x, x/t, x/t^2$, every
;; recursive step separates $x_l$ and $x_r$ by another factor of $t$. So
;;
;; $${x_l \over x_r} = {x \over {x \over t^n}} = t^n$$
;;
;; Where $n$ is the difference between the positions of $x_l$ and $x_r$. So the formula simplifies further to:
;;
;; $$P(0) = [({t^n} P_r(0)) - P_l(x)] \over [{t^n} - 1]$$
;;
;; Now it looks exactly like Richardson extrapolation. The only difference is
;; that Richardson extrapolation leaves `n` general (and calls it $p_1, p_2$
;; etc), so that you can customize the jumps in the error series. (I'm sure
;; there is some detail I'm missing here, so please feel free to make a PR and
;; jump in!)
;;
;; For the example above, we used a geometric series with $p, q = 2$ to fit the
;; archimedean $\pi$ sequence. Another way to think about this is that we're
;; fitting a polynomial to the SQUARE of `h` (the side length), not to the
;; actual side length.
;;
;; Let's confirm that polynomial extrapolation to 0 gives the same result, if we
;; generate squared $x$ values:

#_
(let [h**2 (fn [i]
             ;; (1/t^{i + 1})^2
             (-> (/ 1 (Math/pow 2 (inc i)))
                 (Math/pow 2)))
      xs (map-indexed (fn [i fx] [(h**2 i) fx])
                      archimedean-pi-sequence)]
  (= (us/seq-limit
      (richardson-sequence archimedean-pi-sequence 4 1 1))

     (us/seq-limit
      (ip/modified-neville xs 0.0))))

;; Success!
