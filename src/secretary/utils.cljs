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
   ["table" :as table]
   ["plist" :as plist]
   ["js-yaml" :rename {safeLoad load-yaml}]
   ["path" :as path]
   ["fs" :as fs]
   ["child_process" :as cp]))

(defn- prefix-home [p]
  (path/join js/process.env.HOME p))

(def root? (= "root" js/process.env.USER))

(def boot-service-folder
  "/Library/LaunchDaemons")

(def alluser-service-folder
  "/Library/LaunchAgents")

(def curr-user-service-folder
  (prefix-home "Library/LaunchAgents"))

(def service-definition-folders
  (map prefix-home [".secretary"]))

(def default-service-folders
  {:service-folders
   (concat [boot-service-folder alluser-service-folder]
           (if root? [] [curr-user-service-folder]))

   :service-definition-folders
   service-definition-folders})

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

(defn read-service-definition [^string filepath]
  (go-let [filename (path/basename filepath)]
    (->> (<n! fs -readFile filepath)
         .toString
         load-yaml
         ((fn [content]
            {:id (.-Label content)
             :path filepath
             :data content
             :plist (plist/build content)
             :plist-name (str (.-Label content) ".plist")
             :definition-name (path/basename filename (path/extname filename))
             :definition-path filepath})))))

(defn read-service-definitions [^string folder]
  (ro/map
   #(read-service-definition (path/join folder %))
   [(read-dir-files folder)]))

#_(async/take!
   (go-let [res (<<! (read-service-definitions (path/join js/process.env.HOME ".secretary-services")))]
     res)
   #(js/console.log (:content %)))

(defn read-service-file [^string filepath]
  (go-let [plist (.toString (<n! fs -readFile filepath))
           data (->> (plist/parse plist)
                     (#(js->clj % :keywordize-keys true)))]
    {:id (:Label data)
     :path filepath
     :data data
     :plist plist}))

(defn read-service-files [^string folder]
  (ro/map
   #(read-service-file (path/join folder %))
   [(read-dir-files folder)]))

#_(async/take!
   (async/into
    []
    (read-service-files (path/join js/process.env.HOME "/Library/LaunchAgents")))
   pr)

(defn get-service-infos
  ([] (get-service-infos default-service-folders))
  ([{:keys [service-folders service-definition-folders]}]
   (go-let [service-definition-chs
            (map read-service-definitions service-definition-folders)
            service-chs
            (map read-service-files service-folders)

            [service-definitions services]
            (<! (ro/map
                 vector
                 (map
                  #(async/into [] (async/merge %))
                  [service-definition-chs
                   service-chs])))]

     (map (fn [definition]
            (let [service-equal? #(when (= (:id definition) (:id %)) %)
                  service (some service-equal? services)
                  service-path (:path service)
                  user (condp #(%1 %&) service-path
                         #(nil? service-path)
                         nil

                         #(or (= "/Library/LaunchAgents" service-path)
                              (= "/Library/LaunchDaemons" service-path))
                         "all users"

                         #(re-find #"/Users/([^\/]+)" service-path)
                         :>>
                         (fn [[_ username]]
                           (or username "unknown"))

                         "unknown")
                  load-phase (cond
                               (nil? service-path) nil
                               (= "/Library/LaunchDaemons" service-path) "Boot"
                               :else "Login")]
              {:definition definition
               :service-info service
               :user user
               :load-phase load-phase}))
          service-definitions))))

(defn get-service-info
  ([name]
   (get-service-info default-service-folders name))
  ([folder-info name]
   (go (->> (<! (get-service-infos folder-info))
            (filter #(= name (:definition-name (:definition %))))
            first))))

(defn get-service-info! [& args]
  (go-let [service-info (<! (apply get-service-info args))]
    (if-not service-info
      (throw (ex-info "Service not found" {}))
      service-info)))

(defn writable? [folder]
  (let [chan (async/chan)]
    (fs/access
     folder
     fs/constants.W_OK
     #(async/put! chan (nil? %1)))
    chan))

(defn pr-services [service-infos & {:keys [plist]}]
  (let [table-header ["Name" "Status" "User" "LoadAt"]
        table-header (if plist (conj table-header "Plist") table-header)

        table-record (map (fn [{:keys [definition] :as info}]
                            (-> [(:definition-name definition)
                                 (if (:service-info info) "Enabled" "Disabled")
                                 (:user info)
                                 (:load-phase info)]
                                (#(if plist (conj % (:path (:service-info info))) %))))
                          service-infos)]
    (-> (.concat #js []
                 (into-array [(into-array table-header)])
                 (into-array (map into-array table-record)))
        (table/table #js {:border (table/getBorderCharacters "void")
                          :drawHorizontalLine (constantly false)})
        .trimEnd)))

(defn get-plist-folder [& {:keys [phase alluser]}]
  (go (cond
        (= phase "boot")
        (if (<! (writable? boot-service-folder))
          boot-service-folder
          (-> (str "Can not write file in path " boot-service-folder "\n"
                   "Maybe you should execute this command by sudo")
              (ac/error! {})))

        alluser
        (if (<! (writable? alluser-service-folder))
          alluser-service-folder
          (-> (str "Can not write file in path " alluser-service-folder "\n"
                   "Maybe you should execute this command by sudo")
              (ac/error! {})))

        root?
        (throw (ex-info "Can not enable service for root" {}))

        :else
        (if (<! (writable? curr-user-service-folder))
          curr-user-service-folder
          (throw (ex-info (str "Can not write file in path " curr-user-service-folder) {}))))))

(defn exec! [cmd & [args opts]]
  (let [[args opts] (if (map? args) [[] args] [args opts])]
    (if opts
      (cp/spawnSync cmd (into-array args) (clj->js opts))
      (cp/spawnSync cmd (into-array args)))))

(defn launchctl-exec! [& [args opts]]
  (let [[args opts] (if (map? args) [[] args] [args opts])
        res (exec! "launchctl" args (merge {:stdio "inherit"} opts))]
    (cond
      (.-error res)
      (throw (.-error res))

      (not= 0 (.-status res))
      (do (js/console.error (-> (exec! "launchctl" ["error" (.-status res)])
                                .-stdout
                                .toString
                                .trimEnd))
          (js/process.exit (.-status res)))

      :else
      res)))
