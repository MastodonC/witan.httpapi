(ns witan.httpapi.components.database-test
  (:require [witan.httpapi.components.database :refer :all]
            [clojure.test :refer :all]))

(deftest keyword-encoding-test
  (is (= :foo/bar (db->keywordns "foo__bar")))
  (is (= "foo__bar" (keywordns->db :foo/bar)))
  (is (= {:foo/bar "baz" :bar/foo "qux"} (convert-keywords-from-db {"foo__bar" "baz"
                                                                    "bar__foo" "qux"})))
  (is (= {"foo__bar" "baz"
          "bar__foo" "qux"} (convert-keywords-for-db {:foo/bar "baz" :bar/foo "qux"}))))
