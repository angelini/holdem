(ns holdem.handler
  (:require
            [holdem.layout :refer [error-page]]
            [holdem.routes.game :refer [game-routes]]
            [compojure.core :refer [routes wrap-routes]]
            [compojure.route :as route]
            [holdem.env :refer [defaults]]
            [mount.core :as mount]
            [holdem.middleware :as middleware]))

(mount/defstate init-app
  :start ((or (:init defaults) identity))
  :stop  ((or (:stop defaults) identity)))

(mount/defstate app
  :start
  (middleware/wrap-base
    (routes
     (-> #'game-routes
         (wrap-routes middleware/wrap-csrf)
         (wrap-routes middleware/wrap-formats))
     (route/not-found
      (:body
       (error-page {:status 404
                    :title "page not found"}))))))
