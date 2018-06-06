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
WITH latest_events AS (
    SELECT seat_number, MAX(event_time) AS event_time
    FROM seats
    WHERE game_id = :game-id
    GROUP BY seat_number
    )
SELECT player_id, seats.seat_number
FROM seats
    INNER JOIN latest_events
    ON seats.seat_number = latest_events.seat_number
    AND seats.event_time = latest_events.event_time
WHERE player_id IS NOT NULL
ORDER BY seats.seat_number

-- :name add-player! :<! :1
INSERT INTO seats (game_id, player_id, seat_number, event_time)
    VALUES (:game-id, :player-id, :seat-number, now())
    RETURNING seat_number

-- :name last-dealer :? :1
SELECT players[array_upper(players, 1)] AS player_id
FROM hands
WHERE id = (
    SELECT max(id)
    FROM hands
    WHERE game_id = :game-id
)

-- :name start-hand! :<! :1
INSERT INTO hands (game_id, seed, players, event_time)
    VALUES (:game-id, :seed, :players, now())
    RETURNING id

-- :name insert-card! :<! :1
INSERT INTO cards (hand_id, player_id, board_idx, deal_to, card_suit, card_rank, event_time)
    VALUES (:hand-id, :player-id, :board-idx, :deal-to, :card-suit, :card-rank, now())
    RETURNING id

-- :name insert-action! :<! :1
INSERT INTO actions (hand_id, player_id, idx, player_action, amount, event_time)
    VALUES (:hand-id, :player-id, :idx, :action, :amount, now())
    RETURNING idx

-- :name players-total-stack :? :1
SELECT SUM(delta)
FROM stacks
WHERE game_id = :game-id
    AND player_id = :player-id
GROUP BY game_id, player_id

-- :name insert-stack-delta :! :n
INSERT INTO stacks (game_id, player_id, delta, event_time)
    VALUES (:game-id, :player-id, :delta, now())

-- :name insert-small-blind-delta :! :n
INSERT INTO stacks (game_id, player_id, delta, event_time)
    VALUES (:game-id, :player-id, -1 * (SELECT small_blind FROM games WHERE id = :game-id), now())

-- :name insert-big-blind-delta :! :n
INSERT INTO stacks (game_id, player_id, delta, event_time)
    VALUES (:game-id, :player-id, -1 * (SELECT big_blind FROM games WHERE id = :game-id), now())
