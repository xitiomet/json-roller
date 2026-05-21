#!/bin/bash
echo -n "Uploading files to openstatic.org..."
{
    scp target/json-roller-*.jar openstatic.org:openstatic.org/projects/json-roller/
    scp target/json-roller-*.deb openstatic.org:openstatic.org/projects/json-roller/
    scp target/json-roller-setup.exe openstatic.org:openstatic.org/projects/json-roller/
    scp target/json-roller.exe openstatic.org:openstatic.org/projects/json-roller/
    scp README.md openstatic.org:openstatic.org/projects/json-roller/
} &> /dev/null
echo " Done."