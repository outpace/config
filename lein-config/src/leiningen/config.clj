(ns leiningen.config
  (:require [bultitude.core :as b]
            [clojure.java.io :as io]
            [leiningen.core.eval :as eval]
            [leiningen.core.main :as main]))

(def generate-file
  `(let [config-map#     conf/config
         default-map#    @conf/defaults
         required-set#   @conf/required
         config-keyset#  (set (keys config-map#))
         default-keyset# (set (keys default-map#))
         available-set#  (set/union config-keyset# default-keyset#)
         wanted-set#     (set/union required-set# default-keyset#)
         missing-set#    (set/difference required-set# available-set#)
         unused-set#     (set/difference available-set# wanted-set#)
         explicit-set#   (set/intersection config-keyset# wanted-set#)
         implicit-set#   (set/difference default-keyset# explicit-set#)
         content#        (with-out-str
                           (println "{")
                           (when (seq missing-set#)
                             (println)
                             (println ";; REQUIRED CONFIG VARS WITHOUT VALUES -- FIX THEM.")
                             (println)
                             (println "#_ #{")
                             (doseq [sym# missing-set#]
                               (when-let [doc# (:doc (meta (find-var sym#)))]
                                 (doseq [doc-line# (str/split-lines doc#)]
                                   (println ";" doc-line#)))
                               (prn sym#)
                               (println))
                             (println "}")
                             (println))
                           (when (seq unused-set#)
                             (println)
                             (println ";; UNUSED CONFIG ENTRIES.")
                             (println)
                             (doseq [sym# unused-set#]
                               (prn sym# (get config-map# sym#))
                               (println))
                             (println))
                           (when (seq explicit-set#)
                             (println)
                             (println ";; CONFIG VARS.")
                             (println)
                             (doseq [sym# explicit-set#]
                               (when-let [doc# (:doc (meta (find-var sym#)))]
                                 (doseq [doc-line# (str/split-lines doc#)]
                                   (println ";" doc-line#)))
                               (prn sym# (get config-map# sym#))
                               (println))
                             (println))
                           (when (seq implicit-set#)
                             (println)
                             (println ";; DEFAULTED CONFIG VARS.")
                             (println)
                             (println "#_ {")
                             (doseq [sym# implicit-set#]
                               (when-let [doc# (:doc (meta (find-var sym#)))]
                                 (doseq [doc-line# (str/split-lines doc#)]
                                   (println ";" doc-line#)))
                               (prn sym# (get default-map# sym#))
                               (println))
                             (println "}")
                             (println))
                           (println "}"))]
     (spit "config.edn" content#)))

(defn config
  "Generate a 'config.edn' file.
   
USAGE: lein config
(Re-)Generate a 'config.edn' file. Re-generation preserves existing entries."
  [project]
  (let [source-files (map io/file (:source-paths project))
        nses (b/namespaces-on-classpath :classpath source-files
                                        :ignore-unreadable? false)
        load-em `(doseq [ns# '~nses]
                   ;; load will add the .clj, so can't use ns/path-for.
                   (let [ns-file# (-> (str ns#)
                                    (.replace \- \_)
                                    (.replace \. \/))]
                     (load ns-file#)))]
    (try
      (binding [eval/*pump-in* false]
        (eval/eval-in-project project
          `(do
             (println "Loading namespaces")
             ~load-em
             (println "Generating config.edn")
             ~generate-file)
          '(require '[com.outpace.config :as conf]
                    '[clojure.java.io :as io]
                    '[clojure.pprint :refer [pprint]]
                    '[clojure.set :as set]
                    '[clojure.string :as str])))
      (catch clojure.lang.ExceptionInfo e
        (main/abort "Failed.")))))
