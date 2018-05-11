(ns witan.httpapi.test-base
  (:require [aleph.http :as http]
            [clojure.test :as t]
            [clojure.java.io :as io]
            [clojure data
             [test :refer :all]]
            [witan.httpapi.system :as sys]
            [witan.httpapi.api :as api]
            [com.stuartsierra.component :as component]
            [clojure.spec.alpha :as spec]
            [environ.core :refer [env]]))

(def profile (keyword (env :system-profile "test")))

(def wait-tries (Integer/parseInt (env :wait-tries "120")))
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

(defn url
  [method]
  (if (= profile :staging-jenkins)
    (str "https://staging-http-api.witanforcities.com" method)
    (str "http://localhost:8015" method)))

(defn receipt-resp->receipt-url
  [receipt-resp]
  (if (map? receipt-resp)
    (->> (get-in receipt-resp [:body :receipt-id])
         (str "/api/receipts/")
         url)
    (throw (Exception. "Not a map!"))))

(defn wait-for-pred
  ([p]
   (wait-for-pred p wait-tries))
  ([p tries]
   (wait-for-pred p tries wait-per-try))
  ([p tries ms]
   (loop [try tries]
     (when (and (pos? try))
       (let [result (p)]
         (if (not result)
           (do
             (Thread/sleep ms)
             (recur (dec try)))
           result))))))

(defn wait-for-receipt
  ([auth receipt-resp]
   (wait-for-receipt auth (receipt-resp->receipt-url receipt-resp) wait-tries 1 nil))
  ([auth receipt-url tries cnt last-result]
   (if (<= cnt tries)
     (let [rr @(http/get receipt-url
                         {:throw-exceptions false
                          :as :json
                          :headers {:authorization (:auth-token auth)}})]
       (if (and (not= 500 (:status rr))
                (not= 200 (:status rr)))
         (do
           (when (zero? (mod cnt every-count-tries-emit))
             (println "Waited" cnt "times for" receipt-url ". Getting:" rr))
           (Thread/sleep wait-per-try)
           (recur auth receipt-url tries (inc cnt) rr))
         rr))
     last-result)))

(defmacro is-spec
  [spec r]
  `(do
     (println "Checking spec validity " ~spec " V: " ~r)
     (is (spec/valid? ~spec ~r)
         (str (spec/explain-data ~spec ~r)))))

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
    (io/make-parents target-file)
    (io/copy (io/file file-name)
             (doto target-file
               (.createNewFile)))))

(defmethod upload-file "https"
  [target file-name]
  @(http/put target {:body (io/file file-name)
                     :headers {"Content-Length" (file-size file-name)}}))

(defn get-upload-link
  [auth]
  (let [resp @(http/post (url "/api/files/upload")
                         {:throw-exceptions false
                          :as :json
                          :headers {:authorization (:auth-token auth)}})]
    (when-accepted resp
      (let [receipt-resp (wait-for-receipt auth resp)]
        (is (= 200 (:status receipt-resp))
            "upload link receipt")
        (let [body (:body (api/coerce-response receipt-resp))
              uri (:witan.httpapi.spec/uri body)
              file-id (:kixi.datastore.filestore/id body)]
          (is uri)
          (is file-id)
          [uri file-id])))))

(defn get-metadata
  [auth id]
  @(http/get (url (str "/api/files/" id "/metadata"))
             {:as :json
              :content-type :json
              :throw-exceptions false
              :headers {:authorization (:auth-token auth)}}))

(defn put-metadata
  [auth metadata]
  @(http/put (url (str "/api/files/" (::ms/id metadata) "/metadata"))
             {:throw-exceptions false
              :content-type :json
              :as :json
              :headers {:authorization (:auth-token auth)}
              :form-params (select-keys metadata [::ms/size-bytes ::ms/file-type ::ms/description ::ms/name ::ms/header ::ms/tags])}))

(defn post-metadata-update
  [auth id params]
  @(http/post (url (str "/api/files/" id "/metadata"))
              {:throw-exceptions false
               :content-type :json
               :as :json
               :headers {:authorization (:auth-token auth)}
               :form-params params}))

(defn create-metadata
  [file-name]
  {::ms/header true
   ::ms/size-bytes (file-size file-name)
   ::ms/name (subs file-name
                   (inc (clojure.string/last-index-of file-name "/"))
                   (clojure.string/last-index-of file-name "."))
   ::ms/description file-name
   ::ms/file-type (subs file-name
                        (inc (clojure.string/last-index-of file-name ".")))
   ::ms/tags #{"foo" "bar" "baz"}})

(defn send-file-and-metadata
  [auth file-name metadata]
  (let [[link id] (get-upload-link auth)]
    (is link)
    (is id)
    (when (and link id)
      (let [md-with-id (assoc metadata ::ms/id id)]
        (upload-file link file-name)
        (let [resp (put-metadata auth md-with-id)]
          (when-accepted resp
            (let [receipt-resp (wait-for-receipt auth resp)]
              (is (= 200 (:status receipt-resp))
                  "metadata receipt")
              (if-not (-> receipt-resp :body :error)
                (do
                  (is-submap metadata (:body (api/coerce-response receipt-resp)))
                  (:body (api/coerce-response receipt-resp)))
                (println "Error was returned:" (-> receipt-resp :body :error) "\nIf error is `file-not-exist` it could be because the tests are running elsewhere.")))))))))

(defn create-empty-datapack
  [auth name id]
  (let [metadata {::ms/id id
                  ::ms/name name}
        resp @(http/put (url (str "/api/datapacks/" id "/metadata"))
                        {:throw-exceptions false
                         :content-type :json
                         :as :json
                         :headers {:authorization (:auth-token auth)}
                         :form-params metadata})]
    (when-accepted resp
      (let [receipt-resp (wait-for-receipt auth resp)]
        (is (= 200 (:status receipt-resp))
            "metadata receipt")
        (= 200 (:status receipt-resp))))))

(defn get-datapack
  [auth id]
  (let [resp @(http/get (url (str "/api/datapacks/" id "/metadata"))
                        {:throw-exceptions false
                         :content-type :json
                         :as :json
                         :headers {:authorization (:auth-token auth)}})]
    (when (= 200 (:status resp))
      (:body (api/coerce-response resp)))))
