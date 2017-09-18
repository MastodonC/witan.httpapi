(ns witan.httpapi.test-base
  (:require  [clojure.test :as t]
             [witan.httpapi.system :as sys]
             [com.stuartsierra.component :as component]
             [environ.core :refer [env]]))

(def profile (keyword (env :system-profile "test")))

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

(def test-auth-token
  "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6InRlc3RAbWFzdG9kb25jLmNvbSIsImlkIjoiMDcwYjM3MTAtMjJiZS00ZDczLWJmY2YtZGM2MWMyM2RlNzgxIiwibmFtZSI6IlRlc3QgVXNlciIsInVzZXItZ3JvdXBzIjpbImMwZDVhOThjLTIwMTEtNGI3ZC04Y2QzLWI4MzljMDczNDQzMSJdLCJzZWxmLWdyb3VwIjoiZGRkMzhlYTAtOGI5MC00Yzk5LWIyZWYtNTRhZGQ2MjFiMWUyIiwiZXhwIjoxNTA0NjExMzgxMzc5fQ.lnbQble-xVhTMCCgJdV-EXCNwJqGOVKL4NRdFS8FfjUa4982iYDp-CYYl389l7xjXjn0CRxHfNEdObhg9KCkYac8USUdD0f8BrDorP_4de7BQy-kXVu214sauTpm0yyzK1Aygmz3rGV2u3_ZDJBlpQG4wmIiHQcp8T-tDNkvDyMJYQ8QVdit1dxvj1inTeBdZ2VavLWAKraK7gw7YHDfBiLLA_J2MCbevQHCaYgqKy9nV2G9yE9C1mSJZ143w8yTV1c5TdnXatxbYKPtOapWu_7VybpiMVrLl-suV2tEoJmAh-Kya2UlwqCqoHbNVgwXpEGHSCmoiyQSc5lR4GNyQQ")
