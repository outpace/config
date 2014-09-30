(ns outpace.config-test
  (:import clojure.lang.ExceptionInfo)
  (:use clojure.test
        outpace.config))

(defn unmap-non-fn-vars [ns]
  (doseq [[sym var] (ns-interns ns)]
    (when-not (fn? @var)
      (ns-unmap ns sym))))

(use-fixtures :each (fn [f]
                      (unmap-non-fn-vars *ns*)
                      (f)))

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
            ev   (read-env name)]
        (is (= name (:name ev)))
        (is (nil? (:value ev)))
        (is (not (:defined? ev)))))))

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
        (is (not (contains? @non-defaulted `ddd)))
        (is (contains? @defaults `ddd)))
      (testing "Including docstring."
        (defconfig ddd "doc" :default2)
        (is (= "doc" (:doc (meta #'ddd))))
        (is (= :default2 ddd))
        (is (not (contains? @non-defaulted `ddd)))
        (is (contains? @defaults `ddd)))
      (testing "Omitting docstring."
        (defconfig ddd :default3)
        (is (nil? (:doc (meta #'ddd))))
        (is (= :default3 ddd))
        (is (not (contains? @non-defaulted `ddd)))
        (is (contains? @defaults `ddd)))
      (testing "Omitting default does not remove it, just like def."
        (defconfig ddd)
        (is (nil? (:doc (meta #'ddd))))
        (is (= :default3 ddd))
        (is (not (contains? @non-defaulted `ddd)))
        (is (contains? @defaults `ddd)))))
  (testing "With config entry"
    (with-redefs [config (delay {`eee :config `fff :config `ggg :config `hhh :config})]
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
          (is (not (contains? @non-defaulted `hhh)))
          (is (= :default (@defaults `hhh))))
        (testing "Including docstring."
          (defconfig hhh "doc" :default2)
          (is (= "doc" (:doc (meta #'hhh))))
          (is (= :config hhh))
          (is (not (contains? @non-defaulted `hhh)))
          (is (= :default2 (@defaults `hhh))))
        (testing "Omitting docstring."
          (defconfig hhh :default3)
          (is (nil? (:doc (meta #'hhh))))
          (is (= :config hhh))
          (is (not (contains? @non-defaulted `hhh)))
          (is (= :default3 (@defaults `hhh))))
        (testing "Omitting default does not remove it, just like def."
          (defconfig hhh)
          (is (nil? (:doc (meta #'hhh))))
          (is (= :config hhh))
          (is (not (contains? @non-defaulted `hhh)))
          (is (= :default3 (@defaults `hhh))))))))

(deftest test-defconfig!
  (testing "Preserves metadata"
    (with-redefs [config (delay {`req1 :config})]
      (defconfig! ^{:doc "foobar"} req1)
      (is (-> #'req1 meta :required))
      (is (= "foobar" (-> #'req1 meta :doc)))))
  (testing "No error when value provided"
    (with-redefs [config (delay {`req2 :config})]
      (defconfig! req2)
      (is (-> #'req2 meta :required))))
  (testing "Error when no value provided"
    (is (thrown? Exception (defconfig! req3)))))

(deftest test-validate
  (let [validate #'outpace.config/validate]
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
      (is (thrown-with-msg? ExceptionInfo #"boom" (validate 5 'foo/bar [even? "boom"]))))))

(deftest test-defconfig-assert
  (testing "without config-value"
    (testing "without default-value"
      (testing "validate not applied when no value provided"
        (is (defconfig ^{:validate [(constantly false) "should not happen"]} foo))))
    (testing "with default-value"
      (testing "no exception when default-value is valid"
        (is (defconfig ^{:validate [even? "boom"]} foo 4)))
      (testing "exception when default-value is invalid"
         (is (thrown? Exception (defconfig ^{:validate [even? "boom"]} foo 5))))))
  (with-redefs [config (delay {`foo 5})]
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


