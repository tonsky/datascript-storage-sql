#!/bin/bash
set -o errexit -o nounset -o pipefail
cd "`dirname $0`/.."

clj -A:test -M -m datascript.storage.sql.test-main
