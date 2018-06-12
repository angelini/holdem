(ns holdem.game
  (:require [buddy.hashers :as hashers]
            [clojure.data.generators :as generators]
            [conman.core :as conman]
            [holdem.bet :as bet]
            [holdem.poker :as poker]
            [holdem.db.core :as db]))

(defn create-player [username password]
  (when (nil? (db/player-id-and-hash {:username username}))
    (-> {:username username
         :password-hash (hashers/encrypt password)}
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

(defn deal-hand [seed players]
  (binding [generators/*rnd* (java.util.Random. seed)]
    (map conj
         (poker/hand-order players)
         (generators/shuffle poker/deck))))

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
          hand (deal-hand seed player-order)]
      (db/start-next-phase! {:hand-id hand-id :phase :phase/pre})
      (db/insert-small-blind-action! {:game-id game
                                      :hand-id hand-id
                                      :player-id (first player-order)})
      (db/insert-big-blind-action! {:game-id game
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

(defn seat-numbers [game]
  (into {} (->> (db/seated-players {:game-id game})
                (map (fn [{player :player_id
                           seat-number :seat_number}]
                       [seat-number player])))))

(defn hide-cards [player state]
  (let [shown-board (get {:pre #{}
                          :flop #{0 1 2}
                          :turn #{0 1 2 3}
                          :river #{0 1 2 3 4}}
                         (:phase state))]
    (-> state
        (assoc :board (map-indexed (fn [idx card]
                                     (if (shown-board idx)
                                       card
                                       :hidden))
                                   (:board state)))
        (assoc :hole-cards (into {}
                               (map (fn [[id cards]]
                                      (if (= id player)
                                        [id cards]
                                        [id [:hidden :hidden]]))
                                    (:hole-cards state)))))))

(defn state [game player]
  (let [{hand :hand_id
         big-blind :big_blind} (db/current-hand-and-big-blind
                                {:game-id game})
        phase (->> (db/current-phase {:hand-id hand})
                   :phase
                   (keyword "phase"))
        db-state (db/game-state {:game-id game
                                 :hand-id hand
                                 :phase phase})
        seats (seat-states db-state)
        [history next-seat] (bet/actions-and-next-seat seats)
        possible (bet/possible-actions history next-seat big-blind)]
    (hide-cards player
                {:hand-id hand
                 :current-player player
                 :next-player (:player next-seat)
                 :possible-actions possible
                 :phase (-> phase name keyword)
                 :board (board hand)
                 :hole-cards (hole-cards db-state)
                 :stacks (stacks db-state)
                 :seat-numbers (seat-numbers game)})))

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

(def phase-order [:pre :flop :turn :river :finished])

(defn next-phase [phase]
  (loop [order phase-order]
    (if (= phase (first order))
      (second order)
      (recur (rest order)))))

(defn insert-action [game hand phase player action amount]
  (conman/with-transaction [db/*db*]
    (let [idx (db/insert-action! {:hand-id hand
                                  :phase (keyword "phase" (name phase))
                                  :player-id player
                                  :action (keyword "player-action" (name action))
                                  :amount amount})
          current-state (state game player)
          next (next-phase phase)]
      (when (empty? (:possible-actions current-state))
        (if (= next :finished)
          (start-hand game)
          (db/start-next-phase! {:hand-id hand
                                 :phase (keyword "phase" (name next))})))
      idx)))

(defn example-game []
  (let [foo (create-player "foo" "foo")
        bar (create-player "bar" "bar")
        game (start-game 10 2 4)
        foo-seat (add-player game foo 200)
        bar-seat (add-player game bar 300)]
    (start-hand game)))
