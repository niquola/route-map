(ns route-map.core
  (:require [clojure.string :as str]))

(defn pathify [path]
  (filterv #(not (str/blank? %)) (str/split path #"/")))

(defn is-glob? [k] (str/ends-with? (name k) "*"))

(defn- get-params [node]
  (when (map? node)
    (filter (fn [[k v]] (vector? k)) node)))

(defn- get-param [node]
  (first (filter (fn [[k v]] (vector? k)) node)))

(defn fn-param? [k]
  (and (vector? k)
       (let [f (first k)]
         (and (not (keyword? f)) (fn? f) ))))

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

(defn -match [acc node [x & rpth :as pth] params parents wgt]
  (if (empty? pth)
    (if node
      (if (and (map? node) (contains? node :.))
        (conj acc {:parents (conj parents (assoc node :params params)) :match (:. node) :w wgt :params params})
        (conj acc {:parents parents :match node :w wgt :params params}))
      acc)
    (let [node (if (var? node) (deref node) node)
          pnode (and (map? node) (assoc node :params params))
          acc (if-let [branch (get node x)] (-match acc branch rpth params (conj parents pnode) (+ wgt 10)) acc)
          acc (if (keyword? x)
                acc
                (reduce (fn [acc [[k] branch]]
                          (if (fn? k)
                            (if-let [[fparams branch] (match-fn-params node x)]
                              (-match acc branch rpth (merge params fparams) parents (+ wgt 10))
                              acc)
                            (if (is-glob? k)
                              (if (keyword? (last pth)) ;; false cljs :. case 
                                (-match acc branch [(last pth)] (assoc params k (into [] (butlast pth)))  (conj parents pnode) (inc wgt))
                                (-match acc branch [] (assoc params k (into [] pth)) (conj parents pnode) (inc wgt)))
                              (cond
                                (when-let [opts (:route-map/enum branch)]
                                  (set? opts))

                                (let [opts (:route-map/enum branch)]
                                  (if (contains? opts x)
                                    (-match acc branch rpth (assoc params k x) (conj parents pnode) (+ wgt 5))
                                    acc))

                                (when-let [opts (:route-map/regexp branch)]
                                  (and (= (type opts) java.util.regex.Pattern)))

                                (let [opts (:route-map/regexp branch)]
                                  (if (re-find opts x)
                                    (-match acc branch rpth (assoc params k x) (conj parents pnode) (+ wgt 4))
                                    acc))

                                :else
                                (-match acc branch rpth (assoc params k x) (conj parents pnode) (+ wgt 2)))))
                          ) acc (get-params node)))]
      acc)))

(defn match
  "path [:get \"/your/path\"] or just \"/your/path\""
  [path routes]
  (let [path (if (vector? path)
               (let [[meth url] path]
                 (conj (pathify url) (-> meth name str/upper-case keyword)))
               (pathify path))
        result (-match  [] routes path {} [] 0)]
    (->> result
         (sort-by :w)
         last)))

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
