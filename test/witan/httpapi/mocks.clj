(ns witan.httpapi.mocks
  (:require [witan.httpapi.components.auth :as auth]
            [witan.httpapi.components.webserver :as webserver]
            [witan.httpapi.components.requests :as requests]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]))

(defn uuid
  []
  (str (java.util.UUID/randomUUID)))

(defrecord MockAuthenticator []
  auth/Authenticate
  (authenticate [this time auth-token]
    {:kixi.user/id (uuid)
     :kixi.user/groups [(uuid)]})
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
