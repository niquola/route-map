# route-map

[![Clojars Project](http://clojars.org/route-map/latest-version.svg)](http://clojars.org/route-map)

[![Build Status](https://travis-ci.org/niquola/route-map.svg)](https://travis-ci.org/niquola/route-map)

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

```clojure
(ns mywebapp
  (:require
    [route-map.core :as rm]
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

[See example app](examples/mywebapp.clj)


## Param options

As second item in param declaration collection you can provide 
set of possible path items as set or regexp to match. This matches 
will have bigger priority than just parameters 

```clojure

(def routes
 {[:entity] {:GET 'admin
             :route-map/enum #{"Admin" "User"}}
  [:matched ] {:GET 'pattern
               :route-map/regexp #"^prefix_"}
  [:default] {:GET 'default}})

(match [:get "/Admin"]) 
  => {:match 'admin :params {:entity "Admin"}}

(match [:get "/prefix_something"]) 
   => {:match 'pattern :params {:matched "prefix_something"}}

(match [:get "/other"]) 
   => {:match 'default :params {:default "other"}}

```

## Functional params

To match on params details you could use `funcional-param`:

```clojure
(defn match-ids [k]
  (when (re-matches #".*,.*" k)
    {:ids (str/split k #",")}))

(def routes
  {"user" {[:id] {:GET 'user}
           [match-ids] {:GET 'specific}}})

(match [:get "/user/1,2"]) => {:match 'specific :params {:ids ["1", "2"]}}
(match [:get "/user/1"]) => {:match 'user :params {:id "1"}}

```

Function should accept part of path, eval any predicate on it and in case of 
success return hash-map with params, otherwise nil.

First matching function will be choosen, so your route could be undeterministic


## ClojureScript

In ClojureScript scenario you do not have methods
and to handle nested routes in the middle use magic keyword ```:.```

```clojure
(def routes
  {"admin" {"users" {:. 'users-list-view
                     [:id] 'user-view}
            "groups" 'groups-list-view}})

(match "/admin/users" routes)
;;=> {:match 'users-list-view ...}

(match "/admin/users/5" routes)
;;=> {:match 'users-list-view :params {:id "5"} ...}

(match "/admin/groups" routes)
;;=> {:match 'groups-list-view ...}
```

## Tips

`match` could be used for links validation in app

```clojure
(defn url [path]
  (if (match path)
    path
    (throw Exception. (str "url " path " does not match any paths"))))
```

One can put additional metadata into routes hash-map and
interpret it in some useful way.
For example dynamicaly build middlewares stack for specific paths:

```clojure
(def routes
  {:interceptors [ensure-admin]
   :GET  list
   :POST create
   [:uid] {:interceptors [ensure-user]
           :GET show
           :PUT udpate
           :DELETE destroy}})

(defn app [{meth :request-method uri :uri params :params :as req}]
  (let [res (rm/match [meth uri] routes)
        ;; collect all :interceptors keys
        interceptors (mapcat :interceptors (:parents res))
        handler (:match res)
        ;; add route params to params
        req (update-in req [:params] merge (:params res))
        ;; build stack
        stack ((apply comp interceptors) handler)]
  ;; apply
  (stack req)))
```

Integrate with Prismatic Schema for input validation:

```clojure
{"users" {:POST [UserSchema create-user]
          [:id] {:PUT [UserSchema create-user]
                 ....}}}

;; somewhere in dispatcher

(let [body (:body request)
      ;; destruct match
      [schema handler] route-match]
  (if (s/check schema body)
      (handler req)
      ....))
```

and generate swagger specification from routes.

## Release notes

### 0.0.7 

* Collect params in parent nodes
* Params selection options: set and regex

### 0.0.6 

* Match in different branches.

## License

Copyright © 2014 niquola

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
