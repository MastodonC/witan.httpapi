(ns witan.httpapi.queries-test
  (:require [witan.httpapi.queries :refer :all]
            [clojure.test :refer :all]))

(deftest encode-kw-test
  (is (= "foo_bar" (encode-kw :foo/bar)))
  (is (= "foo.baz_bar" (encode-kw :foo.baz/bar)))
  (is (= "_foo" (encode-kw :foo))))
