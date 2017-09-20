(ns witan.httpapi.test-base
  (:require [aleph.http :as http]
            [clojure.test :as t]
            [clojure data 
             [test :refer :all]]
            [witan.httpapi.system :as sys]
            [com.stuartsierra.component :as component]
            [environ.core :refer [env]]))

(def profile (keyword (env :system-profile "test")))

(def wait-tries (Integer/parseInt (env :wait-tries "10")))
(def wait-per-try (Integer/parseInt (env :wait-per-try "500")))
(def wait-emit-msg (Integer/parseInt (env :wait-emit-msg "5000")))
(def every-count-tries-emit (int (/ wait-emit-msg wait-per-try)))

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
             (println "Waited" cnt "times for" receipt-url ". Getting:" (:status rr)))
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
