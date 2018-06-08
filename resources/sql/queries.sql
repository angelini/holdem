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

-- :name hand-big-blind :? :1
SELECT big_blind
FROM games
WHERE id = (
    SELECT game_id
    FROM hands
    WHERE id = :hand-id
)

-- :name current-hand-and-big-blind :? :1
SELECT max(h.id) AS hand_id, g.big_blind
FROM hands h
    INNER JOIN games g
    ON h.game_id = g.id
WHERE h.game_id = :game-id
GROUP BY g.id

-- :name start-hand! :<! :1
INSERT INTO hands (game_id, seed, players, event_time)
    VALUES (:game-id, :seed, :players, now())
    RETURNING id

-- :name board :? :*
SELECT card_suit, card_rank
FROM cards
WHERE hand_id = :hand-id
    AND board_idx IS NOT NULL
ORDER BY board_idx

-- :name insert-card! :<! :1
INSERT INTO cards (hand_id, player_id, board_idx, deal_to, card_suit, card_rank, event_time)
    VALUES (:hand-id, :player-id, :board-idx, :deal-to, :card-suit, :card-rank, now())
    RETURNING id

-- :name insert-action! :<! :1
INSERT INTO actions (hand_id, idx, phase, player_id, player_action, amount, event_time)
    VALUES (:hand-id, :idx, :phase, :player-id, :action, :amount, now())
    RETURNING idx

-- :name insert-small-blind-action :! :n
INSERT INTO actions (hand_id, idx, phase, player_id, player_action, amount, event_time)
    VALUES (:hand-id, 0, 'pre', :player-id, 'small', (SELECT small_blind FROM games WHERE id = :game-id), now())

-- :name insert-big-blind-action :! :n
INSERT INTO actions (hand_id, idx, phase, player_id, player_action, amount, event_time)
    VALUES (:hand-id, 1, 'pre', :player-id, 'big', (SELECT big_blind FROM games WHERE id = :game-id), now())

-- :name players-total-stack :? :1
SELECT sum(delta)
FROM stacks
WHERE game_id = :game-id
    AND player_id = :player-id

-- :name insert-stack-delta :! :n
INSERT INTO stacks (game_id, player_id, delta, event_time)
    VALUES (:game-id, :player-id, :delta, now())

-- :name game-state :? :*
WITH player_stacks AS (
    SELECT player_id, sum(delta) AS stack
    FROM stacks
    WHERE game_id = :game-id
    GROUP BY player_id
), player_actions_amounts AS (
    SELECT player_id, array_agg(player_action) AS player_actions, array_agg(amount) AS amounts
    FROM actions
    WHERE hand_id = :hand-id
    GROUP BY player_id
), player_cards AS (
    SELECT player_id, array_agg(card_suit) AS card_suits, array_agg(card_rank) AS card_ranks
    FROM cards
    WHERE hand_id = :hand-id
    GROUP BY player_id
)
SELECT s.player_id, s.stack, a.player_actions, a.amounts, c.card_suits, c.card_ranks
FROM player_stacks s
    INNER JOIN player_actions_amounts a
    ON s.player_id = a.player_id
    INNER JOIN player_cards c
    ON s.player_id = c.player_id

-- :name logs :? :*
SELECT hand_id, array_agg(player_id) AS player_ids, array_agg(player_action) AS player_actions, array_agg(amount) AS amounts
FROM actions
WHERE hand_id IN (
    SELECT id
    FROM hands
    WHERE game_id = :game-id
)
GROUP BY hand_id
ORDER BY hand_id
