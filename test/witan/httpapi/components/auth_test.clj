(ns witan.httpapi.components.auth-test
  (:require [witan.httpapi.components.auth :as auth :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.test :refer :all]
            [witan.httpapi.test-base :refer :all]
            [witan.httpapi.mocks :as mocks]
            [witan.httpapi.system :as sys]
            [clj-time.core :as t]
            [taoensso.timbre :as log]
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
    (log/info "Testing with the profile:" profile)
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

(deftest login-and-refresh-test
  (let [is-valid-response (fn [r]
                            (is (contains? r :token-pair))
                            (is (contains? (:token-pair r) :auth-token))
                            (is (contains? (:token-pair r) :refresh-token)))
        [s r] (auth/login (auth) "test@mastodonc.com" "Secret123")]
    (is (= 201 s))
    (is-valid-response r)
    (when (is (contains? r :token-pair))
      (let [[s r] (auth/refresh (auth) (:token-pair r))]
        (when-created {:status s}
          (is-valid-response r))))))

(deftest authenticate-test
  (let [[s r] (auth/login (auth) "test@mastodonc.com" "Secret123")
        r (auth/authenticate (auth) (t/date-time 0) (-> r :token-pair :auth-token))]
    (when-created {:status s}
      (is (contains? r :kixi.user/id))
      (is (contains? r :kixi.user/self-group))
      (is (and (contains? r :kixi.user/groups) (not-empty (:kixi.user/groups r)))))))
