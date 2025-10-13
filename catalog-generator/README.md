# WildFly catalog generator

## Build the project
mvn clean install

## Generate a catalog for a WildFly version

mvn exec:java -Dwildfly-version=<WildFly Version> 

## Validate only

mvn exec:java -Dwildfly-version=<WildFly Version> -DvalidateOnly=true

