#!/bin/bash
set -o errexit -o nounset -o pipefail
cd "`dirname $0`/.."

clj -A:dev:test:sqlite:h2:postgresql -M -m user
