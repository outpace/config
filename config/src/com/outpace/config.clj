(ns com.outpace.config
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defn valid-key? [k]
  (and (symbol? k) (namespace k)))

(if-let [source (or (System/getProperty "config.edn")
                    (let [file (io/file "config.edn")] (when (.exists file) (.getAbsolutePath file))))]
  (let [config-map (edn/read-string (slurp source))
        invalid-keys (remove valid-key? (keys config-map))]
    (when (seq invalid-keys)
      (throw (IllegalArgumentException. (str "Configuration keys must be namespaced symbols: " (pr-str invalid-keys)))))
    (def ^{::source source
           :doc "The map of explicitly-specified configuration values"}
         config config-map))
  (def ^{:doc "The map of explicitly-specified configuration values"}
       config {}))

(def ^{:doc "An atom containing the map of symbols for the loaded defconfig vars to their default values."}
  defaults (atom {}))

(def ^{:doc "An atom containing the set of symbols for the loaded defconfig vars that do not have a default value."}
  required (atom #{}))

(defmacro defconfig
  ([name]
    `(let [qname# (symbol (str *ns*) (str '~name))]
       (swap! required conj qname#)
       (swap! defaults dissoc qname#)
       (if (contains? config qname#)
         (def ~name (get config qname#))
         (def ~name))))
  ([name default-val]
    (defconfig name nil default-val))
  ([name doc default-val]
    `(let [qname# (symbol (str *ns*) (str '~name))
           default-val# ~default-val]
       (swap! defaults assoc qname# default-val#)
       (swap! required disj qname#)
       (let [val# (get config qname# default-val#)]
         (if ~doc
           (def ~name ~doc val#)
           (def ~name val#))))))

