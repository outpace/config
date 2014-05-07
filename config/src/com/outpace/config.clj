(ns com.outpace.config
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defprotocol Extractable
  (extract [this] "Extracts the value to be bound to a config var"))

(extend-protocol Extractable
  nil
  (extract [x] x)
  Object
  (extract [x] x))

(defprotocol Optional
  (provided? [this] "Returns true if the item should be bound to a config var."))

(extend-protocol Optional
  nil
  (provided? [_] true)
  Object
  (provided? [_] true))

(defrecord EnvVar [name value defined?]
  Extractable
  (extract [_] value)
  Optional
  (provided? [_] defined?))

(defmethod print-method EnvVar [evar ^java.io.Writer w]
  (.write w (str "#config/env " (pr-str (.name evar)))))

(defn read-env [name]
  (if (and name (string? name))
    (->EnvVar name (System/getenv name) (contains? (System/getenv) name))
    (throw (IllegalArgumentException. (str "Argument to #config/env must be a string: " (pr-str name))))))

(defn valid-key?
  "Returns true IFF k is acceptable as a key in config.edn,
   i.e., a namespaced symbol."
  [k]
  (and (symbol? k) (namespace k)))

(if-let [source (or (System/getProperty "config.edn")
                    (let [file (io/file "config.edn")] (when (.exists file) (.getAbsolutePath file))))]
  (let [config-map (edn/read-string {:readers *data-readers*} (slurp source))
        invalid-keys (remove valid-key? (keys config-map))]
    (when (seq invalid-keys)
      (throw (IllegalArgumentException. (str "Configuration keys must be namespaced symbols: " (pr-str invalid-keys)))))
    (def ^{::source source
           :doc "The map of explicitly-specified configuration values. Avoid using this directly."}
         config config-map))
  (def ^{:doc "The map of explicitly-specified configuration values. Avoid using this directly."}
       config {}))

(def ^{:doc "An atom containing the map of symbols for the loaded defconfig vars to their default values."}
  defaults (atom {}))

(def ^{:doc "An atom containing the set of symbols for the loaded defconfig vars that do not have a default value."}
  required (atom #{}))

(defn present?
  "Returns true if a configuration entry exists for the qname and, if an
   Optional value, the value is provided."
  [qname]
  (and (contains? config qname)
       (provided? (get config qname))))

(defn lookup
  "Returns the extracted value if the qname is present, otherwise default-val or nil."
  ([qname]
    (extract (get config qname)))
  ([qname default-val]
    (if (present? qname)
      (extract (get config qname))
      default-val)))

(defmacro defconfig
  "Same as (def name doc-string? init?) except the var's value may be configured
   at load-time by this library. Note that default-val will be evaluated, even
   if there is a configured value"
  ([name]
    `(let [qname# (symbol (str *ns*) (str '~name))]
       (swap! required conj qname#)
       (swap! defaults dissoc qname#)
       (if (present? qname#)
         (def ~name (lookup qname#))
         (def ~name))))
  ([name default-val]
    `(let [qname# (symbol (str *ns*) (str '~name))
           default-val# ~default-val]
       (swap! defaults assoc qname# default-val#)
       (swap! required disj qname#)
       (def ~name (lookup qname# default-val#))))
  ([name doc default-val]
    `(let [qname# (symbol (str *ns*) (str '~name))
           default-val# ~default-val]
       (swap! defaults assoc qname# default-val#)
       (swap! required disj qname#)
       (def ~name ~doc (lookup qname# default-val#)))))

