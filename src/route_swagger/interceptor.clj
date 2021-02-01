(ns route-swagger.interceptor
  (:require [route-swagger.doc :as doc]
            [route-swagger.schema :as schema]
            [ring.util.response :refer [response resource-response redirect]]
            [ring.swagger.swagger2 :as spec]
            [ring.util.http-status :as status]
            [cheshire.core :as json]
            [clojure.spec.alpha :as s]
            [spec-tools.core :as st]
            [spec-tools.openapi.core :as openapi]))

(s/def ::id int?)
(s/def ::name string?)
(s/def ::street string?)
(s/def ::city (s/nilable #{:tre :hki}))
(s/def ::filters (s/coll-of string? :into []))
(s/def ::address (s/keys :req-un [::street ::city]))
(s/def ::user (s/keys :req-un [::id ::name ::address]))
(s/def ::token string?)

(openapi/openapi-spec
 {:openapi "3.0.3"
  :info
  {:title          "Sample Pet Store App"
   :description    "This is a sample server for a pet store."
   :termsOfService "http://example.com/terms/"
   :contact
   {:name  "API Support",
    :url   "http://www.example.com/support"
    :email "support@example.com"}
   :license
   {:name "Apache 2.0",
    :url  "https://www.apache.org/licenses/LICENSE-2.0.html"}
   :version        "1.0.1"}
  :servers
  [{:url         "https://development.gigantic-server.com/v1"
    :description "Development server"}
   {:url         "https://staging.gigantic-server.com/v1"
    :description "Staging server"}
   {:url         "https://api.gigantic-server.com/v1"
    :description "Production server"}]
  :components
  {::openapi/schemas {:user    ::user
                      :address ::address}
   ::openapi/headers {:token ::token}}
  :paths
  {"/api/ping"
   {:get
    {:description "Returns all pets from the system that the user has access to"
     :responses   {200 {::openapi/content
                        {"application/xml" ::user
                         "application/json"
                         (st/spec
                          {:spec             ::address
                           :openapi/example  "Some examples here"
                           :openapi/examples {:admin
                                              {:summary       "Admin user"
                                               :description   "Super user"
                                               :value         {:anything :here}
                                               :externalValue "External value"}}
                           :openapi/encoding {:contentType "application/json"}})}}}}}
   "/user/:id"
   {:post
    {:tags                ["user"]
     :description         "Returns pets based on ID"
     :summary             "Find pets by ID"
     :operationId         "getPetsById"
     :requestBody         {::openapi/content {"application/json" ::user}}
     :responses           {200      {:description "pet response"
                                     ::openapi/content
                                     {"application/json" ::user}}
                           :default {:description "error payload",
                                     ::openapi/content
                                     {"text/html" ::user}}}
     ::openapi/parameters {:path   (s/keys :req-un [::id])
                           :header (s/keys :req-un [::token])}}}}})

(defn- openapi-json [descriptor]
  (openapi/openapi-spec descriptor))

(defn- default-json-converter [swagger-object]
  (spec/swagger-json
    swagger-object
    {:default-response-description-fn
     #(get-in status/status [% :description] "")}))

(defn swagger-json
  "Creates an interceptor that serves the generated documentation on the path of
  your choice. Accepts an optional function f that takes the swagger-object and
  returns a json body."
  ([] (swagger-json default-json-converter))
  ([f]
   {:name  ::doc/swagger-json
    :enter (fn [{:keys [route] :as context}]
             (let [descriptor (-> route meta ::doc/swagger-object)
                   f (if (:spec? descriptor)
                       openapi-json
                       f)]
               (assoc context :response
                      {:status 200 :body (f descriptor)})))}))

(defn swagger-ui
  "Creates an interceptor that serves the swagger ui on a path of your choice.
  Note that the path MUST specify a splat argument named \"resource\" e.g.
  \"my-path/*resource\". Acceps additional options used to construct the
  swagger-object url (such as :app-name :your-app-name), using pedestal's
  'url-for' syntax."
  [& path-opts]
  {:name  ::doc/swagger-ui
   :enter (fn [{:keys [request] :as context}]
            (let [{:keys [path-params path-info url-for]} request
                  res (:resource path-params)]
              (assoc context :response
                             (case res
                               "" (redirect (str path-info "index.html"))
                               "config.json" (response (json/encode {:url (apply @url-for ::doc/swagger-json path-opts)}))
                               (resource-response res {:root "swagger-ui"})))))})

(defn coerce-request
  "Creates an interceptor that coerces the params for the selected route,
  according to the route's swagger documentation. A coercion function f that
  acceps the route params schema and a request and return a request can be
  supplied. The default implementation throws if any coercion error occurs."
  ([] (coerce-request (schema/make-coerce-request)))
  ([f]
   {:name  ::coerce-request
    :enter (fn [{:keys [route] :as context}]
             (if-let [schema (->> route doc/annotation :parameters)]
               (update context :request (partial f schema))
               context))}))

(defn validate-response
  "Creates an interceptor that validates the response for the selected route,
  according to the route's swagger documentation. A validation function f that
  accepts the route response schema and a response and return a response can be
  supplied. The default implementation throws if a validation error occours."
  ([] (validate-response (schema/make-validate-response)))
  ([f]
   {:name  ::validate-response
    :leave (fn [{:keys [response route] :as context}]
             (let [schemas (->> route doc/annotation :responses)]
               (if-let [schema (or (get schemas (:status response))
                                   (get schemas :default))]
                 (update context :response (partial f schema))
                 context)))}))
