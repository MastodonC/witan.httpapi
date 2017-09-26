(ns witan.httpapi.spec
  (:require [clojure.spec.alpha :as s]
            [spec-tools.spec :as spec]
            [spec-tools.core :as st]
            [schema.spec.leaf :as leaf]
            [schema.spec.core :as sspec]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [kixi.comms :as comms]
            [kixi.spec.conformers :as sc]
            [kixi.spec :refer [api-spec]]
            [kixi.datastore.metadatastore :as kdm]
            [kixi.datastore.metadatastore
             [geography :as kdm-geography]
             [license :as kdm-license]
             [time :as kdm-time]]
            [kixi.datastore.filestore :as kdf]
            [kixi.datastore.metadatastore.update :as kdmu]
            [com.gfredericks.schpec :refer [alias]]
            [kixi.user :as user]))

(alias 'kdmu-geography 'kixi.datastore.metadatastore.geography.update)
(alias 'kdmu-license 'kixi.datastore.metadatastore.license.update)
(alias 'kdmu-time 'kixi.datastore.metadatastore.time.update)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; This is so that context-level header-params
;; can use spec for swagger, but they need Schema internally.
;; This Schema extension simply wraps a call to spec/valid?
(extend-protocol schema.core/Schema
  clojure.lang.Keyword
  (spec [this]
    (leaf/leaf-spec
     (sspec/precondition
      this
      #(s/valid? this %)
      #(s/explain-data this %))))
  (explain [this]
    (str "clojure.spec: " this)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Specs

(s/def ::id (api-spec sc/uuid? "string"))

(s/def ::file-metadata
  (s/keys :req [::kdm/size-bytes
                ::kdm/file-type
                ::kdm/header
                ::kdm/name
                ::kdm/id
                ::kdm/type
                ::kdm/provenance]
          :opt [::kdm/description
                ::kdm/tags
                ::kdm-license/license
                ::kdm/author
                ::kdm/source
                ::kdm/maintainer
                ::kdm/source-created
                ::kdm/source-updated
                ::kdm-time/temporal-coverage
                ::kdm-geography/geography]))

(s/def ::file-metadata-post
  (s/keys :opt [::kdmu-time/temporal-coverage
                ::kdmu-geography/geography
                ::kdmu/source-created
                ::kdmu/source-updated
                ::kdmu-license/license
                ::kdmu/tags
                ::kdmu/author
                ::kdmu/source
                ::kdmu/maintainer
                ::kdmu/name
                ::kdmu/description]))

(s/def ::file-metadata-put
  (s/merge
   ::file-metadata-post
   (s/keys :req [::kdm/size-bytes
                 ::kdm/file-type
                 ::kdm/header
                 ::kdm/name])))

(s/def ::file-sharing
  (s/keys :req [::kdm/sharing]))

(s/def ::file-info
  (s/merge ::file-metadata
           ::file-sharing))

(s/def ::metadata-update
  (s/merge (s/keys :req [::kdm/id])
           ::file-metadata-post))

;; Files
(s/def ::total spec/int?)
(s/def ::count spec/int?)
(s/def ::index spec/int?)
(s/def ::items (s/coll-of (s/keys)))
(s/def ::paging (s/keys :req-un [::total ::count ::index]))
(s/def ::paged-items (s/keys :req-un [::items ::paging]))

;; Receipts
(s/def ::status #{"pending" "complete" "error"})
(s/def ::created-at (api-spec sc/timestamp? "string"))
(s/def ::last-updated-at sc/timestamp?)
(s/def ::uri spec/string?)
(s/def ::receipt
  (s/keys :req [::id
                :kixi.user/id
                ::status
                ::created-at
                ::last-updated-at]
          :opt [::uri]))

(s/def ::receipt-update
  (s/keys :req [::uri
                ::status
                ::last-updated-at]))

;; Upload Link
(s/def ::upload-link
  (s/keys :req [::id
                ::kdf/id
                ::created-at
                ::uri]))

(s/def ::upload-link-response
  (s/keys :req [::kdf/id
                ::uri]))

;; Errors
(s/def ::reason string?)
(s/def ::error
  (s/keys :req [::id
                ::kdf/id
                ::created-at
                ::reason]))
(s/def ::error-container
  (s/keys :req-un [::error]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Commands

(defmethod comms/command-payload
  [::kdf/create-upload-link "1.0.0"]
  [_]
  (s/keys))

(defmethod comms/command-payload
  [::kdf/create-file-metadata "1.0.0"]
  [_]
  ::file-info)

(defmethod comms/command-payload
  [::kdm/update "1.0.0"]
  [_]
  ::metadata-update)
