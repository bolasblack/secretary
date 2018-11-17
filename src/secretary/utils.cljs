(ns secretary.utils
  (:require-macros
   [adjutant.core :refer [def-]]
   [rxcljs.core :refer [go go-let <! >!]]
   [rxcljs.transformers :refer [<p! <n! <<!]])
  (:require
   [cljs.core.async :as async]
   [adjutant.core :as ac]
   [rxcljs.operators :as ro]
   [rxcljs.transformers :as rt]
   ["plist" :as plist]
   ["js-yaml" :rename {safeLoad load-yaml}]
   ["path" :as path]
   ["fs" :as fs]))

(defn read-dir-files [^string folder]
  (let [chan (async/chan)]
    (go-let [files (->> (<n! fs -readdir folder #js {:withFileTypes true})
                        (apply vector)
                        (filter #(.isFile %))
                        (map #(.-name %)))]
      (doseq [file files]
        (>! chan file))
      (async/close! chan))
    chan))

#_(async/take!
   (read-dir-files (path/join js/process.env.HOME ".secretary-services"))
   js/console.log)

(defn read-service-definitions [^string folder]
  (ro/map
   (fn [^string filename]
     (go-let [filepath (path/join folder filename)]
       (->> (<n! fs -readFile filepath)
            .toString
            load-yaml
            ((fn [content]
               {:id (.-Label content)
                :path filepath
                :data content
                :plist (plist/build content)
                :definition-name (path/basename filename (path/extname filename))
                :definition-path filepath})))))
   [(read-dir-files folder)]))

#_(async/take!
   (go-let [res (<<! (read-service-definitions (path/join js/process.env.HOME ".secretary-services")))]
     res)
   #(js/console.log (:content %)))

(defn read-service-files [^string folder]
  (ro/map
   (fn [^string filename]
     (go-let [filepath (path/join folder filename)
              plist (.toString (<n! fs -readFile filepath))
              data (->> (plist/parse plist)
                        (#(js->clj % :keywordize-keys true)))]
       {:id (:Label data)
        :path filepath
        :data data
        :plist plist}))
   [(read-dir-files folder)]))

#_(async/take!
   (async/into
    []
    (read-service-files (path/join js/process.env.HOME "/Library/LaunchAgents")))
   pr)
