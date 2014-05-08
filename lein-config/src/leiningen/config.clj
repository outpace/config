(ns leiningen.config
  (:require [bultitude.core :as b]
            [clojure.java.io :as io]
            [leiningen.core.eval :as eval]
            [leiningen.core.main :as main]))

(def doc-str
  `(fn [sym#]
     (if-let [doc# (try (-> sym# find-var meta :doc) (catch Exception e# nil))]
       (let [[line# & more#] (->> (str/split-lines doc#)
                               (drop-while str/blank?)
                               (reverse)
                               (drop-while str/blank?)
                               (reverse))
             trim-len# (->> more#
                         (map #(re-find #"^\s*" %))
                         (map count)
                         (apply min 0))
             doc-lines# (cons line# (map #(.substring % trim-len#) more#))]
         (with-out-str
           (doseq [doc-line# doc-lines#]
             (println ";" doc-line#))))
       "")))

(def unbound-str
  `(fn [sym#]
     (str (~doc-str sym#) "#_" (prn-str sym#))))

(def entry-str
  `(fn [sym# val# def#]
     (let [sym-str# (pr-str sym#)
           val-str# (pr-str val#)
           def-str# (when def# (pr-str def#))
           split?#  (or (.contains val-str# "\n")
                        (and def-str# (.contains def-str# "\n"))
                        (< 80 (if def-str#
                                (+ (count sym-str#) 1 (count val-str#) 3 (count def-str#))
                                (+ (count sym-str#) 1 (count val-str#)))))]
       (with-out-str
         (print (~doc-str sym#))
         (if split?#
           (do
             (println sym-str#)
             (println val-str#)
             (when def-str#
               (print "#_") (println def-str#)))
           (do
             (print sym-str#) (print " ") (print val-str#)
             (if def-str#
               (do (print " #_") (println def-str#))
               (println))))))))

(def default-str
  `(fn [sym# def#]
     (let [sym-str# (pr-str sym#)
           def-str# (pr-str def#)
           split?#  (or (.contains def-str# "\n")
                        (< 80 (+ 2 (count sym-str#)
                                 3 (count def-str#))))]
       (with-out-str
         (print (~doc-str sym#))
         (if split?#
           (do
             (print "#_") (println sym-str#)
             (print "#_") (println def-str#))
           (do
             (print "#_") (print sym-str#) (print " #_") (println def-str#)))))))

(def generate-file
  `(let [config-map#     conf/config
         default-map#    @conf/defaults
         required-set#   @conf/required
         config-keyset#  (set (keys config-map#))
         default-keyset# (set (keys default-map#))
         available-set#  (set/union config-keyset# default-keyset#)
         wanted-set#     (set/union required-set# default-keyset#)
         unbound-set#    (set/difference required-set# available-set#)
         unused-set#     (set/difference available-set# wanted-set#)
         used-set#       (set/intersection available-set# wanted-set#)
         unbound-str#    ~unbound-str
         entry-str#      ~entry-str
         default-str#    ~default-str
         newline#        (println-str)
         content#        (with-out-str
                           (println "{")
                           (when (seq unbound-set#)
                             (println)
                             (println ";; UNBOUND CONFIG VARS:")
                             (println)
                             (print (->> unbound-set#
                                      (sort)
                                      (map unbound-str#)
                                      (interpose newline#)
                                      (apply str)))
                             (println))
                           (when (seq unused-set#)
                             (println)
                             (println ";; UNUSED CONFIG ENTRIES:")
                             (println)
                             (print (->> unused-set#
                                      (sort)
                                      (map (fn [sym#]
                                             (entry-str# sym# (get config-map# sym#) nil)))
                                      (interpose newline#)
                                      (apply str)))
                             (println))
                           (when (seq used-set#)
                             (println)
                             (println ";; CONFIG ENTRIES:")
                             (println)
                             (print (->> used-set#
                                      (sort)
                                      (map (fn [sym#]
                                             (if (contains? config-keyset# sym#)
                                               (entry-str# sym# (get config-map# sym#) (get default-map# sym#))
                                               (default-str# sym# (get default-map# sym#)))))
                                      (interpose newline#)
                                      (apply str)))
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
