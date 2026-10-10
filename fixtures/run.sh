#!/bin/sh
# Replays every fixture in manifest.txt through the real parser (blackberry-deploy -parse) and compares
# the events with <name>.expected.ndjson.
#
#   Usage:  sh fixtures/run.sh [--update]
#   Run it from the project root, or set BBD to the executable:  BBD=/path/to/blackberry-deploy sh fixtures/run.sh
#
# Exit code: 0 only if every fixture matched.

BBD=${BBD:-./blackberry-deploy/target/blackberry-deploy}

# Make a relative path absolute BEFORE changing directory below.
case "$BBD" in
  /*) ;;
  */*) BBD="$PWD/$BBD" ;;
esac

if [ ! -x "$BBD" ] && ! command -v "$BBD" >/dev/null 2>&1; then
  echo "executable not found: $BBD" >&2
  echo "set BBD to the blackberry-deploy executable, e.g.  BBD=/path/to/blackberry-deploy sh $0" >&2
  exit 2
fi

cd "$(dirname "$0")" || exit 2
fail=0

while read -r name method; do
  case "$name" in ''|'#'*) continue ;; esac

  if [ ! -f "$name.out" ]; then
    echo "MISSING $name.out"; fail=1; continue
  fi

  actual=$("$BBD" -parse "$method" < "$name.out")
  status=$?
  if [ "$status" -ne 0 ] || [ -z "$actual" ]; then
    echo "FAILED  $name (parser exited with $status, or printed nothing)"; fail=1; continue
  fi

  if [ "$1" = "--update" ]; then
    printf '%s\n' "$actual" > "$name.expected.ndjson"
    echo "updated $name"
  elif [ ! -f "$name.expected.ndjson" ]; then
    echo "MISSING $name.expected.ndjson (copy it from the delivered fixtures, or run with --update to create it)"; fail=1
  elif [ "$actual" = "$(cat "$name.expected.ndjson")" ]; then
    echo "ok      $name"
  else
    echo "FAILED  $name"; fail=1
    printf '%s\n' "$actual" | diff - "$name.expected.ndjson" | head -20
  fi
done < manifest.txt

exit $fail
