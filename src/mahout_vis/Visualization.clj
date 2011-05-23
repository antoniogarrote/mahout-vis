(ns mahout-vis.Visualization
  (:use [mahout-vis.core])
  (:gen-class
   :methods [#^{:static true} [plot [String String "[Ljava.lang.Object;" "[Ljava.lang.Object;" java.util.HashMap] "[Lorg.jfree.chart.JFreeChart;"]
             #^{:static true} [start [String] void]]))

(defn -start
  ([conf-file]
     (bootstrap! conf-file)))

(defn -plot
  ([clusters-file points-file dims-x dims-y labels]
     (let [results (clustering-algorithm-output clusters-file points-file)
           plots (compute-comps results (vec dims-x) (vec dims-y) {:display-centroids true :labels labels})]
       (into-array (vec plots)))))
