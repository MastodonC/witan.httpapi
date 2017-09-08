(ns witan.httpapi.config
  (:require [aero.core :as aero]
            [clojure.java.io :as io]))

(def profile (atom nil))

(defn save-profile!
  [p]
  (reset! profile p))

(defn read-config
  ([]
   (read-config @profile))
  ([profile]
   (aero/read-config (io/resource "config.edn") {:profile profile})))
