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

(defmacro with-sys-prop-val [value & body]
  `(try
     (System/setProperty "config.edn" ~value)
     ~@body
     (finally
       (System/clearProperty "config.edn"))))

(defmacro with-explicit-val [value & body]
  `(try
     (alter-var-root #'explicit-config-source (constantly ~value))
     ~@body
     (finally
       (alter-var-root #'explicit-config-source (constantly nil)))))

(deftest test-find-config-source
  (testing "nil when no source provided"
    (is (nil? (find-config-source))))
  
  (testing "\"config.edn\" when only file is present"
    (with-config-file
      (is (= "config.edn" (find-config-source)))))
  
  (testing "system property value when only system property is present"
    (with-sys-prop-val "sys-prop-config.edn"
      (is (= "sys-prop-config.edn" (find-config-source)))))
  
  (testing "system property value when file is also present"
    (with-config-file
      (with-sys-prop-val "sys-prop-config.edn"
        (is (= "sys-prop-config.edn" (find-config-source))))))
  
  (testing "explicit value when only explicit-config-source is present"
    (with-explicit-val "explicit-config.edn"
      (is (= "explicit-config.edn" (find-config-source)))))
  
  (testing "explicit value when system property is also present"
    (with-sys-prop-val "sys-prop-config.edn"
      (with-explicit-val "explicit-config.edn"
        (is (= "explicit-config.edn" (find-config-source))))))
  
  (testing "explicit value when system property and file are also present"
    (with-config-file
      (with-sys-prop-val "sys-prop-config.edn"
        (with-explicit-val "explicit-config.edn"
          (is (= "explicit-config.edn" (find-config-source))))))))