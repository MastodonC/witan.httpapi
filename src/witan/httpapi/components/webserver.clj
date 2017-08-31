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
    (log/info "Starting WebServer on port" (:port config))
    (assoc this :server (start-aleph-server handler (:port config))))
  (stop [this]
    (log/info "Stopping WebServer")
    (when-let [server (:server this)]
      (.close server))
    (dissoc this :server)))
