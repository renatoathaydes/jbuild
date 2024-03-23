#!/usr/bin/env sh

JBUILD_JAR=${JBUILD_JAR:-jbuild.jar}
BUILD_DIR=${BUILD_DIR:-build}

API_FILES=$(find jbuild-api/src -name "*.java")
CLASS_FILE_PARSER_FILES=$(find jbuild-classfile-parser/src -name "*.java")
JBUILD_FILES=$(find src/main/java -name "*.java")

if [[ -z "$JAVA_HOME" ]]; then
  JAVA_DIR=""
else
  JAVA_DIR="$JAVA_HOME/bin/"
fi

mkdir -p build
set -e

echo "Compiling class files to $BUILD_DIR..."

"${JAVA_DIR}javac" --release=11 -encoding utf-8 -Werror -parameters -d "$BUILD_DIR" $API_FILES $CLASS_FILE_PARSER_FILES $JBUILD_FILES

echo "Creating runnable jar $JBUILD_JAR..."

"${JAVA_DIR}jar" --create --file "$JBUILD_JAR" -e jbuild.cli.Main -C "$BUILD_DIR" .

echo "Done!"
