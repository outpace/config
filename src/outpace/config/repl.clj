(ns outpace.config.repl
  (:require [outpace.config :as config]
            [clojure.core.memoize :as memo]))

(defn clear-cache! []
  (memo/memo-clear! config/lookup))

(defn set-source-edn! [path]
  (clear-cache!)
  (reset! config/source (config/->EdnSource path)))

(defn set-source-etcd! [uri]
  (clear-cache!)
  (config/ensure-etcd-connection uri)
  (reset! config/source (config/->EtcdSource uri)))

(defn set-source-map! [m]
  (clear-cache!)
  (reset! config/source m))
