(ns witan.httpapi.api-test
  (:require [witan.httpapi.api :refer :all]
            [clojure.test :refer :all]))

(deftest failme
  (is (= 3 (+ 1 1))))
