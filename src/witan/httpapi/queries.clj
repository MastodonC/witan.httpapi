(ns witan.httpapi.queries
  (:require [witan.httpapi.components.requests :as requests]
            [witan.httpapi.response-codes :refer :all]))

(defn encode-kw
  [kw]
  (str (namespace kw)
       "_"
       (name kw)))

(defn user-header
  [{:keys [kixi.user/id kixi.user/groups] :as u}]
  {"user-groups" (clojure.string/join "," groups)
   "user-id" id})

(defn file?
  [{:keys [kixi.datastore.metadatastore/type]}]
  (= type "stored"))

(defn get-files-by-user
  [requester user {:keys [count index]}]
  (let [[s r] (requests/GET requester
                            :datastore
                            "/metadata"
                            {:query-params (merge {:activity (mapv encode-kw [:kixi.datastore.metadatastore/meta-read])}
                                                  (when count {:count count})
                                                  (when index {:index index}))
                             :headers (user-header user)})
        {:keys [items]} r
        files (filter file? items)] ;; `count` could now be wrong
    [s (-> r
           (assoc :files files)
           (dissoc :items))]))

(defn- get-file-info [requester user id]
  (requests/GET requester
                :datastore
                (str "/metadata/" id)
                {:headers (user-header user)}))

(defn get-file-metadata [requester user id]
  (let [[status cds-response] (get-file-info requester user id)]
    (if (= status OK)
      [status (dissoc cds-response :kixi.datastore.metadatastore/sharing)]
      [status cds-response])))

(defn get-file-sharing-info [requester user id]
  (let [[status cds-response] (get-file-info requester user id)]
    (if (= status OK)
      [status (select-keys cds-response [:kixi.datastore.metadatastore/sharing])]
      [status cds-response])))
