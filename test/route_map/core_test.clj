(ns route-map.core-test
  (:require [clojure.test :refer :all]
            [route-map.core :as rm]
            [clojure.string :as str]))
;; TODO
;; * nested params (full naming or fallback to id)
;; * dsl
;; * meta-info
;; * handler
;; * [:*]
;; * [:param #"regexp"]

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

  (is (= (get-desc [:get "users/active"]) "Filtering users"))
  (is (= (get-desc [:get "/"]) "Root"))
  (is (= (get-desc [:post "users/5/activate"]) "Activate"))

  (is (= (get-params [:post "users/5/activate"]) {:user-id "5"}))

  (is (=
       (count
         (:parents
           (rm/match [:post "users/5/activate"] routes)))
       4))
  (is (= (mapv :.filters (:parents (rm/match [:get "posts/1"] routes)))
         [nil [:user-required] nil]))

  (is (= (get-params [:get "sites/blog/imgs/logo.png"]) {:site "blog"
                                                         :path* ["imgs" "logo.png"]}))
  )

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
  (is (= 'user-view (f-match "/admin/users/5")))
  (is (= 'groups-list-view (f-match "/admin/groups")))
  (is (= {:id "5"} (:params (rm/match "/admin/users/5" frontend-routes)))))

(deftest not-map-test
  (is (nil? (rm/match "/test/unexisting" {"test" :test}))))

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

(defn match-ids [k]
  (when (re-matches #".*,.*" k)
    {:ids (str/split k #",")}))

(match-ids "1")
(match-ids "1,2")

(def fn-params-routes
  {"user" {[:id] {:GET 'user}
           [match-ids] {:GET 'specific}}})

(defn fn-match [url]
  (rm/match url fn-params-routes))

(deftest frontend-routes-test
  (is (= 'user (:match (fn-match [:get "/user/1"]))))
  (is (= {:id "1"} (:params (fn-match [:get "/user/1"]))))

  (is (= 'specific (:match (fn-match [:get "/user/1,2"]))))
  (is (= {:ids ["1", "2"]} (:params (fn-match [:get "/user/1,2"])))))
