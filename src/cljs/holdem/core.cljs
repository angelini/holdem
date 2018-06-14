(ns holdem.core
  (:require [ajax.core :refer [GET POST]]
            [clojure.string :as string]
            [goog.string :as gstring]
            [goog.string.format]
            [reagent.core :as reagent]))

(defonce state
  (reagent/atom nil))

(defn refresh-state []
  (GET (gstring/format "/games/%d/state" js/gameId)
       {:headers {"Accept" "application/transit+json"}
        :handler #(reset! state %)}))

(defn post-action [action-kw amount]
  (POST (gstring/format "/games/%d/hands/%d" js/gameId (:hand-id @state))
        {:params {:action action-kw
                  :amount amount
                  :phase (:phase @state)}
         :headers {"x-csrf-token" js/csrfToken}
         :handler #(refresh-state)}))

(defn next-hand []
  (POST (gstring/format "/games/%d/hands" js/gameId)
        {:headers {"x-csrf-token" js/csrfToken}
         :handler #(refresh-state)}))

(defn card [c key]
  [:div.card.col {:key key}
   (if (= c :hidden)
     [:div "Hidden"]
     [:div {:class (when (#{:heart :diamond} (:suit c)) "text-danger")}
      [:div.rank
       (if (> (:rank c) 10)
         (get {11 "Jack"
               12 "Queen"
               13 "King"
               14 "Ace"} (:rank c))
         (:rank c))]
      [:div.suit
       (get {:heart "♥"
             :spade "♠"
             :diamond "♦"
             :club "♣"} (:suit c))]])])

(defn board []
  [:div.board.row
   (map-indexed (fn [idx board-card]
                  (card board-card (gstring/format "board-%d" idx)))
                (:board @state))])

(defn player [seat-number]
  (if (not (contains? (:seat-numbers @state) seat-number))
    [:span "Open"]
    (let [{player-id :player-id
           username :username} (get-in @state [:seat-numbers seat-number])
          committed (get-in @state [:committed player-id] 0)
          stack (- (get-in @state [:stacks player-id]) committed)]
      [:div {:class [(when (= player-id (:current-player @state)) "text-primary")
                     (when (= player-id (:next-player @state)) "border border-primary")]}
       [:div (if (#{3 4 5 6 7} seat-number)
               (string/join [(str committed) " / " stack])
               (string/join [stack " / " (str committed)]))]
       [:div username]])))

(defn pots []
  [:div.col
   (map-indexed (fn [idx [amount players]]
                  [:span {:key (gstring/format "pot-%d" idx)} amount])
                (:pots @state))])

(defn players []
  [:div.players
   [:div.row
    [:div.col] [:div.col]
    [:div.col (player 2)] [:div.col] [:div.col (player 3)]
    [:div.col] [:div.col]]
   [:div.row
    [:div.col] [:div.col (player 1)]
    [:div.col] [:div.col] [:div.col]
    [:div.col (player 4)] [:div.col]]
   [:div.row
    [:div.col (player 0)] [:div.col]
    [:div.col] (pots) [:div.col]
    [:div.col] [:div.col (player 5)]]
   [:div.row
    [:div.col] [:div.col (player 9)]
    [:div.col] [:div.col] [:div.col]
    [:div.col (player 6)] [:div.col]]
   [:div.row
    [:div.col] [:div.col]
    [:div.col (player 8)] [:div.col] [:div.col (player 7)]
    [:div.col] [:div.col]]])

(defn action [[action-kw minimum] key]
  [:div.action.form-group {:key key}
   [:input.btn.form-control {:type "button"
                             :value (get {:bet "Bet"
                                          :fold "Fold"
                                          :check "Check"
                                          :call "Call"
                                          :raise "Raise"
                                           :all "All In"} action-kw)
                             :on-click (fn [event]
                                         (post-action action-kw minimum))}]
   (if (not= minimum 0)
     [:input.form-control {:placeholder minimum}]
     [:div.col])])

(defn hand []
  (let [player-id (:current-player @state)
        hole-cards (get-in @state [:hole-cards player-id])]
    [:div.hand.row
     (card (first hole-cards) "hand-0")
     (card (second hole-cards) "hand-1")
     (when (= player-id (:next-player @state))
       (if (not-empty (:winners @state))
         [:div.col-sm
          [:div.action.form-group
           [:input.btn.form-control {:type "button"
                                     :value "Next Hand"
                                     :on-click (fn [event] (next-hand))}]]]
         [:div.col-sm
          (map-indexed (fn [idx possible]
                         (action possible (gstring/format "action-%d" idx)))
                       (:possible-actions @state))]))]))

(defn player-username [player-id]
  (->> (:seat-numbers @state)
       vals
       (filter #(= player-id (:player-id %)))
       first
       :username))

(defn winners []
  (when (not-empty (:winners @state))
    [:div.alert.alert-success
     (map-indexed (fn [idx [player-id amount]]
                    [:div {:key (gstring/format "winner-%d" idx)}
                     (gstring/format "%s won $%d"
                                     (player-username player-id)
                                     amount)])
                  (:winners @state))]))

(defn home []
  (refresh-state)
  (fn []
    [:div.container
     (board)
     (players)
     (hand)
     (winners)]))

(defn init! []
  (reagent/render [#'home]
                  (.getElementById js/document "app")))
