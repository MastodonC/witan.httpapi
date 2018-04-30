(ns witan.httpapi.queries
  (:require [witan.httpapi.components.requests :as requests]
            [witan.httpapi.response-codes :refer :all]
            [taoensso.timbre :as log]
            [kixi.group :as group]))

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

(defn get-groups [requester user {:keys [count index]}]
  (let [[s r] (requests/GET requester
                            :heimdall
                            "/groups/search"
                            (merge {:headers (user-header user)}
                                   (when (or count index)
                                     {:query-params
                                      (merge {}
                                             (when count {:count count})
                                             (when index {:index index}))})))]
    [s (when (= OK s)
         (-> r
             (assoc :groups (map #(select-keys % [::group/id
                                                  ::group/name
                                                  ::group/type]) (:items r)))
             (dissoc :items)))]))

(defn get-files-by-user
  [requester user {:keys [count index]}]
  (let [[s r] (requests/POST requester
                             :search
                             "/metadata"
                             {:form-params (merge {:query {:kixi.datastore.metadatastore.query/type {:equals "stored"}}}
                                                  (when count
                                                    {:size count})
                                                  (when index
                                                    {:from index}))
                              :content-type :json
                              :headers (user-header user)})
        {:keys [items]} r]
    (log/debug "Search /metadata returned:" s r)
    [s (-> r
           (assoc :files items) ;;maintain pre-search api
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
