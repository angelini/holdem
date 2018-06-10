(ns holdem.routes.game
  (:require [compojure.core :refer [defroutes GET]]
            [holdem.game :as game]
            [holdem.layout :as layout]
            [ring.util.http-response :as response]))

"
// State
{
  'next_player': 3,
  'possible_actions': [
    ['call', 2],
    ['raise', 6],
    ['fold', nil],
    ['all', nil],
  ],
  'phase': 'pre',
  'board': [],
  'hands': {
    3: [['hearts', 4], ['spade', 10]],
    2: ['hidden', 'hidden'],
  },
  'stacks': {
    3: 200,
    2: 400,
  },
  'committed': {
    3: 2,
    2: 4,
  },
}
"

"
// Log
[
  {
    'hand_id': 1,
    'actions': [
      [3, 'small', 2],
      [2, 'big', 4],
    ],
  },
]
"

(defroutes game-routes
  (GET "/" [] (layout/render "home.html"))
  (GET "/state/:game-id" [game-id] {:body (game/state (Integer/parseInt game-id))})
  (GET "/logs/:game-id" [game-id] {:body (game/logs (Integer/parseInt game-id))}))
