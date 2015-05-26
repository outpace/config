(ns outpace.config.bootstrap
  (:require [clojure.java.io :as io]
            [etcd-clojure.core :as etcd]))

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

(def explicit-config-source
  "When set to a non-nil value, will be used as the config-source.
   The value must be a source acceptable to clojure.java.io/reader.
   Note: Code that sets this must do so prior to loading outpace.config
   namespace or any namespace that transitively requires outpace.config."
  nil)

(def initialized (atom false))

(defn initialize! []
  (etcd/connect! "local-trek.outpace.com" 4001)
  (load-data-readers)
  (reset! initialized true))

(defn find-config-source
  "Returns the first config EDN source found from:
     - explicit-config-source, if non-nil
     - the value of the \"config.edn\" system property, if present
     - \"config.edn\", if the file exists in the current working directory
   otherwise nil."
  []
  (when-not @initialized
    (initialize!))
  (or explicit-config-source
      (System/getProperty "config.edn")
      (when (.exists (io/file "config.edn"))
        "config.edn")))
