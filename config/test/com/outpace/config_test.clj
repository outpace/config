(ns com.outpace.config-test
  (:use clojure.test
        com.outpace.config)
  (:require [clojure.edn :as edn]))


(deftest test-EnvVar
  (let [name     "name"
        value    "value"
        defined? true
        ev       (->EnvVar name value defined?)]
    (testing "EnvVar fields"
      (is (= name (:name ev)))
      (is (= value (:value ev)))
      (is (= defined? (:defined? ev))))
    (testing "EnvVar protocol implementations"
      (is (= value (extract ev)))
      (is (= defined? (provided? ev))))
    (testing "EnvVar edn-printing"
      (is (= (str "#config/env " (pr-str name)) (pr-str ev))))))

(deftest test-read-env
  (when-let [name (first (keys (java.lang.System/getenv)))]
    (testing "EnvVar for extant environment variable."
      (let [value (System/getenv name)
            ev    (read-env name)]
        (is (= name (:name ev)))
        (is (= value (:value ev)))
        (is (:defined? ev))))
    (testing "EnvVar for missing environment variable."
      (let [ks   (set (keys (System/getenv)))
            name (first (remove ks (map str (range))))
            ev    (read-env name)]
        (is (= name (:name ev)))
        (is (nil? (:value ev)))
        (is (not (:defined? ev)))))))
