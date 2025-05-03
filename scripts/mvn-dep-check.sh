#!/bin/bash

####################################################################################################
# Checks for available dependency updates using Maven.
# Useful for keeping project dependencies up to date.
# 
# Usage (from project root directory): ./scripts/mvn-dep-check.sh
####################################################################################################

# Library dependency updates
mvn org.codehaus.mojo:versions-maven-plugin:display-dependency-updates "-Dmaven.version.ignore=.*-M.*,.*-alpha.*,.*-beta.*,.*-rc.*"

# Plugin updates
mvn org.codehaus.mojo:versions-maven-plugin:display-plugin-updates
