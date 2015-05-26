(ns outpace.config-test
  (:use clojure.test
        outpace.config)
  (:require [etcd-clojure.core :as etcd])
  (:import [clojure.lang ExceptionInfo]
           [java.io File FileNotFoundException]
           [outpace.config EdnVal EnvVal FileVal EtcdVal]))

(defn unmap-non-fn-vars [ns]
  (doseq [[sym var] (ns-interns ns)]
    (when-not (fn? @var)
      (ns-unmap ns sym))))

(use-fixtures :each (fn unmap-vars-fixture [f]
                      (unmap-non-fn-vars *ns*)
                      (f)))

(deftest test-read-etcd
  (with-redefs [etcd/get {"greeting" (pr-str "hello")}]
    (testing "EtcdVal for extant variable."
      (let [name  "greeting"
            value "hello"
            ev    (read-etcd name)]
        (is (instance? EtcdVal ev))
        (is (= value (extract ev)))
        (is (provided? ev))))
    (testing "EtcdVal for missing variable."
      (let [name "missing"
            ev   (read-etcd name)]
        (is (instance? EtcdVal ev))
        (is (nil? (extract ev)))
        (is (not (provided? ev)))))))

(deftest test-EnvVal
  (let [name     "name"
        value    "value"
        defined? true
        ev       (->EnvVal name value defined?)]
    (testing "EnvVal fields"
      (is (= name (:name ev)))
      (is (= value (:value ev)))
      (is (= defined? (:defined? ev))))
    (testing "EnvVal protocol implementations"
      (is (= value (extract ev)))
      (is (= defined? (provided? ev))))
    (testing "EnvVal edn-printing"
      (is (= (str "#config/env " (pr-str name)) (pr-str ev))))))

(defn env-var-name []
  (let [name (first (keys (java.lang.System/getenv)))]
    (assert name "Cannot test read-env without environment variables")
    name))

(defn missing-env-var-name []
  (let [ks   (set (keys (System/getenv)))]
    (first (remove ks (map str (range))))))

(deftest test-read-env
  (testing "EnvVal for extant environment variable."
    (let [name  (env-var-name)
          value (System/getenv name)
          ev    (read-env name)]
      (is (instance? EnvVal ev))
      (is (= value (extract ev)))
      (is (provided? ev))))
  (testing "EnvVal for missing environment variable."
    (let [name (missing-env-var-name)
          ev   (read-env name)]
      (is (instance? EnvVal ev))
      (is (nil? (extract ev)))
      (is (not (provided? ev))))))

(deftest test-FileVal
  (let [path     "path"
        contents ""
        exists?  false
        fv       (->FileVal path)]
    (testing "FileVal fields"
      (is (= path (:path fv))))
    (testing "FileVal protocol implementations"
      (is (satisfies? Extractable fv))
      (is (satisfies? Optional fv)))
    (testing "FileVal edn-printing"
      (is (= (str "#config/file " (pr-str path)) (pr-str fv))))))

(deftest test-read-file
  (let [file     (doto (File/createTempFile "test-read-file" ".txt")
                   (.deleteOnExit))
        path     (.getPath file)
        contents "contents"]
    (spit file contents)
    (testing "FileVal for extant file."
     (let [fv (read-file path)]
       (is (instance? FileVal fv))
       (is (= contents (extract fv)))
       (is (provided? fv))))
    (.delete file)
    (testing "FileVal for missing file."
      (let [fv (read-file path)]
        (is (instance? FileVal fv))
        (is (thrown? FileNotFoundException (extract fv)))
        (is (not (provided? fv)))))))

(deftest test-EdnVal
  (let [source           "{}"
        value            {}
        source-provided? true
        ev               (->EdnVal source value source-provided?)]
    (testing "EdnVal fields"
      (is (= source (:source ev)))
      (is (= value (:value ev)))
      (is (= source-provided? (:source-provided? ev))))
    (testing "EdnVal protocol implementations"
      (is (= value (extract ev)))
      (is (= source-provided? (provided? ev))))
    (testing "EdnVal edn-printing"
      (is (= (str "#config/edn " (pr-str source)) (pr-str ev))))))

(defn extractable [value provided]
  (reify
    Extractable
    (extract [_] value)
    Optional
    (provided? [_] provided)))

(deftest test-read-edn
  (testing "EdnVal for string"
    (let [source "{:foo 123}"
          value   {:foo 123}
          ev (read-edn source)]
      (is (instance? EdnVal ev))
      (is (= value (extract ev)))
      (is (provided? ev))))
  (testing "EdnVal for nil"
    (let [source nil
          ev (read-edn source)]
      (is (instance? EdnVal ev))
      (is (nil? (extract ev)))
      (is (not (provided? ev)))))
  (testing "EdnVal of provided source"
    (let [source (extractable "{:foo 123}" true)
          ev (read-edn source)]
      (is (instance? EdnVal ev))
      (is (= {:foo 123} (extract ev)))
      (is (provided? ev))))
  (testing "EdnVal of not-provided source"
    (let [source (extractable nil false)
          ev (read-edn source)]
      (is (instance? EdnVal ev))
      (is (nil? (extract ev)))
      (is (not (provided? ev)))))
  (testing "EdnVal recurses for extant environment variable."
    (let [name (env-var-name)
          value (System/getenv name)
          source (str "{:foo 123 :bar (#{[{:baz #config/env " (pr-str name) "}]})}")
          value   {:foo 123 :bar (list #{[{:baz value}]})}
          ev (read-edn source)]
      (is (instance? EdnVal ev))
      (is (= value (extract ev)))
      (is (provided? ev))))
  (testing "EdnVal recurses for missing environment variable."
    (let [name (missing-env-var-name)
          source (str "{:foo 123 :bar (#{[{:baz #config/env " (pr-str name) "}]})}")
          value   {:foo 123 :bar (list #{[{:baz nil}]})}
          ev (read-edn source)]
      (is (instance? EdnVal ev))
      (is (= value (extract ev)))
      (is (not (provided? ev))))))


(deftest test-defconfig
  (testing "Without config entry."
    (testing "Error on required."
      (is (thrown? Exception (defconfig ^:required req))))
    (testing "No default val, no docstring."
      (is (thrown? ExceptionInfo (defconfig aaa))))
    (testing "With default, no docstring."
      (defconfig bbb :default)
      (is (= :default @bbb)))
    (testing "With default and docstring."
      (defconfig ccc "doc" :default)
      (is (= :default @ccc))
      (is (= "doc" (:doc (meta #'ccc)))))
    (testing "Repeat defconfigs yield consistent state."
      (defconfig ddd "ignore me")
      (testing "Including default."
        (defconfig ddd :default)
        (is (nil? (:doc (meta #'ddd))))
        (is (= :default @ddd))
        (is (not (contains? @non-defaulted `ddd)))
        (is (contains? @defaults `ddd)))
      (testing "Including docstring."
        (defconfig ddd "doc" :default2)
        (is (= "doc" (:doc (meta #'ddd))))
        (is (= :default2 @ddd))
        (is (not (contains? @non-defaulted `ddd)))
        (is (contains? @defaults `ddd)))
      (testing "Omitting docstring."
        (defconfig ddd :default3)
        (is (nil? (:doc (meta #'ddd))))
        (is (= :default3 @ddd))
        (is (not (contains? @non-defaulted `ddd)))
        (is (contains? @defaults `ddd)))))
  (testing "With config entry"
    (reset! source {`eee :config `fff :config `ggg :config `hhh :config})
    (testing "No default val, no docstring"
      (defconfig eee)
      (is (= :config @eee)))
    (testing "With default, no docstring"
      (defconfig fff :default)
      (is (= :config @fff)))
    (testing "With default and docstring"
      (defconfig ggg "doc" :default)
      (is (= :config @ggg))
      (is (= "doc" (:doc (meta #'ggg)))))
    (testing "Repeat defconfigs yield consistent state."
      (defconfig hhh)
      (testing "Including default."
        (defconfig hhh :default)
        (is (nil? (:doc (meta #'hhh))))
        (is (= :config @hhh))
        (is (not (contains? @non-defaulted `hhh)))
        (is (= :default (@defaults `hhh))))
      (testing "Including docstring."
        (defconfig hhh "doc" :default2)
        (is (= "doc" (:doc (meta #'hhh))))
        (is (= :config @hhh))
        (is (not (contains? @non-defaulted `hhh)))
        (is (= :default2 (@defaults `hhh))))
      (testing "Omitting docstring."
        (defconfig hhh :default3)
        (is (nil? (:doc (meta #'hhh))))
        (is (= :config @hhh))
        (is (not (contains? @non-defaulted `hhh)))
        (is (= :default3 (@defaults `hhh))))
      (testing "Omitting default does not remove it, just like def."
        (defconfig hhh)
        (is (nil? (:doc (meta #'hhh))))
        (is (= :config @hhh))
        (is (not (contains? @non-defaulted `hhh)))
        (is (= :default3 (@defaults `hhh)))))))

(deftest test-defconfig!
  (testing "Preserves metadata"
    (reset! source {`req1 :config})
    (defconfig! ^{:doc "foobar"} req1)
    (is (-> #'req1 meta :required))
    (is (= "foobar" (-> #'req1 meta :doc))))
  (testing "No error when value provided"
    (reset! source {`req2 :config})
    (defconfig! req2)
    (is (-> #'req2 meta :required)))
  (testing "Error when no value provided"
    (is (thrown? Exception (defconfig! req3))))
  (testing "Recursive extraction"
    (testing "no error when value provided"
      (let [name (env-var-name)
            value (System/getenv name)]
        (reset! source {`req4 {:foo (read-env name)}})
        (defconfig! req4)
        (is (= {:foo value} @req4))
        (is (-> #'req4 meta :required))))
    (testing "error when no value provided"
      (let [name (missing-env-var-name)]
        (reset! source {`req5 {:foo (read-env name)}})
        (is (thrown? Exception (defconfig! req5)))))))

(deftest test-validate
  (testing "Tests must be a vector"
    (is (thrown? AssertionError (validate 5 'foo {}))))
  (testing "Tests vector must be even"
    (is (thrown? AssertionError (validate 5 'foo [even?]))))
  (testing "Tests vector must be alternating fns"
    (is (thrown? AssertionError (validate 5 'foo [even? "a" 5 "b"]))))
  (testing "Tests vector must be alternating strings"
    (is (thrown? AssertionError (validate 5 'foo [even? "a" even? 5]))))
  (testing "No error when no tests"
    (is (nil? (validate 5 'foo []))))
  (testing "Exceptions in order of declaration"
    (is (thrown-with-msg? ExceptionInfo #"bar" (validate 5 'foo [odd? "foo" even? "bar" even? "baz"]))))
  (testing "Exception message contains qualified var name."
    (is (thrown-with-msg? ExceptionInfo #"foo/bar" (validate 5 'foo/bar [even? "boom"]))))
  (testing "Exception message contains error message."
    (is (thrown-with-msg? ExceptionInfo #"boom" (validate 5 'foo/bar [even? "boom"])))))

(deftest test-defconfig-validate
  (testing "without config-value"
    (reset! source {})
    (testing "with default-value"
      (testing "no exception when default-value is valid"
        (is (defconfig ^{:validate [even? "boom"]} foo 4)))
      (testing "exception when default-value is invalid"
         (is (thrown? Exception (defconfig ^{:validate [even? "boom"]} foo 5))))))
  (testing "with config-value"
    (reset! source {`foo 5})
    (testing "without default-value"
      (testing "no exception when configured value is valid"
        (is (defconfig ^{:validate [odd? "boom"]} foo)))
      (testing "exception when configured value is invalid"
        (is (thrown? Exception (defconfig ^{:validate [even? "boom"]} foo)))))
    (testing "with default-value"
      (testing "no exception when configured value is valid, but default isn't"
        (is (defconfig ^{:validate [odd? "boom"]} foo 4)))
      (testing "exception when configured value is invalid, but default isn't"
        (is (thrown? Exception (defconfig ^{:validate [even? "boom"]} foo 4)))))))

(deftest test-extract
  (testing "recursively extract"
    (is (= {:foo "bar"} (extract {:foo (extractable "bar" true)})))
    (is (= #{"bar"} (extract #{(extractable "bar" true)})))
    (is (= (list "bar") (extract (list (extractable "bar" true)))))
    (is (= ["bar"] (extract [(extractable "bar" true)])))))

(deftest test-provided?
  (testing "recursively provide"
    (is (not (provided? {:foo (extractable "bar" false)})))
    (is (not (provided? #{(extractable "bar" false)})))
    (is (not (provided? (list (extractable "bar" false)))))
    (is (not (provided? [(extractable "bar" false)])))))
