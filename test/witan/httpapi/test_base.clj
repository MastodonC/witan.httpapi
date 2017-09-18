(ns witan.httpapi.test-base
  (:require  [clojure.test :as t]
             [witan.httpapi.system :as sys]
             [com.stuartsierra.component :as component]
             [environ.core :refer [env]]))

(def profile (keyword (env :system-profile "test")))

(defn uuid
  []
  (str (java.util.UUID/randomUUID)))

(defn random-user
  []
  {:kixi.user/id (uuid)
   :kixi.user/groups [(uuid)]})

(defn std-system
  [sys-fn]
  (sys-fn))
