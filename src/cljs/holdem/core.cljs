(ns holdem.core
  (:require [ajax.core :refer [GET POST]]
            [clojure.string :as string]
            [goog.string :as gstring]
            [goog.string.format]
            [reagent.core :as reagent]))

(defonce state
  (reagent/atom nil))

(defonce logs
  (reagent/atom nil))

(defn refresh-state []
  (GET (gstring/format "/games/%d/state" js/gameId)
       {:headers {"Accept" "application/transit+json"}
        :handler #(reset! state %)}))

(defn refresh-logs []
  (GET (gstring/format "/games/%d/logs" js/gameId)
       {:headers {"Accept" "application/transit+json"}
        :handler #(reset! logs %)}))

(defn refresh []
  (refresh-state)
  (refresh-logs))

(defn post-action [action-kw amount]
  (POST (gstring/format "/games/%d/hands/%d" js/gameId (:hand-id @state))
        {:params {:action action-kw
                  :amount amount
                  :phase (:phase @state)}
         :headers {"x-csrf-token" js/csrfToken}
         :handler #(refresh)}))

(defn next-hand []
  (POST (gstring/format "/games/%d/hands" js/gameId)
        {:headers {"x-csrf-token" js/csrfToken}
         :handler #(refresh)}))

(defn card [c key]
  [:div.col-2 {:key key}
   [:div.border.border-dark.card-value
    (if (= c :hidden)
      [:div
       [:div.rank " "]
       [:div.suit " "]]
      [:div.text-center {:class (when (#{:heart :diamond} (:suit c)) "text-danger")}
       [:div.rank
        (if (> (:rank c) 10)
          (get {11 "J"
                12 "Q"
                13 "K"
                14 "A"} (:rank c))
          (:rank c))]
       [:div.suit
        (get {:heart "♥"
              :spade "♠"
              :diamond "♦"
              :club "♣"} (:suit c))]])]])

(defn board []
  [:div.board.row
   [:div.col]
   (map-indexed (fn [idx board-card]
                  (card board-card (gstring/format "board-%d" idx)))
                (:board @state))
   [:div.col]])

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

(defn player [seat-number]
  (if (not (contains? (:seat-numbers @state) seat-number))
    [:div.text-center "Open"]
    (let [{player-id :player-id
           username :username} (get-in @state [:seat-numbers seat-number])
          committed (get-in @state [:committed player-id] 0)
          stack (- (get-in @state [:stacks player-id]) committed)]
      [:div.text-center
       {:class [(when (= player-id (:current-player @state)) "text-primary")
                (when (= player-id (:next-player @state)) "border border-primary")]}
       [:div (if (#{3 4 5 6 7} seat-number)
               (string/join [(str committed) " < " stack])
               (string/join [stack " > " (str committed)]))]
       [:div username]])))

(defn pots []
  [:div.col-2
   (map-indexed (fn [idx [amount players]]
                  [:div.text-center {:key (gstring/format "pot-%d" idx)}
                   (* amount (count players))])
                (:pots @state))])

(defn players []
  [:div.players
   [:div.row
    [:div.col] [:div.col]
    [:div.col (player 2)] [:div.col (player 3)]
    [:div.col] [:div.col]]
   [:div.row
    [:div.col] [:div.col (player 1)]
    [:div.col] [:div.col]
    [:div.col (player 4)] [:div.col]]
   [:div.row
    [:div.col (player 0)] [:div.col]
    (pots)
    [:div.col] [:div.col (player 5)]]
   [:div.row
    [:div.col] [:div.col (player 9)]
    [:div.col] [:div.col]
    [:div.col (player 6)] [:div.col]]
   [:div.row
    [:div.col] [:div.col]
    [:div.col (player 8)] [:div.col (player 7)]
    [:div.col] [:div.col]]])

(defn action [[action-kw minimum] key]
  [:div.action.form-group.row {:key key}
   [:div.col
    [:input.col.btn.btn-primary.form-control
     {:type "button"
      :value (get {:bet "Bet"
                   :fold "Fold"
                   :check "Check"
                   :call "Call"
                   :raise "Raise"
                   :all "All In"} action-kw)
      :on-click (fn [event]
                  (let [nodes (-> (.-target event)
                                  .-parentNode
                                  .-parentNode
                                  (.querySelectorAll "input"))
                        node-value (if (= 2 (.-length nodes))
                                     (-> nodes
                                         (aget 1)
                                         .-value)
                                     "")
                        value (if (= node-value "")
                                minimum
                                (js/parseInt node-value))]
                    (post-action action-kw value)))}]]
   (if (not= minimum 0)
     [:div.col
      [:input.col.form-control {:placeholder minimum}]]
     [:div.col])])

(defn hand []
  (let [player-id (:current-player @state)
        hole-cards (get-in @state [:hole-cards player-id])]
    [:div.hand.row
     [:div.col]
     (card (first hole-cards) "hand-0")
     (card (second hole-cards) "hand-1")
     [:div.col-2]
     (if (= player-id (:next-player @state))
       (if (not-empty (:winners @state))
         [:div.col-4
          [:div.action.form-group
           [:input.btn.btn-primary.form-control
            {:type "button"
             :value "Next Hand"
             :on-click (fn [event] (next-hand))}]]]
         [:div.col-4
          (map-indexed (fn [idx possible]
                         (action possible (gstring/format "action-%d" idx)))
                       (:possible-actions @state))])
       [:div.col-4])
     [:div.col]]))

(defn hand-logs [hand actions key]
  (map-indexed (fn [idx [player action-type value]]
                 [:span {:key (gstring/format "%s-%d" key idx)}
                  (gstring/format "h: %d, p: %-10s, t: %-5s, v: %d"
                                  hand
                                  (player-username player)
                                  (name action-type)
                                  value)])
               actions))

(defn log-viewer []
  [:div.logs.row
   [:pre.col-4
    (map-indexed (fn [idx {hand :hand-id
                            actions :actions}]
                   (hand-logs hand actions (gstring/format "log-%d" hand)))
                 (or @logs []))]])

(defn home []
  (refresh)
  (fn []
    [:div.container
     (board)
     (winners)
     (players)
     (hand)
     (log-viewer)]))

(defn init! []
  (reagent/render [#'home]
                  (.getElementById js/document "app")))
