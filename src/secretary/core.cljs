(ns secretary.core
  (:require-macros
   [adjutant.core :refer [def-]]
   [rxcljs.core :refer [go go-let <! >!]]
   [rxcljs.transformers :refer [<p! <n! <<!]])
  (:require
   ["path" :as path]
   ["table" :as table]
   [cljs.core.async :as async]
   [adjutant.core :as ac]
   [rxcljs.core :as rc :include-macros true]
   [rxcljs.operators :as ro]
   [rxcljs.transformers :as rt]
   [secretary.utils :as utils]))

(defn- prefix-home [p]
  (path/join js/process.env.HOME p))

(def- root-service-folders
  ["/Library/LaunchAgents"
   "/Library/LaunchDaemons"])

(def- user-service-folders
  (map prefix-home ["Library/LaunchAgents"]))

(def- user-service-definition-folder
  (map prefix-home [".secretary"]))

(defn- get-services-info []
  (go-let [user-service-definition-chs
           (map utils/read-service-definitions user-service-definition-folder)
           user-service-chs
           (map utils/read-service-files user-service-folders)
           root-service-chs
           (map utils/read-service-files root-service-folders)

           [user-service-definitions
            user-services
            root-services]
           (<! (ro/map
                vector
                (map
                 #(async/into [] %)
                 [(ro/map conj user-service-definition-chs)
                  (ro/map conj user-service-chs)
                  (ro/map conj root-service-chs)])))]

    (map (fn [definition]
           (let [service-equal? #(when (= (:id definition) (:id %)) %)]
             (condp #(some %2 %1) service-equal?
               user-services
               :>>
               (fn [user-service]
                 {:definition definition, :type :user, :service-info user-service})

               root-services
               :>>
               (fn [root-service]
                 {:definition definition, :type :root, :service-info root-service})

               {:definition definition, :type nil, :service-info nil})))
         user-service-definitions)))

(defn- get-services-list []
  (go-let [services-info (<! (get-services-info))
           table-header ["Name" "Status" "User" "Definition" "Plist"]
           table-record (map (fn [{:keys [definition] :as info}]
                               [(:definition-name definition)
                                (if (:type info) "Enabled" "Disabled")
                                (if (= :root (:type info)) "root" js/process.env.USER)
                                (:definition-path definition)
                                (:path (:service-info info))])
                             services-info)
           final-input (.concat
                        #js []
                        (into-array [(into-array table-header)])
                        (into-array (map into-array table-record)))]
    (table/table
     final-input
     #js {:border (table/getBorderCharacters "void")
          :drawHorizontalLine (constantly false)})))

#_(async/take!
   (get-services-list)
   #(js/console.log %))

(defn -main
  "Support sub commands:

  * list
  * enable [--root|--user] [service name]
  * disable [--root|--user] [service name]
  * reload [--root|--user] [service name]
  * restart [--root|--user] [service name]
  * edit [service name]"
  [])
