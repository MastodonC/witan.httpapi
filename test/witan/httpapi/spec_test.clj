(ns witan.httpapi.spec-test
  (:require [witan.httpapi.spec :as s]
            [clojure.test :refer :all]
            [spec-tools.swagger.core :as swagger]))

(deftest no-empty-requireds
  "This proves our specs aren't doing anything weird"
  (testing "file-info"
    (let [schema (swagger/transform ::s/file-info)
          results (atom [])]
      (clojure.walk/postwalk #(do (when (= [] (:required %))
                                    (swap! results conj %)) %) schema)
      (is (empty? @results))))
  (testing "paged-metadata-items"
    (let [schema (swagger/transform ::s/paged-files)
          results (atom [])]
      (clojure.walk/postwalk #(do (when (= [] (:required %))
                                    (swap! results conj %)) %) schema)
      (is (empty? @results)))))
