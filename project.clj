(defproject mahout-vis "1.0.0-SNAPSHOT"
  :description "FIXME: write description"
  :jvm-opts ["-Xmx1g -server"] 
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [incanter "1.2.3"]
                 [org.apache.mahout/mahout-core "0.4"]
                 [org.apache.mahout/mahout-utils "0.4"]]
  :aot [mahout-vis.Visualization])                      
