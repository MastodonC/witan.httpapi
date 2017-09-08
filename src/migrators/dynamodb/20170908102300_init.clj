(ns migrators.dynamodb.20170908102300-init
  (:require [witan.httpapi.components.database :as db]
            [witan.httpapi.config :as config]
            [witan.httpapi.queries :as queries]
            [taoensso.timbre :as log]))

(defn get-db-config
  []
  (let [conf (config/read-config)]
    (:dynamodb conf)))

(defn up
  [db]
  (let [conn (db/new-session (get-db-config) @config/profile)]
    (db/create-table conn
                     queries/receipts-table
                     [:id :s]
                     {:throughput {:read 1 :write 1}
                      :block? true})))

(defn down
  [db]
  (let [conn (db/new-session (get-db-config) @config/profile)]
    (db/delete-table conn queries/receipts-table)))
