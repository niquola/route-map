(ns mywebapp
  (:require
    [route-map :as rm]
    [ring.adapter.jetty :as raj]))

(def user-routes
  {:interceptors ['ensure-admin]
   :GET  'list
   :POST 'create
   [:uid] {:interceptors ['ensure-user]
           :GET 'show
           :PUT 'udpate
           :DELETE 'destroy}})

(def file-routes
  {:GET  'list
   :POST 'create
   [:path*] {:GET 'list}})


(def routes
  {:interceptors ['ensure-logged-in]
   :GET 'root
   "files" #'file-routes
   "users" #'user-routes})

(defn wrap-not-found [h]
  (fn [{rm :route-match :as req}]
    (if rm (h req)
        {:body (str "Ups, no route for " (:uri req))
         :status 404
         :headers {"Content-Type" "text/html"}})))

(defn handler [{params :params rm :route-match :as req}]
  (let [interceptors (mapcat :interceptors (:parents rm))
        handler (:match rm)
        params (merge params (:params rm))]
    {:body (str "Interceptors:" (into [] interceptors) "; Handler <" handler ">; params: " (pr-str params))
     :status 200
     :headers {"Content-Type" "text/html"}}))

(def app
  (-> handler
      (wrap-not-found)
      (rm/wrap-route-map routes)))

(def server (atom nil))

(defn start []
  (reset! server (raj/run-jetty #'app {:port 3003 :join? false})))

(defn stop [] (.stop @server))

(comment
  (start)
  (stop))
