(ns route-map
  (:require [clojure.string :as str]
            [clojure.zip :as zip]))

(defn to-url
  "build url from route and params"
  [route & [params]])

(defn pathify [path]
  (filterv #(not (str/blank? %)) (str/split path #"/")))

(defrecord Match [parents params match])

(defn is-glob? [k] (.endsWith (name k) "*"))

(defn- get-param [node]
  (first (filter (fn [[k v]] (vector? k)) node)))

;; TODO: add rs validation
(defn -match [rs pth]
  (loop [acc (->Match [] {} nil) ;; {:parents [] :params {}}
         [x & rpth :as pth] pth
         node rs]
    (if (empty? pth)
      ;; path end  find or not
      (if node (assoc acc :match node) nil)
      ;; attempt to get by get
      (if-let [nnode (get node x)] 
        (recur (update-in acc [:parents] conj node) rpth nnode)
        ;; looking for params
        (when-let [[[k] nnode] (and (not (keyword? x)) (get-param node))]
          (let [acc (update-in acc [:parents] conj node)]
            ;; if glob then eat the path
            (if (is-glob? k)
              (recur (update-in acc [:params] assoc k (into [] (butlast pth))) [(last pth)] nnode)
              (recur (update-in acc [:params] assoc k x) rpth nnode))))))))

(defn match [[meth url] routes]
  (-match routes
          (conj (pathify url)
                (-> meth name .toUpperCase keyword))))



(comment
  (def routes
    {"users" {:get 'users
              :post 'users
              [:id] {:get 'show}}
     "site" {[:name] {[:path*] {:get 'site-file}}}
     "files" {[:path*] {:get 'show-file}}
     [:type] {:get 'abstract}})

  (-match routes [:get])

  (-match routes ["users" 5 :get])
  (-match routes ["users" 5 :get])
  (-match routes ["files" "x" "y" "z" :get])
  (-match routes ["site" "blog" "x" "y" "z" :get]))
