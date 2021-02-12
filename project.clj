(defproject oliyh/route-swagger "0.1.6"
  :dependencies [[metosin/ring-swagger "0.26.2" :exclusions [frankiesardo/linked]]
                 [ikitommi/linked "1.3.1-alpha1"]
                 [metosin/ring-swagger-ui "3.36.0"]]
  :source-paths ["src"]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.10.1"]]}
             :dev {:dependencies [[io.pedestal/pedestal.service "0.5.8"]
                                  [io.pedestal/pedestal.jetty "0.5.8"]
                                  [metosin/scjsv "0.6.1" :exclusions [org.clojure/core.async
                                                                      metosin/jsonista]]
                                  [metosin/jsonista "0.3.0"]]}})
