CREATE TABLE players (
        id serial PRIMARY KEY,
        username varchar NOT NULL,
        password_hash varchar NOT NULL,
        event_time timestamp NOT NULL
        );

CREATE TABLE games (
        id serial PRIMARY KEY,
        table_size integer NOT NULL CHECK (table_size >= 1 AND table_size <= 10),
        small_blind integer NOT NULL CHECK (small_blind >= 0),
        big_blind integer NOT NULL CHECK (big_blind >= 0),
        event_time timestamp NOT NULL
        );

CREATE TABLE seats (
        game_id integer REFERENCES games(id) NOT NULL,
        player_id integer REFERENCES players(id),
        seat_number smallint NOT NULL CHECK (seat_number >= 0),
        event_time timestamp NOT NULL
        );

CREATE TABLE hands (
        id serial PRIMARY KEY,
        game_id integer REFERENCES games(id) NOT NULL,
        seed integer NOT NULL,
        players integer[] NOT NULL,
        event_time timestamp NOT NULL
        );

CREATE TYPE phase
    AS ENUM ('pre', 'flop', 'turn', 'river');

CREATE TABLE pots (
        id serial PRIMARY KEY,
        hand_id integer REFERENCES hands(id) NOT NULL,
        phase phase NOT NULL,
        amount integer NOT NULL CHECK (amount >= 0),
        players integer[] NOT NULL,
        event_time timestamp NOT NULL
        );

CREATE TYPE deal_to
    AS ENUM ('hand', 'board', 'burn');

CREATE TYPE card_suit
    AS ENUM ('heart', 'spade', 'diamond', 'club');

CREATE TABLE cards (
        id serial PRIMARY KEY,
        hand_id integer REFERENCES hands(id) NOT NULL,
        player_id integer REFERENCES players(id),
        board_idx integer,
        deal_to deal_to NOT NULL,
        card_suit card_suit NOT NULL,
        card_rank smallint NOT NULL CHECK (card_rank >= 2 AND card_rank <= 14),
        event_time timestamp NOT NULL
        );

CREATE TYPE player_action
    AS ENUM ('small', 'big', 'bet', 'fold', 'check', 'call', 'raise', 'all');

CREATE TABLE actions (
        hand_id integer REFERENCES hands(id) NOT NULL,
        idx integer NOT NULL CHECK (idx >= 0),
        phase phase NOT NULL,
        player_id integer REFERENCES players(id) NOT NULL,
        player_action player_action NOT NULL,
        amount integer,
        event_time timestamp NOT NULL,
        PRIMARY KEY (hand_id, idx)
        );

CREATE TABLE stacks (
        game_id integer REFERENCES games(id) NOT NULL,
        player_id integer REFERENCES players(id) NOT NULL,
        delta integer NOT NULL,
        event_time timestamp NOT NULL
        );
