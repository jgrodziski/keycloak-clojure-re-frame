#!/usr/bin/env bash

ARTIFACT_NAME=$(clj -A:artifact-name)
ARTIFACT_ID=$(echo "$ARTIFACT_NAME" | cut -f1)
ARTIFACT_VERSION=$(echo "$ARTIFACT_NAME" | cut -f2)
JAR_FILENAME="$ARTIFACT_ID-$ARTIFACT_VERSION.jar"

LIB_ID=keycloak-clojure-re-frame

echo -e "Build \"$LIB_ID\" jar: target/$JAR_FILENAME"

clj -A:build --app-group-id $LIB_ID --app-artifact-id $ARTIFACT_ID --app-version $ARTIFACT_VERSION 2>&1 > /dev/null

if [ $? -eq 0 ]; then
    echo "Successfully built \"$LIB_ID\"'s artifact: target/$JAR_FILENAME"
else
    echo "Fail to built \"$LIB_ID\"'s artifact!"
    exit 1
