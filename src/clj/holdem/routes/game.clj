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
        (-> (redirect "/games/1")
            (assoc :session updated-session)))
      (redirect "/login"))))

(defn game-page [game-id request]
  (if (authenticated? request)
    (layout/render "game.html" {:game-id game-id})
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

(defn insert-action [hand-id request]
  (let [player (get-in request [:session :identity :player-id])
        {action :action
         amount :amount
         phase :phase} (:params request)]
    (if (authenticated? request)
      {:body {:idx (game/insert-action (Integer/parseInt hand-id)
                                       phase player action amount)}
       :status 201}
      (redirect "/login"))))

(defroutes game-routes
  (GET "/login" [] (layout/render "login.html"))
  (POST "/login" [] authenticate)
  (GET "/games/:game-id" [game-id :as request] (game-page game-id request))
  (GET "/games/:game-id/state" [game-id :as request] (state game-id request))
  (GET "/games/:game-id/logs" [game-id :as request] (logs game-id request))
  (POST "/hands/:hand-id" [hand-id :as request] (insert-action hand-id request)))
