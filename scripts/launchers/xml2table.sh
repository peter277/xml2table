#!/usr/bin/env bash

# Detect MSYS2 or Cygwin environment (running on Windows) vs true Linux/Unix
function is_msys2() {
  if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" ]]; then
    return 0  # True for MSYS2 or Cygwin
  elif [[ "$(uname -o 2>/dev/null)" == "Msys" || "$(uname -o 2>/dev/null)" == "Cygwin" ]]; then
    return 0  # True for MSYS2 or Cygwin
  fi
  return 1  # False for other environments
}

# Resolve module path relative to script location
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$SCRIPT_DIR/modules"

# Launch function
function xml2table() {
  if is_msys2; then
    # Convert module path to Windows format for MSYS2
    WIN_MODULE_PATH=$(cygpath -w "$MODULE_DIR")
    MSYS2_ARG_CONV_EXCL='/' \
    java --module-path "$WIN_MODULE_PATH" \
         --module com.github.peter277.xml2table/com.github.peter277.xml2table.Main "$@"
  else
    # Linux/Unix: use regular path
    java --module-path "$MODULE_DIR" \
         --module com.github.peter277.xml2table/com.github.peter277.xml2table.Main "$@"
  fi
}

# Call xml2table with all the arguments passed to the script
xml2table "$@"
