(ns witan.httpapi.components.webserver
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [aleph.http :as http]))

(defn wrap-components
  "Assoc given components to the request."
  [handler components]
  (fn [req]
    (handler (assoc req :components components))))

(defn start-aleph-server
  [handler port components]
  (http/start-server (-> handler
                         (wrap-components components))
                     {:port port}))

(defrecord WebServer [handler config]
  component/Lifecycle
  (start [this]
    (log/info "Starting Web Server on port" (:port config) (keys this))
    (let [s (start-aleph-server handler (:port config) this)]
      (log/debug "Web Server started")
      (assoc this :server s)))
  (stop [this]
    (log/info "Stopping Web Server")
    (when-let [server (:server this)]
      (.close server))
    (dissoc this :server)))
