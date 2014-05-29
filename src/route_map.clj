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
        pth   (pathify url)
        match (->Match [] {} nil)
        matches (match-recur [] match meth pth routes)]
    (if (> (count matches) 1)
      (throw (Exception. "You routes is ubiquotus" (pr-str matches)))
      (first matches))))

(defn param? [x] (vector? x))

(defn match-recur [acc
                   {ps :parents
                    pr :params :as res}
                   meth
                   [it & pth] node]
  (let [new-parents (conj ps node)]
    (cond
      (and (nil? it) (contains? node meth)) (conj acc (merge res {:match (meth node)
                            :parents new-parents}))
      (contains? node it) (match-recur
                            acc
                            (assoc res :parents new-parents)
                            meth pth (get node it))
      (not (nil? it)) (reduce (fn [acc [k next-node]]
                                (if (param? k)
                                  (match-recur acc
                                               (merge res {:parents new-parents
                                                           :params (assoc pr (first k) it)})
                                               meth pth next-node)
                                  acc))
                              acc node)
      :else acc)))
