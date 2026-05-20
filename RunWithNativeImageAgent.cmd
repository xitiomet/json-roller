@echo off
java -agentlib:native-image-agent=config-merge-dir=src\main\resources\META-INF\native-image\ -jar target\json-roller-1.2.jar %*
