(defproject route-map "0.0.4"
  :description "clojure routing library, where route is written as hash-map"
  :url "https://github.com/niquola/route-map"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]]
  :repositories  [["clojars"  {:url "https://clojars.org/repo"
                               :sign-releases false}]]
  :profiles {:dev {:source-paths ["examples" "src"]
                   :dependencies [[ring "1.2.2"] ]}})

