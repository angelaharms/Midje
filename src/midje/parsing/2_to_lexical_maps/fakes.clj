(ns ^{:doc "An intermediate stage in the compilation of prerequisites."}
  midje.parsing.2-to-lexical-maps.fakes
  (:use midje.clojure.core
        midje.parsing.util.core
        midje.parsing.util.zip
        [midje.util.object-utils :only [object-name]]
        [midje.checkers :only [exactly]]
        [midje.checking.checkers.defining :only [checker? checker-makers]]
        [midje.parsing.1-to-explicit-form.expects :only [expect? up-to-full-expect-form]]
        [midje.util.form-utils :only [map-difference
                                      pred-cond]]
        [midje.checking.extended-equality :only [extended-= extended-list-=]]
        [midje.parsing.util.file-position :only [user-file-position]]
        [midje.util.thread-safe-var-nesting :only [namespace-values-inside-out
                                                   with-altered-roots]]
        [midje.parsing.util.wrapping :only [with-wrapping-target]]
        [clojure.algo.monads :only [defmonad domonad]]
        [midje.parsing.arrow-symbols]
        midje.error-handling.validation-errors
        midje.error-handling.semi-sweet-validations
        [clojure.tools.macro :only [macrolet]])
  (:require [midje.data.metaconstant :as metaconstant]
            [clojure.zip :as zip]
            [midje.config :as config]
            [midje.parsing.util.fnref :as fnref]
            [midje.error-handling.exceptions :as exceptions]
            [midje.parsing.lexical-maps :as lexical-maps]
            [midje.emission.api :as emit])
  (:import midje.data.metaconstant.Metaconstant))

(defn fake? [form]
  (or (first-named? form "fake") 
      (first-named? form "data-fake")))

(defn tag-as-background-fake [fake]
  `(~@fake :background :background :times (~'range 0)))



(defn #^:private
  statically-disallowed-prerequisite-function
  "To prevent people from mocking functions that Midje itself uses,
   we mostly rely on dynamic checking. But there are functions within
   the dynamic checking code that must also not be replaced. These are
   the ones that are known."
  [some-var]
  (#{#'deref #'assoc} some-var))

(defn raise-disallowed-prerequisite-error [function-name]
  (throw
   (exceptions/user-error
    "You seem to have created a prerequisite for"
    (str (pr-str function-name) " that interferes with that function's use in Midje's")
    (str "own code. To fix, define a function of your own that uses "
         (or (:name (meta function-name)) function-name) ", then")
    "describe that function in a provided clause. For example, instead of this:"
    "  (provided (every? even? ..xs..) => true)"
    "do this:"
    "  (def all-even? (partial every? even?))"
    "  ;; ..."
    "  (provided (all-even? ..xs..) => true)")))



(defn to-lexical-map-form [a-list]
  (when-valid a-list
    (let [[_ [fnref & args :as call-form] arrow result & overrides] a-list]
      (when (statically-disallowed-prerequisite-function (fnref/fnref-var-object fnref))
        (raise-disallowed-prerequisite-error (fnref/fnref-var-object fnref)))
      (lexical-maps/fake call-form fnref args arrow result overrides))))
    