(ns route-map.core
  (:require [clojure.string :as str]))

(defn to-url
  "build url from route and params"
  [route & [params]])

(defn pathify [path]
  (filterv #(not (str/blank? %)) (str/split path #"/")))

(defrecord Match [parents params match])

#?(:clj (defn is-glob? [k] (.endsWith (name k) "*")))
#?(:cljs (defn is-glob? [k] (let [s (name k)]
                              (= (.indexOf s "*")
                                 (- (.length s) 1)))))

(defn- get-param [node]
  (first (filter (fn [[k v]] (vector? k)) node)))

;; TODO: add rs validation
(defn -match [rs pth]
  (loop [acc (->Match [] {} nil) ;; {:parents [] :params {}}
         [x & rpth :as pth] pth
         node rs]
    ;; support var as node
    (let [node (if (var? node) (deref node) node)]
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
                (recur (update-in acc [:params] assoc k x) rpth nnode)))))))))

(defn match [[meth url] routes]
  (-match routes
          (conj (pathify url)
                (-> meth name str/upper-case keyword))))

(defn wrap-route-map [h routes]
  "search appropriate route in routes
   and put match under :route-match
   route match contains
     :parents - parent nodes to matched node
     :params - params collected from route
     :match - matched node in route map
  "
  (fn [{meth :request-method uri :uri :as req}]
    (if-let [match (match [meth uri] routes)]
      (h (assoc req :route-match match))
      (h req))))
