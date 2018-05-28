(ns holdem.game
  (:require [holdem.poker :as poker]
            [holdem.db.core :as db]))

(defn create-player [username]
  (when (nil? (db/username-exists {:username username}))
    (-> {:username username}
        db/create-player!
        :id)))

(defn start-game [size small big]
  (-> {:table-size size
       :small-blind small
       :big-blind big}
      db/start-game!
      :id))

;; Seat IDs
;;
;;     2  3
;;   1      4
;; 0          5
;;   9      6
;;     8  7

(def seat-order [0 5 2 7 3 8 4 9 6 1])

(defn add-player [game player]
  (let [seated (db/seated-players {:game-id game})
        filled-seats (set (map :seat_number seated))
        already-seated? (contains?
                         (set (map :player_id seated)) player)
        seat (->> seat-order
                  (filter #(not (contains? filled-seats %)))
                  first)]
    (when (and (not already-seated?) seat)
      (-> {:game-id game
           :player-id player
           :seat-number seat}
          db/add-player!
          :seat_number))))

(defn start-hand [game players]
  (-> {:game-id game
       :seed (rand-int Integer/MAX_VALUE)
       :players players}
      db/start-hand!
      :id))

(defn next-action [hand])

(defn insert-action [hand player seq action amount])
