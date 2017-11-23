(ns route-map.core-test
  (:require [clojure.test :refer :all]
            [route-map.core :as rm]
            [matcho.core :as matcho]
            [clojure.string :as str]))

(deftest test-pathify
  (is (rm/pathify "/") [])
  (is (rm/pathify "/users/") ["users"])
  (is (rm/pathify "users/") ["users"])
  (is (rm/pathify "users") ["users"])
  (is (rm/pathify "users/1/show") ["users" "1" "show"]))


;; DSL example
(defn resource [nm & [chld]]
  "DSL example"
  {nm {:GET {:fn 'index}
       :POST {:fn 'create}
       [(keyword (str nm "_id"))]
       (merge
         {:GET {:fn 'show}
          :PUT {:fn 'update}
          :DELETE {:fn 'delete}} (or chld {})) }})


(def meta-routes
  {:GET ^{:fls [1]} 'root })

(def GET :GET)
(def POST :POST)
(def PUT :PUT)

(def user-routes
  {:.name :users
   :.roles   #{:admin}
   GET       {:.desc "List users"}
   POST      {:.desc "Create user" :.roles #{:admin}}
   "active"   {:.name :active-users
               GET {:.desc "Filtering users"}}

   [:user-id] {:.name :user
               GET       {:.desc "Show user"}
               POST      {:.desc "Update user"}
               "activate" {:.name :activate-user
                           POST {:.desc "Activate"}}}})

(def routes
  {:.name :root
   GET    {:.desc "Root"}
   "posts" {:.roles   #{:author :admin}
            :.filters [:user-required]
            GET       {:.desc "List posts"}
            POST      {:.desc "Create post"}
            [:post-id] {GET       {:.desc "Show post"}
                        POST      {:.desc "Update post"}
                        "publish"  {POST {:.desc "Publish post"}}
                        "comments" {GET  {:.desc "comments"}
                                    [:comment-id] {GET {:.desc "show comment"}
                                                   PUT {:.desc "update comment"}}}}}
   "sites" {[:site]  {[:path*] {GET {:.desc "Glob files"}}}}
   "users" #'user-routes})

(defn- get-desc [path]
  (:.desc
    (:match
      (rm/match path routes))))

(defn- get-params [path]
  (:params
    (rm/match path routes)))

(rm/match [:get "users/1"] routes)

#_(time
  (doseq [x (take 100000 (range))]
    (rm/match [:post (str "/users/" x "/activate")] routes)))

(deftest match-routes
  (is (= (rm/match [:post "/"] routes)
         nil))

  (is (= (rm/match [:get "some/unexistiong/route"] routes)
         nil))

  (matcho/match
   (rm/match [:get "users/active"] routes)
   {:match {:.desc "Filtering users"}
    :parents #(= 3 (count %))})

  (matcho/match
   (rm/match [:get "/users/active"] routes)
   {:match {:.desc "Filtering users"}
    :parents #(= 3 (count %))})

  (matcho/match
   (rm/match [:get "/"] routes)
   {:match {:.desc "Root"}})

  (matcho/match
   (rm/match [:post "users/5/activate"] routes)
   {:match {:.desc "Activate"}
    :params {:user-id "5"}
    :parents #(= 4 (count %))})


  (is (= (mapv :.filters (:parents (rm/match [:get "posts/1"] routes)))
         [nil [:user-required] nil]))

  (matcho/match (rm/match [:get "sites/blog/imgs/logo.png"] routes)
                {:params {:site "blog"
                          :path* ["imgs" "logo.png"]}}))

(def routes-2
  {"metadata" {:GET {:fn '=metadata}}
   [:type] {:POST {:fn '=create}
            [:id] {:GET   {:fn '=read}
                   :DELETE  {:fn '=delete} }}})


(deftest empty-root-test
  (is (= (rm/match [:get "/"] routes-2)
         nil)))

(defn guard [x]
  (= "special" (get-in x [:param :params])))

(def routes-specific
  {[:param] {"action" {:GET {:fn 'action }}
             :GET {:fn 'special :guard guard}}})

(defn match-specific [meth path]
  (get-in (rm/match [meth path] routes-specific) [:match :fn]))

(deftest specila-test
  (is (= (match-specific :get "/special") 'special))
  (is (= (match-specific :get "/special/action") 'action)))

(def frontend-routes
  {"admin" {"users" {:. 'users-list-view
                     [:id] 'user-view}
            "groups" 'groups-list-view}})

(defn f-match [url]
  (:match (rm/match url frontend-routes)))

(deftest frontend-routes-test
  (is (= 'users-list-view (f-match "/admin/users")))

  (matcho/match
   (rm/match "/admin/users/5" frontend-routes)
   {:match 'user-view
    :params {:id "5"}})

  (is (= 'groups-list-view (f-match "/admin/groups")))
  )

(deftest not-map-test
  (is (nil? (rm/match "/test/unexisting" {"test" :test}))))


(defn match-ids [k]
  (when (re-matches #".*,.*" k)
    {:ids (str/split k #",")}))

(match-ids "1")

(match-ids "1,2")

(def fn-params-routes
  {"user" {[:id] {:GET 'user}
           [match-ids] {:GET 'specific}}})

(deftest frontend-routes-test
  (matcho/match
   (rm/match [:get "/user/1"] fn-params-routes)
   {:match 'user
    :params {:id "1"}})

  (matcho/match
   (rm/match [:get "/user/1,2"] fn-params-routes)
   {:match 'specific
    :params {:ids ["1", "2"]}}))

(deftest no-method-glob-test
  (let [routes {"page" {[:bits*] 'bits}}]
    (matcho/match
     (rm/match "/page/test" routes)
     {:params {:bits* ["test"]}
      :match 'bits})

    (matcho/match
     (rm/match "/page/test/a/b/c" routes)
     {:params {:bits* ["test" "a" "b" "c"]}
      :match 'bits}))

  (let [routes {"page" {[:bits*] {:GET 'get-bits
                           :POST 'post-bits}}}]

    (matcho/match
     (rm/match [:get "/page/test"] routes)
     {:params {:bits* ["test"]}})

    (matcho/match
     (rm/match [:get "/page/test/a/b/c"] routes)
     {:params {:bits* ["test" "a" "b" "c"]}})

    (matcho/match
     (rm/match [:get "/page/test"] routes)
     {:match 'get-bits})

    (matcho/match
     (rm/match [:post "/page/test"] routes)
     {:match 'post-bits
      :params {:bits* ["test"]}})))

(def multi-rs
  {[:resource-type] {:GET :list
                     [:id] {:GET :find
                            :PUT :update}}
   "AidboxJob" {[:id] {"$run" {:POST :post}}}

   "Appointment" {"$op" {:POST :op
                         :GET  :op}
                  [:id] {"$sub" {:GET :sub}}}})

(deftest multi-routes

  (rm/match [:post "/AidboxJob/3/$run"] multi-rs)
  (rm/match [:get "/AidboxJob"] multi-rs)


  (matcho/match
   (rm/match [:get "/Patient/1"] multi-rs)
   {:match :find
    :params {:resource-type "Patient"
             :id "1"}})

  (matcho/match
   (rm/match [:get "/Appointment/1"] multi-rs)
   {:match :find
    :params {:resource-type "Appointment"
             :id "1"}})

  (matcho/match
   (rm/match [:get "/Appointment/1"] multi-rs)
   {:match :find
    :params {:resource-type "Appointment"
             :id "1"}})

  (matcho/match
   (rm/match [:put "/Appointment/1"] multi-rs)
   {:match :update
    :params {:resource-type "Appointment"
             :id "1"}})

  (matcho/match
   (rm/match [:get "/Appointment/$op"] multi-rs)
   {:match :op})

  (matcho/match
   (rm/match [:get "/Appointment/5/$sub"] multi-rs)
   {:match :sub}))



;; TODO
;; * nested params (full naming or fallback to id)
;; * dsl
;; * meta-info
;; * handler
;; * [:*]
;; * [:param #"regexp"]

(deftest url-test
  (is (= (rm/url routes :root) "/"))
  (is (= (rm/url routes :not-exists) nil))
  (is (= (rm/url routes :posts) "/posts"))
  (is (= (rm/url routes :posts [42]) "/posts/42"))
  (is (= (rm/url routes :posts {:post-id 42}) "/posts/42"))
  (is (= (rm/url routes :posts-comments [42 24]) "/posts/42/comments/24"))
  (is (= (rm/url routes :posts-comments {:comment-id 24 :post-id 42}) "/posts/42/comments/24"))
  (is (= (rm/url routes :active-users) "/users/active"))
  (is (= (rm/url routes :activate-user [111]) "/users/111/activate"))
  (is (= (rm/url routes :activate-user {:user-id 111}) "/users/111/activate")))

