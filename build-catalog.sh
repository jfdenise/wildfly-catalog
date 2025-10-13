#!/bin/bash

set -e
latest=$( tail -n 1 versions.txt )
if [ -z "$latest" ]; then
  echo "No WildFly version found in versions.txt"
  exit 1
fi

echo "Building the catalog for WildFly $latest"
pushd catalog-generator
mvn clean install
mvn exec:java "-Dwildfly-version=${latest}" "$@"
popd




