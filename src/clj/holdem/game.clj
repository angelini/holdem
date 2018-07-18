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
        (db/insert-stack-delta! {:game-id game
                                 :hand-id nil
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

(defn seat-states [db-state player-order]
  (->> db-state
       (map (fn [{player :player_id
                  stack :stack
                  actions :player_actions
                  amounts :amounts}]
              (bet/->Seat
               player
               stack
               (mapv (fn [action amount]
                       [(keyword action) amount])
                     actions amounts))))
       (filter #((set player-order) (:player %)))
       (sort-by #(.indexOf player-order (:player %)))
       vec))

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
                           username :username
                           seat-number :seat_number}]
                       [seat-number {:player-id player
                                     :username username}])))))

(defn player-order [hand phase]
  (let [order (-> (db/player-order {:hand-id hand})
                  :players)
        cannot-act (->> (db/cannot-act {:hand-id hand
                                        :phase (keyword "phase" (name phase))})
                        (mapv :player_id)
                        set)]
    (filterv #(not (contains? cannot-act %))
             order)))

(def phase-order [:pre :flop :turn :river :end])

(defn next-phase [phase]
  (loop [order phase-order]
    (if (= phase (first order))
      (second order)
      (recur (rest order)))))

(defn current-phase [pots]
  (if (empty? pots)
    :pre
    (->> pots
         (map #(keyword (:phase %)))
         (sort-by #(.indexOf phase-order %))
         first
         next-phase)))

(defn hide-cards [player state]
  (let [shown-board (get {:pre #{}
                          :flop #{0 1 2}
                          :turn #{0 1 2 3}
                          :river #{0 1 2 3 4}
                          :end #{0 1 2 3 4}}
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
                                    (:hole-cards state))))
        (assoc :current-player player))))

(defn state [game]
  (let [{hand :hand_id
         big-blind :big_blind} (db/current-hand-and-big-blind
                                {:game-id game})
        pots (db/current-pots {:hand-id hand})
        phase (current-phase pots)
        db-state (db/game-state {:game-id game
                                 :hand-id hand
                                 :phase (keyword "phase" (name phase))})
        player-order (player-order hand phase)
        seats (seat-states db-state player-order)
        winners (map (fn [{id :player_id
                           total :total}]
                       [id total])
                     (db/winners {:hand-id hand}))
        curr {:hand-id hand
              :phase (-> phase name keyword)
              :board (board hand)
              :hole-cards (hole-cards db-state)
              :stacks (stacks db-state)
              :seat-numbers (seat-numbers game)
              :pots (map (fn [pot] [(:amount pot) (:players pot)])
                         pots)}]
    (if (not (empty? winners))
      (let [winner (first (last winners))]
        (assoc curr
               :next-player winner
               :possible-actions '()
               :committed {}
               :winners winners))
      (let [[history next-seat] (bet/actions-and-next-seat seats)
            possible (bet/possible-actions history next-seat player-order big-blind)]
        (assoc curr
               :next-player (:player next-seat)
               :possible-actions possible
               :committed (bet/committed-by-player history)
               :winners '())))))

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

(defn insert-committed [game hand phase committed]
  (doseq [[amount players] (bet/pots committed)]
    (when (not= 0 amount)
      (db/insert-pot! {:hand-id hand
                       :phase (keyword "phase" (name phase))
                       :amount amount
                       :players players})
      (doseq [player players]
        (db/insert-stack-delta! {:game-id game
                                 :hand-id hand
                                 :player-id player
                                 :delta (* -1 amount)})))))

(defn finish-hand-folds [game hand winner]
  (doseq [{amount :amount
           players :players} (db/current-pots {:hand-id hand})]
    (db/insert-stack-delta! {:game-id game
                             :hand-id hand
                             :player-id winner
                             :delta (* amount (count players))})))

(defn finish-hand-showdown [game hand current-state]
  (doseq [{amount :amount
           players :players} (db/current-pots {:hand-id hand})]
    (let [[winner _] (->> (:hole-cards current-state)
                          (filter (fn [[player _]]
                                    ((set players) player)))
                          (map (fn [[player hole-cards]]
                                 [player (poker/best-possible-hand hole-cards (:board current-state))]))
                          (sort-by second poker/compare-hands)
                          last)]
      (db/insert-stack-delta! {:game-id game
                               :hand-id hand
                               :player-id winner
                               :delta (* amount (count players))}))))

(defn insert-action [game hand phase player action amount]
  (conman/with-transaction [db/*db*]
    (let [idx (db/insert-action! {:hand-id hand
                                  :phase (keyword "phase" (name phase))
                                  :player-id player
                                  :action (keyword "player-action" (name action))
                                  :amount amount})
          players-left (count (player-order hand phase))
          current-state (state game)
          player-order (player-order hand (next-phase phase))]
      (when (empty? (:possible-actions current-state))
        (insert-committed game hand phase (:committed current-state))
        (cond
          (= 1 (count player-order))
          (finish-hand-folds game hand (first player-order))
          (= :end (next-phase phase))
          (finish-hand-showdown game hand current-state)))
      idx)))

(defn example-game []
  (let [foo (create-player "foo" "foo")
        bar (create-player "bar" "bar")
        baz (create-player "baz" "baz")
        game (start-game 10 2 4)
        foo-seat (add-player game foo 200)
        bar-seat (add-player game bar 300)
        baz-seat (add-player game baz 150)]
    (start-hand game)))
