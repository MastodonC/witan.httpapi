(ns seeds.dynamodb
  (:require [witan.httpapi.components.database :as db]
            [witan.httpapi.config :as config]
            [taoensso.timbre :as log]))

(defn get-db-config
  []
  (let [conf (config/read-config)]
    (:dynamodb conf)))

(defn run-dev [target & args])

(defn run-staging [target & args])

(defn run-prod [target & args])
