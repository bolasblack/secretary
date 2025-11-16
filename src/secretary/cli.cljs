(ns secretary.cli
  (:require
   ["yargs" :as yargs]
   [cljs.core.async :as async]
   [goog.object :as go]
   [secretary.core :as sc]
   [secretary.utils :as su]
   [rxcljs.core :as rc :include-macros true]))

(def ^:dynamic *cli-debug* false)

(defn- wrap-channel-errors [f]
  (fn [argv & rest-args]
    (let [res (apply f (js->clj argv :keywordize-keys true) rest-args)]
      (when (rc/chan? res)
        (async/take! res #(when (rc/rxerror? %)
                            (if-let [exmsg (ex-message (deref %))]
                              (if *cli-debug*
                                (js/console.error (deref %))
                                (js/console.error exmsg))
                              (js/console.error (deref %)))
                            (js/process.exit 1)))))))

(defn- prepare-subcommand [yargs]
  (-> yargs
      (.version false)
      .help))

(defn- get-argv []
  (-> yargs
      (.scriptName "secretary")
      (.option
       "debug"
       #js {:alias "d"
            :type "boolean"
            :describe "Print error stack"})
      (.command "list" "List all defined services"
                (fn [yargs]
                  (-> yargs
                      prepare-subcommand
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
                      prepare-subcommand
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
                prepare-subcommand
                (wrap-channel-errors sc/disable-service))
      (.command "reload [service]" "Regenerate plist file and restart service"
                prepare-subcommand
                (wrap-channel-errors sc/reload-services))
      (.command "start [service]" "Delegate to `launchctl start [service label]`"
                prepare-subcommand
                (wrap-channel-errors sc/start-services))
      (.command "stop [service]" "Delegate to `launchctl stop [service label]`"
                prepare-subcommand
                (wrap-channel-errors sc/stop-services))
      (.command "plist [service]" "Print service plist file path"
                prepare-subcommand
                (wrap-channel-errors sc/get-plist))
      (.command "edit [service]" "Create or edit service definition file by EDITOR"
                prepare-subcommand
                (wrap-channel-errors sc/edit-definition))
      (.demandCommand 1 "You need at least one command before moving on")
      .help
      .-argv))

(defn -main []
  (when (go/get (get-argv) "debug")
    (set! *cli-debug* true)))
