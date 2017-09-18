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

(defn db->keywordns [kw] (-> kw
                             name
                             (clojure.string/replace-first db-keyword-delimiter "/")
                             keyword))

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

(def table-spec-types
  #{:put :update})

(defmulti table-spec
  (fn [type table-name]
    [type table-name]))

(defn invalid?
  [record-spec record]
  (s/explain-data record-spec record))

(defn invalid-submap?
  [record-spec record]
  (when-let [err (invalid? record-spec record)]
    (let [spec-keys (->> record-spec
                         s/get-spec
                         s/form
                         rest
                         (partition 2)
                         (map last)
                         (reduce concat)
                         set)
          key-intersection (-> record
                               keys
                               set
                               (clojure.set/intersection spec-keys))]
      (if (not-empty key-intersection)
        (let [errors (reduce (fn [a key-spec]
                               (if-let [err2 (invalid? key-spec (get record key-spec))]
                                 (assoc a key-spec err2)
                                 a)) {} key-intersection)]
          (not-empty errors))
        {:error (str record " is not a valid submap of " record-spec)}))))

(defn record->dynamo-updates
  [record]
  (letfn [(update-update-expr
            [a c]
            (update a :update-expr #(str % (if (:update-expr a) ", " "SET ") (str "#"c " = :" c))))
          ;;
          (update-attr-names-expr
            [a c k]
            (update a :expr-attr-names assoc (str "#"c) k))
          ;;
          (update-attr-vals-expr
            [a k v]
            (update a :expr-attr-vals assoc (str ":"k) v))
          ;;
          (pop-available-name
            [a]
            (update a :available-names rest))
          ;;
          (build-exp [a k v]
            (let [c (-> a :available-names first)
                  k' (keywordns->db k)]
              (-> a
                  (update-update-expr c)
                  (update-attr-names-expr c k')
                  (update-attr-vals-expr c v)
                  (pop-available-name))))]
    (->
     (reduce-kv
      build-exp
      {:available-names (map (comp str char) (range 97 123))
       :update-expr nil
       :expr-attr-names nil
       :expr-attr-vals nil} record)
     (dissoc :available-names))))

(defprotocol Database
  (create-table [this table index opts])
  (delete-table [this table])
  (update-table [this table opts])
  (put-item [this table record opts])
  (get-item [this table where opts])
  (query [this table where opts])
  (update-item [this table where record opts])
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
    (log/debug "Deleting table" table)
    (far/delete-table (db this)
                      (decorated-table table (prefix this))))
  (update-table [this table opts]
    (log/debug "Updating table" table)
    (far/update-table (db this)
                      (decorated-table table (prefix this))
                      opts))
  (put-item [this table record opts]
    (log/debug "Putting item to table" table)
    (let [record-spec (table-spec :put table)]
      (if-let [err (invalid? record-spec record)]
        (throw (java.lang.IllegalArgumentException.
                (str "Record was not a valid " record-spec "\n" (prn-str err))))
        (far/put-item (db this)
                      (decorated-table table (prefix this))
                      (convert-keywords-for-db record)
                      opts))))
  (get-item [this table where opts]
    (log/debug "Getting item from table" table)
    (-> (db this)
        (far/get-item
         (decorated-table table (prefix this))
         (convert-keywords-for-db where)
         opts)
        convert-keywords-from-db
        not-empty))
  (query [this table where opts]
    (far/query (db this)
               (decorated-table table (prefix this))
               where
               opts))
  (update-item [this table where record opts]
    (log/debug "Updating item in table" table "where" where)
    (let [record-spec (table-spec :update table)]
      (if-let [err (invalid-submap? record-spec record)]
        (throw (java.lang.IllegalArgumentException.
                (str "Record was not a valid " record-spec " or valid submap\n" (prn-str err))))
        (let [update-map (record->dynamo-updates record)]
          (far/update-item (db this)
                           (decorated-table table (prefix this))
                           (convert-keywords-for-db where)
                           (merge opts update-map))))))
  (delete-item [this table where opts]
    (log/debug "Deleting item from table" table "where" where)
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
    (let [client {:endpoint (-> db-conf :db :endpoint)
                  :profile profile}
          joplin-config {:migrators {:migrator "joplin/migrators/dynamodb"}
                         :databases {:dynamodb (merge
                                                {:type :dynamo
                                                 :migration-table (decorated-table "migrations" (prefix component))}
                                                client)}
                         :environments {:env [{:db :dynamodb :migrator :migrator}]}}]
      (log/info "About to migrate" client)
      (migrate  joplin-config :env)
      #_(->>
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
  (->DynamoDB opts profile))
