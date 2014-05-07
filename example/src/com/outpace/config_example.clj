(ns com.outpace.config-example
  (:require [com.outpace.config :refer [defconfig]]))

(defconfig ^{:doc "The doc for my-var."} my-var)

(defconfig var-with-default "The doc for var-with-default." :default-value)

(defconfig ^:dynamic *rebindable-var* "The doc for *rebindable-var*." :rebindable-default)

(defn show []
  (println "my-var:          " my-var)
  (println "var-with-default:" var-with-default)
  (println "*rebindable-var*:" *rebindable-var*))
