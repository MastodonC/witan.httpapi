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
