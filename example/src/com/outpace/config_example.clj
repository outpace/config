(ns com.outpace.config-example
  (:require [com.outpace.config :refer [defconfig]]))

(defconfig ^{:doc "The doc for my-var."} my-var)

(defconfig var-with-default "The doc for var-with-default." :default-value)

(defconfig ^:dynamic *rebindable-var* "The doc for *rebindable-var*." :rebindable-default)

(defconfig the-user "The name of the user" nil)

(defconfig ^{:doc "Missing env-var without a default value."} missing-env)

(defconfig ^{:doc "Missing env-var with a default value."} missing-env-default :default-missing)

(defn -main []
  (println "my-var:              " my-var)
  (println "var-with-default:    " var-with-default)
  (println "*rebindable-var*:    " *rebindable-var*)
  (println "the-user:            " the-user)
  (println "missing-env:         " missing-env)
  (println "missing-env-default: " missing-env-default))
