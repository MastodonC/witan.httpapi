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
            [kixi.user :as user]
            [kixi.group :as group]))

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

;; We can't just use the spec from kixi.spec
;; because it's a multimethod and swagger can't turn
;; that into a describable data structure
(s/def ::file-metadata-get
  (s/keys :req [::kdm/size-bytes
                ::kdm/file-type
                ::kdm/header
                ::kdm/name
                ::kdm/id
                ::kdm/type
                ::kdm/provenance]
          :opt [::kdm/description
                ::kdm/logo
                ::kdm/tags
                ::kdm-license/license
                ::kdm/author
                ::kdm/maintainer
                ::kdm/source
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
                ::kdmu/description
                ::kdmu/logo]))

(s/def ::file-metadata-put
  (s/keys :req [::kdm/size-bytes
                ::kdm/file-type
                ::kdm/header
                ::kdm/name]
          :opt [::kdm-time/temporal-coverage
                ::kdm-geography/geography
                ::kdm/source-created
                ::kdm/source-updated
                ::kdm-license/license
                ::kdm/tags
                ::kdm/author
                ::kdm/source
                ::kdm/maintainer
                ::kdm/name
                ::kdm/description
                ::kdm/logo]))

(s/def ::file-sharing
  (s/keys :req [::kdm/sharing]))

(s/def ::file-info
  (s/merge ::file-metadata-get
           ::file-sharing))

(s/def ::metadata-update
  (s/merge (s/keys :req [::kdm/id])
           ::file-metadata-post))

(s/def ::sharing-operation
  #{"add" "remove"})

(s/def ::sharing-command
  (s/keys :req [::kdm/id
                ::kdm/activity
                ::kdm/sharing-update
                ::group/id]))

;; Files
(s/def ::total spec/int?)
(s/def ::count spec/int?)
(s/def ::index spec/int?)
(s/def ::items (s/coll-of (s/keys)))
(s/def ::paging (s/keys :req-un [::total ::count ::index]))
(s/def ::paged-items (s/keys :req-un [::items ::paging]))

(s/def ::files (s/coll-of ::file-info))
(s/def ::paged-metadata-items (s/keys :req-un [::files ::paging]))

;; Receipts
(s/def ::status #{"pending" "complete" "error"})
(s/def ::created-at (api-spec sc/timestamp? "string"))
(s/def ::last-updated-at sc/timestamp?)
(s/def ::uri spec/string?)
(s/def ::receipt-id (api-spec sc/uuid? "string"))
(s/def ::receipt-id-container (s/keys :req-un [::receipt-id]))
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

(defmethod comms/command-payload
  [::kdm/sharing-change "1.0.0"]
  [_]
  ::sharing-command)
