(ns route-map.core
  (:require [clojure.string :as str]))

(defn pathify [path]
  (filterv #(not (str/blank? %)) (str/split path #"/")))

(defrecord Match [parents params match])

#?(:clj (defn is-glob? [k] (.endsWith (name k) "*")))
#?(:cljs (defn is-glob? [k] (let [s (name k)]
                              (= (.indexOf s "*")
                                 (- (.-length s) 1)))))

(defn- get-param [node]
  (first (filter (fn [[k v]] (vector? k)) node)))

(defn fn-param? [k]
  (and (vector? k)
       (let [f (first k)]
         (and (fn? f) (not (keyword? f))))))

(defn match-fn-params [node x]
  (when (map? node)
    (->> node
         (filter (fn [[k v]] (fn-param? k)))
         (reduce (fn  [acc [k v]]
                   (if-let [params ((first k) x)]
                     (conj acc [params v])
                     acc))
                 [])
         first)))

;; TODO: add rs validation
(defn -match [rs pth]
  (loop [acc (->Match [] {} nil) ;; {:parents [] :params {}}
         [x & rpth :as pth] pth
         node rs]
    ;; support var as node
    (if (empty? pth)
      ;; path end  find or not
      (when node
        ;; found
        (if (and (map? node) (contains? node :.))
          (-> (update-in acc [:parents] conj node)
              (assoc :match (:. node)))
          (assoc acc :match node)))
      ;; attempt to get by get
      ;; deref vars
      (let [node (if (var? node) (deref node) node)]
        (if-let [branch (get node x)]
         (recur (update-in acc [:parents] conj node) rpth branch)
         (if-let [[fparams branch] (match-fn-params node x)]
           (recur (update-in acc [:params] merge fparams) rpth branch)
           ;; looking for params
           (when-let [[[k] branch] (and (not (keyword? x))
                                       (map? node)
                                       (get-param node))]
             (let [acc (update-in acc [:parents] conj node)]
               ;; if glob then eat the path
               (if (is-glob? k)
                 (if (keyword? (last pth))
                   (recur (update-in acc [:params] assoc k (into [] (butlast pth))) [(last pth)] branch)
                   (recur (update-in acc [:params] assoc k (into [] pth)) [] branch))
                 (recur (update-in acc [:params] assoc k x) rpth branch))))))))))


(defn match [path routes]
  (if (vector? path)
    (let [[meth url] path]
      (-match routes
              (conj (pathify url)
                    (-> meth name str/upper-case keyword))))
    (-match routes (pathify path))))

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

(defn- first-not-nil [coll]
  (let [not-nils (filter #(not= nil %) coll)
        all-nils (nil? not-nils)]
    (if all-nils 
      nil
      (first not-nils))))

(defn- get-static-paths [routes]
  (map #(first %)
       (filter #(let [[k _] %]
                  (string? k))
               routes)))

(defn- get-ways [routes]
  (let [params (first (get-param routes))
        static-paths (get-static-paths routes)]
  (filter #(not= nil %) (concat params static-paths))))

(defn- find-url [routes name auto-name params path]
  (let [path-found (or (= name (:.name routes))
                       (and (= name (keyword auto-name))
                            (= 0 (count params))))]
    (if path-found
      (if (= "" path) "/" path)
      (first-not-nil (map #(let [[next-path
                                  next-params
                                  next-routes
                                  next-auto-name] (cond
                                                    (string? %) [%
                                                                 params
                                                                 (get routes %)
                                                                 (if (= "" auto-name)
                                                                   %
                                                                   (str auto-name "-" %))]
                                                    (keyword? %) (if (map? params)
                                                                   [(get params %)
                                                                    (dissoc params %)
                                                                    (if (get params %)
                                                                      (get routes [%])
                                                                      nil)
                                                                    auto-name]
                                                                   [(first params)
                                                                    (rest params)
                                                                    (get routes [%])
                                                                    auto-name]))]
                             (find-url (if (var? next-routes)
                                         (deref next-routes)
                                         next-routes)
                                       name
                                       next-auto-name
                                       next-params
                                       (str path "/" next-path)))
                          (get-ways routes))))))

(defn url
  ([routes name]
   (url routes name []))
  ([routes name params]
   (find-url routes name "" params "")))
