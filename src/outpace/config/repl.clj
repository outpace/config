(ns outpace.config.repl
  (:require [outpace.config :as config]
            [clojure.core.memoize :as memo]
            [outpace.config.bootstrap :as boot]))

(defn clear-cache! []
  (memo/memo-clear! config/read-config)
  (memo/memo-clear! config/lookup))

(defn set-source! [config-source]
  (alter-var-root #'boot/explicit-config-source (constantly config-source))
  (clear-cache!)
  "O.K.")
