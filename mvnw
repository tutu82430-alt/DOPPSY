#!/bin/sh
# ==============================================================================
# Doppsy Healthcare Concierge — Maven Wrapper
# ==============================================================================
set -e

# Delegate to system Maven if available (standard in GitHub Actions ubuntu-latest)
if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi

# Fallback to M2_HOME / MAVEN_HOME
if [ -n "$M2_HOME" ] && [ -x "$M2_HOME/bin/mvn" ]; then
  exec "$M2_HOME/bin/mvn" "$@"
elif [ -n "$MAVEN_HOME" ] && [ -x "$MAVEN_HOME/bin/mvn" ]; then
  exec "$MAVEN_HOME/bin/mvn" "$@"
fi

echo "Error: Maven not found on PATH. Please ensure JDK/Maven is configured." >&2
exit 1
