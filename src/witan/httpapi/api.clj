(ns witan.httpapi.api
  (:require [compojure.api.sweet :refer [context GET POST resource api]]
            [ring.util.http-response :refer [ok]]
            [clojure.spec.alpha :as s]
            [spec-tools.spec :as spec]
            [spec-tools.core :as st]))

(s/def ::x spec/int?)
(s/def ::y spec/int?)
(s/def ::total spec/int?)
(s/def ::total-map (s/keys :req-un [::total]))
(s/def ::result (s/keys))

(s/def ::xs (s/coll-of spec/int?))

(defn -regex?
  [rs]
  (fn [x]
    (if (and (string? x) (re-find rs x))
      x
      ::s/invalid)))

(def uuid?
  (-regex? #"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"))

(def uuid (s/conformer uuid? identity))

(defmacro api-spec
  [symb typename]
  `(st/create-spec {:spec ~symb
                    :form '~symb
                    :json-schema/type ~typename}))

(s/def ::id (api-spec uuid "string"))
(s/def ::name string?)

(s/def ::file (s/keys :req-un [::id ::x ::name]))

#_(s/def ::id uuid)
#_(s/def ::id (st/create-spec {:spec uuid
                               :form `uuid
                               :json-schema/type "string"}))
(def routes
  (context "/api" []
    :tags ["api"]
    :coercion :spec

    (GET "/plus" []
      :summary "plus with clojure.spec"
      :query-params [x :- ::x, {y :- ::y 0}]
      :return ::total-map
      (ok {:total (+ x y)}))

    (GET "/plus2" []
      :summary "plus2 with clojure.spec"
      :query-params [x :- ::xs]
      :return ::total-map
      (ok {:total (apply + x)}))

    (POST "/uuid" []
      :summary "my funky uuid"
      :body-params [id :- ::id]
      :return ::result
      (ok {:result id}))

    (POST "/file" []
      :summary "my funky file"
      :body-params [file :- ::file]
      :return ::result
      (ok {:result file}))

    #_(context "/data-plus" []
        (resource
         {:post
          {:summary "data-driven plus with clojure.spec"
           :parameters {:body-params (s/keys :req-un [::x ::y])}
           :responses {200 {:schema ::total-map}}
           :handler (fn [{{:keys [x y]} :body-params}]
                      (ok {:total (+ x y)}))}}))))

(def handler
  (api
   {:swagger
    {:ui "/"
     :spec "/swagger.json"
     :options {:ui {:jsonEditor true}}
     :data {:info {:title "Witan API (Datastore) "}
            :tags [{:name "api", :description "API routes for Witan"}]}}}
   routes))
