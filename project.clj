(defproject com.outpace/config "0.7.1-SNAPSHOT"
  :description "A library for declaring and setting configuration vars."
  :url "https://github.com/outpace/config"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.namespace "0.2.5"]]
  :plugins [[codox "0.8.10"]]
  :codox {:src-dir-uri "http://github.com/outpace/config/blob/master/"
          :src-linenum-anchor-prefix "L"}
  :deploy-repositories [["releases" :clojars]])
