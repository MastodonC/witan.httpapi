(ns witan.httpapi.components.activities
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre            :as log]
            [clojure.spec.alpha :as s]
            [com.gfredericks.schpec :as sh]
            [kixi.comms :as comms]
            [kixi.spec.conformers :as sc]
            [witan.httpapi.queries :refer [user-header]]
            [witan.httpapi.components.database :as database]
            [witan.httpapi.components.requests :as requests]
            [witan.httpapi.spec :as spec]
            [witan.httpapi.response-codes :refer :all]
            [kixi.datastore.metadatastore :as ms]))

(sh/alias 'command 'kixi.command)
(sh/alias 'kdcs 'kixi.datastore.communication-specs)

(def receipts-table "receipts")
(def upload-links-table "upload-links")
(def download-links-table "download-links")
(def file-errors-table "file-errors")

(defmethod database/table-spec
  [:put receipts-table] [& _] ::spec/receipt)

(defmethod database/table-spec
  [:update receipts-table] [& _] ::spec/receipt-update)

(defmethod database/table-spec
  [:put upload-links-table] [& _] ::spec/upload-link)

(defmethod database/table-spec
  [:put download-links-table] [& _] ::spec/download-link)

(defmethod database/table-spec
  [:put file-errors-table] [& _] ::spec/error)

(defn send-valid-command!*
  "Eventually deprecate this function for comms/send-valid-command!"
  [comms command opts]
  (let [cmd-with-id (assoc command ::command/id
                           (or (::command/id command)
                               (comms/uuid))
                           :kixi.message/type :command
                           ::command/created-at (comms/timestamp))
        {:keys [kixi.command/type
                kixi.command/version
                kixi.command/id
                kixi/user]} cmd-with-id
        {:keys [partition-key]} opts]
    (when-not (s/valid? :kixi/command cmd-with-id)
      (throw (ex-info "Invalid command" (s/explain-data :kixi/command cmd-with-id))))
    (when-not (s/valid? ::command/options opts)
      (throw (ex-info "Invalid command options" (s/explain-data ::command/options opts))))
    (log/debug "Sending command" type version)
    (comms/send-command! comms type version user (dissoc cmd-with-id
                                                         ::command/id
                                                         ::command/type
                                                         ::command/version
                                                         ::command/created-at
                                                         :kixi.message/type
                                                         :kixi/user)
                         {:kixi.comms.command/partition-key partition-key
                          :kixi.comms.command/id id})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Receipts

(defn create-receipt! [database user id]
  (let [spec-receipt {::spec/id id
                      :kixi.user/id (:kixi.user/id user)
                      ::spec/status "pending"
                      ::spec/created-at (comms/timestamp)
                      ::spec/last-updated-at (comms/timestamp)}]
    (database/put-item database receipts-table spec-receipt nil)    ))

(defn- retrieve-receipt
  [db id]
  (database/get-item db receipts-table {::spec/id id} nil))

(defn get-receipt-response
  [act user id]
  (let [receipt (retrieve-receipt (:database act) id)]
    (cond
      (nil? receipt)                                      [NOT_FOUND nil nil]
      (not= (:kixi.user/id receipt) (:kixi.user/id user)) [UNAUTHORISED nil nil]
      (= "pending" (::spec/status receipt))               [ACCEPTED nil nil]
      (and (= "complete" (::spec/status receipt))
           (nil? (::spec/uri receipt)))                   [OK nil nil]
      (= "complete" (::spec/status receipt))              [SEE_OTHER nil {"Location" (::spec/uri receipt)}])))

(defn complete-receipt!
  [db id uri]
  (database/update-item
   db
   receipts-table
   {::spec/id id}
   (merge {::spec/status "complete"}
          (when uri {::spec/uri uri}))
   nil)
  nil)

(defn return-receipt
  [id]
  [ACCEPTED
   {:receipt-id id}
   {"Location" (str "/api/receipts/" id)}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Errors

(defn create-error!
  [database id file-id reason]
  (let [spec-error {::spec/id id
                    :kixi.datastore.filestore/id file-id
                    ::spec/created-at (comms/timestamp)
                    ::spec/reason reason}]
    (database/put-item database file-errors-table spec-error nil)
    (str "/api/files/" file-id "/errors/" id)))

(defn- retreive-error
  [db id]
  (database/get-item db file-errors-table {::spec/id id} nil))

(defn get-error-response
  [act user error-id file-id]
  (let [row (retreive-error (:database act) error-id)]
    (if (and row (= (:kixi.datastore.filestore/id row) file-id))
      [OK {:error row}]
      [NOT_FOUND nil])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Uploads

(defn create-upload-link! [database user id file-id upload-link]
  (let [spec-upload-link {::spec/id id
                          :kixi.user/id (:kixi.user/id user)
                          :kixi.datastore.filestore/id file-id
                          ::spec/created-at (comms/timestamp)
                          ::spec/uri upload-link}]
    (database/put-item database upload-links-table spec-upload-link nil)
    (str "/api/files/" file-id "/upload/" id)))

(defn create-file-upload!
  [{:keys [comms database]} user]
  (let [id (comms/uuid)]
    (create-receipt! database user id)
    (send-valid-command!* comms {::command/id id
                                 ::command/type :kixi.datastore.filestore/create-upload-link
                                 ::command/version "1.0.0"
                                 :kixi/user user}
                          {:partition-key id})
    (return-receipt id)))

(defn- retreive-upload-link
  [db id]
  (database/get-item db upload-links-table {::spec/id id} nil))

(defn get-upload-link-response
  [act user id upload-id]
  (let [row (retreive-upload-link (:database act) upload-id)]
    (cond
      (nil? row)                                      [NOT_FOUND nil nil]
      (not= (:kixi.datastore.filestore/id row) id)    [NOT_FOUND nil nil]
      (not= (:kixi.user/id row) (:kixi.user/id user)) [UNAUTHORISED nil nil]
      :else [OK (select-keys row [::spec/uri :kixi.datastore.filestore/id]) nil])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Downloads

(defn create-download-link! [database user id file-id download-link]
  (let [spec-download-link {::spec/id id
                            :kixi.user/id (:kixi.user/id user)
                            :kixi.datastore.filestore/id file-id
                            ::spec/created-at (comms/timestamp)
                            ::spec/uri download-link}]
    (database/put-item database download-links-table spec-download-link nil)
    (str "/api/files/" file-id "/link/" id)))

(defn create-file-download!
  [{:keys [comms database]} user requester file-id]
  (let [id (comms/uuid)]
    (create-receipt! database user id)
    (let [{:keys [status headers]}
          (requests/GET* requester
                         :datastore
                         (str "/file/" file-id "/link")
                         {:headers (user-header user)
                          :accept "application/octet-stream"
                          :content-type :none
                          :as :none
                          :follow-redirects false
                          :redirect-strategy :none})]
      (complete-receipt!
       database id
       (if (= REDIRECT status)
         (create-download-link! database user id file-id (get headers "location"))
         (create-error! database id file-id status))))
    (return-receipt id)))

(defn- retreive-download-link
  [db id]
  (database/get-item db download-links-table {::spec/id id} nil))

(defn get-download-link-response
  [act user id download-id]
  (let [row (retreive-download-link (:database act) download-id)]
    (cond
      (nil? row)                                      [NOT_FOUND nil nil]
      (not= (:kixi.datastore.filestore/id row) id)    [NOT_FOUND nil nil]
      (not= (:kixi.user/id row) (:kixi.user/id user)) [UNAUTHORISED nil nil]
      :else [OK (select-keys row [::spec/uri :kixi.datastore.filestore/id]) nil])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Metadata

(defn create-metadata!
  [{:keys [comms database]} user payload file-id]
  (let [id (comms/uuid)
        payload' (assoc payload
                        :kixi.datastore.metadatastore/id file-id
                        :kixi.datastore.metadatastore/type "stored"
                        :kixi.datastore.metadatastore/provenance {:kixi.datastore.metadatastore/source "upload"
                                                                  :kixi.datastore.metadatastore/created (comms/timestamp)
                                                                  :kixi.user/id (:kixi.user/id user)}
                        :kixi.datastore.metadatastore/sharing {:kixi.datastore.metadatastore/meta-read #{(:kixi.user/self-group user)}
                                                               :kixi.datastore.metadatastore/meta-update #{(:kixi.user/self-group user)}
                                                               :kixi.datastore.metadatastore/file-read #{(:kixi.user/self-group user)}})]

    (create-receipt! database user id)
    (send-valid-command!* comms (merge {::command/id id
                                        ::command/type :kixi.datastore.filestore/create-file-metadata
                                        ::command/version "1.0.0"
                                        :kixi/user user}
                                       payload')
                          {:partition-key file-id})
    (return-receipt id)))

(defn conform-metadata-updates
  "This fn attempts to heal any differences between specs across the applications"
  [m]
  (log/debug "Conforming metadata updates:" m)
  (let [m' (reduce-kv (fn [a k v]
                        (assoc a k (s/unform k (s/conform k v)))) {} m)]
    (log/debug "Conformed metadata updates:" m')
    m'))

(defn update-metadata!
  [{:keys [comms database]} user metadata-updates file-id]
  (if (empty? metadata-updates)
    [BAD_REQUEST {:error "Update can't be empty"} nil]
    (let [id (comms/uuid)
          update-payload (-> metadata-updates
                             conform-metadata-updates
                             (assoc :kixi.datastore.metadatastore/id file-id))]
      (create-receipt! database user id)
      (send-valid-command!* comms (merge {::command/id id
                                          ::command/type :kixi.datastore.metadatastore/update
                                          ::command/version "1.0.0"
                                          :kixi/user user}
                                         update-payload)
                            {:partition-key file-id})
      (return-receipt id))))

(def sharing-ops
  {"add" ::ms/sharing-conj
   "remove" ::ms/sharing-disj})

(defn update-sharing!
  [{:keys [comms database]} user file-id op activity group-id]
  (let [id (comms/uuid)]
    (create-receipt! database user id)
    (send-valid-command!* comms (merge {::command/id id
                                        ::command/type :kixi.datastore.metadatastore/sharing-change
                                        ::command/version "1.0.0"
                                        :kixi/user user}
                                       {:kixi.datastore.metadatastore/id file-id
                                        :kixi.datastore.metadatastore/activity (s/conform sc/ns-keyword? activity)
                                        :kixi.group/id group-id
                                        :kixi.datastore.metadatastore/sharing-update (get sharing-ops op)})
                          {:partition-key file-id})
    (return-receipt id)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Events

(defn get-metadatastore-id
  [payload]
  (or (get-in payload [::ms/id])
      (get-in payload [:original ::ms/id])
      (get-in payload [:original ::ms/payload :kixi.comms.command/payload ::ms/id])))

(defmulti on-event
  (fn [_ {:keys [kixi.comms.event/key
                 kixi.comms.event/version]}] [key version]))

(defmethod on-event
  [:kixi.datastore.filestore/upload-link-created "1.0.0"]
  [db {:keys [kixi.comms.event/payload] :as event}]
  (let [command-id (:kixi.comms.command/id event)]
    (when-let [receipt (retrieve-receipt db command-id)]
      (let [{:keys [kixi.datastore.filestore/upload-link
                    kixi.datastore.filestore/id]} payload]
        (complete-receipt!
         db command-id
         (create-upload-link! db
                              (select-keys payload [:kixi.user/id])
                              command-id
                              id
                              upload-link))))))

(defmethod on-event
  [:kixi.datastore.file-metadata/rejected "1.0.0"]
  [db {:keys [kixi.comms.event/payload] :as event}]
  (let [command-id (:kixi.comms.command/id event)]
    (when-let [receipt (retrieve-receipt db command-id)]
      (let [file-id (get-in payload [:kixi.datastore.metadatastore/file-metadata :kixi.datastore.metadatastore/id])]
        (complete-receipt!
         db
         command-id
         (create-error! db command-id file-id (-> payload :reason name)))))))

(defmethod on-event
  [:kixi.datastore.file/created "1.0.0"]
  [db {:keys [kixi.comms.event/payload] :as event}]
  (let [command-id (:kixi.comms.command/id event)]
    (when (retrieve-receipt db command-id)
      (let [file-id (:kixi.datastore.metadatastore/id payload)]
        (complete-receipt! db command-id (str "/api/files/" file-id "/metadata"))))))

(defmethod on-event
  [:kixi.datastore.file-metadata/updated "1.0.0"]
  [db {:keys [kixi.comms.event/payload] :as event}]
  (cond
    (or (= (::kdcs/file-metadata-update-type payload) ::kdcs/file-metadata-update)
        (= (::kdcs/file-metadata-update-type payload) ::kdcs/file-metadata-sharing-updated))
    (let [command-id (:kixi.comms.command/id event)]
      (when (retrieve-receipt db command-id)
        (complete-receipt! db command-id nil)))))

(defmethod on-event
  [:kixi.datastore.metadatastore/update-rejected "1.0.0"]
  [db {:keys [kixi.comms.event/payload] :as event}]
  (let [command-id (:kixi.comms.command/id event)]
    (when-let [receipt (retrieve-receipt db command-id)]
      (if-let [file-id (get-metadatastore-id payload)]
        (complete-receipt!
         db
         command-id
         (create-error! db command-id file-id (-> payload :reason name)))
        (log/error "Could't find a metadatastore ID in payload:" payload)))))

(defmethod on-event
  [:kixi.datastore.metadatastore/sharing-change-rejected "1.0.0"]
  [db {:keys [kixi.comms.event/payload] :as event}]
  (let [command-id (:kixi.comms.command/id event)]
    (when-let [receipt (retrieve-receipt db command-id)]
      (if-let [file-id (get-metadatastore-id payload)]
        (complete-receipt!
         db
         command-id
         (create-error! db command-id file-id (-> payload :reason name)))
        (log/error "Could't find a metadatastore ID in payload:" payload)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;

(def event-handlers
  {:kixi.datastore.filestore/upload-link-created         ["1.0.0" :witan-httpapi-activity-upload-file]
   :kixi.datastore.file-metadata/rejected                ["1.0.0" :witan-httpapi-activity-file-metadata-rejected]
   :kixi.datastore.file/created                          ["1.0.0" :witan-httpapi-activity-file-created]
   :kixi.datastore.file-metadata/updated                 ["1.0.0" :witan-httpapi-activity-metadata-updated]
   :kixi.datastore.metadatastore/update-rejected         ["1.0.0" :witan-httpapi-activity-metadata-update-rejected]
   :kixi.datastore.metadatastore/sharing-change-rejected ["1.0.0" :witan-httpapi-activity-sharing-change-rejected]})

(defn event-data->handler
  [{:keys [comms database]} [type [version group]]]
  (comms/attach-event-handler!
   comms group type version (partial on-event database)))

(defrecord Activities []
  component/Lifecycle
  (start [{:keys [comms database] :as component}]
    (log/info "Starting Activities" (type comms))
    (let [ehs (mapv (partial event-data->handler component) event-handlers)]
      (assoc component :ehs ehs)))

  (stop [{:keys [comms] :as component}]
    (log/info "Stopping Activities")
    (when-let [ehs (:ehs component)]
      (run! (partial comms/detach-handler! comms) ehs))
    (dissoc component :ehs)))
