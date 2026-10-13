#!/usr/bin/env bash
# Validates Flowseal-derived ciadpi preset args by starting ciadpi with each preset.
# Usage: bash tools/validate-presets.sh [path-to-presets.list]
set -u
here="$(cd "$(dirname "$0")" && pwd)"
byedpi_dir="$here/../app/src/main/cpp/byedpi"
list="${1:-$here/../app/src/main/assets/flowseal_presets.list}"
port=11099

cd "$byedpi_dir" || exit 1
[ -x ./ciadpi ] || { echo "ciadpi not built; run: make in $byedpi_dir"; exit 1; }

total=0; failed=0
while IFS='|' read -r name args; do
  [ -z "${args:-}" ] && continue
  case "$args" in \#*) continue;; esac
  total=$((total+1))
  test_args=$(printf '%s' "$args" | sed 's/{list:[^}]*}/discord.com youtube.com google.com/g')
  out=$(eval "timeout 2 ./ciadpi -p $port $test_args" 2>&1)
  rc=$?
  if [ "$rc" -eq 124 ] || [ "$rc" -eq 143 ] || [ "$rc" -eq 0 ]; then
    printf 'OK   %s\n' "$name"
  else
    failed=$((failed+1))
    printf 'FAIL %s (rc=%s) %s\n' "$name" "$rc" "$(printf '%s' "$out" | head -3 | tr '\n' ' ')"
  fi
done < "$list"
printf 'checked=%s failed=%s\n' "$total" "$failed"