(def metrics-version "2.9.0")
(defproject witan.httpapi "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-beta2"]
                 [com.cognitect/transit-clj "0.8.290"]
                 [com.stuartsierra/component "0.3.1"]
                 [com.taoensso/timbre "4.7.4"]
                 [com.taoensso/faraday "1.9.0" :exclusions [com.amazonaws/aws-java-sdk-dynamodb]]
                 [cheshire "5.6.3" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [environ "1.1.0"]
                 [aero "1.1.2"]
                 [com.gfredericks/schpec "0.1.2"]
                 [spootnik/signal "0.2.1"]
                 [metosin/spec-tools "0.5.0"]
                 [metosin/compojure-api "2.0.0-alpha10" :exclusions [metosin/spec-tools]]
                 [kixi/kixi.spec "0.1.7"]
                 [kixi/kixi.comms "0.2.31"]
                 [kixi/kixi.log "0.1.5"]
                 [kixi/kixi.metrics "0.4.0" :exclusions [org.slf4j/slf4j-api]]
                 [kixi/aleph "0.4.4-alpha5"]
                 [kixi/buddy "1.2.1" :exclusions [cheshire]]
                 [kixi/joplin.core "0.3.10-SNAPSHOT"]
                 [kixi/joplin.dynamodb "0.3.10-SNAPSHOT"]
                 [metrics-clojure ~metrics-version  :exclusions [org.slf4j/slf4j-api]]
                 [metrics-clojure-jvm ~metrics-version :exclusions [org.slf4j/slf4j-api]]
                 [metrics-clojure-ring ~metrics-version :exclusions [org.slf4j/slf4j-api]]]

  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]]
                   :source-paths ["dev"]
                   :repl-options {:init-ns user}}
             :uberjar {:aot  :all
                       :main witan.httpapi.system
                       :uberjar-name "witan.httpapi-standalone.jar"}}
  :test-selectors {:default (constantly true)
                   :integration :integration})
