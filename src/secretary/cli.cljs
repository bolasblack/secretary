(ns secretary.cli
  (:require
   ["yargs" :as yargs]
   [secretary.core :as sc]
   [rxcljs.core :as rc :include-macros true]
   [cljs.core.async :as async]))

(defn- wrap-channel-errors [f]
  (fn [argv & rest-args]
    (async/take!
     (apply f (js->clj argv :keywordize-keys true) rest-args)
     (fn [v]
       (when (rc/rxerror? v)
         (js/console.error @v))))))

(defn -main []
  (-> yargs
      (.command "list" "List all defined services" identity (wrap-channel-errors sc/list-services))
      (.demandCommand 1 "You need at least one command before moving on")
      (.help)
      .-argv))

(-main)
