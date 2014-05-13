(ns outpace.config-test
  (:use clojure.test
        outpace.config))

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
        (is (not (contains? @required `ddd)))
        (is (contains? @defaults `ddd)))
      (testing "Including docstring."
        (defconfig ddd "doc" :default2)
        (is (= "doc" (:doc (meta #'ddd))))
        (is (= :default2 ddd))
        (is (not (contains? @required `ddd)))
        (is (contains? @defaults `ddd)))
      (testing "Omitting docstring."
        (defconfig ddd :default3)
        (is (nil? (:doc (meta #'ddd))))
        (is (= :default3 ddd))
        (is (not (contains? @required `ddd)))
        (is (contains? @defaults `ddd)))
      (testing "Omitting default does not remove it, just like def."
        (defconfig ddd)
        (is (nil? (:doc (meta #'ddd))))
        (is (= :default3 ddd))
        (is (not (contains? @required `ddd)))
        (is (contains? @defaults `ddd)))))
  (testing "With config entry"
    (with-redefs [config {`eee :config `fff :config `ggg :config `hhh :config}]
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
          (is (not (contains? @required `hhh)))
          (is (= :default (@defaults `hhh))))
        (testing "Including docstring."
          (defconfig hhh "doc" :default2)
          (is (= "doc" (:doc (meta #'hhh))))
          (is (= :config hhh))
          (is (not (contains? @required `hhh)))
          (is (= :default2 (@defaults `hhh))))
        (testing "Omitting docstring."
          (defconfig hhh :default3)
          (is (nil? (:doc (meta #'hhh))))
          (is (= :config hhh))
          (is (not (contains? @required `hhh)))
          (is (= :default3 (@defaults `hhh))))
        (testing "Omitting default does not remove it, just like def."
          (defconfig hhh)
          (is (nil? (:doc (meta #'hhh))))
          (is (= :config hhh))
          (is (not (contains? @required `hhh)))
          (is (= :default3 (@defaults `hhh)))))))
  (doseq [v [#'aaa #'bbb #'ccc #'ddd #'eee #'fff #'ggg #'hhh]]
    (.unbindRoot v)))

(deftest test-defconfig!
  (testing "Preserves metadata"
    (with-redefs [config {`req1 :config}]
      (defconfig! ^{:doc "foobar"} req1)
      (is (-> #'req1 meta :required))
      (is (= "foobar" (-> #'req1 meta :doc)))))
  (testing "No error when value provided"
    (with-redefs [config {`req2 :config}]
      (defconfig! req2)
      (is (-> #'req2 meta :required))))
  (testing "Error when no value provided"
    (is (thrown? Exception (defconfig! req3)))))
