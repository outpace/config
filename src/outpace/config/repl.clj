(ns outpace.config.repl
  (:require [clojure.tools.namespace.repl :as nsr]
            [outpace.config.bootstrap :as boot]))

(defn reload
  "Reloads all Clojure source files, reapplying possibly updated configuration.
   If provided, config-source will be used as the configuration source, and must
   be a value acceptable to clojure.java.io/reader."
  ([]
    (nsr/disable-reload! (find-ns 'outpace.config.bootstrap))
    (nsr/refresh-all))
  ([config-source]
    (alter-var-root #'boot/explicit-config-source (constantly config-source))
    (reload)))