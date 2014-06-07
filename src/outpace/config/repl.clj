(ns outpace.config.repl
  (:require [clojure.tools.namespace.repl :as nsr]))

(defn reload
  "Reloads all Clojure source files to use the specified configuration source.
   The config-source must be a string acceptable to clojure.java.io/reader."
  [config-source]
  (System/setProperty "config.edn" config-source)
  (nsr/refresh-all))