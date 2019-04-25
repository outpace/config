(ns outpace.config-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [outpace.config :as c :refer [defconfig defconfig!]])
  (:import (clojure.lang ExceptionInfo)
           (java.io File)
           (outpace.config EdnVal EnvVal FileVal OrVal PropVal)))

(defn unmap-non-fn-vars [ns]
  (doseq [[sym var] (ns-interns ns)]
    (when-not (fn? @var)
      (ns-unmap ns sym))))

(t/use-fixtures :each (fn [f]
                        (unmap-non-fn-vars *ns*)
                        (f)))

(deftest test-EnvVal
  (let [name     "name"
        value    "value"
        defined? true
        ev       (c/->EnvVal name value defined?)]
    (testing "EnvVal fields"
      (is (= name (:name ev)))
      (is (= value (:value ev)))
      (is (= defined? (:defined? ev))))
    (testing "EnvVal protocol implementations"
      (is (= value (c/extract ev)))
      (is (= defined? (c/provided? ev))))
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
          ev    (c/read-env name)]
      (is (instance? EnvVal ev))
      (is (= value (c/extract ev)))
      (is (c/provided? ev))))
  (testing "EnvVal for missing environment variable."
    (let [name (missing-env-var-name)
          ev   (c/read-env name)]
      (is (instance? EnvVal ev))
      (is (nil? (c/extract ev)))
      (is (not (c/provided? ev))))))

(deftest test-PropVal
  (let [name     "name"
        value    "value"
        defined? true
        ev       (c/->PropVal name value defined?)]
    (testing "PropVal fields"
      (is (= name (:name ev)))
      (is (= value (:value ev)))
      (is (= defined? (:defined? ev))))
    (testing "PropVal protocol implementations"
      (is (= value (c/extract ev)))
      (is (= defined? (c/provided? ev))))
    (testing "PropVal edn-printing"
      (is (= (str "#config/property " (pr-str name)) (pr-str ev))))))

(defn prop-var-name []
  (let [name (first (keys (java.lang.System/getProperties)))]
    (assert name "Cannot test read-property without properties defined")
    name))

(defn missing-prop-var-name []
  (let [ks (set (keys (System/getProperties)))]
    (first (remove ks (map str (range))))))

(deftest test-read-prop
  (testing "PropVal for extant property variable."
    (let [name  (prop-var-name)
          value (System/getProperty name)
          ev    (c/read-property name)]
      (is (instance? PropVal ev))
      (is (= value (c/extract ev)))
      (is (c/provided? ev))))
  (testing "PropVal for missing property variable."
    (let [name (missing-prop-var-name)
          ev   (c/read-property name)]
      (is (instance? PropVal ev))
      (is (nil? (c/extract ev)))
      (is (not (c/provided? ev))))))

(deftest test-FileVal
  (let [path     "path"
        contents "contents"
        exists?  true
        fv       (c/->FileVal path contents exists?)]
    (testing "FileVal fields"
      (is (= path (:path fv)))
      (is (= contents (:contents fv)))
      (is (= exists? (:exists? fv))))
    (testing "FileVal protocol implementations"
      (is (= contents (c/extract fv)))
      (is (= exists? (c/provided? fv))))
    (testing "FileVal edn-printing"
      (is (= (str "#config/file " (pr-str path)) (pr-str fv))))))

(deftest test-read-file
  (let [file     (File/createTempFile "test-read-file" ".txt")
        path     (.getPath file)
        contents "contents"]
    (spit file contents)
    (testing "FileVal for extant file."
      (let [fv (c/read-file path)]
        (is (instance? FileVal fv))
        (is (= contents (c/extract fv)))
        (is (c/provided? fv))))
    (.delete file)
    (testing "FileVal for missing file."
      (let [fv (c/read-file path)]
        (is (instance? FileVal fv))
        (is (nil? (c/extract fv)))
        (is (not (c/provided? fv)))))))

(deftest test-OrVal
  (testing "no values"
    (let [or-val (c/->OrVal [])]
      (is (nil? (c/extract or-val)))
      (is (false? (c/provided? or-val)))
      (is (= "#config/or []"
             (pr-str or-val)))))
  (testing "one undefined value"
    (let [or-val (c/->OrVal [(c/->EnvVal "foo" nil false)])]
      (is (nil? (c/extract or-val)))
      (is (false? (c/provided? or-val)))
      (is (= "#config/or [#config/env \"foo\"]"
             (pr-str or-val)))))
  (testing "one defined value"
    (let [or-val (c/->OrVal [(c/->EnvVal "foo" 42 true)])]
      (is (= 42 (c/extract or-val)))
      (is (true? (c/provided? or-val)))
      (is (= "#config/or [#config/env \"foo\"]"
             (pr-str or-val)))))
  (testing "two defined values"
    (let [or-val (c/->OrVal [(c/->EnvVal "foo" 42 true)
                             (c/->EnvVal "bar" 3.14 true)])]
      (is (= 42 (c/extract or-val)))
      (is (true? (c/provided? or-val)))
      (is (= "#config/or [#config/env \"foo\" #config/env \"bar\"]"
             (pr-str or-val)))))
  (testing "one undefined value and one defined value"
    (let [or-val (c/->OrVal [(c/->EnvVal "foo" nil false)
                             (c/->EnvVal "bar" 3.14 true)])]
      (is (= 3.14 (c/extract or-val)))
      (is (true? (c/provided? or-val)))
      (is (= "#config/or [#config/env \"foo\" #config/env \"bar\"]"
             (pr-str or-val))))))

(deftest test-read-or
  (testing "no values"
    (let [val (c/read-or [])]
      (is (instance? OrVal val))
      (is (nil? (c/extract val)))
      (is (false? (c/provided? val)))))
  (testing "one defined value"
    (let [ev-name (env-var-name)
          value (System/getenv ev-name)
          val (c/read-or [(c/read-env ev-name)])]
      (is (instance? OrVal val))
      (is (= value (c/extract val)))
      (is (c/provided? val))))
  (testing "one undefined value"
    (let [ev-name (missing-env-var-name)
          val (c/read-or [(c/read-env ev-name)])]
      (is (instance? OrVal val))
      (is (nil? (c/extract val)))
      (is (false? (c/provided? val)))))
  (testing "one constant and one undefined value"
    (let [ev-name (missing-env-var-name)
          val (c/read-or [1 (c/read-env ev-name)])]
      (is (instance? OrVal val))
      (is (= 1 (c/extract val)))
      (is (true? (c/provided? val)))))
  (testing "one undefined value and one constant"
    (let [ev-name (missing-env-var-name)
          val (c/read-or [(c/read-env ev-name) 1])]
      (is (instance? OrVal val))
      (is (= 1 (c/extract val)))
      (is (true? (c/provided? val)))))
  (testing "one undefined value and one defined value"
    (let [ev-name (missing-env-var-name)
          prop-name (prop-var-name)
          val (c/read-or [(c/read-env ev-name) (c/read-property prop-name)])]
      (is (instance? OrVal val))
      (is (= (System/getProperty prop-name)
             (c/extract val)))
      (is (true? (c/provided? val))))))

(deftest test-EdnVal
  (let [source           "{}"
        value            {}
        source-provided? true
        ev               (c/->EdnVal source value source-provided?)]
    (testing "EdnVal fields"
      (is (= source (:source ev)))
      (is (= value (:value ev)))
      (is (= source-provided? (:source-provided? ev))))
    (testing "EdnVal protocol implementations"
      (is (= value (c/extract ev)))
      (is (= source-provided? (c/provided? ev))))
    (testing "EdnVal edn-printing"
      (is (= (str "#config/edn " (pr-str source)) (pr-str ev))))))

(defn extractable [value provided]
  (reify
    c/Extractable
    (extract [_] value)
    c/Optional
    (provided? [_] provided)))

(deftest test-read-edn
  (testing "EdnVal for string"
    (let [source "{:foo 123}"
          value   {:foo 123}
          ev (c/read-edn source)]
      (is (instance? EdnVal ev))
      (is (= value (c/extract ev)))
      (is (c/provided? ev))))
  (testing "EdnVal for nil"
    (let [source nil
          ev (c/read-edn source)]
      (is (instance? EdnVal ev))
      (is (nil? (c/extract ev)))
      (is (c/provided? ev))))
  (testing "EdnVal of provided source"
    (let [source (extractable "{:foo 123}" true)
          ev (c/read-edn source)]
      (is (instance? EdnVal ev))
      (is (= {:foo 123} (c/extract ev)))
      (is (c/provided? ev))))
  (testing "EdnVal of not-provided source"
    (let [source (extractable nil false)
          ev (c/read-edn source)]
      (is (instance? EdnVal ev))
      (is (nil? (c/extract ev)))
      (is (not (c/provided? ev)))))
  (testing "EdnVal recurses for extant environment variable."
    (let [name (env-var-name)
          value (System/getenv name)
          source (str "{:foo 123 :bar (#{[{:baz #config/env " (pr-str name) "}]})}")
          value   {:foo 123 :bar (list #{[{:baz value}]})}
          ev (c/read-edn source)]
      (is (instance? EdnVal ev))
      (is (= value (c/extract ev)))
      (is (c/provided? ev))))
  (testing "EdnVal recurses for missing environment variable."
    (let [name (missing-env-var-name)
          source (str "{:foo 123 :bar (#{[{:baz #config/env " (pr-str name) "}]})}")
          value   {:foo 123 :bar (list #{[{:baz nil}]})}
          ev (c/read-edn source)]
      (is (instance? EdnVal ev))
      (is (= value (c/extract ev)))
      (is (not (c/provided? ev)))))
  (testing "EdnVal recurses for extant property value."
    (let [name (prop-var-name)
          value (System/getProperty name)
          source (str "{:foo 123 :bar (#{[{:baz #config/property " (pr-str name) "}]})}")
          value   {:foo 123 :bar (list #{[{:baz value}]})}
          ev (c/read-edn source)]
      (is (instance? EdnVal ev))
      (is (= value (c/extract ev)))
      (is (c/provided? ev))))
  (testing "EdnVal recurses for missing property value."
    (let [name (missing-prop-var-name)
          source (str "{:foo 123 :bar (#{[{:baz #config/property " (pr-str name) "}]})}")
          value   {:foo 123 :bar (list #{[{:baz nil}]})}
          ev (c/read-edn source)]
      (is (instance? EdnVal ev))
      (is (= value (c/extract ev)))
      (is (not (c/provided? ev))))))


(deftest test-defconfig
  (testing "Without config entry."
    (testing "Error on required."
      (is (thrown? Exception (defconfig ^:required req))))
    (testing "No default val, no docstring."
      (defconfig aaa)
      (is (not (bound? #'aaa))))
    (testing "With default, no docstring."
      (defconfig bbb :default)
      (is (= :default bbb)))
    (testing "With default and docstring."
      (defconfig ccc "doc" :default)
      (is (= :default ccc))
      (is (= "doc" (:doc (meta #'ccc)))))
    (testing "Repeat defconfigs yield consistent state."
      (defconfig ddd)
      (testing "Including default."
        (defconfig ddd :default)
        (is (nil? (:doc (meta #'ddd))))
        (is (= :default ddd))
        (is (not (contains? @c/non-defaulted `ddd)))
        (is (contains? @c/defaults `ddd)))
      (testing "Including docstring."
        (defconfig ddd "doc" :default2)
        (is (= "doc" (:doc (meta #'ddd))))
        (is (= :default2 ddd))
        (is (not (contains? @c/non-defaulted `ddd)))
        (is (contains? @c/defaults `ddd)))
      (testing "Omitting docstring."
        (defconfig ddd :default3)
        (is (nil? (:doc (meta #'ddd))))
        (is (= :default3 ddd))
        (is (not (contains? @c/non-defaulted `ddd)))
        (is (contains? @c/defaults `ddd)))
      (testing "Omitting default does not remove it, just like def."
        (defconfig ddd)
        (is (nil? (:doc (meta #'ddd))))
        (is (= :default3 ddd))
        (is (not (contains? @c/non-defaulted `ddd)))
        (is (contains? @c/defaults `ddd)))))
  (testing "With config entry"
    (with-redefs [c/config (delay {`eee :config `fff :config `ggg :config `hhh :config})]
      (testing "No default val, no docstring"
        (defconfig eee)
        (is (= :config eee)))
      (testing "With default, no docstring"
        (defconfig fff :default)
        (is (= :config fff)))
      (testing "With default and docstring"
        (defconfig ggg "doc" :default)
        (is (= :config ggg))
        (is (= "doc" (:doc (meta #'ggg)))))
      (testing "Repeat defconfigs yield consistent state."
        (defconfig hhh)
        (testing "Including default."
          (defconfig hhh :default)
          (is (nil? (:doc (meta #'hhh))))
          (is (= :config hhh))
          (is (not (contains? @c/non-defaulted `hhh)))
          (is (= :default (@c/defaults `hhh))))
        (testing "Including docstring."
          (defconfig hhh "doc" :default2)
          (is (= "doc" (:doc (meta #'hhh))))
          (is (= :config hhh))
          (is (not (contains? @c/non-defaulted `hhh)))
          (is (= :default2 (@c/defaults `hhh))))
        (testing "Omitting docstring."
          (defconfig hhh :default3)
          (is (nil? (:doc (meta #'hhh))))
          (is (= :config hhh))
          (is (not (contains? @c/non-defaulted `hhh)))
          (is (= :default3 (@c/defaults `hhh))))
        (testing "Omitting default does not remove it, just like def."
          (defconfig hhh)
          (is (nil? (:doc (meta #'hhh))))
          (is (= :config hhh))
          (is (not (contains? @c/non-defaulted `hhh)))
          (is (= :default3 (@c/defaults `hhh)))))))
  (testing "When compiling should not read config"
    (binding [*compile-files* true]
      (with-redefs [c/config (delay (throw (java.io.FileNotFoundException. "should never happen")))]
        (defconfig iii)))))

(deftest test-defconfig!
  (testing "Preserves metadata"
    (with-redefs [c/config (delay {`req1 :config})]
      (defconfig! ^{:doc "foobar"} req1)
      (is (-> #'req1 meta :required))
      (is (= "foobar" (-> #'req1 meta :doc)))))
  (testing "No error when value provided"
    (with-redefs [c/config (delay {`req2 :config})]
      (defconfig! req2)
      (is (-> #'req2 meta :required))))
  (testing "Error when no value provided"
    (is (thrown? Exception (defconfig! req3))))
  (testing "Recursive extraction"
    (testing "no error when value provided"
      (let [name (env-var-name)
            value (System/getenv name)]
        (with-redefs [c/config (delay {`req4 {:foo (c/read-env name)}})]
          (defconfig! req4)
          (is (= {:foo value} req4))
          (is (-> #'req4 meta :required)))))
    (testing "error when no value provided"
      (let [name (missing-env-var-name)]
        (with-redefs [c/config (delay {`req5 {:foo (c/read-env name)}})]
          (is (thrown? Exception (defconfig! req5))))))))

(deftest test-validate
  (testing "Tests must be a vector"
    (is (thrown? AssertionError (c/validate 5 'foo {}))))
  (testing "Tests vector must be even"
    (is (thrown? AssertionError (c/validate 5 'foo [even?]))))
  (testing "Tests vector must be alternating fns"
    (is (thrown? AssertionError (c/validate 5 'foo [even? "a" 5 "b"]))))
  (testing "Tests vector must be alternating strings"
    (is (thrown? AssertionError (c/validate 5 'foo [even? "a" even? 5]))))
  (testing "No error when no tests"
    (is (nil? (c/validate 5 'foo []))))
  (testing "Exceptions in order of declaration"
    (is (thrown-with-msg? ExceptionInfo #"bar" (c/validate 5 'foo [odd? "foo" even? "bar" even? "baz"]))))
  (testing "Exception message contains qualified var name."
    (is (thrown-with-msg? ExceptionInfo #"foo/bar" (c/validate 5 'foo/bar [even? "boom"]))))
  (testing "Exception message contains error message."
    (is (thrown-with-msg? ExceptionInfo #"boom" (c/validate 5 'foo/bar [even? "boom"])))))

(deftest test-defconfig-validate
  (testing "without config-value"
    (testing "without default-value"
      (testing "validate not applied when no value provided"
        (is (defconfig ^{:validate [(constantly false) "should not happen"]} foo))))
    (testing "with default-value"
      (testing "no exception when default-value is valid"
        (is (defconfig ^{:validate [even? "boom"]} foo 4)))
      (testing "exception when default-value is invalid"
        (is (thrown? Exception (defconfig ^{:validate [even? "boom"]} foo 5))))))
  (with-redefs [c/config (delay {`foo 5})]
    (testing "with config-value"
      (testing "without default-value"
        (testing "no exception when configured value is valid"
          (is (defconfig ^{:validate [odd? "boom"]} foo)))
        (testing "exception when configured value is invalid"
          (is (thrown? Exception (defconfig ^{:validate [even? "boom"]} foo)))))
      (testing "with default-value"
        (testing "no exception when configured value is valid, but default isn't"
          (is (defconfig ^{:validate [odd? "boom"]} foo 4)))
        (testing "exception when configured value is invalid, but default isn't"
          (is (thrown? Exception (defconfig ^{:validate [even? "boom"]} foo 4))))))))

(deftest test-extract
  (testing "recursively extract"
    (is (= {:foo "bar"} (c/extract {:foo (extractable "bar" true)})))
    (is (= #{"bar"} (c/extract #{(extractable "bar" true)})))
    (is (= (list "bar") (c/extract (list (extractable "bar" true)))))
    (is (= ["bar"] (c/extract [(extractable "bar" true)])))))

(deftest test-provided?
  (testing "recursively provide"
    (is (not (c/provided? {:foo (extractable "bar" false)})))
    (is (not (c/provided? #{(extractable "bar" false)})))
    (is (not (c/provided? (list (extractable "bar" false)))))
    (is (not (c/provided? [(extractable "bar" false)])))))

(deftest config-source-test
  (let [source "config.clj"
        config (atom (with-meta {} {:source source}))]
    (with-redefs [c/config config]
      (is (= source (c/config-source))))))
