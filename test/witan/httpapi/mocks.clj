(ns witan.httpapi.mocks
  (:require [witan.httpapi.components.auth :as auth]
            [witan.httpapi.components.webserver :as webserver]
            [witan.httpapi.components.requests :as requests]
            [witan.httpapi.test-base :refer :all]
            [com.stuartsierra.component :as component]
            [kixi.comms :as comms]
            [taoensso.timbre :as log]))

(defrecord MockAuthenticator [user-id groups]
  auth/Authenticate
  (authenticate [this time auth-token]
    (let [self-group-id (uuid)]
      {:kixi.user/id (or user-id (uuid))
       :kixi.user/self-group (or (and (not-empty groups) (first groups)) self-group-id)
       :kixi.user/groups (or groups [self-group-id])}))
  (login [this username password]
    (log/debug "Received login request for" username password)
    [201 {:token-pair {:auth-token "012"
                       :refresh-token "345"}}])
  (refresh [this token-pair]
    (log/debug "Received refresh request")
    [201 {:token-pair {:auth-token "678"
                       :refresh-token "901"}}])

  component/Lifecycle
  (start [component]
    (log/info "Starting Mock Authenticator")
    component)

  (stop [component]
    (log/info "Stopping Mock Authenticator")
    component))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord MockRequester []
  requests/Request
  (GET [this service route opts]
    (log/debug "Received GET request" service route opts)
    [:get service route])
  (POST [this service route opts]
    (log/debug "Received POST request" service route opts)
    [:post service route])

  component/Lifecycle
  (start [component]
    (log/info "Starting Mock Requester")
    component)

  (stop [component]
    (log/info "Stopping Mock Requester")
    component))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(alias 'ke 'kixi.comms.event)
(alias 'kc 'kixi.comms.command)
(alias 'mdu 'kixi.datastore.metadatastore.update)
(alias 'kdm 'kixi.datastore.metadatastore)
(alias 'kdfm 'kixi.datastore.file-metadata)
(alias 'cs 'kixi.datastore.communication-specs)


(defrecord MockDatastore [comms]
  component/Lifecycle
  (start [component]
    (log/info "Starting Mock Datastore")
    (let [chs [(comms/attach-command-handler! comms :mock-datastore-upload-link
                                              :kixi.datastore.filestore/create-upload-link
                                              "1.0.0"
                                              (fn [c]
                                                (let [cmd-id (:kixi.comms.command/id c)
                                                      file-id (uuid)]
                                                  {:kixi.comms.event/key :kixi.datastore.filestore/upload-link-created
                                                   :kixi.comms.event/version "1.0.0"
                                                   :kixi.comms.command/id cmd-id
                                                   :kixi.comms.event/payload
                                                   {:kixi.datastore.filestore/upload-link (str "file:///tmp/" file-id)
                                                    :kixi.datastore.filestore/id file-id
                                                    :kixi.user/id (get-in c [:kixi.comms.command/user :kixi.user/id])}})))
               (comms/attach-command-handler! comms :mock-datastore-file-metadata-create
                                              :kixi.datastore.filestore/create-file-metadata
                                              "1.0.0"
                                              (fn [c]
                                                (let [cmd-id (:kixi.comms.command/id c)
                                                      metadata (get-in c [:kixi.comms.command/payload])
                                                      file-id (get-in c [:kixi.comms.command/payload
                                                                         :kixi.datastore.metadatastore/id])
                                                      file-name (get-in c [:kixi.comms.command/payload
                                                                           :kixi.datastore.metadatastore/name])]
                                                  (if (= "fail" file-name)
                                                    {:kixi.comms.event/key :kixi.datastore.file-metadata/rejected
                                                     :kixi.comms.event/version "1.0.0"
                                                     :kixi.comms.command/id cmd-id
                                                     :kixi.comms.event/payload
                                                     {:reason :mock-fail
                                                      :kixi.datastore.metadatastore/file-metadata
                                                      {:kixi.datastore.metadatastore/id file-id}}}
                                                    ;;
                                                    [{:kixi.comms.event/key :kixi.datastore.file/created
                                                      :kixi.comms.event/version "1.0.0"
                                                      :kixi.comms.command/id cmd-id
                                                      :kixi.comms.event/payload metadata}
                                                     {::ke/key ::kdfm/updated
                                                      ::ke/version "1.0.0"
                                                      ::ke/partition-key file-id
                                                      ::ke/payload {::kdm/file-metadata metadata
                                                                    ::cs/file-metadata-update-type
                                                                    ::cs/file-metadata-created}}]))))]]
      (assoc component :chs chs)))

  (stop [component]
    (log/info "Stopping Mock Datastore")
    (let [chs (:chs component)]
      (run! (partial comms/detach-handler! comms) chs))
    (dissoc component :chs)))
