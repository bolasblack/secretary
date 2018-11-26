(ns secretary.cli
  (:require
   ["yargs" :as yargs]
   [secretary.core :as sc]
   [rxcljs.core :as rc :include-macros true]
   [cljs.core.async :as async]))

(defn- wrap-channel-errors [f]
  (fn [argv & rest-args]
    (let [res (apply f (js->clj argv :keywordize-keys true) rest-args)]
      (when (rc/chan? res)
        (async/take! res #(when (rc/rxerror? %)
                            (if-let [exmsg (ex-message (deref %))]
                              (js/console.error (deref %))
                              (js/console.error (deref %)))
                            (js/process.exit 1)))))))

(defn -main []
  (-> yargs
      (.scriptName "secretary")
      (.command "list" "List all defined services"
                (fn [yargs]
                  (-> yargs
                      (.option
                       "plist"
                       #js {:alias "p"
                            :type "boolean"
                            :describe "Show plist file path"
                            :default false})))
                (wrap-channel-errors sc/list-services))
      (.command "enable [service]" "Enable service"
                (fn [yargs]
                  (-> yargs
                      (.option
                       "alluser"
                       #js {:alias "a"
                            :type "boolean"
                            :describe "Enable for all user"
                            :default false})
                      (.option
                       "phase"
                       #js {:alias "p"
                            :type "string"
                            :describe "Enable at specified phase"
                            :choices #js ["boot" "login"]
                            :default "login"})))
                (wrap-channel-errors sc/enable-service))
      (.command "disable [service]" "Disable service"
                identity
                (wrap-channel-errors sc/disable-service))
      (.command "reload [service]" "Regenerate plist file and restart service"
                identity
                (wrap-channel-errors sc/reload-services))
      (.command "start [service]" "Start service by command `launchctl start [service label]`"
                identity
                (wrap-channel-errors sc/start-services))
      (.command "stop [service]" "Stop service by command `launchctl start [service label]`"
                identity
                (wrap-channel-errors sc/stop-services))
      (.command "plist [service]" "Print service plist file path"
                identity
                (wrap-channel-errors sc/get-plist))
      (.command "edit [service]" "Edit service definition file by EDITOR"
                identity
                (wrap-channel-errors sc/edit-definition))
      (.demandCommand 1 "You need at least one command before moving on")
      (.help)
      .-argv))

(-main)
