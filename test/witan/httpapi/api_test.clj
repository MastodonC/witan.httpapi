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
            [cheshire.core :as json]))

(def user-id (uuid))

(alias 'ms 'kixi.datastore.metadatastore)

(defn test-system
  [sys-fn]
  (with-redefs [sys/new-authenticator (fn [config] (mocks/->MockAuthenticator user-id [(uuid)]))]
    (sys-fn)))

(defn start-system
  [all-tests]
  (if (= :staging-jenkins profile)
    (all-tests)
    (let [mocks-fn (if (= :test profile)
                     test-system
                     std-system)
          sys (atom (component/start
                     (mocks-fn
                      #(sys/new-system profile))))]
      (all-tests)
      (component/stop @sys))))

(use-fixtures :once start-system)

(defn ->result
  [resp]
  ((juxt :body :status) @resp))

(defn with-default-opts
  [opts]
  (merge {:content-type :json
          :as :json
          :accept :json
          :throw-exceptions false}
         opts))

(defmacro is-spec
  [spec r]
  `(is (spec/valid? ~spec ~r)
       (str (spec/explain-data ~spec ~r))))

(defn login
  []
  (->result
   (http/post (local-url "/api/login")
              (with-default-opts
                {:form-params
                 {:username "test@mastodonc.com"
                  :password "Secret123"}}))))

(defn get-auth-tokens
  []
  (let [[r s] (login)]
    (is (= 201 s) (prn-str r))
    (:token-pair r)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest healthcheck-test
  (let [[r s] (->result (http/get (local-url "/healthcheck")))]
    (is (=  200 s))
    (is (= "hello" (slurp r)))))

(deftest noexist-404-test
  (let [[r s] (->result (http/get (local-url "/notexist")
                                  {:throw-exceptions false}))]
    (is (= 404 s))))

;; Currently doesn't pass and there doesn't appear to be a
;; nice way to achieve this
#_(deftest nomethod-405-test
    (let [[r s] (->result (http/post (local-url "/healthcheck")
                                     {:throw-exceptions false}))]
      (is (= 405 s))))

(deftest login-test
  (let [[r s] (login)]
    (is (= 201 s))
    (is-spec ::s/token-pair-container r)))

(deftest refresh-test
  (let [[rl sl] (login)
        [r s] (->result
               (http/post (local-url "/api/refresh")
                          (with-default-opts
                            {:form-params rl})))]
    (is (= 201 s))
    (is-spec ::s/token-pair-container r)))

(deftest swagger-test
  (let [[r s] (->result
               (http/get (local-url "/swagger.json")
                         {:throw-exceptions false
                          :as :json}))]
    (is (= 200 s) "An error here could indicate a problem generating the swagger JSON")
    (is (= "2.0" (:swagger r)))))

(deftest retrieve-upload-link
  (let [auth (get-auth-tokens)
        file-name  "./test-resources/metadata-one-valid.csv"
        metadata (create-metadata file-name)
        retrieved-metadata (send-file-and-metadata auth file-name metadata)]))

(deftest file-errors-test
  "Trigger an error by trying to PUT metadata that doesn't exist"
  (let [auth (get-auth-tokens)
        file-name  "./test-resources/metadata-one-valid.csv"
        metadata (create-metadata file-name)
        receipt-resp (put-metadata auth (assoc metadata
                                               ::ms/id (uuid)))]
    (if-not (= 202 (:status receipt-resp))
      (is false (str "Receipt did not return 202: " receipt-resp))
      (let [receipt-resp (wait-for-receipt auth receipt-resp)]
        (is (= 200 (:status receipt-resp))
            "metadata receipt")
        (is (= "file-not-exist"
               (get-in receipt-resp [:body :error :witan.httpapi.spec/reason])))))))
