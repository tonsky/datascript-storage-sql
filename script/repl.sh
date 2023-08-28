#!/bin/bash
set -o errexit -o nounset -o pipefail
cd "`dirname $0`/.."

clj -A:dev:test:sqlite:postgresql -M -m user
