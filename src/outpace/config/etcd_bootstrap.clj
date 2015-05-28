(ns outpace.config.etcd-bootstrap
  (:require
    [clojure.edn :as edn]
    [etcd-clojure.core :as etcd])
  (:import
    [java.net URI]))

(defn uri-components [uri-str]
  (let [uri (URI. uri-str)]
    {:path (.getPath uri)
     :host (.getHost uri)
     :port (.getPort uri)}))

(defn -main
  "Bootstraps etcd with a config EDN file"
  [edn-file etcd-uri]
  (let [config (edn/read-string (slurp edn-file))
        {:keys [host port path]} (uri-components etcd-uri)]
    (println "Pushing config from" edn-file "to" etcd-uri)
    (etcd/connect! host port)
    (doseq [[k v] config
            :let [prefixed-k (if (seq path)
                               (str (apply str (rest path)) "/" k)
                               (str k))]]
      (println k)
      (when (nil? (etcd/set prefixed-k (pr-str v)))
        (throw (ex-info "Failed to set key"
                        {:key prefixed-k
                         :value v})))))
  (shutdown-agents))
