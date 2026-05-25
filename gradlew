@rem
@rem Copyright 2015 the original author or authors.
@rem Licensed under the Apache License...
@rem

#!/bin/sh

# Add default JVM options here.
DEFAULT_JVM_OPTS=""

APP_NAME="Gradle"
APP_HOME=$( cd "${0%[/\\]*}" > /dev/null; cd .. && pwd )

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

exec "$JAVA_HOME/bin/java" $DEFAULT_JVM_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
