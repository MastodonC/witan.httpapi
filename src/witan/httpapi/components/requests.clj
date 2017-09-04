(ns witan.httpapi.components.requests
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre            :as log]
            [aleph.http :as http]))

(defprotocol Request
  (GET [this service route opts])
  (POST [this service route opts]))

(defn build-route
  [{:keys [host port]} route]
  (str "http://" host ":" port route))

(defrecord HttpRequester [directory]
  Request
  (GET [this service route opts]
    (let [full-route (build-route (get directory service) route)]
      (log/debug "GET request sent to" service full-route)
      @(http/get full-route (assoc opts :content-type :transit+json))))
  (POST [this service route opts]
    (let [full-route (build-route (get directory service) route)]
      (log/debug "POST request sent to" service full-route)
      @(http/post full-route (assoc opts :content-type :transit+json))))

  component/Lifecycle
  (start [component]
    (log/info "Starting HTTP Requester")
    component)

  (stop [component]
    (log/info "Stopping HTTP Requester")
    component))
