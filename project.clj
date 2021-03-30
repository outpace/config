(defproject com.outpace/config "0.13.6-SNAPSHOT"
  :description "A library for declaring and setting configuration vars."
  :url "https://github.com/outpace/config"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :dependencies [[org.clojure/clojure "1.10.3" :scope "provided"]
                 [org.clojure/tools.namespace "1.1.0"]]
  :profiles {:dev {:resource-paths ["test_resources"]}}
  :plugins [[lein-codox "0.10.7"]]
  :codox {:src-dir-uri "http://github.com/outpace/config/blob/master/"
          :src-linenum-anchor-prefix "L"}
  :deploy-repositories [["releases" :clojars]])
