#A catalog generator

mvn clean install
MAVEN_OPTS="-agentlib:jdwp=transport=dt_socket,address=8000,server=y,suspend=y" mvn exec:java -Dwildfly-version=36.0.1.Final "-agentlib:jdwp=transport=dt_socket,address=8000,server=y,suspend=y"

