Running the check.
```
mvn -DoutputFileName=/tmp/output3.txt  com.ning.maven.plugins:maven-duplicate-finder-plugin:1.0.8-SNAPSHOT:check
```

## Setting your JAVA_HOME
```
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0.jdk/Contents/Home/
```

## Debugging Maven

```
export MAVEN_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=y"
```
