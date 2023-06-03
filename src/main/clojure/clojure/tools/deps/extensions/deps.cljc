;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^{:skip-wiki true}
  clojure.tools.deps.extensions.deps
  (:require
    #?(:clj [clojure.java.io :as jio] :cljr [clojure.clr.io :as cio])
    [clojure.tools.deps :as deps]
    [clojure.tools.deps.extensions :as ext]
    [clojure.tools.deps.util.dir :as dir]
    [clojure.tools.deps.util.io :as io]
    [clojure.tools.deps.util.session :as session])
  (:import
    #?(:clj [java.io File] :cljr [System.IO FileInfo DirectoryInfo])))

(set! *warn-on-reflection* true)

(defn- deps-map
  [config dir]
  (let [f (#?(:clj jio/file :cljr cio/file-info) dir "deps.edn")]
    (session/retrieve
      {:deps :map :file (#?(:clj .getAbsolutePath :cljr .FullName) f)} ;; session key
      #(if (#?(:clj .exists :cljr .Exists) f)
         (deps/merge-edns [(deps/root-deps) (deps/slurp-deps f)])
         (deps/root-deps)))))

(defmethod ext/coord-deps :deps
  [_lib {:keys [deps/root] :as _coord} _mf config]
  (dir/with-dir (#?(:clj jio/file :cljr cio/file-info) root)
    (seq (:deps (deps-map config root)))))

(defmethod ext/coord-paths :deps
  [_lib {:keys [deps/root] :as _coord} _mf config]
  (dir/with-dir (#?(:clj jio/file :cljr cio/file-info) root)
    (->> (:paths (deps-map config root))
      (map #(dir/canonicalize (#?(:clj jio/file :cljr identity) %)))
      (map #(do
              (when (not (dir/sub-path? %))
                (io/printerrln "WARNING: Deprecated use of path" % "external to project" root))
              %))
      #?(:clj (map #(.getCanonicalPath ^File %))
	     :cljr (map #(.FullName ^FileInfo %)))
      vec)))

(defmethod ext/manifest-file :deps
  [_lib {:keys [deps/root] :as _coord} _mf _config]
  (let [manifest (#?(:clj jio/file :cljr cio/file-info) root "deps.edn")]
    (when (#?(:clj .exists :cljr .Exists) manifest)
      (#?(:clj .getAbsolutePath :cljr .FullName) manifest))))

(defmethod ext/coord-usage :deps [lib {:keys [deps/root] :as _coord} manifest-type config]
  (dir/with-dir (#?(:clj jio/file :cljr cio/file-info) root)
    (:tools/usage (deps-map config root))))

(defmethod ext/prep-command :deps [lib {:keys [deps/root] :as _coord} manifest-type config]
  (dir/with-dir (#?(:clj jio/file :cljr cio/file-info) root)
    (let [external-deps (deps-map config root)]
      (when-let [prep-info (:deps/prep-lib external-deps)]
        (let [exec-args (-> external-deps :aliases (get (:alias prep-info)) :exec-args)]
          (cond-> prep-info
            exec-args (assoc :exec-args exec-args)))))))