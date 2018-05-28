-- :name username-exists :? :1
SELECT id
FROM players
WHERE username = :username

-- :name create-player! :<! :1
INSERT INTO players (username, event_time)
    VALUES (:username, now())
    RETURNING id

-- :name start-game! :<! :1
INSERT INTO games (table_size, small_blind, big_blind, event_time)
    VALUES (:table-size, :small-blind, :big-blind, now())
    RETURNING id

-- :name seated-players :? :*
SELECT player_id, seat_number
FROM seats
WHERE game_id = :game-id

-- :name add-player! :<! :1
INSERT INTO seats (game_id, player_id, seat_number, event_time)
    VALUES (:game-id, :player-id, :seat-number, now())
    RETURNING seat_number

-- :name start-hand! :<! :1
INSERT INTO hands (game_id, seed, players, event_time)
    VALUES (:game-id, :seed, :players, now())
    RETURNING id

-- :name show-card! :<! :1
INSERT INTO cards (player_id, deal_to, card_suit, card_rank, event_time)
    VALUES (:player-id, :deal-to, :card-suit, :card-rank, now())
    RETURNING id

-- :name insert-action! :<! :1
INSERT INTO actions (hand_id, player_id, seq, player_action, amount, event_time)
    VALUES (:hand-id, :player-id, :seq, :action, :amount, now())
    RETURNING seq
