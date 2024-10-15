
[![Clojars Project](https://img.shields.io/clojars/v/io.github.evenmoreirrelevance/magic.svg)](https://clojars.org/io.github.evenmoreirrelevance/magic)

Stream gatherers are just transducers dipped in Java sauce.
It's only fair for Clojure to be able to use them as ergonomically
as the original.

## Usage
Just look at the comments in the source, it's quite easy really.
`xf` is a drop-in replacement for `comp` which also accepts Gatherer objects;
`xf*` is your extension point to introduce more coercions.