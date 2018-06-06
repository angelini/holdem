(ns holdem.game
  (:require [conman.core :as conman]
            [holdem.bet :as bet]
            [holdem.poker :as poker]
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

;; Seat Numbers
;;
;;     2  3
;;   1      4
;; 0          5
;;   9      6
;;     8  7

(def seat-assignment-order [0 5 2 7 3 8 4 9 6 1])

(defn add-player [game player stack]
  (let [seated (db/seated-players {:game-id game})
        filled-seats (set (map :seat_number seated))
        already-seated? (contains?
                         (set (map :player_id seated)) player)
        seat (->> seat-assignment-order
                  (filter #(not (contains? filled-seats %)))
                  first)]
    (when (and (not already-seated?) seat)
      (conman/with-transaction [db/*db*]
        (db/insert-stack-delta {:game-id game
                                :player-id player
                                :delta stack})
        (-> {:game-id game
             :player-id player
             :seat-number seat}
            db/add-player!
            :seat_number)))))

(defn next-filled-seat [seats player]
  (loop [seen false
         curr (first seats)
         rest-seats (rest (cycle seats))]
    (if seen
      curr
      (recur (= (:player_id curr) player)
             (first rest-seats)
             (rest rest-seats)))))

(defn deal-order [seats last-dealer]
  (let [dealer (next-filled-seat seats (:player_id last-dealer))]
    (loop [seat (next-filled-seat seats (:player_id dealer))
           player-order []]
      (if (= seat dealer)
        (conj player-order (:player_id seat))
        (recur (next-filled-seat seats (:player_id seat))
               (conj player-order (:player_id seat)))))))

(defn start-hand [game]
  (conman/with-transaction [db/*db*]
    (let [seed (rand-int Integer/MAX_VALUE)
          seats (db/seated-players {:game-id game})
          last-dealer (or (db/last-dealer {:game-id game})
                          (first seats))
          player-order (deal-order seats last-dealer)
          hand-id (-> {:game-id game
                       :seed seed
                       :players player-order}
                      db/start-hand!
                      :id)
          hand (poker/deal-hand seed player-order)]
      (db/insert-small-blind-delta {:game-id game
                                    :player-id (first player-order)})
      (db/insert-big-blind-delta {:game-id game
                                  :player-id (second player-order)})
      (doall
       (for [[deal-to id-or-idx card] hand]
         (db/insert-card! {:hand-id hand-id
                           :player-id (when (= deal-to :hand) id-or-idx)
                           :board-idx (when (= deal-to :board) id-or-idx)
                           :deal-to (keyword "deal-to" (name deal-to))
                           :card-suit (keyword "card-suit" (name (:suit card)))
                           :card-rank (:rank card)}))))))

(defn next-action [hand])

(defn insert-action [hand player seq action amount])

(defn example-game []
  (let [foo (create-player "foo")
        bar (create-player "bar")
        game (start-game 10 2 4)
        foo-seat (add-player game foo 200)
        bar-seat (add-player game bar 300)]
    (start-hand game)))
