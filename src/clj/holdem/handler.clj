(ns holdem.handler
  (:require [buddy.auth.backends :as backends]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [holdem.layout :refer [error-page]]
            [holdem.routes.game :refer [game-routes]]
            [compojure.core :refer [routes wrap-routes]]
            [compojure.route :as route]
            [holdem.env :refer [defaults]]
            [mount.core :as mount]
            [holdem.middleware :as middleware]))

(def backend (backends/session))

(mount/defstate init-app
  :start ((or (:init defaults) identity))
  :stop  ((or (:stop defaults) identity)))

(mount/defstate app
  :start
  (middleware/wrap-base
    (routes
     (-> #'game-routes
         (wrap-routes middleware/wrap-logging)
         (wrap-authorization backend)
         (wrap-authentication backend)
         (wrap-routes middleware/wrap-csrf)
         (wrap-routes middleware/wrap-formats))
     (route/not-found
      (:body
       (error-page {:status 404
                    :title "page not found"}))))))
