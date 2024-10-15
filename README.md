
[![Clojars Project](https://img.shields.io/clojars/v/io.github.evenmoreirrelevance/magic.svg)](https://clojars.org/io.github.evenmoreirrelevance/magic)

Stream gatherers are just transducers dipped in Java sauce.
It's only fair for Clojure to be able to use them as ergonomically
as the original.

## Usage
Just look at the comments in the source, it's quite easy really.
`xf` is a drop-in replacement for `comp` which also accepts Gatherer objects;
`xf*` is your extension point to introduce more coercions.

Note that while most of the behavior is exactly the same in a naive
translation between transducer-producers and Gatherer-xfs, Gatherer-xfs always implicitly honor `reduced`, which leads to immediate and unavoidable short-circuiting when a `reduced` is snuck in as an `acc` e.g. by passing it in as the initializer to a reduction. 

This library is meant to facilitate the usage of existing Gatherers where the equivalent Clojure transducers aren't readily available so this shouldn't really be an issue, but it is a useful heads up should you want e.g. to manually re-implement a Gatherer as a transducer.