(ns witan.httpapi.components.database
  (:require [com.stuartsierra.component :as component]
            [clojure.java.io :as io]
            [joplin.repl :as jrepl :refer [migrate load-config]]
            [taoensso.timbre :as log]
            [taoensso.faraday :as far]
            [clojure.spec.alpha :as s]
            [witan.httpapi.cloudwatch :refer [table-dynamo-alarms]]))

(def app "witan.httpapi")

(def db-keyword-delimiter "__")

(defn db->keywordns [kw] (keyword (clojure.string/replace-first kw db-keyword-delimiter "/")))

(defn keywordns->db [kw] (str (namespace kw) db-keyword-delimiter (name kw)))

(defn convert-keywords-from-db [m]
  (reduce-kv (fn [a k v] (assoc a (db->keywordns k) v)) {} m))

(defn convert-keywords-for-db [m]
  (reduce-kv (fn [a k v] (assoc a (keywordns->db k) v)) {} m))

(defn decorated-table
  [table prefix]
  (keyword (clojure.string/join "-" [prefix app table])))

(defn prefix
  [conf]
  (:prefix (:db-conf conf)))

(defn alerts
  [conf]
  (:alerts (:db-conf conf)))

(defn db
  [conf]
  (or (:db (:db-conf conf)) {}))

(defn alert-conf
  [conf]
  (get-in conf [:db-conf :alerts]))

(defmulti table-spec identity)

(defprotocol Database
  (create-table [this table index opts])
  (delete-table [this table])
  (update-table [this table opts])
  (put-item [this table record opts])
  (get-item [this table where opts])
  (query [this table where opts])
  (update-item [this table where opts])
  (delete-item [this table where opts])
  (scan [this table]))

(defrecord DynamoDB [db-conf profile]
  Database
  (create-table [this table index {:keys [throughput] :as opts}]
    (let [table-name (decorated-table table (prefix this)) ]
      (far/create-table (db this)
                        table-name
                        index
                        opts)
      (when (alerts this)
        (try
          (table-dynamo-alarms table-name (assoc (alert-conf this)
                                                 :read-provisioned (:read throughput)
                                                 :write-provisioned (:write throughput)))
          (catch Exception e
            (log/error e "failed to create cloudwatch alarm with:"))))))
  (delete-table [this table]
    (far/delete-table (db this)
                      (decorated-table table (prefix this))))
  (update-table [this table opts]
    (far/update-table (db this)
                      (decorated-table table (prefix this))
                      opts))
  (put-item [this table record opts]
    (let [record-spec (table-spec table)]
      (if-let [err (s/explain-data record-spec record)]
        (throw (java.lang.IllegalArgumentException.
                (str "Record was not a valid " record-spec "\n" (prn-str err))))
        (far/put-item (db this)
                      (decorated-table table (prefix this))
                      (convert-keywords-for-db record)
                      opts))))
  (get-item [this table where opts]
    (far/get-item (db this)
                  (decorated-table table (prefix this))
                  (convert-keywords-for-db where)
                  opts))
  (query [this table where opts]
    (far/query (db this)
               (decorated-table table (prefix this))
               where
               opts))
  (update-item [this table where opts]
    (far/update-item (db this)
                     (decorated-table table (prefix this))
                     where
                     opts))
  (delete-item [this table where opts]
    (far/delete-item (db this)
                     (decorated-table table (prefix this))
                     where
                     opts))
  (scan [this table]
    (far/scan (db this)
              (decorated-table table (prefix this))))

  component/Lifecycle
  (start [component]
    (log/info "Starting dynamodb component ...")
    (let [client {:endpoint (:endpoint component)
                  :profile profile}
          joplin-config {:migrators {:migrator "joplin/migrators/dynamodb"}                                        
                         :databases {:dynamodb (merge
                                                {:type :dynamo
                                                 :migration-table (str profile "-witan.httpapi.migrations")}
                                                client)}
                         :environments {:env [{:db :dynamodb :migrator :migrator}]}}]
      (log/info "About to migrate")
      (->> (migrate  joplin-config :env)
           (with-out-str)
           (clojure.string/split-lines)
           (run! #(log/info "> JOPLIN:" %))))
    (log/info "Migrated")
    (log/info "Started")
    component)
  (stop [component]
    (log/info "Stopping dynamodb component")
    component))

(defn new-session
  [opts profile]
  (->DynamoDB opts profile)  )
