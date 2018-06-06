(ns holdem.poker
  (:require [clojure.data.generators :as generators]))

(defrecord Card [suit rank])

(def deck
  (mapcat (fn [rank]
            (map (fn [suit] (Card. suit rank)) [:heart :spade :diamond :club]))
          (range 2 15)))

(defn hand-order [players]
  (concat
   (map (fn [i] [:hand i]) players)
   (map (fn [i] [:hand i]) players)
   [[:burn nil]
    [:board 0]
    [:board 1]
    [:board 2]
    [:burn nil]
    [:board 3]
    [:burn nil]
    [:board 4]]))

(defn deal-hand [seed players]
  (binding [generators/*rnd* (java.util.Random. seed)]
    (map conj
         (hand-order players)
         (generators/shuffle deck))))

(defn straight? [hand]
  (let [sorted (sort-by :rank hand)]
    (loop [card (first sorted)
           rest-hand (rest sorted)]
      (if (or (= (+ (:rank card) 1) (:rank (first rest-hand)))
              ;; 2, 3, 4, 5, 14 for low-ace
              (and (= (:rank card) 5) (= (:rank (first rest-hand)) 14)))
        (if (= (count rest-hand) 1)
          true
          (recur (first rest-hand) (rest rest-hand)))
        false))))

(defn flush? [hand]
  (loop [card (first hand)
         rest-hand (rest hand)]
    (if (= (:suit card) (:suit (first rest-hand)))
      (if (= (count rest-hand) 1)
        true
        (recur (first rest-hand) (rest rest-hand)))
      false)))

(defn straight-flush? [hand] (and (straight? hand) (flush? hand)))

(defn rank-groups [size hand]
  (->> (group-by :rank hand)
       (map second)
       (filter (fn [group] (= (count group) size)))))

(defn pairs [hand] (rank-groups 2 hand))
(defn triples [hand] (rank-groups 3 hand))
(defn quads [hand] (rank-groups 4 hand))

(defn pairs? [hand] (not (empty? (pairs hand))))
(defn two-pair? [hand] (= (count (pairs hand)) 2))
(defn triples? [hand] (not (empty? (triples hand))))
(defn quads? [hand] (not (empty? (quads hand))))

(defn full-house? [hand]
  (and (= (count (pairs hand)) 1)
       (= (count (triples hand)) 1)))

(defn cmp-sorted-ranks [left right]
  (compare
   (->> left (sort-by :rank) (map :rank) reverse vec)
   (->> right (sort-by :rank) (map :rank) reverse vec)))

(defn group-ranks [group-fn hand]
  (->> (group-fn hand)
       (map first)
       (sort-by :rank)
       (map :rank)
       reverse
       vec))

(defn cmp-group-ranks [group-fn left right]
  (let [left-group-ranks (group-ranks group-fn left)
        right-group-ranks (group-ranks group-fn right)]
    (condp = (compare left-group-ranks
                      right-group-ranks)
      0 (cmp-sorted-ranks (->> left
                               (remove #(contains? left-group-ranks (:rank %))))
                          (->> right
                               (remove #(contains? right-group-ranks (:rank %)))))
      1 1
      -1 -1)))

(def hand-categories
  [{:check straight-flush?
    :cmp-equal cmp-sorted-ranks}
   {:check quads?
    :cmp-equal (partial cmp-group-ranks quads)}
   {:check full-house?
    :cmp-equal (fn [left right]
                 (let [cmp-triples (cmp-group-ranks triples left right)]
                   (if (not= 0 cmp-triples)
                     cmp-triples
                     (cmp-group-ranks pairs left right))))}
   {:check flush?
    :cmp-equal cmp-sorted-ranks}
   {:check straight?
    :cmp-equal cmp-sorted-ranks}
   {:check triples?
    :cmp-equal (partial cmp-group-ranks triples)}
   {:check two-pair?
    :cmp-equal (fn [left right]
                 (compare
                  (group-ranks pairs left)
                  (group-ranks pairs right)))}
   {:check pairs?
    :cmp-equal (partial cmp-group-ranks pairs)}
   {:check (constantly true)
    :cmp-equal cmp-sorted-ranks}
   ])

(defn compare-hands [left right]
  (loop [{check :check
          cmp-equal :cmp-equal} (first hand-categories)
         rest-categories (rest hand-categories)]
    (condp = [(check left) (check right)]
      [true true] (cmp-equal left right)
      [true false] 1
      [false true] -1
      [false false] (recur (first rest-categories)
                           (rest rest-categories)))))
