(ns io.github.evenmoreirrelevance.magic
  (:require [clojure.core :as clojure])
  (:import (java.util.stream Gatherer Gatherers Gatherer$Downstream)))

#_"
in 1.11.4 you can get away with just implementing IFn, 
but for whatever reason in 1.12.0 the compiler screws up."
(definterface Reducer 
  (rfInvoke [])
  (rfInvoke [out])
  (rfInvoke [acc in]))

(defonce ^:private unset-acc (Object.))

(deftype ^:private GathererXf
         [^Gatherer g
          rf
          ^:unsynchronized-mutable state
          ^:unsynchronized-mutable acc]
  Gatherer$Downstream
  (isRejecting [_]
    (reduced? acc))
  (push [self in]
    (when (identical? acc unset-acc)
      (throw (IllegalStateException. "step with no initialization.")))
    (when-not (.isRejecting self) (set! acc (rf acc in)))
    (not (.isRejecting self)))
  Reducer
  (rfInvoke [_]
    (rf))
  (rfInvoke [self out]
    (let [res (do (.accept (.finisher g) state (do (set! acc out) self))
                  (rf acc))]
      (set! state (.get (.initializer g)))
      (set! acc unset-acc)
      res))
  (rfInvoke [self new-acc in]
    (let [consume-more? (.integrate (.integrator g) state in (do (set! acc new-acc) self))
          res (cond-> acc (not consume-more?) (ensure-reduced))]
      (set! acc unset-acc)
      res)))

(defprotocol ->Xf
  (xf* [self] "
A single step towards coercing `self` to a transducer-outputting IFn or Gatherer."))

(defn xf
  "
Outputs the composition of all the arguments coerced to xfs.
More precisely `IFn`s are passed through, `Gatherer`s are adapted
and anything else is passed through xf* until needed."
  {:inline (fn [& gs] `(comp ~@(for [g gs] `(@#'xf ~g))))}
  ([g]
   (cond
     (ifn? g)
     g
     (instance? Gatherer g)
     (fn [rf]
       (let [^Gatherer g g
             out ^Reducer (->GathererXf g rf (.get (.initializer g)) unset-acc)]
         (fn
           ([] (.rfInvoke out))
           ([out_] (.rfInvoke out out_))
           ([acc in] (.rfInvoke out acc in)))))
     :else
     (recur (xf* g))))
  ([g & gs]
   (apply comp (map xf (cons g gs)))))

(comment
  (defn gtake-while
    [pred]
    (Gatherer/of
     (reify java.util.stream.Gatherer$Integrator
       (integrate [_ _state in ds]
         (boolean (when (pred in) (.push ds in)))))))

  (let [integrator (reify java.util.stream.Gatherer$Integrator
                     (integrate [_ *state in ds]
                       (let [state @*state]
                         (and (> state 0)
                              (do (vreset! *state (dec state))
                                  (.push ds in))))))]
    (defn gtake
      [n]
      (reify Gatherer
        (initializer [_] (reify java.util.function.Supplier (get [_] (volatile! n))))
        (integrator [_] integrator))))

  (into [] (xf
            (take 7)
            (gtake-while #(< % 10))
            (Gatherers/windowSliding 3)
            (gtake 3)
            (gtake 2)
            (map #(cons :lol %)))
        [1 2 3 4 5 6 69 75])
  
  (do
    (require '[criterium.core :as criterium])
    (let [r (into [] (range 10000000))]
      (criterium/quick-bench (into [] (xf (gtake 100000)) r))
      (criterium/quick-bench (into [] (take 100000) r))))
  )
