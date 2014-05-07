# Config


A library for declaring configuration vars in a centralized fashion. The complementary plugin allows one to gather and emit all configured vars and their docstrings, default values, etc.


## Motiviation

Most configuration systems rely on consumers pulling named values from something akin to a global map (cf. [environ](https://github.com/weavejester/environ)). This yields a number of negative consequences:

- It difficult to identify what configuration values are required by a system.
- There is no single place to put documentation for a configurable entry.
- Default values for missing configuration can be inconsistent across consumers.
- Sources of configuration values are widely varied, e.g., properties files, system environment variables.

This library attempts to address the above issues by:

- Using declaration of configurable vars, which can be documented, defaulted, and used as a canonical source of data for other code just as any other library-specific var would be.
- Providing introspection into the configurable surface of an application and its dependencies.
- Relying on pushing values to all configuration vars from a single source.


## Overview

Configuration is provided in an [EDN](http://edn-format.org) map of namespaced symbols (naming a config var) to the value to be bound to the corresponding var:

```clojure
{
com.example/greeting       "Hello World!"
com.example/tree           {:id 1, :children #{{:id 2} {:id 3}}}
com.example/aws-secret-key #config/env "AWS_SECRET_KEY"
}
```

As shown above, a custom data reader has been provided to allow for pulling in String values from the environment. If the environment does not have that entry, the var will use its default value or remain unbound.

The configuration EDN map is provided to an application in one of two ways:

1. A `config.edn` file in the current working directory.
2. A `config.edn` java system property (e.g., a command line arg `-Dconfig.edn=...`). The value can be any string consumable by [`clojure.java.io/reader`](http://clojure.github.io/clojure/clojure.java.io-api.html#clojure.java.io/reader).

If both are provided, the system property will be used.

Provisioning via environment variable is intentionally unsupported, though feel free to use something like `-Dconfig.edn=$CONFIG_EDN` when starting an application.


## Installation

*Note: until these are pushed into our repo, `git clone` this project and then invoke `lein install` in each subdirectory.*

Applications and libraries wishing to declare config vars, add the following dependency in your `project.clj` file:

```clojure
:dependencies [[com.outpace/config "0.1.0"]]
```

Applications wishing to expose config vars and generate a base `config.edn` file, add the following plugin in your `project.clj` file:

```clojure
:plugins [[com.outpace/lein-config "0.1.0"]]
```

Note: it is inappropriate for libraries to include their own `config.edn` since that is an application deployment concern. Including default values in-code (which can then be exposed by the plugin) is acceptable.


## Config Usage

Declaring config vars is straightforward:

```clojure
(require '[com.outpace.config :refer [defconfig]])

(defconfig my-var)

(defconfig var-with-default 42)

(defconfig ^:dynamic *rebindable-var*)
```

As shown above, the `defconfig` form supports anything a regular `def` form does.

The `com.outpace.config` namespace includes the current state of the configuration, and while it can be used by code to explicitly pull config values, **this is strongly discouraged**; just use `defconfig`.

## Plugin Usage

The plugin has one function, to generate a `config.edn` file containing everything one may need to know about the state of the config vars in the application and its dependent namespaces.

To generate a `config.edn` file, invoke the following the the same directory as your `project.clj` file:

```bash
lein config
```

If a `config.edn` file is already present, its config contents will be loaded, and thus be preserved by the replacing file.

~~Entries that explicitly override a config var that has a default value will have that (commented-out) default value shown as well.~~ TBD

Config vars that do not have a default value, and are not yet specified in the `config.edn` (thus will be unbound at runtime) will be listed at the top of the file as a commented-out set.

Entries that have no corresponding config var will be noted as such, and preserved across regenerations.  This may happen when a library with config vars has been removed from the application's dependencies. If the library is added back, the entries will still be used, and regeneration will re-categorize them.

Config vars that have a default value, but no explicitly set value will be shown as a commented-out map.


