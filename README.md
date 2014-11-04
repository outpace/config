# Config


A library for declaring configuration vars and setting their values in a centralized fashion. Included tooling allows one to gather and emit all config vars and their docstrings, default values, etc.


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
}
```

As shown above, a custom data-reader (`#config/env`) has been provided to allow for pulling in values from the environment. If the environment does not have that entry, the var will use its default value or remain unbound.

The configuration EDN map is provided to an application in one of the following ways:

1. A `config.edn` file in the current working directory.
2. A `config.edn` java system property (e.g., a command line arg `-Dconfig.edn=...`). The value can be any string consumable by [`clojure.java.io/reader`](http://clojure.github.io/clojure/clojure.java.io-api.html#clojure.java.io/reader).
3. Setting `outpace.config.bootstrap/explicit-config-source` to any non-nil value consumable by [`clojure.java.io/reader`](http://clojure.github.io/clojure/clojure.java.io-api.html#clojure.java.io/reader).

The `:profiles` entry of your `project.clj` file can be used to set the system property for an environment-specific configuration EDN file:

```clojure
:profiles {:test {:jvm-opts ["-Dconfig.edn=test-config.edn"]}
           :prod {:jvm-opts ["-Dconfig.edn=prod-config.edn"]}}
```


## Installation

Applications and libraries wishing to declare config vars, add the following dependency in your `project.clj` file:

```clojure
:dependencies [[com.outpace/config "0.5.0"]]
```

Note: it is inappropriate for libraries to include their own `config.edn` file since that is an application deployment concern. Including default values in-code (which can then be exposed by the generator) is acceptable.


## Config Usage

Declaring config vars is straightforward:

```clojure
(require '[outpace.config :refer [defconfig]])

(defconfig my-var)

(defconfig var-with-default 42)

(defconfig ^:dynamic *rebindable-var*)

(defconfig ^:required required-var)

(defconfig ^{:validate [number? "Must be a number."
                        even?   "Must be even."]}
           an-even-number)
```

As shown above, the `defconfig` form supports anything a regular `def` form does, as well as the following metadata:
- `:required` When true, an exception will be thrown if no default nor configured value is provided. See also `defconfig!`
- `:validate` A vector of alternating single-arity predicates and error messages. After a value is set on the var, an exception will be thrown when a predicate, passed the set value, yields false.

The `outpace.config` namespace includes the current state of the configuration, and while it can be used by code to explicitly pull config values, **this is strongly discouraged**; just use `defconfig`.


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

The generator can take an optional `:strict` flag (e.g., `lein config :strict`) that will result in an exception after file generation if there are any config vars with neither a default value nor configured value. This can be used to provide feedback to automated build systems.

The following is an example of a generated `config.edn` file:

```clojure
{

;; UNBOUND CONFIG VARS:

; This is the docstring for the 'foo' var. This
; var does not have a default value.
#_com.example/foo


;; UNUSED CONFIG ENTRIES:

com.example/bar 123


;; CONFIG ENTRIES:

; The docstring for aaa.
com.example/aaa :configured-aaa

; The docstring for bbb. This var has a default value.
com.example/bbb :configured-bbb #_:default-bbb

; The docstring for ccc. This var has a default value.
#_com.example/ccc #_:default-ccc

}
```

The first section lists commented-out config vars that do not have a default value nor configured value, thus will be unbound at runtime. If a config value is provided, these entries will be re-categorized after regeneration.

The second section lists config entries that have no corresponding config var. This may happen after code change, or when a dependent library has been removed. If the config var reappears, these entries will be re-categorized after regeneration.

The third section lists all config vars used by the system, and their respective values.  For reference purposes, commented-out default values will be included after the configured value.  Likewise, commented-out entries will be included when their default values are used.

## Change Log

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
