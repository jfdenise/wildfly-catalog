#!/bin/bash

set -e

if [ -z "$1" ]; then
  echo "The new WildFly version must be passed as argument"
  exit 1
fi

cd catalog-generator
mvn clean install
mvn exec:java "-Dwildfly-version=${1}"

echo "DONE!"
echo "Commit all and push to the main branch"




