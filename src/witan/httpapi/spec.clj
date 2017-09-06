(ns witan.httpapi.spec
  (:require [clojure.spec.alpha :as s]
            [spec-tools.spec :as spec]
            [spec-tools.core :as st]
            [schema.spec.leaf :as leaf]
            [schema.spec.core :as sspec]
            [clj-time.core :as t]
            [clj-time.format :as tf]))

;; This macro allows us to give type hints to swagger
;; when using complex specs
(defmacro api-spec
  [symb typename]
  `(st/create-spec {:spec ~symb
                    :form '~symb
                    :json-schema/type ~typename}))

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
;; Conformers

(defn -regex?
  [rs]
  (fn [x]
    (if (and (string? x) (re-find rs x))
      x
      ::s/invalid)))

(def uuid?
  (-regex? #"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"))

(def uuid (s/conformer uuid? identity))

(def format :basic-date-time)
(def date-format :basic-date)

(def formatter
  (tf/formatters format))

(def date-formatter
  (tf/formatters date-format))

(def time-parser
  (partial tf/parse time/formatter))

(def date-parser
  (partial tf/parse time/date-formatter))

(defn timestamp?
  [x]
  (if (instance? org.joda.time.DateTime x)
    x
    (try
      (if (string? x)
        (time-parser x)
        :clojure.spec/invalid)
      (catch IllegalArgumentException e
        :clojure.spec/invalid))))

(defn email?
  [s]
  (when-not (clojure.string/blank? s)
    (re-find #"^[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,63}" s)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Specs

(s/def ::x spec/int?)
(s/def ::y spec/int?)
(s/def ::total spec/int?)
(s/def ::total-map (s/keys :req-un [::total]))
(s/def ::result (s/keys))
(s/def ::xs (s/coll-of spec/int?))
(s/def ::id (api-spec uuid "string"))

(s/def ::error spec/string?)

;; Auth
(s/def ::password spec/string?)
(s/def ::username (api-spec email? "string"))
(s/def ::auth-token spec/string?)
(s/def ::refresh-token spec/string?)
(s/def ::token-pair (s/keys :req-un [::auth-token ::refresh-token]))
(s/def ::token-pair-container (s/keys :req-un [::token-pair]))

;; Metadata
(s/def :kixi.datastore.metadatastore/size-bytes spec/int?)
(s/def :kixi.datastore.metadatastore/file-type spec/string?)
(s/def :kixi.datastore.metadatastore/header spec/boolean?)
(s/def :kixi.datastore.metadatastore/name spec/string?)
(s/def :kixi.datastore.metadatastore/id (api-spec uuid "string"))
(s/def :kixi.datastore.metadatastore/type spec/string?)
(s/def :kixi.datastore.metadatastore/description spec/string?)
(s/def :kixi.datastore.metadatastore/source spec/string?)
(s/def :kixi.user/id (api-spec uuid "string"))
(s/def :kixi.datastore.metadatastore/created timestamp?)

;; Files
(s/def ::total spec/int?)
(s/def ::count spec/int?)
(s/def ::index spec/int?)
(s/def ::items (s/coll-of (s/keys)))
(s/def ::paging (s/keys :req-un [::total ::count ::index]))
(s/def ::paged-items (s/keys :req-un [::items ::paging]))
