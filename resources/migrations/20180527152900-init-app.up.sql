CREATE TABLE games (
        id serial PRIMARY KEY,
        table_size integer NOT NULL,
        small_blind integer NOT NULL,
        big_blind integer NOT NULL,
        start_time timestamp NOT NULL,
        end_time timestamp NOT NULL
        );

CREATE TABLE players (
        id serial PRIMARY KEY,
        username varchar NOT NULL,
        join_time timestamp NOT NULL
        );

CREATE TABLE hands (
        id serial PRIMARY KEY,
        game_id integer REFERENCES games(id) NOT NULL,
        players integer[] NOT NULL,
        start_time timestamp NOT NULL
        );

CREATE TYPE deal_to
    AS ENUM ('hand', 'board', 'burn');

CREATE TYPE card_suit
    AS ENUM ('heart', 'spade', 'diamond', 'club');

CREATE TABLE cards (
        id serial PRIMARY KEY,
        player_id integer REFERENCES players(id) NOT NULL,
        deal_to deal_to NOT NULL,
        card_suit card_suit NOT NULL,
        card_rank smallint NOT NULL,
        show_time timestamp NOT NULL
        );

CREATE TYPE player_action
    AS ENUM ('bet', 'fold', 'raise', 'call');

CREATE TABLE actions (
        hand_id integer REFERENCES hands(id) NOT NULL,
        player_id integer REFERENCES players(id) NOT NULL,
        seq integer NOT NULL CHECK (seq >= 0),
        player_action player_action NOT NULL,
        amount integer,
        play_time timestamp NOT NULL
        );

CREATE TABLE seats (
        game_id integer REFERENCES games(id) NOT NULL,
        player_id integer REFERENCES players(id) NOT NULL,
        seat_number smallint NOT NULL,
        join_time timestamp NOT NULL
        );
