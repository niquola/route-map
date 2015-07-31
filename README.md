# route-map

Half-page clojure library for routing (dispatching) in web applications.

Routes are represented as hierarchiecal hash-map:

Keys in map could be:
 * methods :GET, :POST, :PUT, :DELETE, :OPTION
 * hardcoded parts of path, for example "users"
 * vector with one keyword - parameter key - match part of path as parameter
   if key name ends with `*` (like :path*), it will match the rest of path
   There are could be only one parameter key per map.
 * any not listed keys, which will be present in result of looking up route
 * leafs could be anything

```clojure
(def routes
  {:GET    'root
   "files" {:path* {:GET 'file}}
   "users" {:GET  'list
            :POST 'create
            [:uid] {:GET 'show
                   :PUT 'udpate
                   :DELETE 'destroy}}})

(route-map/match [:get "/unexisting"] routes) ;;=> nil
(route-map/match [:get "/users/1"] routes)
;;=> {:match 'show
;;    :parents [all nodes in path to match]
;;    :params {:uid "1"}}

(route-map/match [:get "/files/assets/img/icon.png"] routes)
;;=> {:match 'file
;;    :params {:path* ["assets" "img" "icon.png"]}
;;    :parents ...}

```


To match route you call:

`(route-map/match [requrest-method uri] routes)` 

Request uri and method are transformed into vector splited by "/" - 
`GET /users/1 => ["users" "1" :GET]`, which is treated as path in route tree.

If path is found `match` returns hash-map:

```
{:match node ;; matched node
 :parents parents ;; all nodes in path to matched
 :params params ;; params extracted while matching [:param-name] keys}
```

otherwise nil.

Library just match routes and dispatch execution is up to you:

```
(ns mywebapp
  (:require
    [route-map :as rm]
    [ring.adapter.jetty :as jetty]))

(defn list [req]...)
(defn create [req]...)
(defn show [req]...)
(defn update [req]...)
(defn delete [req]...)

(def routes
  {:interceptors ['ensure-admin]
   :GET  list
   :POST create
   [:uid] {:interceptors ['ensure-user]
           :GET show
           :PUT udpate
           :DELETE destroy}})

(defn app [{meth :request-method uri :uri :as req}]
  (if-let [res (rm/match [meth uri] routes)]
    (apply (:match res)  (update-in req [:params] merge (:params req))
    {:status 404 :body "Not found"})))

(jetty/run-jetty #'app {:port 3003 :join? false}))
```

[See example app](blob/master/examples/mywebapp.clj)

## License

Copyright © 2014 niquola

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
