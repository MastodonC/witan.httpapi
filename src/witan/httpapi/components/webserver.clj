(ns witan.httpapi.components.webserver
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [aleph.http :as http]))

(defn start-aleph-server
  [handler port]
  (http/start-server handler {:port port}))

(defrecord WebServer [handler config]
  component/Lifecycle
  (start [this]
    (log/info "Starting Web Server on port" (:port config))
    (let [s (start-aleph-server handler (:port config))]
      (log/debug "Web Server started")
      (assoc this :server s)))
  (stop [this]
    (log/info "Stopping Web Server")
    (when-let [server (:server this)]
      (.close server))
    (dissoc this :server)))
