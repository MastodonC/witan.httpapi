(ns witan.httpapi.components.activities
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre            :as log]
            [clojure.spec.alpha :as s]
            [com.gfredericks.schpec :as sh]
            [kixi.comms :as comms]
            [witan.httpapi.components.database :as database]))

(sh/alias 'command 'kixi.command)

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

(defn new-receipt
  []
  (let [receipt (comms/uuid)
        location (str "/receipts/" receipt)]
    {:receipt receipt
     :location location}))

(defn create-file-upload!
  [{:keys [comms database]} user]
  (let [{:keys [receipt location]} (new-receipt)]
    (send-valid-command!* comms {::command/id receipt
                                 ::command/type :kixi.datastore.filestore/create-upload-link
                                 ::command/version "1.0.0"
                                 :kixi/user user}
                          {:partition-key receipt})
    [202
     {:receipt receipt}
     {"Location" location}]))

(defn check-receipt
  [act user receipt]
  (let [redirect "http://www.google.com"]
    [303
     nil
     {"Location" redirect}]))

(defmulti on-event
  (fn [{:keys [kixi.comms.event/key
               kixi.comms.event/version]}] [key version]))

(defmethod on-event
  [:kixi.datastore.filestore/upload-link-created "1.0.0"]
  [{:keys [kixi.comms.command/id]}]
  (log/debug "Received :kixi.datastore.filestore/create-upload-link event with command id:" id)
  nil)

;;

(defrecord Activities []
  component/Lifecycle
  (start [{:keys [comms] :as component}]
    (log/info "Starting Activities" (type comms))
    (let [ehs [(comms/attach-event-handler!
                comms
                :witan-httpapi-activity-upload-file
                :kixi.datastore.filestore/upload-link-created
                "1.0.0"
                on-event)]]
      (assoc component :ehs ehs)))

  (stop [{:keys [comms] :as component}]
    (log/info "Stopping Activities")
    (when-let [ehs (:ehs component)]
      (run! (partial comms/detach-handler! comms) ehs))
    (dissoc component :ehs)))
