(ns witan.httpapi.api
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer [ok unauthorized not-found]]
            [clj-time.core :as t]
            [taoensso.timbre :as log]
            [witan.httpapi.spec :as s]
            [witan.httpapi.queries :as query]
            [kixi.user :as user]
            [kixi.group :as group]
            [kixi.datastore.metadatastore :as kdm]
            [witan.httpapi.components.auth :as auth]
            [witan.httpapi.components.activities :as activities]
            [witan.httpapi.response-codes :refer :all]))

(defn auth
  [req]
  (get-in req [:components :auth]))

(defn requester
  [req]
  (get-in req [:components :requester]))

(defn activities
  [req]
  (get-in req [:components :activities]))

(defn user
  [req]
  (get-in req [:user]))

(defn fail
  ([status]
   (fail status nil))
  ([status body]
   (log/debug "Returning failure response" status)
   {:status status
    :body   body}))

(defn success
  ([body]
   (success OK body))
  ([status body]
   (success status body nil))
  ([status body headers]
   (log/debug "Returning successful response" status)
   {:status  status
    :body    body
    :headers headers}))

(defn success?
  [status]
  (and (>= status OK)
       (< status BAD_REQUEST)))

(def healthcheck-routes
  (context "/" []
    (GET "/healthcheck" []
      (str "hello"))))

(def not-found-routes
  (let [not-found-resp (not-found "Not Found")]
    (context "/" []
      (ANY "/*" [] not-found-resp))))

(def auth-routes
  (context "/api" []
    :tags ["api"]
    :coercion :spec

    (POST "/login" req
      :summary "Retrieve an authorisation token for further API calls"
      :body-params [username :- ::user/username
                    password :- ::user/password]
      :return ::user/token-pair-container
      (let [[status body] (auth/login (auth req) username password)]
        (if (= status CREATED)
          (success status body)
          (fail status body))))

    (POST "/refresh" req
      :summary "Refreshes an authorisation token"
      :body-params [token-pair :- ::user/token-pair]
      :return ::user/token-pair-container
      (let [[status body] (auth/refresh (auth req) token-pair)]
        (if (= status CREATED)
          (success status body)
          (fail status body))))))

(defn authentication-middleware
  [handler]
  (fn [req]
    (let [auth-header (get-in req [:headers "authorization"])]
      (if-let [user (auth/authenticate (auth req) (t/now) auth-header)]
        (handler (assoc req :user user))
        (unauthorized)))))

(def api-routes
  (context "/api" []
    :tags ["api"]
    :coercion :spec
    :header-params [authorization :- ::user/auth-token]
    :middleware [authentication-middleware]

    (GET "/receipts/:receipt" req
      :summary "Redirects to any results associated with the specified receipt"
      :path-params [receipt :- ::s/id]
      (let [[s r headers] (activities/get-receipt-response (activities req) (user req) receipt)]
        (if (success? s)
          (success s nil headers)
          (fail s r))))

    (GET "/groups" req
      :summary "Return a list of groups."
      :return ::s/paged-group-items
      (let [[s r] (query/get-groups (requester req) (user req))]
        (if (success? s)
          (success s r)
          (fail s))))

    (context "/files" []

      (GET "/" req
        :summary "Return a list of files the user has access to"
        :query-params [{count :- ::s/count nil}
                       {index :- ::s/index nil}]
        :return ::s/paged-metadata-items
        (let [[s r] (query/get-files-by-user (requester req) (user req) {:count count :index index})]
          (if (success? s)
            (success s r)
            (fail s))))

      (POST "/upload" req
        :summary "Creates an upload address for a new file"
        :return ::s/receipt-id-container
        (let [[s r headers] (activities/create-file-upload!
                              (activities req)
                              (user req))]
          (if (success? s)
            (success ACCEPTED r headers)
            (fail s))))

      (context "/:id" []

        (GET "/upload/:upload-id" req
          :summary "Return details of an upload request"
          :path-params [id :- ::s/id
                        upload-id :- ::s/id]
          :return ::s/upload-link-response
          (let [[s r headers] (activities/get-upload-link-response
                                (activities req)
                                (user req)
                                id
                                upload-id)]
            (if (success? s)
              (success s r headers)
              (fail s))))

        (GET "/metadata" req
          :summary "Return metadata for a specific file"
          :path-params [id :- ::s/id]
          :return ::s/file-metadata-get
          (let [[s r] (query/get-file-metadata (requester req) (:user req) id)]
            (if (success? s)
              (success s r)
              (fail s))))

        (PUT "/metadata" req
          :summary "Create new metadata for a specific file"
          :path-params [id :- ::s/id]
          :body [metadata ::s/file-metadata-put]
          :return ::s/receipt-id-container
          (let [[s r headers] (activities/create-metadata!
                                (activities req)
                                (user req)
                                metadata
                                id)]
            (if (success? s)
              (success ACCEPTED r headers)
              (fail s r))))

        (POST "/metadata" req
          :summary "Update metadata for a specific file"
          :path-params [id :- ::s/id]
          :body [metadata-updates ::s/file-metadata-post]
          :return ::s/receipt-id-container
          (let [[s r headers] (activities/update-metadata!
                                (activities req)
                                (user req)
                                metadata-updates
                                id)]
            (if (success? s)
              (success ACCEPTED r headers)
              (fail s))))

        (GET "/errors/:error-id" req
          :summary "Return specific error for a specific file"
          :path-params [id :- ::s/id
                        error-id :- ::s/id]
          :return ::s/error-container
          (let [[s r] (activities/get-error-response
                        (activities req)
                        (user req)
                        error-id
                        id)]
            (if (success? s)
              (success s r)
              (fail s))))

        (GET "/sharing" req
          :summary "Return sharing data for a specific file"
          :path-params [id :- ::s/id]
          :return ::s/file-sharing
          (let [[s r] (query/get-file-sharing-info (requester req) (:user req) id)]
            (if (success? s)
              (success s r)
              (fail s))))

        (POST "/sharing" req
          :summary "Add a group to the specified file with a particular permission"
          :path-params [id :- ::s/id]
          :body-params [activity :- ::kdm/activity
                        group-id :- ::group/id
                        operation :- ::s/sharing-operation]
          :return ::s/receipt-id-container
          (let [[s r headers] (activities/update-sharing!
                                (activities req)
                                (user req)
                                id
                                operation
                                activity
                                group-id)]
            (if (success? s)
              (success ACCEPTED r headers)
              (fail s))))

        (POST "/link" req
          :summary "Creates a download token for a specific file"
          :path-params [id :- ::s/id]
          :return ::s/receipt-id-container
          (let [[s r headers] (activities/create-file-download!
                                (activities req)
                                (user req)
                                (requester req)
                                id)]
            (if (success? s)
              (success ACCEPTED r headers)
              (fail s))))

        (GET "/link/:link-id" req
          :summary "Returns a specific download token for a specific file"
          :path-params [id :- ::s/id
                        link-id :- ::s/id]
          :return ::s/download-link-response
          (let [[s r headers] (activities/get-download-link-response
                                (activities req)
                                (user req)
                                id
                                link-id)]
            (if (success? s)
              (success s r headers)
              (fail s))))))))

(def handler
  (api
    {:swagger
     {:ui      "/"
      :spec    "/swagger.json"
      :options {:ui {:jsonEditor true}}
      :data    {:info {:title "Witan API (Datastore) "}
                :tags [{:name "api", :description "API routes for Witan"}]}}}
    healthcheck-routes
    auth-routes
    api-routes
    not-found-routes))
