(ns holdem.poker)

(defrecord Card [suit rank])

(def deck
  (mapcat (fn [rank] (map (fn [suit] (Card. suit rank)) [:heart :spade :diamond :club])) (range 1 14)))

(defn deal-hand [deck]
  (->> (shuffle deck)
       (take 5)))

(defn straight? [hand]
  (let [sorted (sort-by :rank hand)]
    (-> (reduce (fn [[bool last] curr]
                  (if (and bool
                           (= (+ (:rank last) 1) (:rank curr)))
                    [true curr]
                    [false curr]))
                [true (first sorted)]
                (rest sorted))
        (first))))

(defn flush? [hand]
  (-> (reduce (fn [[bool last] curr]
                (if (and bool
                         (= (:suit last) (:suit curr)))
                  [true curr]
                  [false curr]))
              [true (first hand)]
              (rest hand))
      (first)))

(defn straight-flush? [hand] (and (straight? hand) (flush? hand)))

(defn rank-groups [size hand]
  (->> (group-by :rank hand)
       (map second)
       (filter (fn [group] (= (count group) size)))))

(defn doubles [hand] (rank-groups 2 hand))
(defn triples [hand] (rank-groups 3 hand))
(defn quads [hand] (rank-groups 4 hand))

;; (def hand-categories
;;   [straight-flush?
;;    (fn [left right]
;;      )])
