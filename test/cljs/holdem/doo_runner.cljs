(ns holdem.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [holdem.core-test]))

(doo-tests 'holdem.core-test)

