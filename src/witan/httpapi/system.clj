(ns witan.httpapi.system
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [aero.core :refer [read-config]]
            [kixi.log :as kixi-log]
            #_[kixi.comms :as comms]
            [taoensso.timbre :as timbre]
            [signal.handler :refer [with-handler]]
            ;;
            [witan.httpapi.api :as api]
            [witan.httpapi.components.webserver :as webserver]))

(defn new-system [profile]
  (timbre/debug "Profile" profile)
  (let [config (read-config (clojure.java.io/resource "config.edn") {:profile profile})
        log-config (assoc (:log config)
                          :timestamp-opts kixi-log/default-timestamp-opts)]
    ;; logging config
    (timbre/set-config!
     (assoc log-config
            :appenders (if (or (= profile :staging)
                               (= profile :prod))
                         {:direct-json (kixi-log/timbre-appender-logstash)}
                         {:println (timbre/println-appender)})))

    #_(comms/set-verbose-logging! (:verbose-logging? config))

    (component/system-map
     :webserver (webserver/->WebServer api/handler (:webserver config)))))

(defn -main [& [arg]]
  (let [profile (or (keyword arg) :staging)
        sys (atom nil)]

    ;; https://stuartsierra.com/2015/05/27/clojure-uncaught-exceptions
    (Thread/setDefaultUncaughtExceptionHandler
     (reify Thread$UncaughtExceptionHandler
       (uncaughtException [_ thread ex]
         (timbre/error ex "Unhandled exception:" (.getMessage ex)))))

    (reset! sys (component/start (new-system profile)))
    (with-handler :term
      (timbre/info "SIGTERM was caught: shutting down...")
      (component/stop @sys)
      (reset! sys nil))

    (while @sys
      (Thread/sleep 500))))
