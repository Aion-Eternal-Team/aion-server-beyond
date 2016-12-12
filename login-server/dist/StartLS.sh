#!/bin/bash
#=====================================================================================
# Usage:        StartLS.sh [-noloop]
# Parameters:   -noloop
#                   disables restart loop service (server restart command won't work)
# Description:  Starts LS.
#=====================================================================================

run_ls() {
  if [ ! -d libs ]; then
    echo "Exiting script: ./libs not found. Please check your working directory."
    exit 1
  elif [ -f loginserver.pid ] && ps -p `cat loginserver.pid` > /dev/null 2>&1; then
    echo "Exiting script: LoginServer is already running."
    exit 2
  fi

  echo "LoginServer is starting now."
  # activate job control in this script
  set -m
  # run server as a background job to instantly write PID file
  java -Xms32m -Xmx32m -XX:+TieredCompilation -XX:+UseNUMA -server -ea -cp "libs/*" com.aionemu.loginserver.LoginServer &
  echo $! > loginserver.pid
  # put job in foreground again (wait for LS termination) and return exit code
  fg %+
  return $?
}

loop() {
  echo "LoginServer restart loop is active."
  while true; do
    run_ls
    err=$?
    case ${err} in
      0) # LS exit code: shutdown
        echo "LoginServer stopped."
        break
        ;;
      1) # LS exit code: critical config or build error
        echo "LoginServer stopped with a fatal error."
        break
        ;;
      2) # LS exit code: restart
        echo "Restarting LoginServer..."
        ;;
      137) # LS process was killed
        echo "LoginServer process was killed."
        break
        ;;
      *) # other
        echo "LoginServer has terminated abnormally, restarting in 5 seconds."
        sleep 5
        ;;
    esac
  done
  echo "LoginServer restart loop has ended."
}

cd `dirname $(readlink -f $0)`
if [ $# -gt 0 ] && [ $1 = "-noloop" ]; then
  run_ls
else
  loop
fi
