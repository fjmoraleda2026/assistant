#!/usr/bin/env sh
# ----------------------------------------------------------------------------
# Maven Wrapper
# ----------------------------------------------------------------------------

basedir="$(dirname "$0")"
exec java -jar "$basedir/.mvn/wrapper/maven-wrapper.jar" "$@"
