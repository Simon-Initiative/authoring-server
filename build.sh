#!/bin/sh
if [[ -n "$1" ]]; then
    mvn clean package && docker build -t oli/content-service:$1 .
else
    mvn clean package && docker build -t oli/content-service .
fi