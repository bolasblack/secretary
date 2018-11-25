(ns secretary.core
  (:require-macros
   [adjutant.core :refer [def-]]
   [rxcljs.core :refer [go go-let <! >!]]
   [rxcljs.transformers :refer [<p! <n! <<!]])
  (:require
   ["fs" :as fs]
   ["path" :as path]
   ["table" :as table]
   ["chalk" :as chalk]
   [cljs.core.async :as async]
   [clojure.string :as str]
   [adjutant.core :as ac]
   [rxcljs.core :as rc :include-macros true]
   [rxcljs.operators :as ro]
   [rxcljs.transformers :as rt]
   [secretary.utils :as utils]))

(defn list-services
  [{:keys [plist] :as argv}]
  (go-let [service-infos (<! (utils/get-service-infos))]
    (js/console.log (utils/pr-services service-infos :plist plist))))

#_(async/take!
   (list-services)
   #(when (rc/rxerror? v)
      (js/console.error @v)))

(defn enable-service
  [{:keys [service alluser phase] :as argv}]
  (go-let [service-info (<! (utils/get-service-info! service))]
    (cond
      (not (:service-info service-info))
      (let [plist-folder (<! (utils/get-plist-folder :phase phase :alluser alluser))
            plist-path (path/join plist-folder (:plist-name (:definition service-info)))]
        (<n! fs/writeFile plist-path (:plist (:definition service-info)))
        (utils/launchctl-exec! ["load" plist-path]))

      ;; service already enabled but status not match
      (or (not= alluser (= "alluser" (:user service-info)))
          (not= (str/lower-case phase) (str/lower-case (:load-phase service-info))))
      (throw (ex-info (str "Service already enabled, but status not matched\n\n"
                           (utils/pr-services [service-info] :plist true)) {}))

      ;; service already enabled but content not match
      (not= (:plist (:definition service-info))
            (:plist (:service-info service-info)))
      (throw (ex-info (str "Service already enabled, but content not matched\n\n"
                           (utils/pr-services [service-info] :plist true)) {}))

      :else
      (do #_(nothing)))))

(defn disable-service
  [{:keys [service] :as argv}]
  (go-let [service-info (<! (utils/get-service-info! service))
           plist-path (:path (:service-info service-info))]
    (when plist-path
      (utils/launchctl-exec! ["unload" plist-path])
      (<n! fs/unlink plist-path))))

(defn reload-services
  [{:keys [service] :as argv}]
  (go-let [service-info (<! (utils/get-service-info! service))
           plist-path (:path (:service-info service-info))]
    (when-not plist-path
      (throw (ex-info "Service should be enabled before reload" {})))
    (utils/launchctl-exec! ["unload" plist-path])
    (<n! fs/writeFile plist-path (:plist (:definition service-info)))
    (utils/launchctl-exec! ["load" plist-path])))

(defn start-services
  [{:keys [service] :as argv}]
  (go-let [service-info (<! (utils/get-service-info! service))]
    (when-not (:path (:service-info service-info))
      (throw (ex-info "Service should be enabled before start" {})))
    (utils/launchctl-exec! ["start" (:id (:definition service-info))])))

(defn stop-services
  [{:keys [service] :as argv}]
  (go-let [service-info (<! (utils/get-service-info! service))]
    (when (:path (:service-info service-info))
      (utils/launchctl-exec! ["stop" (:id (:definition service-info))]))))

(defn get-plist
  [{:keys [service] :as argv}]
  (go-let [service-info (<! (utils/get-service-info! service))]
    (when-let [plist-path (:path (:service-info service-info))]
      (js/console.log plist-path))))

(defn edit-definition
  [{:keys [service] :as argv}]
  (go-let [service-info (<! (utils/get-service-info! service))
           editor (or js/process.env.EDITOR "vi")]
    (utils/exec! editor [(:definition-path (:definition service-info))] {:stdio "inherit"})))
