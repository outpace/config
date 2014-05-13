(defproject com.outpace/config "0.1.0"
  :description "A library for declaring and setting configuration vars."
  :url "https://github.com/outpace/config"
  :plugins [[lein-maven-s3-wagon "0.2.3"]]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.namespace "0.2.4"]]
  :deploy-repositories [["private" {:url "s3://outpace-maven/releases/"
                                    :username :env/aws_access_key_id
                                    :passphrase :env/aws_secret_access_key
                                    :sign-releases false}]])
