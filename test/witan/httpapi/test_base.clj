(ns witan.httpapi.test-base
  (:require [aleph.http :as http]
            [clojure.test :as t]
            [clojure.java.io :as io]
            [clojure data 
             [test :refer :all]]
            [witan.httpapi.system :as sys]
            [com.stuartsierra.component :as component]
            [environ.core :refer [env]]))

(def profile (keyword (env :system-profile "test")))

(def wait-tries (Integer/parseInt (env :wait-tries "200")))
(def wait-per-try (Integer/parseInt (env :wait-per-try "500")))
(def wait-emit-msg (Integer/parseInt (env :wait-emit-msg "5000")))
(def every-count-tries-emit (int (/ wait-emit-msg wait-per-try)))

(defn alias
  "Like clojure.core/alias, but can alias to non-existing namespaces"
  [alias namespace-sym]
  (try (clojure.core/alias alias namespace-sym)
       (catch Exception _
         (create-ns namespace-sym)
         (clojure.core/alias alias namespace-sym))))

(alias 'ms 'kixi.datastore.metadatastore)
(alias 'ss 'kixi.datastore.schemastore)

(defn file-size
  [^String file-name]
  (.length (io/file file-name)))

(defn uuid
  []
  (str (java.util.UUID/randomUUID)))

(defn random-user
  []
  {:kixi.user/id (uuid)
   :kixi.user/groups [(uuid)]})

(defn std-system
  [sys-fn]
  (sys-fn))

(defn local-url
  [method]
  (str "http://localhost:8015" method))

(defn receipt-resp->receipt-url
  [receipt-resp]
  (->> (get-in receipt-resp [:body :receipt])
       (str "/api/receipts/")
       local-url))

(defn wait-for-receipt
  ([auth receipt-resp]
   (wait-for-receipt auth (receipt-resp->receipt-url receipt-resp) wait-tries 1 nil))
  ([auth receipt-url tries cnt last-result]
   (if (<= cnt tries)
     (let [rr @(http/get receipt-url
                         {:throw-exceptions false
                          :as :json
                          :headers {:authorization {:witan.httpapi.spec/auth-token (:auth-token auth)}}})]
       (if (not= 200 (:status rr))
         (do
           (when (zero? (mod cnt every-count-tries-emit))
             (println "Waited" cnt "times for" receipt-url ". Getting:" rr))
           (Thread/sleep wait-per-try)
           (recur auth receipt-url tries (inc cnt) rr))
         rr))
     last-result)))

(defmacro is-submap
  [expected actual & [msg]]
  `(try
     (let [act# ~actual
           exp# ~expected
           [only-in-ex# only-in-ac# shared#] (clojure.data/diff exp# act#)]
       (if only-in-ex#
         (clojure.test/do-report {:type :fail
                                  :message (or ~msg "Missing expected elements.")
                                  :expected only-in-ex# :actual act#})
         (clojure.test/do-report {:type :pass
                                  :message "Matched"
                                  :expected exp# :actual act#})))
     (catch Throwable t#
       (clojure.test/do-report {:type :error :message "Exception diffing"
                                :expected nil :actual t#}))))

(defmacro is-match
  [expected actual & [msg]]
  `(try
     (let [act# ~actual
           exp# ~expected
           [only-in-ac# only-in-ex# shared#] (clojure.data/diff act# exp#)]
       (cond
         only-in-ex#
         (clojure.test/do-report {:type :fail
                                  :message (or ~msg "Missing expected elements.")
                                  :expected only-in-ex# :actual act#})
         only-in-ac#
         (clojure.test/do-report {:type :fail
                                  :message (or ~msg "Has extra elements.")
                                  :expected {} :actual only-in-ac#})
         :else (clojure.test/do-report {:type :pass
                                        :message "Matched"
                                        :expected exp# :actual act#})))
     (catch Throwable t#
       (clojure.test/do-report {:type :error :message "Exception diffing"
                                :expected ~expected :actual t#}))))

(defmacro has-status
  [status resp]
  `(is-submap {:status ~status}
              ~resp))

(defmacro when-status
  [status resp rest]
  `(let [rs# (:status ~resp)]
     (has-status ~status ~resp)
     (when (= ~status
              rs#)
       ~@rest)))

(defmacro when-accepted
  [resp & rest]
  `(when-status 202 ~resp ~rest))

(defmacro when-created
  [resp & rest]
  `(when-status 201 ~resp ~rest))

(defmacro when-success
  [resp & rest]
  `(when-status 200 ~resp ~rest))

(defmacro when-event-key
  [event k & rest]
  `(let [k-val# (:kixi.comms.event/key ~event)]
     (is (= ~k
            k-val#))
     (when (= ~k
            k-val#)
        ~@rest)))

(defmulti upload-file
  (fn [^String target file-name]
    (subs target 0
          (.indexOf target
                    ":"))))

(defn strip-protocol
  [^String path]
  (subs path
        (+ 3 (.indexOf path
                       ":"))))

(defmethod upload-file "file"
  [target file-name]
  (let [target-file (io/file (str "." (strip-protocol target)))]
    (prn "TARGET: " target-file)
    (io/make-parents target-file)
    (io/copy (io/file file-name)
             (doto target-file
               (.createNewFile)))))

(defmethod upload-file "https"
  [target file-name]
  @(http/put target
             {:body (io/file file-name)}))

(defn get-upload-link
  [auth]
  (let [resp @(http/post (local-url "/api/files/upload")
                         {:throw-exceptions false
                          :as :json
                          :headers {:authorization {:witan.httpapi.spec/auth-token (:auth-token auth)}}})]
    (when-accepted resp
      (let [receipt-resp (wait-for-receipt auth resp)]
        (is (= 200 (:status receipt-resp))
            "upload link receipt")        
        (let [body (:body receipt-resp)
              uri (:witan.httpapi.spec/uri body)
              file-id (:kixi.datastore.filestore/id body)]
          (is uri)
          (is file-id)
          [uri file-id])))))

(defn put-metadata
  [auth metadata]
  (let [resp @(http/put (local-url (str "/api/files/" (::ms/id metadata) "/metadata"))
                        {:throw-exceptions false
                         :content-type :json
                         :as :json                          
                         :headers {:authorization {:witan.httpapi.spec/auth-token (:auth-token auth)}}
                         :form-params (select-keys metadata [::ms/size-bytes ::ms/file-type ::ms/description ::ms/name ::ms/header])})]
    (when-accepted resp
      (let [receipt-resp (wait-for-receipt auth resp)]
        (is (= 200 (:status receipt-resp))
            "metadata receipt")        
        (is-submap metadata
                   (:body receipt-resp))
        (:body receipt-resp)))))

(defn create-metadata
  ([uid file-name]
   (create-metadata uid file-name nil))
  ([uid file-name schema-id]
   (create-metadata
    {:file-name file-name
     :type "stored"
     :provenance {::ms/source "upload"
                  :kixi.user/id uid}
     :size-bytes (file-size file-name)
     :schema-id schema-id
     :header true}))
  ([{:keys [^String file-name type schema-id user-groups sharing header size-bytes provenance]}]
   (merge {}
          (when type
            {::ms/type type})
          (when file-name
            {::ms/name (subs file-name
                             (inc (clojure.string/last-index-of file-name "/"))
                             (clojure.string/last-index-of file-name "."))})
          (when file-name
            {::ms/description file-name})
          (when file-name
            {::ms/file-type file-name})
          (when-not (nil? header)
            {::ms/header header})
          (when schema-id
            {::ss/id schema-id})
          (when provenance
            {::ms/provenance provenance})
          (when size-bytes
            {::ms/size-bytes size-bytes}))))

(defn send-file-and-metadata
  [auth file-name metadata]
  (let [[link id] (get-upload-link auth)]
    (is link)
    (is id)
    (let [md-with-id (assoc metadata ::ms/id id)]
      (when link
        (upload-file link
                     file-name)
        (put-metadata auth
                      md-with-id))
      md-with-id)))
