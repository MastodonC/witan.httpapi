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
            [kixi.user :as user]))

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

;; Permissions
;;(s/def ::kdm/meta-read (s/coll-of sc/uuid?))
;;(s/def ::kdm/meta-update (s/coll-of sc/uuid?))
;;(s/def ::kdm/file-read (s/coll-of sc/uuid?))

;; Metadata
;;(s/def ::kdm/size-bytes (api-spec varint? "integer"))
;;(s/def ::kdm/file-type spec/string?)
;;(s/def ::kdm/header spec/boolean?)
;;(s/def ::kdm/name spec/string?)
;;(s/def ::kdm/id (api-spec uuid? "string"))
;;(s/def ::kdm/type spec/string?)
;;(s/def ::kdm/description spec/string?)
;;(s/def ::kdm/source spec/string?)
;;(s/def ::kdm/created (api-spec timestamp? "string"))

;; (s/def ::kdm/provenance (s/keys :req [::kdm/source
;;                                       ::kdm/created
;;                                       :kixi.user/id]))
;; (s/def ::kdm/sharing (s/keys :opt [::kdm/meta-read
;;                                    ::kdm/meta-update
;;                                    ::kdm/file-read]))
;; (s/def ::kdf/id (api-spec uuid? "string"))

;; (s/def ::kdm-geography/level string?)
;; (s/def ::kdm-geography/type #{"smallest"})

;; (defmulti geography ::type)

;; (defmethod geography "smallest"
;;   [_]
;;   (s/keys :req [::type
;;                 ::level]))

;; (s/def ::kdm-geography/geography
;;   (s/multi-spec geography ::type))

;; ;;;;

;; (s/def ::kdm-license/usage string?)
;; (s/def ::kdm-license/type string?)

;; (s/def ::kdm-license/license
;;   (s/keys :req [::type ::usage]))

;; ;;;;

;; (s/def ::kdm-time/from (s/nilable date))
;; (s/def ::kdm-time/to (s/nilable date))

;; (s/def ::kdm-time/temporal-coverage
;;   (s/keys :opt [::from ::to]))

;;;;

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

(s/def ::file-metadata-put
  (s/keys :req [:kixi.datastore.metadatastore/size-bytes
                :kixi.datastore.metadatastore/file-type
                :kixi.datastore.metadatastore/header
                :kixi.datastore.metadatastore/name]
          :opt [:kixi.datastore.metadatastore/description]))

(s/def ::file-metadata-post
  (s/keys :opt [::kdm-time/temporal-coverage
                ::kdm-geography/geography
                ::kdm/source-created
                ::kdm/source-updated
                ::kdm-license/license
                ::kdm/tags
                ::kdm/author
                ::kdm/source
                ::kdm/maintainer
                ::kdm/name
                ::kdm/description]))

(s/def ::file-sharing
  (s/keys :req [::kdm/sharing]))

(s/def ::file-info
  (s/merge ::file-metadata
           ::file-sharing))

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
