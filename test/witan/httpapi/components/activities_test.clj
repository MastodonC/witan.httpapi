(ns witan.httpapi.components.activities-test
  (:require [witan.httpapi.components.activities :refer :all]
            [witan.httpapi.test-base :refer :all]
            [witan.httpapi.system :as sys]
            [witan.httpapi.mocks :as mocks]
            [witan.httpapi.spec :as spec]
            [com.stuartsierra.component :as component]
            [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))

(def sys (atom nil))

(defn activities
  []
  (:activities @sys))

(defn start-system
  [all-tests]
  (reset! sys (component/start
               (with-redefs [sys/new-requester (fn [config] (mocks/->MockRequester))
                             sys/new-authenticator (fn [config] (mocks/->MockAuthenticator nil nil))]
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
    (let [[s _ _] (get-upload-link-response (activities) user (uuid) (uuid))]
      (is (= 404 s))) ;; not yet created
    ;;
    (create-upload-link! (:database (activities)) user id fid "/foo/bar")
    (let [[s b _] (get-upload-link-response (activities) user fid id)]
      (is (= 200 s))
      (is (= "/foo/bar" (::spec/uri b)))
      (is (= fid (:kixi.datastore.filestore/id b)))) ;; still pending
    ;;
    (let [[s _ _] (get-upload-link-response (activities) (assoc user :kixi.user/id (uuid)) (uuid) id)]
      (is (= 404 s)))
    ;;
    (let [[s _ _] (get-upload-link-response (activities) (assoc user :kixi.user/id (uuid)) fid id)]
      (is (= 401 s)))))

(deftest get-download-link-response-test
  (let [user (random-user)
        id (uuid)
        fid (uuid)]
    (let [[s _ _] (get-download-link-response (activities) user (uuid) (uuid))]
      (is (= 404 s))) ;; not yet created
    ;;
    (create-download-link! (:database (activities)) user id fid "/foo/bar")
    (let [[s b _] (get-download-link-response (activities) user fid id)]
      (is (= 200 s))
      (is (= "/foo/bar" (::spec/uri b)))
      (is (= fid (:kixi.datastore.filestore/id b)))) ;; still pending
    ;;
    (let [[s _ _] (get-download-link-response (activities) (assoc user :kixi.user/id (uuid)) (uuid) id)]
      (is (= 404 s)))
    ;;
    (let [[s _ _] (get-download-link-response (activities) (assoc user :kixi.user/id (uuid)) fid id)]
      (is (= 401 s)))))

(deftest get-error-response-test
  (let [user (random-user)
        id (uuid)
        fid (uuid)]
    (let [[s _] (get-error-response (activities) user id fid)]
      (is (= 404 s))) ;; not yet created
    ;;
    (create-error! (:database (activities)) id fid "foobar")
    (let [[s b] (get-error-response (activities) user id fid)]
      (is (= 200 s) id)
      (when (= 200 s)
        (is (= "foobar" (get-in b [:error ::spec/reason])))
        (is (= fid (get-in b [:error :kixi.datastore.filestore/id]))))) ;; still pending
    ))

(deftest conform-metadata-updates-test
  (let [test-fn (fn [p]
                  (let [cnfmd (conform-metadata-updates p)]
                    (is p)
                    (is (every? #(not (string? %)) (vals cnfmd)))))]
    (run! test-fn (gen/sample (s/gen ::spec/file-metadata-post) 100))))
