(ns migrators.dynamodb.20170908102300-init
  (:require [witan.httpapi.components.database :as db]
            [witan.httpapi.config :as config]
            [witan.httpapi.components.activities :as activities]
            [taoensso.timbre :as log]
            [witan.httpapi.spec :as spec]))

(defn get-db-config
  []
  (let [conf (config/read-config)]
    (:dynamodb conf)))

(defn up
  [db]
  (let [conn (db/new-session (get-db-config) @config/profile)]
    (db/create-table conn
                     activities/receipts-table
                     [(db/keywordns->db ::spec/id) :s]
                     {:throughput {:read 1 :write 1}
                      :block? true})
    (db/create-table conn
                     activities/upload-links-table
                     [(db/keywordns->db ::spec/id) :s]
                     {:throughput {:read 1 :write 1}
                      :block? true})))

(defn down
  [db]
  (let [conn (db/new-session (get-db-config) @config/profile)]
    (db/delete-table conn activities/receipts-table)
    (db/delete-table conn activities/upload-links-table)))
