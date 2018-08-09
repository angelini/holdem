# holdem

generated using Luminus version "2.9.12.54"

## run

1. start Postgres with a `holdem` database
2. `lein figwheel`
3. `lein repl`

```clojure
(restart)
(do (reset-db) (restart-db) (holdem.game/example-game))
```

## deploy

1. start sql-1 & web-1

```
./scripts/deploy.sh web-1
```
