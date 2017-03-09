#!/bin/bash

mkdir -p ~/.ipython/kernels/simple-kernel-nashorn/
START_SCRIPT_PATH=$(cd `dirname "${BASH_SOURCE[0]}"` && pwd)/build/libs/simple-kernel-nashorn-all-1.0-SNAPSHOT.jar
JAVA_PATH=$(which java)
CONTENT='{
   "argv": ["'${JAVA_PATH}'", "'-jar'", "'${START_SCRIPT_PATH}'", "{connection_file}"],
                "display_name": "simple-kernel-nashorn",
                "language": "simple-kernel-nashorn"
}'
echo $CONTENT > ~/.ipython/kernels/simple-kernel-nashorn/kernel.json
