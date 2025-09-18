#A catalog generator

mvn clean install
mvn exec:java -Dwildfly-version=36.0.1.Final -Dexec.jvmArgs="-agentlib:jdwp=transport=dt_socket,address=8000,server=y,suspend=y"

