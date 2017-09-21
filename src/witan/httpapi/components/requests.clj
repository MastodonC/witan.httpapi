(ns witan.httpapi.components.requests
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre            :as log]
            [aleph.http :as http]
            [aleph.http.client-middleware :refer [parse-transit]]))

(defprotocol Request
  (GET [this service route opts])
  (POST [this service route opts]))

(defn build-route
  [{:keys [host port]} route]
  (str "http://" host ":" port route))

(defn add-default-opts
  [opts]
  (merge {:content-type :transit+json
          :accept :transit+json
          :as :transit+json
          :throw-exceptions false}
         opts))

(defrecord HttpRequester [directory]
  Request
  (GET [this service route opts]
    (let [full-route (build-route (get directory service) route)]
      (log/debug "GET request sent to" service full-route)
      (let [{:keys [body status]} @(http/get full-route (add-default-opts opts))]
        [status body])))
  (POST [this service route opts]
    (let [full-route (build-route (get directory service) route)]
      (log/debug "POST request sent to" service full-route)
      (let [{:keys [body status]} @(http/post full-route (add-default-opts opts))]
        [status body])))

  component/Lifecycle
  (start [component]
    (log/info "Starting HTTP Requester")
    component)

  (stop [component]
    (log/info "Stopping HTTP Requester")
    component))
