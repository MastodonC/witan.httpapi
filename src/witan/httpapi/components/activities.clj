(ns witan.httpapi.components.activities
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre            :as log]
            [clojure.spec.alpha :as s]
            [com.gfredericks.schpec :as sh]
            [kixi.comms :as comms]
            [witan.httpapi.components.database :as database]
            [witan.httpapi.spec :as spec]))

(sh/alias 'command 'kixi.command)

(def receipts-table "receipts")
(def upload-links-table "upload-links")

(defmethod database/table-spec
  receipts-table [_] ::spec/receipt)

(defmethod database/table-spec
  upload-links-table [_] ::spec/upload-link)

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
    (comms/send-command! comms type version user (dissoc cmd-with-id
                                                         ::command/id
                                                         ::command/type
                                                         ::command/version
                                                         ::command/created-at
                                                         :kixi.message/type
                                                         :kixi/user)
                         {:kixi.comms.command/partition-key partition-key
                          :kixi.comms.command/id id})))

(defn save-receipt! [database user receipt]
  (let [spec-receipt {::spec/id receipt
                      :kixi.user/id (:kixi.user/id user)
                      ::spec/status "pending"
                      ::spec/created-at (comms/timestamp)
                      ::spec/last-updated-at (comms/timestamp)}]
    (database/put-item database receipts-table spec-receipt nil)))

(defn save-upload-link! [database user id file-id upload-link]
  (let [spec-upload-link {::spec/id id
                          :kixi.user/id (:kixi.user/id user)
                          :kixi.datastore.filestore/id file-id
                          ::spec/created-at (comms/timestamp)
                          ::spec/uri upload-link}]
    (database/put-item database upload-links-table spec-upload-link nil)))

(defn new-receipt
  []
  (let [receipt (comms/uuid)
        location (str "/receipts/" receipt)]
    {:receipt receipt
     :location location}))

(defn check-receipt
  [act user receipt]
  (let [redirect "http://www.google.com"]
    [303
     nil
     {"Location" redirect}]))

(defn retreive-receipt
  [db receipt]
  (database/get-item db receipts-table {::spec/id receipt} nil))

(defn complete-receipt!
  [db id uri])

(defn create-file-upload!
  [{:keys [comms database]} user]
  (let [{:keys [receipt location]} (new-receipt)]
    (save-receipt! database user receipt)
    (send-valid-command!* comms {::command/id receipt
                                 ::command/type :kixi.datastore.filestore/create-upload-link
                                 ::command/version "1.0.0"
                                 :kixi/user user}
                          {:partition-key receipt})
    [202
     {:receipt receipt}
     {"Location" location}]))

(defmulti on-event
  (fn [_ {:keys [kixi.comms.event/key
               kixi.comms.event/version]}] [key version]))

(defmethod on-event
  [:kixi.datastore.filestore/upload-link-created "1.0.0"]
  [db {:keys [kixi.comms.event/payload] :as event}]
  (let [command-id (:kixi.comms.command/id event)]
    (when-let [receipt (retreive-receipt db command-id)]
      (let [{:keys [kixi.datastore.filestore/upload-link
                    kixi.datastore.filestore/id]} payload]
        (save-upload-link! db
                           (select-keys payload [:kixi.user/id])
                           command-id
                           id
                           upload-link)
        (complete-receipt! db command-id (str "/files/upload/" command-id)))))
  nil)

;;

(defrecord Activities []
  component/Lifecycle
  (start [{:keys [comms database] :as component}]
    (log/info "Starting Activities" (type comms))
    (let [ehs [(comms/attach-event-handler!
                comms
                :witan-httpapi-activity-upload-file
                :kixi.datastore.filestore/upload-link-created
                "1.0.0"
                (partial on-event database))]]
      (assoc component :ehs ehs)))

  (stop [{:keys [comms] :as component}]
    (log/info "Stopping Activities")
    (when-let [ehs (:ehs component)]
      (run! (partial comms/detach-handler! comms) ehs))
    (dissoc component :ehs)))
