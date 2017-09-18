(ns witan.httpapi.components.auth-test
  (:require [witan.httpapi.components.auth :as auth :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.test :refer :all]
            [witan.httpapi.test-base :refer :all]
            [witan.httpapi.mocks :as mocks]
            [witan.httpapi.system :as sys]
            [clj-time.core :as t]
            [environ.core :refer [env]]))

(def sys (atom nil))

(defn test-system
  [sys-fn]
  (with-redefs [sys/new-requester (fn [config] (mocks/->MockRequester))]
    (sys-fn)))

(defn start-system
  [all-tests]
  (let [mocks-fn (if (= :test profile)
                   std-system
                   test-system)]
    (reset! sys (component/start
                 (mocks-fn
                  #(sys/new-system profile))))
    (all-tests)
    (component/stop @sys)
    (reset! sys nil)))

(defn auth
  []
  (:auth @sys))

(use-fixtures :once start-system)

(deftest login-test
  (let [[s r] (auth/login (auth) "test@mastodonc.com" "Secret123")]
    (is (contains? r :token-pair))
    (is (contains? (:token-pair r) :auth-token))
    (is (contains? (:token-pair r) :refresh-token))))

(deftest refresh-test
  (let [r (auth/refresh (auth) {})]
    (is (= [:post :heimdall "/refresh-auth-token"] r))))

#_(deftest authenticate-test
    (let [r (auth/authenticate (auth) (t/date-time 0) test-auth-token)]
      (is (= #:kixi.user{:id "070b3710-22be-4d73-bfcf-dc61c23de781",
                         :groups ["ddd38ea0-8b90-4c99-b2ef-54add621b1e2" "c0d5a98c-2011-4b7d-8cd3-b839c0734431"]
                         :self-group "ddd38ea0-8b90-4c99-b2ef-54add621b1e2"} r))))
