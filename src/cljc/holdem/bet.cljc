(ns holdem.bet)

(defrecord Seat [player chips actions])

(defn initial-seats [players_and_chips small big]
  (-> (map #(Seat. (get % 0) (get % 1) [])
           players_and_chips)
      vec
      (assoc-in [0 :actions] [[:small small]])
      (assoc-in [1 :actions] [[:big big]])))

(defn actions-and-next-seat [seats]
  (loop [rotation-idx 0
         seat-idx 0
         history []]
    (let [seat (get seats seat-idx)
          next-seat-idx (if (= seat-idx (dec (count seats)))
                          0 (inc seat-idx))
          [action-type action-val] (get (:actions seat) rotation-idx)]
      (if (= (count (:actions seat)) rotation-idx)
        [history seat]
        (recur (if (= next-seat-idx 0)
                 (inc rotation-idx) rotation-idx)
               next-seat-idx
               (conj history
                     {:player (:player seat)
                      :action-type action-type
                      :action-val action-val}))))))

(defn sum-bets [history]
  (reduce +
          0
          (map #(or (:action-val %) 0) history)))

(defn amount-to-call [history player]
  (let [player-sums (->> history
                         (group-by :player)
                         (map (fn [[player actions]]
                                [player (sum-bets actions)]))
                         (into {}))
        highest-bet (apply max (vals player-sums))]
    (- highest-bet
       (get player-sums player 0))))

(defn find-last-val-by-type [type history]
  (->> history
       (filter #(= (:action-type %) type))
       (map :action-val)
       last))

(defn minimum-raise [history]
  (or (find-last-val-by-type :raise history)
      (find-last-val-by-type :bet history)
      (find-last-val-by-type :big history)))

(def state-transitions
  {nil    #{:bet :check :all}
   :small #{:big-blind}
   :big   #{:fold :call :raise :all}
   :bet   #{:fold :call :raise :all}
   :fold  #{:fold :check :call :raise :all}
   :check #{:fold :call :raise :all}
   :call  #{:fold :check :call :raise :all}
   :raise #{:fold :call :raise :all}
   :all   #{:fold :call :raise :all}
   })

(defn action-minimum-fn [type]
  (get {:bet (fn [_ _ big] big)
        :fold (constantly 0)
        :check (constantly 0)
        :call (fn [history {chips :chips player :player} _]
                (amount-to-call history player))
        :raise (fn [history {chips :chips player :player} _]
                 (+ (amount-to-call history player)
                    (minimum-raise history)))
        :all (constantly 0)
        }
       type))

(defn possible-actions [history seat big]
  (->> (get state-transitions (:action-type (last history)))
       (map (fn [action-type]
              [action-type ((action-minimum-fn action-type) history seat big)]))
       (filter #(>= (:chips seat) (get % 1)))))
