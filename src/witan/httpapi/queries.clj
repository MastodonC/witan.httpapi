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

(defn datapack?
  [{:keys [kixi.datastore.metadatastore/type
           kixi.datastore.metadatastore/bundle-type]}]
  (= type "bundle")
  (= bundle-type "datapack"))

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

(defn get-query-by-user
  [query result-key requester user {:keys [count index]}]
  (let [[s r] (requests/POST requester
                             :search
                             "/metadata"
                             {:form-params (merge {:query query}
                                                  (when count
                                                    {:size count})
                                                  (when index
                                                    {:from index}))
                              :content-type :json
                              :headers (user-header user)})
        {:keys [items]} r]
    (log/debug "Search /metadata returned:" s r)
    [s (-> r
           (assoc result-key items) ;;maintain pre-search api
           (dissoc :items))]))

(def get-files-by-user
  (partial get-query-by-user
           {:kixi.datastore.metadatastore.query/type {:equals "stored"}}
           :files))

(defn- get-metadata-info [requester user id]
  (requests/GET requester
                :datastore
                (str "/metadata/" id)
                {:headers (user-header user)}))

(defn get-file-metadata [requester user id]
  (let [[status cds-response] (get-metadata-info requester user id)]
    (if (= status OK)
      (if (file? cds-response)
        [status (dissoc cds-response :kixi.datastore.metadatastore/sharing)]
        [NOT_FOUND nil])
      [status cds-response])))

(defn get-datapack-metadata [requester user id]
  (let [[status cds-response] (get-metadata-info requester user id)]
    (if (= status OK)
      (if (datapack? cds-response)
        [status (dissoc cds-response :kixi.datastore.metadatastore/sharing)]
        [NOT_FOUND nil])
      [status cds-response])))

(defn get-file-sharing-info [requester user id]
  (let [[status cds-response] (get-metadata-info requester user id)]
    (if (= status OK)
      (if (file? cds-response)
        [status (select-keys cds-response [:kixi.datastore.metadatastore/sharing])]
        [NOT_FOUND nil])
      [status cds-response])))

(def get-datapacks-by-user
  (partial get-query-by-user
           {:kixi.datastore.metadatastore.query/type {:equals "bundle"}
            ;;:kixi.datastore.metadatastore.query/bundle-type {:equals "datapack"} ;; TODO we need to add this once we have more bundle types
            }
           :datapacks))
