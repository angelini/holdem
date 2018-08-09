(ns holdem.bet)

(defrecord Seat [player chips actions])

(defn initial-seats [players_and_chips small big]
  (-> (map #(Seat. (get % 0) (get % 1) [])
           players_and_chips)
      vec
      (assoc-in [0 :actions] [[:small small]])
      (assoc-in [1 :actions] [[:big big]])))

(defn skip-seat? [seat]
  (let [actions (:actions seat)]
    (if (empty? actions)
      false
      (->> actions
           last
           first
           #{:fold :all}
           boolean))))

(defn actions-and-next-seat [seats]
  (let [max-rotation-idx (->> seats
                              (map #(count (:actions %)))
                              sort
                              last)]
    (loop [rotation-idx 0
           seat-idx 0
           history []]
      (let [seat (get seats seat-idx)
            next-seat-idx (if (= seat-idx (dec (count seats)))
                            0 (inc seat-idx))
            action (get (:actions seat) rotation-idx)]
        (cond
          (and (not (skip-seat? seat))
               (= (count (:actions seat)) rotation-idx))
          [history seat]
          (> rotation-idx max-rotation-idx)
          [history nil]
          :else
          (recur (if (= next-seat-idx 0)
                   (inc rotation-idx) rotation-idx)
                 next-seat-idx
                 (if (nil? action)
                   history
                   (conj history
                         {:player (:player seat)
                          :action-type (first action)
                          :action-val (second action)}))))))))

(defn sum-bets [history]
  (reduce +
          0
          (map #(or (:action-val %) 0) history)))

(defn committed-by-players [history]
  (->> history
       (group-by :player)
       (map (fn [[player actions]]
              [player (sum-bets actions)]))
       (into {})))

(defn committed-by-player [history player]
  (get (committed-by-players history) player 0))

(defn highest-bet [history]
  (let [committed (committed-by-players history)]
    (if (empty? committed)
      0
      (apply max (vals committed)))))

(defn amount-to-call [history player]
  (let [committed (committed-by-player history player)
        highest (highest-bet history)]
    (- highest
       committed)))

(defn amount-to-all-in [history player chips]
  (let [committed (committed-by-player history player)]
    (- chips committed)))

(defn all-others-folded? [history players player]
  (let [others (->> players
                    (filter #(not= player %))
                    (into #{}))
        folded (->> history
                    (group-by :player)
                    (filter (fn [[player actions]]
                              (boolean (some #(= :fold (:action-type %))
                                             actions))))
                    (map first)
                    (into #{}))]
    (= others folded)))

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
  {nil    #{:fold :bet :check :all}
   :small #{:big-blind}
   :big   #{:fold :call :raise :all}
   :bet   #{:fold :call :raise :all}
   :fold  #{:bet :fold :check :call :raise :all}
   :check #{:bet :fold :check :call :raise :all}
   :call  #{:fold :check :call :raise :all}
   :raise #{:fold :call :raise :all}
   :all   #{:fold :call :raise :all}
   })

(def transitions-current-player
  {nil    #{:fold :bet :check :call :raise :all}
   :small #{:fold :call :raise :all}
   :big   #{:fold :check :call :raise :all}
   :bet   #{:fold :check :call :raise :all}
   :fold  #{}
   :check #{:fold :call :raise :all}
   :call  #{:fold :raise :all}
   :raise #{:fold :check :call :raise :all}
   :all   #{}
   })

(defn action-minimum-fn [type]
  (get {:bet (fn [_ _ big] big)
        :fold (constantly 0)
        :check (constantly 0)
        :call (fn [history {player :player} _]
                (amount-to-call history player))
        :raise (fn [history {player :player} big]
                 (+ (amount-to-call history player)
                    (minimum-raise history big)))
        :all (fn [history {chips :chips player :player} _]
               (amount-to-all-in history player chips))
        } type))

(defn last-action [history]
  (when (not (empty? history))
    (:action-type (last history))))

(defn last-action-by-seat [{actions :actions}]
  (when (not (empty? actions))
    (first (last actions))))

(defn possible-actions [history seat player-order big]
  (let [{chips :chips player :player} seat
        last (last-action history)
        last-by-seat (last-action-by-seat seat)
        to-call (amount-to-call history player)
        to-all-in (amount-to-all-in history player chips)
        all-folded (all-others-folded? history player-order player)]
    (if (or (and (#{:bet :check :call :raise} last-by-seat)
                 (= 0 to-call))
            all-folded)
      '()
      (->> (get transitions-last-player last)
           (filter #((get transitions-current-player last-by-seat) %))
           (filter (fn [action-type]
                     (not (and (= :call action-type)
                               (= 0 to-call)))))
           (map (fn [action-type]
                  [action-type ((action-minimum-fn action-type) history seat big)]))
           (filter #(>= to-all-in (get % 1)))))))

(defn find-all-ins [committed stacks]
  (->> committed
       (filter (fn [[player amount]]
                 (= (stacks player) amount)))
       (sort-by second)))

(defn sub-from-all [player-amounts val]
  (->> player-amounts
       (map (fn [[player amount]]
              [player (- amount val)]))
       (filter (fn [[_ amount]]
                 (> amount 0)))
       (into {})))

(defn pots [committed stacks]
  (loop [committed committed
         stacks stacks
         pots* []
         all-ins (find-all-ins committed stacks)]
    (if (empty? all-ins)
      (if (empty? committed)
        pots*
        (let [max-commit (-> (vals committed)
                             sort
                             last)
              eligible (->> committed
                            (filter (fn [[_ amount]]
                                      (= amount max-commit))))
              others (->> committed
                          (filter (fn [[_ amount]]
                                    (not= amount max-commit))))
              total (+ (* (count eligible) max-commit)
                       (->> others
                            (map second)
                            (reduce +)))]
          (conj pots* [total eligible others])))
      (let [min-all-in (second (first all-ins))
            bet-more-eq (->> committed
                          (filter (fn [[_ amount]]
                                    (>= amount min-all-in))))
            eligible (->> bet-more-eq
                          (map (fn [[player _]]
                                 [player min-all-in])))
            bet-less (->> committed
                          (filter (fn [[_ amount]]
                                    (< amount min-all-in))))
            total (+ (* (count eligible) min-all-in)
                     (->> bet-less
                          (map second)
                          (reduce +)))
            remaining-committed (sub-from-all bet-more-eq min-all-in)
            remaining-stacks (sub-from-all stacks min-all-in)]
        (recur remaining-committed
               remaining-stacks
               (conj pots* [total eligible bet-less])
               (find-all-ins remaining-committed
                             remaining-stacks))))))
