## simple-kernel-nashorn

This builds a JavaScript kernel on top of https://github.com/dpressel/simple-kernel-java (which is a port https://github.com/dsblank/simple_kernel/) using java, jeromq and jackson.  
In this version, I extend the simple kernel to do something useful, but still as simply as possible.  

The changes between this and the simple-kernel-java version are minimal!  If you understand the simple kernel, you will probably understand what's happening here too.

Currently the following features are supported

  - Execution of any valid Nashorn JavaScript statements
  - Serialization of backtrace errors, execution results, and stdout capture back to jupyter lab (or notebook)
    - Serializing stdout to the iopub channel is done by changing System.out to temporarily point at ExecutionPrintStream, and then back again after execution
  - Pygments JS highlighting
  - JavaScript pre-loading of extension libraries

A couple of things are yet to be implemented:

  - History
  - Command completion

*Note*: Do not use the Nashorn builtin _print_ function.  Use _console.log_ instead!


### Building it

Build the kernel and install it:
```
dpressel@dpressel:~/dev/work/simple-kernel-nashorn$ ./gradlew build && ./gradlew fatJar
:compileJava
Note: /home/dpressel/dev/work/simple-kernel-nashorn/src/main/java/Message.java uses unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.
:processResources UP-TO-DATE
:classes
:jar
:assemble
:compileTestJava UP-TO-DATE
:processTestResources UP-TO-DATE
:testClasses UP-TO-DATE
:test UP-TO-DATE
:check UP-TO-DATE
:build

BUILD SUCCESSFUL

Total time: 1.923 secs
:compileJava UP-TO-DATE
:processResources UP-TO-DATE
:classes UP-TO-DATE
:fatJar

BUILD SUCCESSFUL

Total time: 2.149 secs
dpressel@dpressel:~/dev/work/simple-kernel-nashorn$ ./install_script.sh 
```
### Running it

```
dpressel@dpressel:~/dev/work$ jupyter-notebook 
```

Or

```
dpressel@dpressel:~/dev/work$ jupyter lab

```

You should see simple-kernel-nashorn in the list of kernels

### Extending it

The JavaScript can be extended easily.  Have a look at extensions.list to see how to add a js library to the Kernel as a pre-defined import.