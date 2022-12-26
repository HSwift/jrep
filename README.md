# Jrep

A tool for searching for classes and methods based on a specified template. In the template you can specify the class
name, parent class, method name, method modifier, method parameter type and method return value type to find a specific
target.

## Usage

```
jrep options_list
Arguments:
    template -> jrep will look for the corresponding class and its methods based on the template { String }
Options:
    --files, -f -> files or directories for scanning, spilt by ':' (always required) { String }
    --show-statistics, -s [false] -> whether to show scan statistics
    --help, -h -> Usage info
```

## Template Syntax

Template = CLASS_NAME **:** PARENT_CLASS **{** METHOD ... **}**  
CLASS_NAME = * | **"regexp"** | **name**  
PARENT_CLASS = * | **class** **,** ...  
METHOD = MODIFIER ... METHOD_NAME **(** PARAM_TYPE **,** ... **)** RETURN_TYPE **;**  
MODIFIER = NULL | **static** | **final** | **abstract** | **transient** | **volatile** | **synchronized** | **native** |
**strictfp** | **interface**  
METHOD_NAME = * | **"regexp"** | **name**  
PARAM_TYPE = * | CLASS_TYPE | CLASS_TYPE **?**  
RETURN_TYPE = NULL | **:** CLASS_TYPE  
CLASS_TYPE = CLASS_TRANSFORMATION **class**  
CLASS_TRANSFORMATION = NULL | **extends** | **super**

Notes:

- `*` indicates any
- `...` indicates that the previous element can be repeated
- MODIFIER can be multiple, and jrep will search for methods that **contain** these modifiers
- If PARAM_TYPE is `*`, it means that the match of the remaining parameters is ignored
- If PARAM_TYPE is `CLASS_TYPE ?`, it means that this parameter type can be in any position and the number of parameters
  will be ignored for matching
- If CLASS_TRANSFORMATION is `NULL`, it means jrep strictly matches this type
- If CLASS_TRANSFORMATION is `extends`, it means jrep matches this type and its *subclasses*
- If CLASS_TRANSFORMATION is `super`, it means jrep matches this type and its *parent class*
- If there are multiple methods in the template, then jrep will only output the class that contains all the methods in
  the template. This means that the methods are `AND` related to each other

## Examples

Search for static methods of all classes in rt.jar and this method has only one parameter of type `java.lang.String`.

```shell
java -jar jrep-1.0-SNAPSHOT.jar ' * : * { static ".*"(java.lang.String); }' -f /usr/lib/jvm/java-8-openjdk/jre/lib/rt.jar
```

Search for all public static methods of classes under the "java" package in java.base.jmod, and this method has only one
parameter of type `java.lang.String`.

```shell
java -jar jrep-1.0-SNAPSHOT.jar ' "^java\\..*" : * { public static ".*"(java.lang.String); }' -f /usr/lib/jvm/java-11-openjdk/jmods/java.base.jmod
```

Search all classes in cobaltstrik-4.7.jar that inherit from `java.awt.Component` and have a method starting with `set`
that accepts only one argument of type `java.lang.String`.

```shell
java -jar jrep-1.0-SNAPSHOT.jar ' * : java.awt.Component { public "set.*"(java.lang.String); }' -f ./cobaltstrik-4.7.jar
```

## Thanks

https://github.com/classgraph/classgraph  
https://github.com/ronmamo/reflections