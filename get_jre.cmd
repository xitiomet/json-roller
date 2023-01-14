@echo off
jlink.exe -v --add-modules java.base,java.logging,java.management,java.naming,java.security.jgss,java.sql,java.xml --output c:\Users\brian\src\json-roller\jre --strip-debug --compress 2 --no-header-files --no-man-pages
pause
