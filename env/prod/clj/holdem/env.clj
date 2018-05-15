(ns holdem.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[holdem started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[holdem has shut down successfully]=-"))
   :middleware identity})
