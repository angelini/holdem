(ns user
  (:require [holdem.config :refer [env]]
            [clojure.spec.alpha :as s]
            [expound.alpha :as expound]
            [mount.core :as mount]
            [holdem.figwheel :refer [start-fw stop-fw cljs]]
            [holdem.core :refer [start-app]]
            [conman.core :as conman]
            [luminus-migrations.core :as migrations]))

(alter-var-root #'s/*explain-out* (constantly expound/printer))

(defn start []
  (mount/start-without #'holdem.core/repl-server))

(defn stop []
  (mount/stop-except #'holdem.core/repl-server))

(defn restart []
  (stop)
  (start))

(defn restart-db []
  (mount/stop #'holdem.db.core/*db*)
  (mount/start #'holdem.db.core/*db*)
  (binding [*ns* 'holdem.db.core]
    (conman/bind-connection holdem.db.core/*db* "sql/queries.sql")))

(defn reset-db []
  (migrations/migrate ["reset"] (select-keys env [:database-url])))

(defn migrate []
  (migrations/migrate ["migrate"] (select-keys env [:database-url])))

(defn rollback []
  (migrations/migrate ["rollback"] (select-keys env [:database-url])))

(defn create-migration [name]
  (migrations/create name (select-keys env [:database-url])))


