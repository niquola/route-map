(ns route-map-test
  (:require [clojure.test :refer :all]
            [route-map :as rm]))
;; TODO
;; * url helper
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
  {:.roles   #{:admin}
   GET       {:.desc "List users"}
   POST      {:.desc "Create user" :.roles #{:admin}}
   "active"   {GET {:.desc "Filtering users"}}

   [:user-id] {GET       {:.desc "Show user"}
               POST      {:.desc "Update user"}
               "activate" {POST {:.desc "Activate"}}}})

(def routes
  {GET    {:.desc "Root"}
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

(time
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
             :GET {:fn 'special :guard guard}
             }})

(defn match-specific [meth path]
  (get-in (rm/match [meth path] routes-specific) [:match :fn]))

(deftest specila-test
  (is (= (match-specific :get "/special") 'special))
  (is (= (match-specific :get "/special/action") 'action)))
