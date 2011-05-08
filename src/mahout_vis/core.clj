(ns mahout-vis.core
  (:use [incanter core charts])
  (:import [org.apache.hadoop.conf Configuration] 
           [org.apache.hadoop.fs FileSystem Path]
           [org.apache.hadoop.io Text Writable] 
           [org.apache.mahout.clustering Cluster]
           [java.awt.geom Ellipse2D$Double]
           [org.jfree.chart.annotations XYShapeAnnotation XYTextAnnotation]))


;; Configuration

(def *fs* nil)
(def *conf* nil)

(defn path
  ([path-str] (Path. path-str)))

(defn configuration
  ([] (Configuration.))
  ([path-str & others]
     (let [conf (Configuration.)]
       (.addResource conf (path path-str))
       (loop [pstr others]
         (if (empty? others)
           conf
           (do (.addResource conf (path (first pstr)))
               (recur (rest pstr))))))))

(defn file-system
  ([] (FileSystem/get *conf*)))

(defn set-file-system!
  ([]
     (let [fs (file-system)]
       (alter-var-root #'*fs* (constantly fs)))))

(defn set-config!
  ([] (let [conf (configuration)]
        (alter-var-root #'*conf* (constantly conf))))
  ([& paths] (let [conf (apply configuration paths)]
               (alter-var-root #'*conf* (constantly conf)))))

(defn bootstrap!
  ([] (do (set-config!)
          (set-file-system!)
          :ok))
  ([& config-files]
     (do (apply set-config! config-files)
         (set-file-system!)
         :ok)))

(defn ls
  ([path-str] (vec (.listStatus *fs* (path path-str)))))

(defn paths
  ([files] (map (fn [f] (.getPath f)) files)))

;; HDFS IO

(defn seq-file-reader
  ([path-str]
     (org.apache.hadoop.io.SequenceFile$Reader. *fs* (path path-str) *conf*)))

(defn pairs
  ([seq-file-reader]
     (let [key (.newInstance (.asSubclass (.getKeyClass seq-file-reader) Writable))
           val (.newInstance (.asSubclass (.getValueClass seq-file-reader) Writable))]
       (let [exists (.next seq-file-reader key val)]
         (if exists
           (lazy-seq (cons [key val] (pairs seq-file-reader)))
           nil)))))

(defn vector-to-seq
  ([v] (let [elems (iterator-seq (.iterator v))]
         (map (fn [elem] (.get elem)) elems))))

(defn key-to-int
  ([k] (.get k)))

(defn pair-to-clustered-vector
  ([[k v]] {:cluster (key-to-int k)
            :components (vector-to-seq (.getVector v))}))

;; Cluster & vectors

(defn cluster-id
  ([c] (.getId c)))

(defn cluster-center
  ([c] (vector-to-seq (.getCenter c))))

(defn cluster-radius
  ([c] (vector-to-seq (.getRadius c))))


(defn pair-to-cluster
  ([[k c]] {:id (cluster-id c)
            :center (cluster-center c)
            :radius (cluster-radius c)}))

(defn k-means-output
  ([clusters-data clustered-points-data]
     (let [clusters (seq-file-reader clusters-data)
           clustered-points (seq-file-reader clustered-points-data)]
       {:clusters (map pair-to-cluster (pairs clusters))
        :points   (map pair-to-clustered-vector (pairs clustered-points))})))

(defn project-vectors
  ([clustering-output indices]
     (map (fn [point]
            {:components (map (fn [index]  (nth (:components point) index)) indices)
             :cluster (:cluster point)})
          (:points clustering-output))))

;; visualization

(defn visualize-plot [plot]
  "Prepare a plot to be displayed"
  (do (clear-background plot)
      (view plot)
      plot))

(defn visualize-plots [plots]
  (for [plot plots] (visualize-plot plot)))

(defn fold-points
  ([clustering-output x-comp y-comp]
     (let [projected-vectors (project-vectors clustering-output [x-comp y-comp])
           grouped-vectors (group-by :cluster projected-vectors)
           grouped-vectors (reduce (fn [ac [k vs]] (assoc ac k (map (fn [v] (:components v)) vs)))
                                   {} grouped-vectors)]
       grouped-vectors)))


(defn compute-scatter-plot
  ([fold-points] (compute-scatter-plot fold-points {}))
  ([folded-points opts]
     (loop [plot nil
            ks (keys folded-points)]
       (if (empty? ks)
         plot
         (let [this-vals (get folded-points (first ks))
               this-vals-0 (map first this-vals)
               this-vals-1 (map second this-vals)
               x-label (:x-label opts "")
               y-label (:y-label opts "")
               the-plot (if (nil? plot)
                          (scatter-plot this-vals-0
                                        this-vals-1
                                        :x-label x-label
                                        :y-label y-label
                                        :series-label (first ks)
                                        :legend true)
                          (do (add-points plot this-vals-0 this-vals-1 :series-label (first ks))
                              plot))]
           (recur the-plot (rest ks)))))))

(defn draw-centroid
  ([plot centroid x y]
     (let [[x y] [ (nth (:center centroid) x) (nth (:center centroid) y)]
           [w h] [ (nth (:radius centroid) x) (nth (:radius centroid) y)]]
       (.addAnnotation (.getPlot plot) (XYShapeAnnotation. (Ellipse2D$Double. (- x w) (- y h)  (* 2 w) (* 2 h))))
       (.addAnnotation (.getPlot plot) (XYTextAnnotation. (str (:id centroid)) x y)))))

(defn draw-centroids
  ([clustering-output plot x y]
     (doseq [centroid (:clusters clustering-output)]
       (draw-centroid plot centroid x y))))

(defn compute-comps
  ([clustering-output xs ys] (compute-comps clustering-output xs ys {}))
  ([clustering-output xs ys opts]
     (for [x xs y ys]
       (let [x-label (get (:labels opts {}) x (str x))
             y-label (get (:labels opts {}) y (str y))]
         (let [plot (compute-scatter-plot (fold-points clustering-output x y) {:x-label x-label :y-label y-label})]
           (when (:display-centroids opts)
             (draw-centroids clustering-output plot x y))
           plot)))))
