#!/usr/bin/env bash
# The home screen's favourites row, for agents and scripts: no remote, no long-press.
#   optimize/dock.sh show                  the current order, left to right
#   optimize/dock.sh apps                  every app that can go in it: package | name
#   optimize/dock.sh set PKG1 PKG2 ...     a new order, left to right (up to 10; fewer than 5 is topped up)
#   optimize/dock.sh reset                 back to the defaults in android/launcher/.../config.xml
#   optimize/dock.sh hidden                apps left off the home screen, and why: package | reason
#   optimize/dock.sh hide PKG ...          take these off the home screen (all its lists)
#   optimize/dock.sh show PKG ...          put these on it, even against the rules (the TV's own home doesn't list them)
#   optimize/dock.sh tvkey PKG             the remote's TV button opens this app (turns on the home app's key service)
#   optimize/dock.sh tvkey none            the TV button does what the TV does (turns the service off)
# The home screen redraws at once. Only ADB can do this (sa.urtv.home/.DockReceiver needs the shell's DUMP).
set -euo pipefail
cd "$(dirname "$0")/.."
PY="${PY:-optimize/venv/bin/python}"
send() {   # $1 action, $2 extra arguments; prints the receiver's answer (the "data" of am broadcast)
  "$PY" -I optimize/tv.py "am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home.$1 ${2:-}" \
    | sed -n '/data="/,$p' | sed '1s/.*data="//; $s/"$//'
}
case "${1:-}:$#" in
  show:1) send GET_DOCK ;;
  apps:*)  send LIST_APPS ;;
  reset:*) send RESET_DOCK ;;
  hidden:*) send HIDDEN ;;
  hide:*|show:*)
    what=$(tr '[:lower:]' '[:upper:]' <<< "$1")
    shift
    [ $# -gt 0 ] || { echo "usage: optimize/dock.sh hide|show PKG ..."; exit 1; }
    for p in "$@"; do [[ "$p" =~ ^[A-Za-z0-9_.]+$ ]] || { echo "not a package name: $p"; exit 1; }; done
    send "$what" "--es apps $(IFS=,; echo "$*")" ;;
  tvkey:2)
    svc=sa.urtv.home/sa.urtv.home.TvKeyService
    if [ "$2" = none ]; then
      send TV_KEY "--es app none"
      # take only our service out of the list, keep any other the owner turned on
      "$PY" -I optimize/tv.py "s=\$(settings get secure enabled_accessibility_services); n=\$(echo \"\$s\" | tr ':' ' ' | xargs -n1 | grep -v -x -e '$svc' -e null | paste -sd: -); settings put secure enabled_accessibility_services \"\${n:-}\"" > /dev/null
    else
      [[ "$2" =~ ^[A-Za-z0-9_.]+$ ]] || { echo "not a package name: $2"; exit 1; }
      send TV_KEY "--es app $2"
      "$PY" -I optimize/tv.py "s=\$(settings get secure enabled_accessibility_services); case \":\$s:\" in *:$svc:*) ;; *) if [ -z \"\$s\" ] || [ \"\$s\" = null ]; then s=$svc; else s=\$s:$svc; fi; settings put secure enabled_accessibility_services \"\$s\";; esac; settings put secure accessibility_enabled 1" > /dev/null
    fi
    "$PY" -I optimize/tv.py "settings get secure enabled_accessibility_services" ;;
  set:*)
    shift
    [ $# -gt 0 ] || { echo "usage: optimize/dock.sh set PKG1 PKG2 ..."; exit 1; }
    for p in "$@"; do [[ "$p" =~ ^[A-Za-z0-9_.]+$ ]] || { echo "not a package name: $p"; exit 1; }; done
    send SET_DOCK "--es apps $(IFS=,; echo "$*")" ;;
  *) sed -n '2,12p' "$0"; exit 1 ;;
esac
