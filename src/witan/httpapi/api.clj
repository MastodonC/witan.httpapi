(ns witan.httpapi.api
  (:require [compojure.api.sweet :refer [context GET POST resource api]]
            [ring.util.http-response :refer [ok unauthorized]]
            [clj-time.core              :as t]
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
      :summary "Retrieve an authorisation token for further API calls."
      :body-params [username :- ::s/username
                    password :- ::s/password]
      :return ::s/token-pair-container
      (let [[status body] (auth/login (auth req) username password)]
        (if (= status 201)
          (success status body)
          (fail status body))))

    (POST "/refresh" req
      :summary "Refreshes an authorisation token."
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

    (GET "/plus" []
      :summary "plus with clojure.spec"
      :query-params [x :- ::s/x, {y :- ::s/y 0}]
      :return ::s/total-map
      (ok {:total (+ x y)}))

    (GET "/plus2" []
      :summary "plus2 with clojure.spec"
      :query-params [x :- ::s/xs]
      :return ::s/total-map
      (ok {:total (apply + x)}))

    (POST "/uuid" []
      :summary "my funky uuid"
      :body-params [id :- ::s/id]
      :return ::s/result
      (ok {:result id}))

    (POST "/file" []
      :summary "my funky file"
      :body-params [file :- ::s/file]
      :return ::s/result
      (ok {:result file}))

    #_(context "/data-plus" []
        (resource
         {:post
          {:summary "data-driven plus with clojure.spec"
           :parameters {:body-params (s/keys :req-un [::s/x ::s/y])}
           :responses {200 {:schema ::s/total-map}}
           :handler (fn [{{:keys [x y]} :body-params}]
                      (ok {:total (+ x y)}))}}))))

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
