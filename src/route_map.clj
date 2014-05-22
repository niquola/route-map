(ns route-map
  (:require [clojure.string :as str]))

(defn to-url
  "build url from route and params"
  [route & [params]])


(defn pathify [path]
  (filterv #(not (str/blank? %))
           (str/split path #"/")))

(declare match-recur)

(defrecord Match [parents params match])

(defn match [[meth url] routes]
  (let [meth  (-> meth name .toUpperCase keyword)
        pth   (conj (pathify url) meth)
        match (->Match [] {} nil)
        matches (match-recur [] match pth routes)]
    (if (> (count matches) 1)
      (throw (Exception. "You routes is ubiquotus" (pr-str matches)))
      (first matches))))

(defn param? [x] (vector? x))

(defn match-recur [acc
                   {ps :parents
                    pr :params :as res}
                   [it & pth] node]
  (if-not it
    (conj acc (assoc res :match node))
    (if-let [next-node (get node it)]
      (match-recur acc
                   (update-in res [:parents] conj node)
                   pth next-node)
      (reduce (fn [acc [k next-node]]
                (if (param? k)
                  (match-recur acc
                               (merge res {:parents (conj ps node)
                                           :params  (assoc pr (first k) it)})
                               pth next-node)
                  acc))
              acc node))))
