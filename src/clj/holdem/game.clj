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
      (db/insert-small-blind-action {:game-id game
                                     :hand-id hand-id
                                     :player-id (first player-order)})
      (db/insert-big-blind-action {:game-id game
                                   :hand-id hand-id
                                   :player-id (second player-order)})
      (doall
       (for [[deal-to id-or-idx card] hand]
         (db/insert-card! {:hand-id hand-id
                           :player-id (when (= deal-to :hand) id-or-idx)
                           :board-idx (when (= deal-to :board) id-or-idx)
                           :deal-to (keyword "deal-to" (name deal-to))
                           :card-suit (keyword "card-suit" (name (:suit card)))
                           :card-rank (:rank card)}))))))

(defn seat-states [db-state]
  (mapv (fn [{player :player_id
              stack :stack
              actions :player_actions
              amounts :amounts}]
          (bet/->Seat
           player
           stack
           (mapv (fn [action amount]
                   [(keyword action) amount])
                 actions amounts)))
        db-state))

(defn hole-cards [db-state]
  (into {}
        (map (fn [{player :player_id
                   suits :card_suits
                   ranks :card_ranks}]
               [player [(poker/->Card (keyword (first suits))
                                      (first ranks))
                        (poker/->Card (keyword (second suits))
                                      (second ranks))]])
             db-state)))

(defn stacks [db-state]
  (into {}
        (map (fn [{player :player_id
                   stack :stack}]
               [player stack])
             db-state)))

(defn board [hand]
  (->> (db/board {:hand-id hand})
       (mapv (fn [{suit :card_suit
                   rank :card_rank}]
               (poker/->Card (keyword suit) rank)))))

(defn state [game]
  (let [{hand :hand_id
         big-blind :big_blind} (db/current-hand-and-big-blind
                                {:game-id game})
        db-state (db/game-state {:game-id game
                              :hand-id hand})
        seats (seat-states db-state)
        [history next-seat] (bet/actions-and-next-seat seats)
        possible (bet/possible-actions history next-seat big-blind)]
    {:next-player (:player next-seat)
     :possible-actions possible
     :phase :pre
     :board (board hand)
     :hole-cards (hole-cards db-state)
     :stacks (stacks db-state)}))

(defn logs [game]
  (->> (db/logs {:game-id game})
       (mapv (fn [{hand :hand_id
                   players :player_ids
                   actions :player_actions
                   amounts :amounts}]
               {:hand-id hand
                :actions (mapv (fn [player action amount]
                                 [player (keyword action) amount])
                               players actions amounts)}))))

(defn insert-action [hand player seq action amount])

(defn example-game []
  (let [foo (create-player "foo")
        bar (create-player "bar")
        game (start-game 10 2 4)
        foo-seat (add-player game foo 200)
        bar-seat (add-player game bar 300)]
    (start-hand game)))
