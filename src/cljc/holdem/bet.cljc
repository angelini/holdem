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
        highest-bet (if (empty? player-sums)
                      0
                      (apply max (vals player-sums)))]
    (- highest-bet
       (get player-sums player 0))))

(defn find-last-val-by-type [type history]
  (->> history
       (filter #(= (:action-type %) type))
       (map :action-val)
       last))

(defn minimum-raise [history big]
  (or (find-last-val-by-type :raise history)
      (find-last-val-by-type :bet history)
      big))

(def transitions-last-player
  {nil    #{:bet :check :all}
   :small #{:big-blind}
   :big   #{:fold :call :raise :all}
   :bet   #{:fold :call :raise :all}
   :fold  #{:fold :check :call :raise :all}
   :check #{:bet :fold :check :call :raise :all}
   :call  #{:fold :check :call :raise :all}
   :raise #{:fold :call :raise :all}
   :all   #{:fold :call :raise :all}
   })

(def transitions-current-player
  {nil    #{:bet :check :call :raise :all}
   :small #{:fold :call :raise :all}
   :big   #{:fold :check :call :raise :all}
   :bet   #{:fold :check :call :raise :all}
   :fold  #{}
   :check #{:fold :call :raise :all}
   :call  #{:fold :raise :all}
   :raise #{:fold :check :call :raise :all}
   :all   #{}})

(defn action-minimum-fn [type]
  (get {:bet (fn [_ _ big] big)
        :fold (constantly 0)
        :check (constantly 0)
        :call (fn [history {chips :chips player :player} _]
                (amount-to-call history player))
        :raise (fn [history {chips :chips player :player} big]
                 (+ (amount-to-call history player)
                    (minimum-raise history big)))
        :all (constantly 0)
        } type))

(defn last-action [history]
  (when (not (empty? history))
    (:action-type (last history))))

(defn last-action-by-seat [{actions :actions}]
  (when (not (empty? actions))
    (first (last actions))))

(defn possible-actions [history seat big]
  (let [last (last-action history)
        last-by-seat (last-action-by-seat seat)
        to-call (amount-to-call history (:player seat))]
    (if (and (#{:check :call :raise} last-by-seat)
             (= 0 to-call))
      '()
      (->> (get transitions-last-player last)
           (filter #((get transitions-current-player last-by-seat) %))
           (filter (fn [action-type]
                     (not (and (= :call action-type)
                               (= 0 to-call)))))
           (map (fn [action-type]
                  [action-type ((action-minimum-fn action-type) history seat big)]))
           (filter #(>= (:chips seat) (get % 1)))))))
