# route-map

A Clojure library designed to deal with routes as data.

Routes are just clojure data structure, which means:

* Composable
* Transformabe
* Can be used as meta information

Dispatching is just search in tree
wich allow you any customization and full control.

### Basic API

Represent url pattern as vector:

```clojure
"/users/1/profile/" => ["users" :id "profile"]
```

Represent routes as tree of clojure data structures

```clojure
(def routes
  {:get    {:fn #'dashboard }
   "users" {:get    {:fn #'list-users }
            "new"   {:get {:fn #'new-user-form }}
            [:name] {:get {:fn #'show-user }
                     "profile" {:get {:fn #'user-profile }}}}} )

```

Dispatching becomes just search in tree:

```clojure
(defn handler [{meth :request-method uri :uri :as req}]
  (if-let [h (route-map/match [meth uri] routes)]
    ((:fn h) (assoc req :params (:params h)))
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (str "No routes for " uri "\n in " (pr-str routes) "\n " (pr-str req))}))

```

For more info see example/mywebapp.clj

To run example:

```bash
lein with-profile +dev repl
(use 'mywebapp)
(start)
```

## License

Copyright © 2014 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
