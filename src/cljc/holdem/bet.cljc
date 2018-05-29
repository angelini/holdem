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
                          0 (inc seat-idx))]
      (if (= (count (:actions seat)) rotation-idx)
        [history seat]
        (recur (if (= next-seat-idx 0)
                 (inc rotation-idx) rotation-idx)
               next-seat-idx
               (conj history
                     (get (:actions seat) rotation-idx)))))))

(defn pot-size [history]
  (->> history
       (map #(or (get % 1) 0))
       +))

(defn bet-size [history]
  ;; todo
  0)

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

(def action-test
  {:bet (fn [history seat]
          (> (:chips seat) 0))
   :fold (constantly true)
   :check (constantly true)
   :call (fn [history seat]
           (> (:chips seat) (bet-size history)))
   :raise (fn [history seat]
            (> (:chips seat) (bet-size history)))
   :all (fn [history seat]
          (> (:chips seat) 0))
   })

(defn possible-actions [history seat]
  (->> (get state-transitions (get (last history) 0))
       (filter #((get action-test %) history seat))))
