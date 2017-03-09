## simple-kernel-nashorn

This builds of of https://github.com/dpressel/simple-kernel-java, which is a port https://github.com/dsblank/simple_kernel/ using java, jeromq and jackson.  
In this version, I extend the simple kernel to do something useful -- execute Nashorn JavaScript, as simply as possible.  The changes between this and the simple-kernel-java version are minimal!  If you understand the simple kernel, you will probably understand what's happening here too.

Currently the following features are supported

  - Execution of any valid Nashorn JavaScript statements
  - Serialization of backtrace errors, execution results, and stdout capture back to jupyter lab (or notebook)
  - Pygments JS highlighting
  
A couple of things are yet to be implemented:

  - History
  - Command completion

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