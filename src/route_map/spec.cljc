(ns route-map.spec
  #_(:require [clojure.spec :as s]))

(comment

  (s/def ::path (s/and string? #(re-matches #"[^/]+" %)))

  (s/def ::handler (s/or :handler fn? :anything (fn [& xs] true)))

  (s/def ::verbs #{:http/GET :http/POST :http/PUT :http/DELETE})

  (s/def ::route
    (s/every
     (s/or :path (s/tuple ::path ::route)
           :verb (s/tuple ::verbs  ::anything)
           :param (s/tuple (s/coll-of keyword? :min-count 1 :max-count 1) ::route) )

     :clojure.spec/kfn (fn [i v] (first v)) :into {}
     :clojure.spec/conform-all true :kind map?))
  )


(comment

  (s/explain-str ::route {"users" {:http/POST 'handler
                                   [:route/id] {:http/GET 'user
                                                "string" 'ups}}})

  (s/explain-data ::route {"ups/dups" 'h})

  )
