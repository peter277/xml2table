#!/bin/bash

####################################################################################################
# Checks for available dependency updates using Maven.
# Useful for keeping project dependencies up to date.
# 
# Usage (from project root directory): ./scripts/mvn-dep-check.sh
####################################################################################################

# Library dependency updates
mvn org.codehaus.mojo:versions-maven-plugin:display-dependency-updates "-Dmaven.version.ignore=(?i).*[-_\.](alpha|beta|rc|M\d).*"

# Plugin updates
mvn org.codehaus.mojo:versions-maven-plugin:display-plugin-updates "-Dmaven.version.ignore=(?i).*[-_\.](alpha|beta|rc|M\d).*"
