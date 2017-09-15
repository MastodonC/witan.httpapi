(ns witan.httpapi.components.activities-test
  (:require [witan.httpapi.components.activities :refer :all]
            [witan.httpapi.test-base :refer :all]
            [witan.httpapi.system :as sys]
            [witan.httpapi.mocks :as mocks]
            [witan.httpapi.spec :as spec]
            [com.stuartsierra.component :as component]
            [clojure.test :refer :all]))

(def sys (atom nil))

(defn activities
  []
  (:activities @sys))

(defn start-system
  [all-tests]
  (reset! sys (component/start
               (with-redefs [sys/new-requester (fn [config] (mocks/->MockRequester))
                             sys/new-authenticator (fn [config] (mocks/->MockAuthenticator))]
                 (sys/new-system profile))))
  (all-tests)
  (component/stop @sys)
  (reset! sys nil))

(use-fixtures :once start-system)

(deftest get-receipt-response-test
  (let [user (random-user)
        id (uuid)]
    (let [[s _ _] (get-receipt-response (activities) user (uuid))]
      (is (= 404 s))) ;; not yet created
    ;;
    (create-receipt! (:database (activities)) user id)
    (let [[s _ _] (get-receipt-response (activities) user id)]
      (is (= 202 s))) ;; still pending
    ;;
    (complete-receipt! (:database (activities)) id "/foo/bar")
    (let [[s _ h] (get-receipt-response (activities) user id)]
      (is (= 303 s))
      (is (= "/foo/bar" (get h "Location")))) ;; created, returns redirect
    ;;
    (let [[s _ _] (get-receipt-response (activities) (assoc user :kixi.user/id (uuid)) id)]
      (is (= 401 s))))) ;; wrong user

(deftest get-upload-link-response-test
  (let [user (random-user)
        id (uuid)
        fid (uuid)]
    (let [[s _ _] (get-upload-link-response (activities) user (uuid))]
      (is (= 404 s))) ;; not yet created
    ;;
    (create-upload-link! (:database (activities)) user id fid "/foo/bar")
    (let [[s b _] (get-upload-link-response (activities) user id)]
      (is (= 200 s))
      (is (= "/foo/bar" (::spec/uri b)))
      (is (= fid (:kixi.datastore.filestore/id b)))) ;; still pending
    ;;
    (let [[s _ _] (get-upload-link-response (activities) (assoc user :kixi.user/id (uuid)) id)]
      (is (= 401 s)))))

(deftest get-error-response-test
  (let [user (random-user)
        id (uuid)
        fid (uuid)]
    (let [[s _ _] (get-upload-link-response (activities) user (uuid))]
      (is (= 404 s))) ;; not yet created
    ;;
    (create-upload-link! (:database (activities)) user id fid "/foo/bar")
    (let [[s b _] (get-upload-link-response (activities) user id)]
      (is (= 200 s))
      (is (= "/foo/bar" (::spec/uri b)))
      (is (= fid (:kixi.datastore.filestore/id b)))) ;; still pending
    ;;
    (let [[s _ _] (get-upload-link-response (activities) (assoc user :kixi.user/id (uuid)) id)]
      (is (= 401 s)))))
