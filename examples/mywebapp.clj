(ns mywebapp
  (:require
    [route-map :as rm]
    [hiccup.core :as hc]
    [ring.adapter.jetty :as raj]))

(defn layout [& content]
  (hc/html
    [:html
     [:head [:title "Mywebpap"]]
     [:body
      [:a {:href "/"} "Home ^"]
      [:p content]]]))


(defn render [& html]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (apply layout html)})

(defn url [& parts]
  (loop [url ""
         pth parts]
    (if (empty? pth)
      url
      (recur (str url "/" (first pth)) (rest pth)))))

(url "a" "b" "c" "d")

;; controllers
(defn dashboard [req]
  (render
    [:h1 "Dashboard"]
    [:p "Welcome to mywebapp"]
    [:p [:a {:href "/users"} "List users"]]
    [:h3 "Routes"]
    [:pre
     [:code (str routes)]]))

(def users (atom {"root" {:name "Root user"}}))
(defn list-users [req]
  (render
    [:h1 "Users"]
    [:ul
     (for [[idn u] @users]
       [:li
        [:a {:href (url "users" idn)} (str idn " " (:name u))]]) ]
    [:hr]
    [:a {:href "/users/new"} "Create"]))

(defn new-user-form [req]
  (render
    [:h1 "New User"]
    [:form {:action "/users" :method "POST"}
     [:label "login"] [:input {:name :login}]
     [:label "name"]  [:input {:name :name}]
     [:button "Create"]]))

(defn show-user [{{nm :name} :params :as req}]
  (let [usr (get @users nm)]
    (render
      [:h1 (str "User: " (:name usr))]
      [:a {:href (url "users" nm "profile")} "Profile"])))

(defn user-profile [{{nm :name} :params :as req}]
  (let [usr (get @users nm)]
    (render
      [:h1 (str "User Profile: " (:name usr))]
      [:a {:href (url "users" nm)} "Back to user"])))

(def routes
  {:get {:fn #'dashboard }
   "users" {:get    {:fn #'list-users }
            "new"   {:get {:fn #'new-user-form }}
            [:name] {:get {:fn #'show-user }
                     "profile" {:get {:fn #'user-profile }}}}} )

(defn handler [{meth :request-method uri :uri :as req}]
  (if-let [h (rm/match [meth uri] routes)]
    ((:fn h) (assoc req :params (:params h)))
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (str "No routes for " uri "\n " (pr-str req))}))


(def server (atom nil))
(defn start []
  (reset! server (raj/run-jetty #'handler {:port 3000 :join? false})))

(defn stop []
  (.stop @server))

