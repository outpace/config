(ns outpace.config.generate
  "Namespace for generating a config EDN file.
   Example usage:
     lein run -m outpace.config.generate :strict"
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.tools.namespace.repl :as nsr]
            [outpace.config :as conf]))

(def ^:private nl-str (println-str))

(defn- doc-str [sym]
  (if-let [doc (try (-> sym find-var meta :doc) (catch Exception e nil))]
    (let [[line & more] (->> (str/split-lines doc)
                          (drop-while str/blank?)
                          (reverse)
                          (drop-while str/blank?)
                          (reverse))
          trim-len (if more
                     (->> more
                       (map #(re-find #"^\s*" %))
                       (map count)
                       (apply min))
                     0)
          doc-lines (cons line (map #(subs % trim-len) more))]
      (apply str (interleave (repeat "; ")
                             doc-lines
                             (repeat nl-str))))
    ""))

(defn- unbound-str [sym]
  (str (doc-str sym)
       "#_" (pr-str sym) nl-str))

(defn- split? [& strs]
  (boolean (or (some (fn [^String s] (.contains s "\n")) strs)
               (< 80 (apply + (dec (count strs)) (map count strs))))))

(defn- entry-str
  ([sym val]
    (let [sym-str (pr-str sym)
          val-str (pr-str val)]
      (if (split? sym-str val-str)
        (str (doc-str sym)
             sym-str nl-str
             val-str nl-str)
        (str (doc-str sym)
             sym-str " " val-str nl-str))))
  ([sym val default]
    (let [sym-str (pr-str sym)
          val-str (pr-str val)
          def-str (str "#_" (pr-str default))]
      (if (split? sym-str val-str def-str)
        (str (doc-str sym)
             sym-str nl-str
             val-str nl-str
             def-str nl-str)
        (str (doc-str sym)
             sym-str " " val-str " " def-str nl-str)))))

(defn- default-str [sym default]
  (let [sym-str (str "#_" (pr-str sym))
        def-str (str "#_" (pr-str default))]
    (if (split? sym-str def-str)
      (str (doc-str sym)
           sym-str nl-str def-str nl-str)
      (str (doc-str sym)
           sym-str " " def-str nl-str))))

(defn- generate-config []
  (let [config-map      @conf/config
        default-map     @conf/defaults
        nodefault-set   @conf/non-defaulted
        config-keyset   (set (keys config-map))
        default-keyset  (set (keys default-map))
        available-set   (set/union config-keyset default-keyset)
        wanted-set      (set/union nodefault-set default-keyset)
        unbound-set     (into (sorted-set) (set/difference nodefault-set available-set))
        unused-set      (into (sorted-set) (set/difference available-set wanted-set))
        used-set        (into (sorted-set) (set/intersection available-set wanted-set))]
    (with-out-str
      (println "{")
      (when (seq unbound-set)
        (println)
        (println ";; UNBOUND CONFIG VARS:")
        (println)
        (print (->> unbound-set
                 (map unbound-str)
                 (interpose nl-str)
                 (apply str)))
        (println))
      (when (seq unused-set)
        (println)
        (println ";; UNUSED CONFIG ENTRIES:")
        (println)
        (print (->> unused-set
                 (map (fn [sym]
                        (entry-str sym (get config-map sym))))
                 (interpose nl-str)
                 (apply str)))
        (println))
      (when (seq used-set)
        (println)
        (println ";; CONFIG ENTRIES:")
        (println)
        (print (->> used-set
                 (map (fn [sym]
                        (if (contains? config-keyset sym)
                          (if (contains? default-map sym)
                            (entry-str sym (get config-map sym) (get default-map sym))
                            (entry-str sym (get config-map sym)))
                          (default-str sym (get default-map sym)))))
                 (interpose nl-str)
                 (apply str)))
        (println))
      (println "}"))))

(defn- generate-config-file []
  (let [dest (or (conf/find-config-source) "config.edn")]
    (println "Generating" dest)
    (spit dest (generate-config))))

(defn- generate-config-file-strict []
  (generate-config-file)
  (when-let [unbound-set (seq (conf/unbound))]
    (throw (Exception. (str "Generated a config EDN file with unbound config vars: " (pr-str (sort unbound-set)))))))

(defn -main
  "Generates a config EDN file from the defconfig entries on the classpath.
   Writes to the configuration source if provided, otherwise to 'config.edn'.

   The following flags are supported:
     :strict Errors on config vars with neither a default nor configured value"
  [& flags]
  (println "Loading namespaces")
  (let [strict? (some #{":strict"} flags)
        config-file (conf/find-config-source)]
    (when (and config-file (not (.exists (io/file config-file))))
      ; When an explicit config-source is provided, make sure the file exists
      ; before loading the config ns, otherwise it would be considered an error.
      (spit config-file "{}"))
    (nsr/refresh :after (if strict?
                          `generate-config-file-strict
                          `generate-config-file))
    (shutdown-agents)))
