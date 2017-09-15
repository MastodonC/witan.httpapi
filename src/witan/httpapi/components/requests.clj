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
          ;;:as :transit+json ;; don't let aleph try to decode, it breaks with empty bodies
          :throw-exceptions false}
         opts))

(defn parse-body
  [^java.io.ByteArrayInputStream body]
  (when (and body (pos? (.available body)))
    (let [x (parse-transit body :json {})]
      #_(log/debug "DECODED BODY" x)
      x)))

(defrecord HttpRequester [directory]
  Request
  (GET [this service route opts]
    (let [full-route (build-route (get directory service) route)]
      (log/debug "GET request sent to" service full-route)
      (let [{:keys [body status]} @(http/get full-route (add-default-opts opts))]
        [status (parse-body body)])))
  (POST [this service route opts]
    (let [full-route (build-route (get directory service) route)]
      (log/debug "POST request sent to" service full-route)
      (let [{:keys [body status]} @(http/post full-route (add-default-opts opts))]
        [status (parse-body body)])))

  component/Lifecycle
  (start [component]
    (log/info "Starting HTTP Requester")
    component)

  (stop [component]
    (log/info "Stopping HTTP Requester")
    component))
