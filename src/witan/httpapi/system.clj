(ns witan.httpapi.system
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [kixi.log :as kixi-log]
            [kixi.comms :as comms]
            [kixi.comms.components.kinesis :as kinesis]
            [taoensso.timbre :as timbre]
            [signal.handler :refer [with-handler]]
            ;;
            [witan.httpapi.config :as config]
            [witan.httpapi.api :as api]
            [witan.httpapi.components.auth :as auth]
            [witan.httpapi.components.webserver :as webserver]
            [witan.httpapi.components.requests :as requests]
            [witan.httpapi.components.activities :as activities]
            [witan.httpapi.components.database :as database]))

(defn new-requester
  [config]
  (requests/->HttpRequester (:directory config)))

(defn new-activities
  []
  (activities/->Activities))

(defn new-authenticator
  [config]
  (auth/map->PubKeyAuthenticator (:auth config)))

(defn new-database
  [config profile]
  (database/->DynamoDB (:dynamodb config) profile))

(defn new-webserver
  [config]
  (webserver/->WebServer api/handler (:webserver config)))

(defn new-comms
  [config]
  (kinesis/map->Kinesis (get-in config [:comms :kinesis])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn new-system [profile]
  (timbre/debug "Profile" profile)
  (config/save-profile! profile)
  (let [config (config/read-config)
        log-config (assoc (:log config)
                          :timestamp-opts kixi-log/default-timestamp-opts)]
    ;; logging config
    (timbre/set-config!
     (assoc log-config
            :appenders (if (or (= profile :staging)
                               (= profile :prod))
                         {:direct-json (kixi-log/timbre-appender-logstash)}
                         {:println (timbre/println-appender)})))

    (comms/set-verbose-logging! (:verbose-logging? config))

    (component/system-map
     :comms (new-comms config)
     :requester (new-requester config)
     :activities (component/using
                  (new-activities)
                  [:comms :database])
     :auth (component/using
            (new-authenticator config)
            [:requester])
     :database (new-database config profile)
     :webserver (component/using
                 (new-webserver config)
                 [:auth :requester :activities]))))

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
