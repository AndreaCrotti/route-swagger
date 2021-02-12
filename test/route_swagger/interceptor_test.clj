(ns route-swagger.interceptor-test
  (:require [clojure.test :refer [deftest testing is]]
            [route-swagger.interceptor :refer [validate-response]]
            [route-swagger.doc :as doc]
            [schema.core :as s]))


(deftest validate-response-test
  (let [{:keys [leave]} (validate-response)
        happy? (fn [response-schemas response]
                 (let [ctx {:route (with-meta {} {::doc/doc {:responses response-schemas}})
                            :response response}]
                   (= ctx (leave ctx))))]

    (testing "uses correct status code to validate response"
      (is (happy? {200 {:body {:id s/Int}
                        :headers {:X-Status s/Str}}}
                  {:status 200
                   :headers {:X-Status "Yay"}
                   :body {:id 123}})))

    (testing "uses `:default` schema if no match for the status code"
      (is (happy? {200 {:body {:id s/Int}}
                   :default {:body {:message s/Str}}}
                  {:status 500
                   :body {:message "Something might have gone wrong"}})))

    (testing "allows extra headers"
      (is (happy? {200 {:body {:id s/Int}
                        :headers {:X-Status s/Str}}}
                  {:status 200
                   :headers {:X-Status "Yay"
                             :X-Another "Hurrah"}
                   :body {:id 123}})))

    (testing "fails if response is incorrect"
      (is (thrown-with-msg?
           Exception
           #"Value does not match schema"
           (happy? {200 {:body {:id s/Int}}} {:status 200 :body {:id "Not an id"}}))))

    (testing "uses `:schema` as the body schema if available"
      (is (happy? {200 {:schema {:id s/Int}
                        :description "The id of the new record"
                        :headers {:X-Status s/Str}}}
                  {:status 200
                   :headers {:X-Status "Yay"}
                   :body {:id 123}})))))
