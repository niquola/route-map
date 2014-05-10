(ns route-map
  (:require [clojure.string :as str]))

(defn to-url
  "build url from route and params"
  [route & [params]])


(defn pathify [path]
  (filterv #(not (str/blank? %))
           (str/split path #"/")))

(declare match-recur)
(defn match [[meth url] routes]
  (let [pth     (pathify url)
        matches (match-recur [] {:parents [] :params {}} (conj pth meth) routes)]
    (if (> (count matches) 1)
      (throw (Exception. "You routes is ubiquotus" (pr-str matches)))
      (first matches))))

(defn match-recur [acc {ps :parents pr :params :as res} [it & pth] node]
  (if-not it
    (conj acc (merge node res))
    (if-let [next-node (get node it)]
      (match-recur acc
                   (update-in res [:parents] conj (or (:attrs node) {}))
                   pth next-node)
      (reduce (fn [acc [k next-node]]
                (if (vector? k)
                  (match-recur acc
                               (merge res {:parents (conj ps (or (:attrs node) {}))
                                           :params  (assoc pr (first k) it)})
                               pth next-node)
                  acc))
              acc node))))
