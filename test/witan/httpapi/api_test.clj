(ns witan.httpapi.api-test
  {:integration true}
  (:require [witan.httpapi.api :refer :all]
            [witan.httpapi.test-base :refer :all]
            [witan.httpapi.system :as sys]
            [witan.httpapi.mocks :as mocks]
            [witan.httpapi.spec :as s]
            [clojure.spec.alpha :as spec]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [taoensso.timbre :as log]
            [aleph.http :as http]
            [cheshire.core :as json]
            ;;
            [kixi.user :as user]
            [kixi.datastore.metadatastore :as ms]))

(def user-id (uuid))
(def file-name  "./test-resources/metadata-one-valid.csv")

(defn test-system
  [sys-fn]
  (with-redefs [sys/new-authenticator (fn [config] (mocks/->MockAuthenticator user-id [(uuid)]))]
    (sys-fn)))

(defn start-system
  [all-tests]
  (if (= :staging-jenkins profile)
    (all-tests)
    (let [mocks-fn std-system #_(if (= :test profile)
                                  test-system
                                  std-system)
          sys (atom (component/start
                     (mocks-fn
                      #(sys/new-system profile))))]
      (try
        (all-tests)
        (finally
          (component/stop @sys))))))


(defn with-default-opts
  [opts]
  (merge {:content-type :json
          :as :json
          :accept :json
          :throw-exceptions false}
         opts))

(defn login
  []
  ((juxt :body :status)
   @(http/post (url "/api/login")
               (with-default-opts
                 {:form-params
                  {:username "test@mastodonc.com"
                   :password "Secret123"}}))))

(defn get-auth-tokens
  []
  (let [[r s] (login)]
    (is (= 201 s) (prn-str r))
    (:token-pair r)))

(def file-id (atom nil))
(def file-metadata (atom nil))

(defn upload-new-file
  [all-tests]
  (let [auth (get-auth-tokens)
        metadata (create-metadata file-name)
        retrieved-metadata (send-file-and-metadata auth file-name metadata)]
    (if (and retrieved-metadata
             (is-spec :witan.httpapi.spec/file-metadata-get retrieved-metadata))
      (let [id (::ms/id retrieved-metadata)]
        (reset! file-id id)
        (reset! file-metadata retrieved-metadata)
        (all-tests))
      (throw (Exception. (str "Couldn't upload metadata: " retrieved-metadata))))))

(defn get-paging
  [r]
  (get-in r [:body :paging]))

(defn upload-file-inline
  []
  (let [m (create-metadata file-name)
        new-metadata (send-file-and-metadata auth file-name m)]
    (when-not (and new-metadata (is-spec :witan.httpapi.spec/file-metadata-get new-metadata))
      (throw (Exception. (str "Couldn't upload metadata: " new-metadata))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :once start-system upload-new-file)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest healthcheck-test
  (let [r @(http/get (url "/healthcheck"))]
    (when-success r
      (is (= "hello" (-> r :body slurp))))))

(deftest noexist-404-test
  (let [r @(http/get (url "/notexist")
                     {:throw-exceptions false})]
    (is (= 404 (:status r)))))

;; Currently doesn't pass and there doesn't appear to be a
;; nice way to achieve this
#_(deftest nomethod-405-test
    (let [r @((http/post (url "/healthcheck")
                         {:throw-exceptions false}))]
      (is (= 405 (:status r)))))

(deftest login-test
  (let [[r s] (login)]
    (when-created {:status s}
      (is-spec ::user/token-pair-container r))))

(deftest refresh-test
  (let [[rl sl] (login)
        r @(http/post (url "/api/refresh")
                      (with-default-opts
                        {:form-params rl}))]
    (when-created r
      (is-spec ::user/token-pair-container (:body r)))))

(deftest swagger-test
  (let [r @(http/get (url "/swagger.json")
                     (with-default-opts {}))]
    (when-success r
      (is (= "2.0" (-> r :body :swagger))))))

(deftest get-files-test
  (let [auth (get-auth-tokens)
        r @(http/get (url "/api/files")
                     (with-default-opts
                       {:headers {:authorization (:auth-token auth)}}))]
    (when-success r)
    (is-spec :witan.httpapi.spec/paged-metadata-items (:body r))))

(deftest get-files-paging-test
  (let [auth (get-auth-tokens)
        r @(http/get (url "/api/files")
                     (with-default-opts
                       {:headers {:authorization (:auth-token auth)}}))]
    (when (= 1 (:total (get-paging r)))
      ;; upload a new metadata - so we know there's more than 1
      (upload-file-inline))

    (testing "Is the number of results being limited?"
      (let [r @(http/get (url "/api/files?count=1")
                         (with-default-opts
                           {:headers {:authorization (:auth-token auth)}}))
            {:keys [total count index]} (get-paging r)]
        (is (= 1 count))))

    (testing "Is the index being increased?"
      (let [r @(http/get (url "/api/files?index=1")
                         (with-default-opts
                           {:headers {:authorization (:auth-token auth)}}))
            {:keys [total count index]} (get-paging r)]
        (is (= 1 index))))))

(deftest get-metadata-test
  (let [auth (get-auth-tokens)
        r @(http/get (url (str "/api/files/" @file-id "/metadata"))
                     (with-default-opts
                       {:headers {:authorization (:auth-token auth)}}))]

    (when-success r
      (is-spec :witan.httpapi.spec/file-metadata-get (:body r)))))

(deftest get-metadata-sharing-test
  (let [auth (get-auth-tokens)
        r @(http/get (url (str "/api/files/" @file-id "/sharing"))
                     (with-default-opts
                       {:headers {:authorization (:auth-token auth)}}))]
    (when-success r
      (is-spec :witan.httpapi.spec/file-sharing (:body r)))))

(deftest update-metadata-test
  (let [auth (get-auth-tokens)
        new-desc (str "New description " (uuid))
        new-name (str "New name " (uuid))
        new-tags #{:foo :bar :baz}
        new-maintainer "Bob"
        new-license "Custom"
        new-source-updated "20170923"
        new-temporal-coverage-from "20170924"
        new-temporal-coverage-to   "20170925"
        update-params {:kixi.datastore.metadatastore.update/description {:set new-desc} ;; optional field
                       :kixi.datastore.metadatastore.update/name {:set new-name} ;; mandatory field
                       :kixi.datastore.metadatastore.update/tags {:conj new-tags}
                       :kixi.datastore.metadatastore.update/source-updated {:set new-source-updated}
                       :kixi.datastore.metadatastore.update/maintainer {:set new-maintainer}

                       :kixi.datastore.metadatastore.license.update/license {:kixi.datastore.metadatastore.license.update/type {:set new-license}}
                       :kixi.datastore.metadatastore.time.update/temporal-coverage {:kixi.datastore.metadatastore.time.update/from
                                                                                    {:set new-temporal-coverage-from}
                                                                                    :kixi.datastore.metadatastore.time.update/to
                                                                                    {:set new-temporal-coverage-to}}}
        update-receipt (post-metadata-update
                        auth @file-id update-params)]
    (when-accepted update-receipt
      (let [receipt-resp (wait-for-receipt auth update-receipt)]
        (when-success receipt-resp
          (Thread/sleep 4000) ;; eventual consistency, innit
          (let [r (get-metadata auth @file-id)]
            (when-success r
              (is-submap (assoc @file-metadata
                                ::ms/description new-desc
                                ::ms/name new-name
                                ::ms/tags (sort (mapv name new-tags))
                                ::ms/source-updated new-source-updated
                                ::ms/maintainer new-maintainer
                                :kixi.datastore.metadatastore.license/license {:kixi.datastore.metadatastore.license/type new-license}
                                :kixi.datastore.metadatastore.time/temporal-coverage {:kixi.datastore.metadatastore.time/from new-temporal-coverage-from
                                                                                      :kixi.datastore.metadatastore.time/to new-temporal-coverage-to})
                         (update (-> r :body)
                                 ::ms/tags sort)))))))))

(deftest file-errors-test
  "Trigger an error by trying to PUT metadata that doesn't exist"
  (let [auth (get-auth-tokens)
        file-name  "./test-resources/metadata-one-valid.csv"
        metadata (assoc (create-metadata file-name) ::ms/id (uuid))
        receipt-resp (put-metadata auth metadata)]
    (when-accepted  receipt-resp
      (let [receipt-resp (wait-for-receipt auth receipt-resp)]
        (when-success receipt-resp
          (is (= "file-not-exist"
                 (get-in receipt-resp [:body :error :witan.httpapi.spec/reason]))))))))

(deftest sharing-update-test
  (let [auth (get-auth-tokens)
        new-grp (uuid)]
    (testing "Adding a new group to the sharing properties of our file"
      (let [r @(http/post (url (str "/api/files/" @file-id "/sharing"))
                          (with-default-opts
                            {:form-params {:activity ::ms/file-read
                                           :operation "add"
                                           :group-id new-grp}
                             :headers {:authorization (:auth-token auth)}}))]
        (when-accepted r
          (let [receipt-resp (wait-for-receipt auth r)]
            (when-success receipt-resp
              (Thread/sleep 4000) ;; eventual consistency, innit
              (let [sharing-r @(http/get (url (str "/api/files/" @file-id "/sharing"))
                                         (with-default-opts
                                           {:headers {:authorization (:auth-token auth)}}))]
                (is (contains? (set (get-in sharing-r [:body ::ms/sharing ::ms/file-read])) new-grp))))))))

    (testing "Removing a group from the sharing properties of our file"
      (let [r @(http/post (url (str "/api/files/" @file-id "/sharing"))
                          (with-default-opts
                            {:form-params {:activity ::ms/file-read
                                           :operation "remove"
                                           :group-id new-grp}
                             :headers {:authorization (:auth-token auth)}}))]
        (when-accepted r
          (Thread/sleep 4000) ;; eventual consistency, innit
          (let [receipt-resp (wait-for-receipt auth r)]
            (when-success receipt-resp
              (let [sharing-r @(http/get (url (str "/api/files/" @file-id "/sharing"))
                                         (with-default-opts
                                           {:headers {:authorization (:auth-token auth)}}))]
                (is (not (contains? (set (get-in sharing-r [:body ::ms/sharing ::ms/file-read])) new-grp)))))))))))
