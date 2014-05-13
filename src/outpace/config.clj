(ns outpace.config
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

(defmethod print-method EnvVar [^EnvVar evar ^java.io.Writer w]
  (.write w (str "#config/env " (pr-str (.name evar)))))

(defn read-env
  "Returns an EnvVar identified by the specified string name."
  [name]
  (if (and name (string? name))
    (->EnvVar name (System/getenv name) (contains? (System/getenv) name))
    (throw (IllegalArgumentException. (str "Argument to #config/env must be a string: " (pr-str name))))))

(defn valid-key?
  "Returns true IFF k is acceptable as a key in config.edn, i.e., a namespaced
   symbol."
  [k]
  (and (symbol? k) (namespace k)))

(defn config-source
  "Returns the source of config.edn if provided, otherwise nil."
  []
  (or (System/getProperty "config.edn")
      (let [file (io/file "config.edn")]
        (when (.exists file)
          (.getAbsolutePath file)))))

(defn read-config
  "Reads the config.edn map from a source acceptable to clojure.java.io/reader."
  [source]
  (let [config-map (edn/read-string {:readers *data-readers*} (slurp source))]
    (when-not (map? config-map)
      (throw (IllegalArgumentException. (str "Configuration must be an EDN map: " (pr-str config-map)))))
    (when-let [invalid-keys (seq (remove valid-key? (keys config-map)))]
      (throw (IllegalArgumentException. (str "Configuration keys must be namespaced symbols: " (pr-str invalid-keys)))))
    (vary-meta config-map assoc ::source source)))

(def config
  "The map of explicit configuration values."
  (if-let [source (config-source)]
    (read-config source)
    {}))

(defn present?
  "Returns true if a configuration entry exists for the qname and, if an
   Optional value, the value is provided."
  [qname]
  (and (contains? config qname)
       (provided? (get config qname))))

(defn lookup
  "Returns the extracted value if the qname is present, otherwise default-val
   or nil."
  ([qname]
    (extract (get config qname)))
  ([qname default-val]
    (if (present? qname)
      (lookup name)
      default-val)))

(def defaults
  "An ref containing the map of symbols for the loaded defconfig vars to their
   default values."
  (ref {}))

(def required
  "An ref containing the set of symbols for the loaded defconfig vars that do
   not have a default value."
  (ref #{}))

(defn var-symbol
  "Returns the namespace-qualified symbol for the var."
  [v]
  (symbol (-> v meta :ns ns-name name)
          (-> v meta :name name)))

(defmacro defconfig
  "Same as (def name doc-string? init?) except the var's value may be configured
   at load-time by this library. If the ^:required metadata is used, an
   exception will be thrown if no default nor configured value is provided.

   Note that default-val will be evaluated, even if there is a configured value."
  ([name]
    `(let [var#   (def ~name)
           qname# (var-symbol var#)]
       (dosync
         ; keep consistent with the fact that redefining a bound var does not unbind it
         (when-not (contains? (ensure defaults) qname#)
           (alter required conj qname#)))
       (if (present? qname#)
         (alter-var-root var# (constantly (lookup qname#)))
         (when (-> var# meta :required)
           (throw (Exception. (str "Missing required value for config var: " qname#)))))
       var#))
  ([name default-val]
    `(let [default-val# ~default-val
           var#         (def ~name default-val#)
           qname#       (var-symbol var#)]
       (dosync
         (alter defaults assoc qname# default-val#)
         (alter required disj qname#))
       (when (present? qname#)
         (alter-var-root var# (constantly (lookup qname#))))
       var#))
  ([name doc default-val]
    `(let [default-val# ~default-val
           var#         (def ~name ~doc default-val#)
           qname#       (var-symbol var#)]
       (dosync
         (alter defaults assoc qname# default-val#)
         (alter required disj qname#))
       (when (present? qname#)
         (alter-var-root var# (constantly (lookup qname#))))
       var#)))

(defmacro defconfig!
  "Equivalent to (defconfig ^:required ...)."
  [name]
  `(defconfig ~(vary-meta name assoc :required true)))