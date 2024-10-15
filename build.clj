(ns build
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as deps-deploy]
            [clojure.string :as str]
            [clojure.edn :as edn]))

(let [deps-edn (edn/read-string (slurp "deps.edn"))]
  (def lib (get-in deps-edn [:io.github.evenmoreirrelevance/libdesc :lib]))
  (def version (get-in deps-edn [:io.github.evenmoreirrelevance/libdesc :mvn/version])))

(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))

;; delay to defer side effects (artifact downloads)
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn- jar-opts [opts]
  (assoc opts
         :lib lib :version version
         :jar-file jar-file
         :scm {:tag (str "v" version)}
         :basis @basis
         :class-dir class-dir
         :target "target"
         :src-dirs ["src"]))

(defn clean [_]
  (b/delete {:path "target"}))

(defn sync-pom [_]
  (b/write-pom (conj (jar-opts {}) {:target "." :class-dir nil})))

(defn export-kondo [_]
  (let [postfix (str (str/replace (namespace lib) \. \/) "/")]
    (b/copy-dir {:src-dirs [(str ".clj-kondo/" postfix)]
                 :target-dir (str "resources/clj-kondo.exports/" postfix)})))

(defn jar [_]
  (sync-pom _)
  (export-kondo _)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn install [_]
  (b/install (jar-opts {})))

(def clojars-creds
  (->
   (edn/read-string (slurp "../clojars-credentials.edn"))
   (as-> % (assoc % :password (get-in % [:passwords (namespace lib)])))))

(alter-var-root (var deps-deploy/default-repo-settings) update "clojars" merge clojars-creds)

(defn dump-reader
  [src targ]
  (let [buffsrc (java.io.BufferedReader. ^java.io.Reader src)]
    (doseq [^String l (take-while some? (repeatedly #(.readLine buffsrc)))]
      (.append ^java.io.Writer targ l))))

(defn runit
  ([args] (runit {} args))
  ([{:keys [input error]} args]
   (let [^java.util.List args (vec args)
         input (or input *out*)
         error (or error *err*)
         p (.start (ProcessBuilder. args))
         re (future (dump-reader (.errorReader p) error))
         ri (future (dump-reader (.inputReader p) input))
         res (try (.waitFor p) (finally (.destroy p)))]
     (run! deref [re ri])
     res)))

(defn deploy [_]
  (let [b (with-out-str (runit {:input *out*} ["git" "rev-parse" "--abbrev-ref" "HEAD"]))]
    (when-not (= b "main")
      (throw (ex-info "must be on main branch" {:branch b}))))
  (when-not (and (= 0 (runit ["git" "diff-index" "--quiet" "--cached" "HEAD" "--"]))
                 (= 0 (runit ["git" "diff-files" "--quiet"])))
    (throw (ex-info "worktree or index not clean" {})))
  (jar _)
  (deps-deploy/deploy
   {:artifact jar-file
    :installer :remote
    :sign-releases? false})
  (runit ["git" "commit" "-am"])
  (when-not (= 0 (runit ["git" "tag" (str "v" version)]))
    (throw (ex-info "version already tagged" {:version version})))
  (runit ["git" "push" "origin" "tag" (str "v" version)]))
