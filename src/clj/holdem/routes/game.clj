(ns holdem.routes.game
  (:require [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.hashers :as hashers]
            [compojure.core :refer [defroutes GET POST]]
            [holdem.db.core :as db]
            [holdem.game :as game]
            [holdem.layout :as layout]
            [ring.util.response :refer [redirect]]
            [ring.util.http-response :as response]))

(defn authenticate [request]
  (let [username (get-in request [:form-params "username"])
        password (get-in request [:form-params "password"])
        session (:session request)
        {player-id :id
         password-hash :password_hash} (db/player-id-and-hash {:username username})]
    (if (and password-hash
             (hashers/check password password-hash))
      (let [updated-session (assoc session :identity {:username username
                                                      :player-id player-id})]
        (-> (redirect "/")
            (assoc :session updated-session)))
      (redirect "/login"))))

(defn home [request]
  (if (authenticated? request)
    (layout/render "home.html")
    (redirect "/login")))

(defn state [game-id request]
  (if (authenticated? request)
    {:body (game/state (Integer/parseInt game-id)
                       (get-in request [:session :identity :player-id]))}
    (redirect "/login")))

(defn logs [game-id request]
  (if (authenticated? request)
    {:body (game/logs (Integer/parseInt game-id))}
    (redirect "/login")))

(defroutes game-routes
  (GET "/" [] home)
  (GET "/login" [] (layout/render "login.html"))
  (POST "/login" [] authenticate)
  (GET "/state/:game-id" [game-id :as request] (state game-id request))
  (GET "/logs/:game-id" [game-id :as request] (logs game-id request)))
