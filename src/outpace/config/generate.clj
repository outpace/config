(ns outpace.config.generate
  "Namespace for generating a config EDN file.
   Example usage:
     lein run -m outpace.config.generate :strict"
  (:require [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.set :as set]
            [clojure.stacktrace :refer [print-cause-trace]]
            [clojure.string :as str]
            [clojure.tools.namespace.repl :as nsr]
            [outpace.config :as conf])
  (:import [outpace.config EdnSource]))

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
                             (repeat \newline))))
    ""))

(defn- pretty-str [val]
  (str/trim-newline
    (with-out-str
      (pprint val))))

(defn- split? [& strs]
  (boolean (or (some (fn [^String s] (.contains s "\n")) strs)
               (< 80 (apply + (dec (count strs)) (map count strs))))))

(defn- join [& strs]
  (str/join (if (apply split? strs) \newline \space)
            strs))

(defn- unprovided-entry [sym]
  (str "#_" (pr-str sym)))

(defn- val-entry [sym val]
  (join (pr-str sym) (pretty-str val)))

(defn- val-default-entry [sym val default]
  (join (pr-str sym)
               (pretty-str val)
               (str "#_" (pretty-str default))))

(defn- default-entry [sym default]
  (join
   (str "#_" (pr-str sym))
   (str "#_" (pretty-str default))))

(defn- section [syms title sym->entry]
  (when (seq syms)
    (->> (for [sym syms]
           (str (doc-str sym) (sym->entry sym)))
         (cons (format ";; %s:" title))
         (mapcat str/split-lines)
         (str/join "\n "))))

(defn- generate-sections []
  (let [config-map      (when (instance? EdnSource @conf/source)
                          (conf/extract (conf/read-edn (conf/->FileVal (:path @conf/source)))))
        default-map     @conf/defaults
        nodefault-set   @conf/non-defaulted
        config-keyset   (set (keys config-map))
        default-keyset  (set (keys default-map))
        available-set   (set/union config-keyset default-keyset)
        wanted-set      (set/union nodefault-set default-keyset)
        unprovided-set  (into (sorted-set) (set/difference nodefault-set available-set))
        unused-set      (into (sorted-set) (set/difference available-set wanted-set))
        used-set        (into (sorted-set) (set/intersection available-set wanted-set))]
    [(section unprovided-set "Unprovided" unprovided-entry)
     (section unused-set "Unused"
              (fn unused-str [sym]
                (val-entry sym (get config-map sym))))
     (section used-set "Existing"
              (fn used-str [sym]
                (if (contains? config-keyset sym)
                  (if (contains? default-map sym)
                    (val-default-entry sym (get config-map sym) (get default-map sym))
                    (val-entry sym (get config-map sym)))
                  (default-entry sym (get default-map sym)))))]))

(defn- generate-config []
  (str "{" (str/join "\n\n " (remove nil? (generate-sections))) "}\n"))

(defn- generate-config-file []
  (let [dest "config.generated.edn"]
    (println "Generating" dest)
    (spit dest (generate-config))))

(defn -main
  "Generates a config EDN file from the defconfig entries on the classpath.
  Writes to the configuration source if provided, otherwise to 'config.edn'."
  [& args]
  (println "Loading namespaces")
  (with-redefs [conf/generating? true]
    (binding [*e nil]
      (nsr/refresh :after `generate-config-file)
      (when *e
        (print-cause-trace *e))))
  (shutdown-agents))
