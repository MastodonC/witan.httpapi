(ns witan.httpapi.components.auth-test
  (:require [witan.httpapi.components.auth :as auth :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.test :refer :all]
            [witan.httpapi.mocks :as mocks]
            [witan.httpapi.system :as sys]
            [clj-time.core :as t]))

(def sys (atom nil))

(def profile (keyword (env :system-profile "test")))

(defn start-system
  [all-tests]
  (reset! sys (component/start
               (with-redefs [sys/new-requester (fn [config] (mocks/->MockRequester))]
                 (sys/new-system profile))))
  (all-tests)
  (component/stop @sys)
  (reset! sys nil))

(defn auth
  []
  (:auth @sys))

(def test-auth-token
  "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6InRlc3RAbWFzdG9kb25jLmNvbSIsImlkIjoiMDcwYjM3MTAtMjJiZS00ZDczLWJmY2YtZGM2MWMyM2RlNzgxIiwibmFtZSI6IlRlc3QgVXNlciIsInVzZXItZ3JvdXBzIjpbImMwZDVhOThjLTIwMTEtNGI3ZC04Y2QzLWI4MzljMDczNDQzMSJdLCJzZWxmLWdyb3VwIjoiZGRkMzhlYTAtOGI5MC00Yzk5LWIyZWYtNTRhZGQ2MjFiMWUyIiwiZXhwIjoxNTA0NjExMzgxMzc5fQ.lnbQble-xVhTMCCgJdV-EXCNwJqGOVKL4NRdFS8FfjUa4982iYDp-CYYl389l7xjXjn0CRxHfNEdObhg9KCkYac8USUdD0f8BrDorP_4de7BQy-kXVu214sauTpm0yyzK1Aygmz3rGV2u3_ZDJBlpQG4wmIiHQcp8T-tDNkvDyMJYQ8QVdit1dxvj1inTeBdZ2VavLWAKraK7gw7YHDfBiLLA_J2MCbevQHCaYgqKy9nV2G9yE9C1mSJZ143w8yTV1c5TdnXatxbYKPtOapWu_7VybpiMVrLl-suV2tEoJmAh-Kya2UlwqCqoHbNVgwXpEGHSCmoiyQSc5lR4GNyQQ")

(use-fixtures :once start-system)

(deftest login-test
  (let [r (auth/login (auth) "test@mastodonc.com" "Secret123")]
    (is (= [:post :heimdall "/create-auth-token"] r))))

(deftest refresh-test
  (let [r (auth/refresh (auth) {})]
    (is (= [:post :heimdall "/refresh-auth-token"] r))))

(deftest authenticate-test
  (let [r (auth/authenticate (auth) (t/date-time 0) test-auth-token)]
    (is (= #:kixi.user{:id "070b3710-22be-4d73-bfcf-dc61c23de781",
                       :groups ["ddd38ea0-8b90-4c99-b2ef-54add621b1e2" "c0d5a98c-2011-4b7d-8cd3-b839c0734431"]} r))))
