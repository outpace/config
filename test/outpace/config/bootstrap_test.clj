(ns outpace.config.bootstrap-test
  (:import java.io.File)
  (:use clojure.test
        outpace.config.bootstrap)
  (:require [clojure.java.io :as io]))

(defmacro with-config-file [& body]
  `(let [^File f# (io/file "config.edn")]
     (try
       (spit f# "")
       ~@body
       (finally
         (.delete f#)))))

(defmacro with-sys-prop-val [name value & body]
  `(try
     (System/setProperty ~name ~value)
     ~@body
     (finally
       (System/clearProperty ~name))))

(defmacro with-explicit-val [value & body]
  `(try
     (alter-var-root #'explicit-config-source (constantly ~value))
     ~@body
     (finally
       (alter-var-root #'explicit-config-source (constantly nil)))))

(deftest test-find-config-source
  (testing "nil when no source provided"
    (is (nil? (find-config-source))))

  (testing "\"config.edn\" when only config.edn is present"
    (with-config-file
      (is (= "config.edn" (find-config-source)))))

  (testing "system property value when only system property is present"
    (with-sys-prop-val "config.edn" "sys-prop-config.edn"
      (is (= "sys-prop-config.edn" (find-config-source)))))

  (testing "resource file when resource system property and resource file are present"
    (with-sys-prop-val "resource.config.edn" "test-config.edn"
      (is (not (nil? (io/resource "test-config.edn"))))
      (is (= (io/resource "test-config.edn") (find-config-source)))))

  (testing "nil when resource system property is present, but resource file is not present"
    (with-sys-prop-val "resource.config.edn" "missing-config.edn"
      (is (nil? (io/resource "missing-config.edn")))
      (is (nil? (find-config-source)))))

  (testing "resource file when resource system property, resource file, and config.edn are present"
    (with-config-file
      (with-sys-prop-val "resource.config.edn" "test-config.edn"
        (is (not (nil? (io/resource "test-config.edn"))))
        (is (= (io/resource "test-config.edn") (find-config-source))))))

  (testing "nil when resource system property and config.edn are present, but resource file is not present"
    (with-config-file
      (with-sys-prop-val "resource.config.edn" "missing-config.edn"
        (is (nil? (io/resource "missing-config.edn")))
        (is (nil? (find-config-source))))))

  (testing "system property value when file is also present"
    (with-config-file
      (with-sys-prop-val "config.edn" "sys-prop-config.edn"
        (is (= "sys-prop-config.edn" (find-config-source))))))

  (testing "explicit value when only explicit-config-source is present"
    (with-explicit-val "explicit-config.edn"
      (is (= "explicit-config.edn" (find-config-source)))))

  (testing "explicit value when system property is also present"
    (with-sys-prop-val "config.edn" "sys-prop-config.edn"
      (with-explicit-val "explicit-config.edn"
        (is (= "explicit-config.edn" (find-config-source))))))

  (testing "explicit value when system property and file are also present"
    (with-config-file
      (with-sys-prop-val "config.edn" "sys-prop-config.edn"
        (with-explicit-val "explicit-config.edn"
          (is (= "explicit-config.edn" (find-config-source)))))))

  (testing "explicit value when both system properties and file are also present"
    (with-config-file
      (with-sys-prop-val "config.edn" "sys-prop-config.edn"
        (with-sys-prop-val "resource.config.edn" "test-config.edn"
          (with-explicit-val "explicit-config.edn"
            (is (= "explicit-config.edn" (find-config-source)))))))))
