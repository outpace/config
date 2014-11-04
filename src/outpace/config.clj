(ns outpace.config
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [outpace.config.bootstrap :refer [find-config-source]]))

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

(defrecord FileVar [name value exists?]
  Extractable
  (extract [_] value)
  Optional
  (provided? [_] exists?))

(defmethod print-method FileVar [^FileVar fvar ^java.io.Writer w]
  (.write w (str "#config/file " (pr-str (.name fvar)))))

(defn read-file
  "Returns a FileVar identified by the specified string path."
  [path]
  (if (and path (string? path))
    (let [f (io/file path)]
      (if (.exists f)
        (->FileVar path (slurp f) true)
        (->FileVar path nil false)))
    (throw (IllegalArgumentException. (str "Argument to #config/file must be a string: " (pr-str name))))))

(defn valid-key?
  "Returns true IFF k is acceptable as a key in a configuration map,
   i.e., a namespaced symbol."
  [k]
  (and (symbol? k) (namespace k)))

(defn load-data-readers
  "Loads the namespaces of data reader Vars whose reader tag symbol has the
   'config' namespace."
  []
  (doseq [lib-sym (->> *data-readers*
                    (filter #(-> % key namespace #{"config"}))
                    (map #(-> % val meta ^clojure.lang.Namespace (:ns) .name))
                    (distinct))
          :when (not= 'outpace.config lib-sym)]
    (require lib-sym)))

(defn read-config
  "Reads the config EDN map from a source acceptable to clojure.java.io/reader."
  [source]
  (load-data-readers)
  (let [config-map (edn/read-string {:readers *data-readers*} (slurp source))]
    (when-not (map? config-map)
      (throw (IllegalArgumentException. (str "Configuration must be an EDN map: " (pr-str config-map)))))
    (when-let [invalid-keys (seq (remove valid-key? (keys config-map)))]
      (throw (IllegalArgumentException. (str "Configuration keys must be namespaced symbols: " (pr-str invalid-keys)))))
    (vary-meta config-map assoc ::source source)))

(def config
  "The delayed map of explicit configuration values."
  (delay (if-let [source (find-config-source)]
           (read-config source)
           {})))

(defn present?
  "Returns true if a configuration entry exists for the qname and, if an
   Optional value, the value is provided."
  [qname]
  (and (contains? @config qname)
       (provided? (get @config qname))))

(defn lookup
  "Returns the extracted value if the qname is present, otherwise default-val
   or nil."
  ([qname]
    (extract (get @config qname)))
  ([qname default-val]
    (if (present? qname)
      (lookup name)
      default-val)))

(def defaults
  "A ref containing the map of symbols for the loaded defconfig vars to their
   default values."
  (ref {}))

(def non-defaulted
  "A ref containing the set of symbols for the loaded defconfig vars that do
   not have a default value."
  (ref #{}))

(defn unbound
  "Returns the set of symbols for the defconfig vars with neither a default nor
   configured value."
  []
  (dosync
    (set/difference @non-defaulted
                    (set (keys @defaults))
                    (set (keys @config)))))

(defn var-symbol
  "Returns the namespace-qualified symbol for the var."
  [v]
  (symbol (-> v meta :ns ns-name name)
          (-> v meta :name name)))

(defn- validate [val var-sym validate-vec]
  (assert (vector? validate-vec) ":validate value must be a vector")
  (assert (even? (count validate-vec)) ":validate vector requires an even number of forms")
  (assert (every? ifn? (take-nth 2 validate-vec)) ":validate vector requires alternating functions")
  (assert (every? string? (take-nth 2 (next validate-vec))) ":validate vector requires alternating message strings")
  (doseq [[pred msg] (partition 2 validate-vec)]
    (when-not (pred val)
      (throw (ex-info (format "Config var %s failed validation: %s. See ex-data." var-sym msg)
                      {:pred pred
                       :msg  msg
                       :sym  var-sym
                       :val  val})))))

(let [validate validate]
(defmacro defconfig
  "Same as (def name doc-string? init?) except the var's value may be configured
   at load-time by this library.

   The following additional metadata is supported:
     :required - When true, an exception will be thrown if no default nor
                 configured value is provided.
     :validate - A vector of alternating single-arity predicates and error
                 messages. After a value is set on the var, an exception will be
                 thrown when a predicate, passed the set value, yields false.

   Note: default-val will be evaluated, even if a configured value is provided."
  ([name]
    `(let [var#   (def ~name)
           qname# (var-symbol var#)]
       (dosync
         ; keep consistent with the fact that redefining a bound var does not unbind it
         (when-not (contains? (ensure defaults) qname#)
           (alter non-defaulted conj qname#)))
       (if (present? qname#)
         (alter-var-root var# (constantly (lookup qname#)))
         (when (and (-> var# meta :required) (not *compile-files*))
           (throw (Exception. (str "Missing required value for config var: " qname#)))))
       (when-let [validate# (and (bound? var#) (not *compile-files*) (-> var# meta :validate))]
         (~validate @var# qname# validate#))
       var#))
  ([name default-val]
    `(let [default-val# ~default-val
           var#         (def ~name default-val#)
           qname#       (var-symbol var#)]
       (dosync
         (alter defaults assoc qname# default-val#)
         (alter non-defaulted disj qname#))
       (when (present? qname#)
         (alter-var-root var# (constantly (lookup qname#))))
       (when-let [validate# (and (not *compile-files*) (-> var# meta :validate))]
         (~validate @var# qname# validate#))
       var#))
  ([name doc default-val]
    `(let [default-val# ~default-val
           var#         (def ~name ~doc default-val#)
           qname#       (var-symbol var#)]
       (dosync
         (alter defaults assoc qname# default-val#)
         (alter non-defaulted disj qname#))
       (when (present? qname#)
         (alter-var-root var# (constantly (lookup qname#))))
       (when-let [validate# (and (not *compile-files*) (-> var# meta :validate))]
         (~validate @var# qname# validate#))
       var#)))
)

(defmacro defconfig!
  "Equivalent to (defconfig ^:required ...)."
  [name]
  `(defconfig ~(vary-meta name assoc :required true)))
