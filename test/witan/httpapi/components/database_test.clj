(ns witan.httpapi.components.database-test
  (:require [witan.httpapi.components.database :refer :all]
            [clojure.test :refer :all]
            [clojure.spec.alpha :as s]))

(deftest keyword-encoding-test
  (is (= :foo/bar (db->keywordns :foo__bar)))
  (is (= "foo__bar" (keywordns->db :foo/bar)))
  (is (= {:foo/bar "baz"
          :bar/foo "qux"} (convert-keywords-from-db {:foo__bar "baz"
                                                     :bar__foo "qux"})))
  (is (= {"foo__bar" "baz"
          "bar__foo" "qux"} (convert-keywords-for-db {:foo/bar "baz" :bar/foo "qux"}))))


(s/def :test/foo int?)
(s/def :test/baz string?)
(s/def :test/qux boolean?)

(s/def :test/bar (s/keys :req [:test/foo
                               :test/baz]
                         :opt [:test/qux]))

(deftest invalid-test
  (is (invalid? :test/foo "123"))
  (is (invalid? :test/bar {:test/foo 123}))
  (is (not (invalid? :test/foo 123)))
  (is (not (invalid? :test/bar {:test/foo 123
                                :test/baz "abc"}))))

(deftest invalid-submap-test
  (is (not (invalid-submap? :test/bar {:test/foo 123})))
  (is (not (invalid-submap? :test/bar {:test/baz "abc"})))
  (is (invalid-submap? :test/bar {:test/foo "123"}))
  (is (invalid-submap? :test/bar {:test/random 123})))

(deftest record->dynamo-updates-test-single
  (is (= {:update-expr "SET #a = :a",
          :expr-attr-names {"#a" "test__foo"},
          :expr-attr-vals {":a" "bar"}}
         (record->dynamo-updates {:test/foo "bar"}))))

(deftest record->dynamo-updates-test-multi
  (is (= {:update-expr "SET #a = :a, #b = :b",
          :expr-attr-names {"#a" "test__foo"
                            "#b" "bar__baz"},
          :expr-attr-vals {":a" "bar"
                           ":b" 123}}
         (record->dynamo-updates {:test/foo "bar"
                                  :bar/baz 123}))))
