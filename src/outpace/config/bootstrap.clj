(ns outpace.config.bootstrap
  (:require [clojure.java.io :as io]))

(def explicit-config-source
  "When set to a non-nil value, will be used as the config-source.
   The value must be a source acceptable to clojure.java.io/reader.
   Note: Code that sets this must do so prior to loading outpace.config
   namespace or any namespace that transitively requires outpace.config."
  nil)

(defn find-config-source
  "Returns the first config EDN source found from:
     - explicit-config-source, if non-nil
     - URL to a resource file pointed to by the value of the \"resource.config.edn\" system property, if present
     - nil if the \"resource.config.edn\" system property is present, but the resource file is not present
     - the value of the \"config.edn\" system property, if present
     - \"config.edn\", if the file exists in the current working directory
   otherwise nil."
  []
  (or explicit-config-source
      (if-let [res (System/getProperty "resource.config.edn")]
        (io/resource res)
        (or (System/getProperty "config.edn")
            (when (.exists (io/file "config.edn"))
              "config.edn")))))
