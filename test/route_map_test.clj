(ns route-map-test
  (:require [clojure.test :refer :all]
            [route-map :as rm]))

(deftest test-pathify
  (is (rm/pathify "/") [])
  (is (rm/pathify "/users/") ["users"])
  (is (rm/pathify "users/") ["users"])
  (is (rm/pathify "users") ["users"])
  (is (rm/pathify "users/1/show") ["users" "1" "show"]))

(def routes
  {:attrs  {:filters [0]}
   :get    {:filter [1] :desc "Root"}
   "posts" {:attrs  {:roles #{:*}}
            :get    {:desc "List posts"}
            :post   {:desc "Create post"}
            [:id]   {:get       {:desc "Show post"}
                     :post      {:desc "Update post"}
                     "publish"  {:post {:desc "Publish post"}}}}
   "users" {:attrs {:roles #{:admin}}
            :get       {:desc "List users"}
            :post      {:desc "Create user" :roles #{:admin}}
            "active"   {:get {:desc "Filtering users"}}

            [:id]   {:get       {:desc "Show user"}
                     :post      {:desc "Update user"}
                     "activate" {:post {:desc "Activate"}}}}})



(deftest test-match
  (is (= (rm/match [:get "some/unexistiong/route"] routes)
         nil))
  (is (=
       (:desc
         (rm/match [:get "users/active"] routes))
       "Filtering users"))

  (is (=
       (:desc
         (rm/match [:get "/"] routes))
       "Root"))

  (is (=
       (:desc
         (rm/match [:post "users/5/activate"] routes))
       "Activate"))

  (is (=
       (:params
         (rm/match [:post "users/5/activate"] routes))
       {:id "5"}))

  (is (=
       (count
         (:parents
           (rm/match [:post "users/5/activate"] routes)))
       4)))

(run-tests)
