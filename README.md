# Config


A library for declaring configuration vars and setting their values in a centralized fashion. Included tooling allows one to gather and emit all config vars and their docstrings, default values, etc.

[Latest API Docs](https://outpace.github.io/config/latest/)

[Stable (0.9.0) API Docs](https://outpace.github.io/config/0.9.0/)

## Installation

Applications and libraries wishing to declare config vars should add the following to the `project.clj` file:

[![Clojars Project](http://clojars.org/com.outpace/config/latest-version.svg)](http://clojars.org/com.outpace/config)

Applications may also include the following to ease [generating a `config.edn` file](#generator-usage):

```clojure
:aliases {"config" ["run" "-m" "outpace.config.generate"]}
```


## Motivation

Most configuration systems rely on consumers pulling named values from something akin to a global map (cf. [environ](https://github.com/weavejester/environ)). This yields a number of negative consequences:

- It is difficult to identify what configuration values are required by a system.
- There is no single place to put documentation for a configurable entry.
- Multiple consumers may independently pull directly from the configuration source, leaving only the configuration key to reveal their shared dependency.
- Default values for missing configuration can be inconsistent across consumers.
- Sources of configuration values are widely varied, e.g., properties files, system environment variables.

This library attempts to address the above issues by:

- Using declaration of configurable vars, which can be documented, defaulted, and used as a canonical source of data for other code just as any other library-specific var would be.
- Providing introspection into the configurable surface of an application and its dependencies.
- Relying on pushing values to all configuration vars, and doing so from a single source.


## Overview

Configuration is provided in an [EDN](http://edn-format.org) map of namespaced symbols (naming a config var) to the value to be bound to the corresponding var:

```clojure
{
com.example/greeting       "Hello World!"
com.example/tree           {:id 1, :children #{{:id 2} {:id 3}}}
com.example/aws-secret-key #config/env "AWS_SECRET_KEY"
com.example/db-password    #config/file "db-password.txt"
com.example/secret-edn     #config/edn #config/file "secret_key.edn"
}
```

As shown above, custom data-reader tags can be used to [pull values from external sources](#external-config-values).

The configuration is provided to an application in one of the following ways:

1. A `config.edn` file in the current working directory.
2. A `config.edn` java system property (e.g., a command line arg `-Dconfig.edn=...`). The value can be anything consumable by [`clojure.java.io/reader`](http://clojure.github.io/clojure/clojure.java.io-api.html#clojure.java.io/reader).
3. A `config.etcd` java system property url (e.g., `-Dconfig.etcd=http://localhost:4000`) may be provided to allow etcd lookup for external values.
4. The primary source of configuration will be etcd if you include `config.etcd` and omit `config.edn`.
5. Setting `outpace.config/source` to any `outpace.config/Source`. See `outpace.config.repl/set-source-edn!`.

The `:profiles` entry of your `project.clj` file can be used to set the system property for an environment-specific configuration EDN file:

```clojure
:profiles {:test {:jvm-opts ["-Dconfig.edn=test-config.edn"]}
           :prod {:jvm-opts ["-Dconfig.edn=prod-config.edn"]}}
```


## Config Usage

Declaring config vars is straightforward:

```clojure
(require '[outpace.config :refer [defconfig]])

(defconfig the-answer)
;; not provided? -> exception
;; derefed? -> don't get here
;; is provided -> 58
;; later it is not provided
;; derefed? -> exception

(defconfig the-answer 42)
;; not provided? 42
;; provided? 58
;; derefed? either 58 or 42

;; meta-data is preserved
(defconfig ^:private the-secret-answer)

(defconfig ^{:validate [number? "Must be a number."
                        even?   "Must be even."]}
           an-even-number)
```

As shown above, the `defconfig` form supports anything a regular `def` form does, as well as the following metadata:
- `:validate` A vector of alternating single-arity predicates and error messages. After a value is set on the var, an exception will be thrown when a predicate, passed the set value, yields false.

The `outpace.config` namespace includes the current state of the configuration, and while it can be used by code to explicitly pull config values, this is discouraged; just use `defconfig`.


## External Config Values

Recognizing that it is not always appropriate to provide configuration values directly in the config file, custom data-readers can be used to instead convert a tagged literal in the config to an externally-provided value.  This allows one to still grasp the full configuration of an app, and at least know where a value will come from, if not the value itself.

The provided data-readers' tags are:

- `#config/env` Tags a string, interpreted as the name of an environment variable, and yields the string value of the environment variable. If the environment does not have that entry, then the var will use its default value or remain unbound.
- `#config/file` Tags a string, interpreted as a path to a file, and yields the string contents of the file. If the file does not exist, then the var will use its default value or remain unbound.
- `#config/edn` Tags a string, interpreted as a single EDN-formatted object, and yields the read object.  When composed with `#config/env` or `#config/file`, if the external value is not provided, then the var will use its default value or remain unbound.
- `#config/etcd` Tags a string, interpreted as a key to lookup a value in etcd.

[Custom data-readers](http://clojure.org/reader#The Reader--Tagged Literals) whose tag namespace is `config` will be automatically loaded during config initialization. See `outpace.config/read-env` for an example of how to properly implement a custom data-reader.


## Generator Usage

The `outpace.config.generate` namespace exists to generate a `config.edn` file containing everything one may need to know about the state of the config vars in the application and its dependent namespaces. If a `config.edn` file is already present, its contents will be loaded, and thus preserved by the replacing file.

To generate a `config.edn` file, invoke the following in the same directory as your `project.clj` file:

```bash
lein run -m outpace.config.generate
```

Alternately, one can just invoke `lein config` by adding the following to `project.clj`:

```clojure
:aliases {"config" ["run" "-m" "outpace.config.generate"]}
```

## Change Log

### v1.0.0
- Breaking change: config must be derefed.
- Breaking change: no more defconfig! Config is required unless a default is provided.
- Etcd config source
- Etcd external reader tag

### v0.9.0
- A required config (e.g. defconfig!) will not cause an error when running the generator.
- When running the generator if an error occurs, its details will be printed.

### v0.8.0
- `extract` and `provides?` now recursively visit data structures.  This allows something like the following in your `config.edn` file (which did not work before):

  ```clojure
  {foo.bar/aws-creds {:access-key #config/env "AWS_ACCESS_KEY_ID"
                      :secret-key #config/env "AWS_SECRET_ACCESS_KEY"}}
  ```

### v0.7.0
- Add `#config/edn` data-reader which can be composed with other readers to interpret values from content.

### v0.6.0
- Add `#config/file` data-reader set a config var's value to the contents of a file.

### v0.5.0

- Add `:validate` metadata support to `defconfig`.
- Generator now pretty-prints wide values.

### v0.4.0

- Add ability to set the config-source explicitly in-code. Allows custom code to set the config-source when neither providing a local `config.edn` file nor setting a system-property can be used (e.g., webapps).

### v0.3.0

- Add support for using custom data-readers when loading the config EDN. Tags with a `config` namespace (e.g., `#config/foo`) will have the corresponding data-reader function's namespace automatically loaded.

### v0.2.0

- Add in-repl ability to reload with a different config.

## License

    Copyright © Outpace Systems, Inc.

    Released under the Apache License, Version 2.0
