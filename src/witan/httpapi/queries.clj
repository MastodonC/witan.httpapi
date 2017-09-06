(ns witan.httpapi.queries
  (:require [witan.httpapi.components.requests :as requests]))

(defn encode-kw
  [kw]
  (str (namespace kw)
       "_"
       (name kw)))

(defn user-header
  [{:keys [kixi.user/id kixi.user/groups] :as u}]
  {"user-groups" (clojure.string/join "," groups)
   "user-id" id})

(defn get-files-by-user
  [requester user]
  (requests/GET requester
                :datastore
                "/metadata"
                {:query-params {:activity (mapv encode-kw [:kixi.datastore.metadatastore/meta-read])}
                 :headers (user-header user)}))

(defn get-file-info [requester user id]
  (requests/GET requester
                :datastore
                (str "/metadata/" id)
                {:headers (user-header user)}))

(defn get-file-metadata [requester user id]
  (let [[status cds-response] (get-file-info requester user id)]
    (if (= status 200)
      [status (dissoc cds-response :kixi.datastore.metadatastore/sharing)]
      [status cds-response])))

(defn get-file-sharing-info [requester user id]
  (let [[status cds-response] (get-file-info requester user id)]
    (if (= status 200)
      [status (select-keys cds-response [:kixi.datastore.metadatastore/sharing])]
      [status cds-response])))
