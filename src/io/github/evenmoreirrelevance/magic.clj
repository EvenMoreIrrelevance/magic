(ns io.github.evenmoreirrelevance.magic
  (:refer-clojure :exclude [comp])
  (:require [clojure.core :as clojure])
  (:import (java.util.stream Gatherer Gatherers Gatherer$Downstream)))

(defonce unset-downstream (Object.))

(definterface Xf
  (xfInvoke [])
  (xfInvoke [out])
  (xfInvoke [acc in]))

(deftype GathererXf [^Gatherer g
                     rf
                     ^:unsynchronized-mutable state
                     ^:unsynchronized-mutable acc]
  Gatherer$Downstream
  (isRejecting [_]
    (reduced? acc))
  (push [self in]
    (when (identical? state unset-downstream)
      (throw (IllegalStateException. "trying to access unset downstream...")))
    (when-not (.isRejecting self)
      (set! acc (rf acc in)))
    (not (.isRejecting self)))
  Xf
  (xfInvoke [_]
    (set! acc (rf)))
  (xfInvoke [self out]
    (let [res (do (.accept (.finisher g) state (do (set! acc out) self))
                  (rf acc))
          _ (set! state (.get (.initializer g)))
          _ (set! acc unset-downstream)]
      res))
  (xfInvoke [self new-acc in]
    (.integrate (.integrator g) state in (do (set! acc new-acc) self))
    acc))

(defn gxf [^Gatherer g]
  (fn [rf]
    (let [out (->GathererXf g rf (.get (.initializer g)) unset-downstream)]
      (fn
        ([] (.xfInvoke out))
        ([out_] (.xfInvoke out out_))
        ([acc in] (.xfInvoke out acc in))))))

(defn comp
  "
Automaatically converts gatherers into their xf equivalents."
  [& xfs-or-gs]
  (apply clojure/comp (map #(cond-> % (instance? Gatherer %) (gxf)) xfs-or-gs)))

(defn gtake-while
  [pred]
  (Gatherer/of
   (reify java.util.stream.Gatherer$Integrator
     (integrate [_ _state in ds]
       (boolean (when (pred in) (.push ds in)))))))

(let [integrator (reify java.util.stream.Gatherer$Integrator
                   (integrate [_ *state in ds]
                     (let [state @*state]
                       (boolean
                        (when (> state 0)
                          (vreset! *state (dec state))
                          (.push ds in))))))]
  (defn gtake
    [n]
    (reify Gatherer
      (initializer [_] (reify java.util.function.Supplier (get [_] (volatile! n))))
      (integrator [_] integrator))))

(into [] (comp
          (take 7)
          (gtake-while #(< % 10))
          (Gatherers/windowSliding 3)
          (gtake 3)
          (gtake 2)
          (map #(cons :lol %)))
      [1 2 3 4 5 6 69 75])

(.get (reify
        java.util.function.Supplier
        (get [_] 3)
        clojure.lang.IFn
        (invoke [_] 4)))
