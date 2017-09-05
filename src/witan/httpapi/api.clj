(ns witan.httpapi.api
  (:require [compojure.api.sweet :refer [context GET POST resource api]]
            [ring.util.http-response :refer [ok unauthorized]]
            [clj-time.core              :as t]
            [taoensso.timbre :as log]
            ;;
            [witan.httpapi.spec :as s]
            ;;
            [witan.httpapi.components.auth :as auth]))

(defn auth
  [req]
  (get-in req [:components :auth]))

(defn fail
  ([status]
   {:status status})
  ([status body]
   {:status status
    :body body}))

(defn success
  [status body]
  {:status status
   :body body})

(def healthcheck-routes
  (context "/" []
    (GET "/healthcheck" []
      (str "hello"))))

(def auth-routes
  (context "/api" []
    :tags ["api"]
    :coercion :spec

    (POST "/login" req
      :summary "Retrieve an authorisation token for further API calls"
      :body-params [username :- ::s/username
                    password :- ::s/password]
      :return ::s/token-pair-container
      (let [[status body] (auth/login (auth req) username password)]
        (if (= status 201)
          (success status body)
          (fail status body))))

    (POST "/refresh" req
      :summary "Refreshes an authorisation token"
      :body-params [token-pair :- ::s/token-pair]
      :return ::s/token-pair-container
      (let [[status body] (auth/refresh (auth req) token-pair)]
        (if (= status 201)
          (success status body)
          (fail status body))))))

(defn authentication-middleware
  [handler]
  (fn [req]
    (let [auth-header (get-in req [:headers "authorization"])]
      (if (auth/authenticate (auth req) (t/now) auth-header)
        (handler req)
        (unauthorized)))))

(def api-routes
  (context "/api" []
    :tags ["api"]
    :coercion :spec
    :header-params [authorization :- ::s/auth-token]
    :middleware [authentication-middleware]

    (context "/files" []

      (GET "/" req
        :summary "Return a list of files the user has access to"
        :return ::s/result
        (ok "hello"))

      (POST "/upload" req
        :summary "Returns an upload address for a new file"
        :return ::s/result
        (ok "hello"))

      (GET "/:id" req
        :summary "Return details of a specific file"
        :return ::s/result
        (ok "hello"))

      (GET "/:id/metadata" req
        :summary "Return metadata for a specific file"
        :return ::s/result
        (ok "hello"))

      (POST "/:id/metadata" req
        :summary "Update metadata for a specific file"
        :return ::s/result
        (ok "hello"))

      (GET "/:id/error" req
        :summary "Return errors for a specific file"
        :return ::s/result
        (ok "hello"))

      (GET "/:id/sharing" req
        :summary "Return sharing data for a specific file"
        :return ::s/result
        (ok "hello"))

      (GET "/:id/link" req
        :summary "Return a download token for a specific file"
        :return ::s/result
        (ok "hello"))

      (GET "/:id/link/:link-id" req
        :summary "Return a download address for a specific file and token"
        :return ::s/result
        (ok "hello")))))

(def handler
  (api
   {:swagger
    {:ui "/"
     :spec "/swagger.json"
     :options {:ui {:jsonEditor true}}
     :data {:info {:title "Witan API (Datastore) "}
            :tags [{:name "api", :description "API routes for Witan"}]}}}
   healthcheck-routes
   auth-routes
   api-routes))
