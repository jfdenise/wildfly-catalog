#!/bin/bash

set -e

# Build the catalog generator
pushd catalog-generator
mvn clean install
popd

latest=$( tail -n 1 versions.txt )
if [ -z "$latest" ]; then
  echo "No WildFly version found in versions.txt, catalog has not been built"
  exit 0
fi

echo "Building the catalog for WildFly $latest"
pushd catalog-generator
mvn exec:java "-Dwildfly-version=${latest}" "$@"
popd
