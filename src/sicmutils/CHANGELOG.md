# Changelog

## 0.13.0 [Unreleased]

The main announcement for this release is _Clojurescript Support!_. Implementing
this resulted in a few upgrades along the way:

- more powerful numerics, specifically `definite-integral` and native
  minimization routines
- a generic numeric tower for Clojurescript
- Many more tests! The test coverage was great before, and it's stayed high as
  we've added new implementations.
- added explicit code coverage metrics via Codecov: [![Codecov branch](https://img.shields.io/codecov/c/github/littleredcomputer/sicmutils/master.svg?maxAge=3600)](https://codecov.io/github/littleredcomputer/sicmutils)

Here are more explicit details on the release.

### Clojurescript Support

Full conversion of SICMUtils to Clojurescript. All functionality from v0.12.1
now works in both Clojure and Clojurescript!

Most of the conversion was straightforward. The major missing piece was a
numeric tower implementation for Clojurescript (complex numbers, ratios) that
bring it up to parity with Clojure:

- Add the `bigfraction` implementation from
  [fraction.js](https://www.npmjs.com/package/fraction.js) sicmutils.ratio for
  cross-platform ratio support (#99)
- Adds CLJS complex number support through [complex.js](https://github.com/infusion/Complex.js) (#41)
- `js/BigInt`, `goog.math.Long` and `goog.math.Integer` implementations round
  out the numeric tower (#45)

### Numerical Routines

The numerical routines in SICMUtils depend heavily on Apache Commons, which of
course only exists in Java. We had to implement much of the numerics code in
native Clojure. It's fast, efficient and functional. Give it a read if you're
curious about how these algorithms work.

- New, native minimization routines have replaced the Apache Commons implementations:

  - **Univariate Minimizers**
    - Port scipy's auto-bracketing + scmutils version (#104)
    - Port golden section search from scipy (#105)
    - Implement Brent's method for fn minimization in native clj (#106)

  - **Multivariate**
    - pure Clojure implementation of Nelder-Mead (#102)

- Native `definite-integral` numerics implementation, written as a series of
  computational essays:

  - **Basics**:
    - [Riemann Sums](https://github.com/littleredcomputer/sicmutils/blob/master/src/sicmutils/numerical/quadrature/riemann.cljc), all the way up through efficient, incremental, "accelerated" versions of these easy-to-understand methods:
    - [Midpoint method](https://github.com/littleredcomputer/sicmutils/blob/master/src/sicmutils/numerical/quadrature/midpoint.cljc), same development but shorter since it reuses functional abstractions. Also incremental, efficient, accelerated
    - [Trapezoid Method](https://github.com/littleredcomputer/sicmutils/blob/master/src/sicmutils/numerical/quadrature/trapezoid.cljc), same idea but for closed intervals.

  - **Sequence Acceleration / Extrapolation Methods**
    - [Polynomial interpolation](https://github.com/littleredcomputer/sicmutils/blob/master/src/sicmutils/numerical/interpolate/polynomial.cljc): the general thing that "richardson extrapolation" is doing below. Historically cool and used to accelerate arbitrary integration sequences
    - [Rational Function extrapolation](https://github.com/littleredcomputer/sicmutils/blob/master/src/sicmutils/numerical/interpolate/rational.cljc): used in bulirsch-stoer integration and ODE solving.
    - "[Richardson extrapolation](https://github.com/littleredcomputer/sicmutils/blob/master/src/sicmutils/numerical/interpolate/richardson.cljc)" is a special case, where we get more efficient by assuming that the x values for the polynomial interpolation go 1, 1/2, 1/4... and that we're extrapolating to 0.

  - **Higher-order Calculus:**
    - [Numerical derivatives](https://github.com/littleredcomputer/sicmutils/blob/master/src/sicmutils/numerical/derivative.cljc): derivatives using three kinds of central difference formulas... accelerated using Richardson extrapolation, with a nice technique for guarding against underflow.
    - [Simpson's Method](https://github.com/littleredcomputer/sicmutils/blob/master/src/sicmutils/numerical/quadrature/simpson.cljc)... fit a parabola to every slice. OR, "accelerate" the trapezoid method with one step of Richarsdson extrapolation!
    - [Simpson's 3/8 Method](https://github.com/littleredcomputer/sicmutils/blob/master/src/sicmutils/numerical/quadrature/simpson38.cljc): Same idea, but accelerate a sequence that triples its slices every iteration.
    - [Boole's Rule](https://github.com/littleredcomputer/sicmutils/blob/master/src/sicmutils/numerical/quadrature/boole.cljc): trapezoid method plus two steps of Richardson extrapolation. (Are you starting to see the pattern??)
    - [Romberg Integration](https://github.com/littleredcomputer/sicmutils/blob/master/src/sicmutils/numerical/quadrature/romberg.cljc): midpoint OR trapezoid, with as many steps of Richardson extrapolation as we can take!
    - [Milne's Rule](https://github.com/littleredcomputer/sicmutils/blob/master/src/sicmutils/numerical/quadrature/milne.cljc), MIDPOINT method, one step of extrapolation!
    - [Bulirsch-Stoer integration](https://github.com/littleredcomputer/sicmutils/blob/master/src/sicmutils/numerical/quadrature/bulirsch_stoer.cljc)... midpoint or trapezoid, with rational function extrapolation, as many steps as we can handle AND some custom step sizes.

  - **Combinators**:
    - [Variable Substitutions](https://github.com/littleredcomputer/sicmutils/blob/master/src/sicmutils/numerical/quadrature/substitute.cljc): implemented as functional wrappers that take an integrator and return a modified integrator.
    - [Improper Integrals](https://github.com/littleredcomputer/sicmutils/blob/master/src/sicmutils/numerical/quadrature/infinite.cljc): a template for a combinator that enables infinite endpoints on any integrator, using variable substitution on an appropriate, tunable range.
    - [Adaptive Integration](https://github.com/littleredcomputer/sicmutils/blob/master/src/sicmutils/numerical/quadrature/adaptive.cljc): a combinator that turns any of the integrators above into an "adaptive" integrator that's able to focus in on difficult regions.
  - And finally, "[Numerical Quadrature](https://github.com/littleredcomputer/sicmutils/blob/master/src/sicmutils/numerical/quadrature.cljc)", the namespace/essay that ties it all together.

- `sicmutils.numerical.compile` uses [SCI](https://github.com/borkdude/sci), the
  Small Clojure Interpreter, to generate compiled numerical code (#133)

- Implemented ODE solving using @littleredcomputer's
  [odex-js](https://github.com/littleredcomputer/odex-js) library (#135)

### Reader Literals

[data_readers.cljc](https://github.com/littleredcomputer/sicmutils/blob/master/src/data_readers.cljc)
provides 3 new data reader literals:

- `#sicm/ratio`

Use this with a ratio literal, like `#sicm/ratio 1/2`, or with a string like
`#sicm/ratio "1/4"`. If the denominator is `1` this literal will return a
`js/BigInt` in Clojurescript, or a Long in Clojure.

- `#sicm/bigint`

Use with a number literal, like, `#sicm/bigint 10`, or a string like
`#sicm/bigint "10000012"` to generate a `js/BigInt` in Clojurescript, or a
`clojure.lang.BigInt` in Clojure.

- `#sicm/complex`

Currently this only works with a string like `#sicm/complex "1 + 2i"`. In the
future it might work with a pair of `(real, complex)`, like:

    #sicm/complex [1 2]

## [v0.21.1]

- Getting Github releases up to parity with the most recent release to Clojars.

## [v0.10.0]

- Did some refactoring and one breaking rename (Struct became Structure, since
  we don't abbreviate other deftypes). This also marks the point of departure
  for working with Functional Differential Geometry.


## [v0.9.8]

- This is the version that was current as of the talk @littleredcomputer gave at
  [Clojure/west 2017](2017.clojurewest.org), entitled "[Physics in
  Clojure](https://www.youtube.com/watch?v=7PoajCqNKpg)."