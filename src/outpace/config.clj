(ns outpace.config
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [etcd-clojure.core :as etcd]
            [outpace.config.bootstrap :refer [find-config-source]])
  (:import [clojure.lang IDeref])
  )

(def generating? false)

(defprotocol Extractable
  (extract [this] "Extracts the value to be bound to a config var"))

(extend-protocol Extractable
  nil
  (extract [x] x)
  Object
  (extract [x]
    (cond
     (map? x) (reduce-kv (fn [x k v]
                           (assoc x (extract k) (extract v)))
                         (empty x)
                         x)
     (coll? x) (reduce (fn [x v]
                         (conj x (extract v)))
                       (empty x)
                       x)
     :else x)))

(defprotocol Optional
  (provided? [this] "Returns true if the item should be bound to a config var."))

(extend-protocol Optional
  nil
  (provided? [_] true)
  Object
  (provided? [x]
    (cond
     (map? x) (reduce-kv (fn [x k v]
                           (and x (provided? k) (provided? v)))
                         true
                         x)
     (coll? x) (reduce (fn [x v]
                         (and x (provided? v)))
                       true
                       x)
     :else true)))

(defrecord EnvVal [name value defined?]
  Extractable
  (extract [_] value)
  Optional
  (provided? [_] defined?))

(defmethod print-method EnvVal [^EnvVal ev ^java.io.Writer w]
  (.write w (str "#config/env " (pr-str (.name ev)))))

(defn read-env
  "Returns an EnvVal identified by the specified string name."
  [name]
  (if (and name (string? name))
    (->EnvVal name (System/getenv name) (contains? (System/getenv) name))
    (throw (IllegalArgumentException. (str "Argument to #config/env must be a string: " (pr-str name))))))

(defrecord EtcdVal [name]
  Extractable
  (extract [_]
    (edn/read-string (etcd/get name)))
  Optional
  (provided? [_]
    (not (nil? (etcd/get name)))))

(defmethod print-method EtcdVal [^EtcdVal ev ^java.io.Writer w]
  (.write w (str "#config/etcd " (pr-str (.name ev)))))

(defn read-etcd
  "Returns an EtcdVal identified by the specified string name."
  [name]
  (if (and name (string? name))
    (->EtcdVal name)
    (throw (IllegalArgumentException. (str "Argument to #config/etcd must be a string: " (pr-str name))))))

(defrecord FileVal [path]
  Extractable
  (extract [_]
    (slurp (io/file path)))
  Optional
  (provided? [_]
    (.exists (io/file path))))

(defmethod print-method FileVal [^FileVal fv ^java.io.Writer w]
  (.write w (str "#config/file " (pr-str (.path fv)))))

(defn read-file
  "Returns a FileVal identified by the specified string path."
  [path]
  (if (and path (string? path))
    (->FileVal path)
    (throw (IllegalArgumentException. (str "Argument to #config/file must be a string: " (pr-str path))))))

(defrecord EdnVal [source value source-provided?]
  Extractable
  (extract [_] value)
  Optional
  (provided? [_] source-provided?))

(defmethod print-method EdnVal [^EdnVal ev ^java.io.Writer w]
  (.write w (str "#config/edn " (pr-str (.source ev)))))

(defn read-edn
  "Returns an EdnVal from a string value. Can be composed with other readers."
  [source]
  (let [s (extract source)]
    (if (or (nil? s) (string? s))
      (let [read-value (edn/read-string {:readers *data-readers*} s)]
        (->EdnVal source (extract read-value) (and (provided? source) (provided? read-value))))
      (throw (IllegalArgumentException. (str "Argument to #config/edn must be a string: " (pr-str source)))))))

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

(defn validate
  "Throws an ex-info if, for any predicate in validate-vec, (pred val) is false.

   The validate-vec must be a vector of alternating single-arity predicate fns
   and associated error messages.
   Example: [number? \"must be a number\" even? \"must be even\"]

  The ex-info's ex-data map will contain the following entries:
    :pred - the predicate fn that failed
    :msg  - the associated error message
    :sym  - the fully-qualified symbol of the config var
    :val  - the value set on the config var"
  [val var-sym validate-vec]
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

(defn allowed-to-deref? []
  (and (not *compile-files*) (not generating?)))

(declare deref-config)

(deftype ConfigDefault [qname required? default]
  IDeref
  (deref [this]
    (deref-config this)))

(deftype Config [qname required?]
  IDeref
  (deref [this]
    (deref-config this)))

(defn deref-config [config]
  (let [qname (.qname config)]
    (cond
      (not (allowed-to-deref?))
      (throw (ex-info "Not allowed to deref config var" {:qualified-name qname
                                                         :compile-files? *compile-files*
                                                         :generating? generating?}))
      (present? qname)
      (lookup qname)

      (isa? ConfigDefault config)
      (.default config)

      (.required? config)
      (throw (ex-info "Missing required value for config var" {:qualified-name qname}))

      :else
      nil)))

(defn check-presence [config]
  (when (allowed-to-deref?)
    (deref config)))

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
    `(let [
           var#    (def ~name nil)
           qname#  (var-symbol var#)
           config# (Config. qname# (-> var# meta :required))]
       (alter-var-root var# (constantly config#))
       (check-presence config#)
       (dosync
         ; keep consistent with the fact that redefining a bound var does not unbind it
         (when-not (contains? (ensure defaults) qname#)
           (alter non-defaulted conj qname#)))
       (when-let [validate# (and (bound? var#) (not *compile-files*) (-> var# meta :validate))]
         (validate @var# qname# validate#))
       var#))
  ([name default-val]
    `(let [default-val# ~default-val
           var#         (def ~name nil)
           qname#       (var-symbol var#)
           config#      (ConfigDefault. qname# (-> var# meta :required) default-val#)]
       (alter-var-root var# (constantly config#))
       (dosync
         (alter defaults assoc qname# default-val#)
         (alter non-defaulted disj qname#))
       (when-let [validate# (and (not *compile-files*) (-> var# meta :validate))]
         (validate @var# qname# validate#))
       var#))
  ([name doc default-val]
    `(let [default-val# ~default-val
           var#         (def ~name ~doc )
           qname#       (var-symbol var#)
           config#      (ConfigDefault. qname# (-> var# meta :required) default-val#)]
       (alter-var-root var# (constantly config#))
       (dosync
         (alter defaults assoc qname# default-val#)
         (alter non-defaulted disj qname#))
       (when-let [validate# (and (not *compile-files*) (-> var# meta :validate))]
         (validate @var# qname# validate#))
       var#)))

(defmacro defconfig!
  "Equivalent to (defconfig ^:required ...)."
  [name]
  `(defconfig ~(vary-meta name assoc :required true)))
