import java.lang.reflect.Method
import java.lang.reflect.Modifier

/*
Template Lexical Structure:
Template = CLASS_NAME **:** PARENT_CLASS **{** METHOD ... **}**
CLASS_NAME = * | **"regexp"** | **name**
PARENT_CLASS = * | **class** **,** ...
METHOD = MODIFIER ... METHOD_NAME **(** PARAM_TYPE **,** ... **)** RETURN_TYPE **;**
MODIFIER = NULL | **static** | **final** | **abstract** | **transient** | **volatile** | **synchronized** | **native** | **strictfp** | **interface**
METHOD_NAME = * | **"regexp"** | **name**
PARAM_TYPE = * | CLASS_TYPE | CLASS_TYPE **?**
RETURN_TYPE = NULL | **:** CLASS_TYPE
CLASS_TYPE = CLASS_TRANSFORMATION **class**
CLASS_TRANSFORMATION = NULL | **extends** | **super**
*/

class ClassTemplate constructor(private val classLoader: ClassLoader) {
    private var className = ""
    private var classNameRegex = ".*".toRegex()
    private val methodTemplate = mutableListOf<MethodTemplate>()
    private val parentTypes = mutableListOf<Class<*>>()
    private var ignoreParentType = false
    private fun matchClassName(clazz: Class<*>): Boolean {
        return if (className == "") {
            classNameRegex.matches(clazz.name)
        } else {
            clazz.name == className
        }
    }

    fun parseClass(context: ParserContext) {
        parseClassName(context)
        if (!context.expectNextChar(':')) {
            throw Exception("expect ':' at ${context.remaining()}")
        }
        parseClassParentType(context)
        if (!context.expectNextChar('{')) {
            throw Exception("expect '{' at ${context.remaining()}")
        }
        while (context.readNextChar() != '}') {
            parseMethodTemplate(context)
        }
    }

    private fun parseClassName(context: ParserContext) {
        when (context.readNextChar()) {
            '"' -> classNameRegex = context.nextToken("\"([^\"]+)\"".toRegex()).toRegex()
            '*' -> context.expectNextChar('*')
            else -> className = context.nextToken("[a-zA-Z0-9_.\$]+".toRegex())
        }
    }

    private fun parseClassParentType(context: ParserContext) {
        if (context.expectNextChar('*')) {
            ignoreParentType = true
        } else {
            do {
                val t = context.nextToken("[a-zA-Z0-9_.\$]+".toRegex())
                try {
                    parentTypes.add(Class.forName(t, false, classLoader))
                } catch (e: ClassNotFoundException) {
                    throw Exception("class $t not found")
                }
            } while (context.expectNextChar(','))
        }
    }

    private fun parseMethodTemplate(context: ParserContext) {
        val template = MethodTemplate(classLoader)
        template.parseMethod(context)
        methodTemplate.add(template)
    }

    fun match(name: String): MatchResult? {
        return match(classLoader.loadClass(name))
    }

    fun match(clazz: Class<*>): MatchResult? {
        if (!matchClassName(clazz)) {
            return null
        }
        if (!ignoreParentType) {
            for (pType in parentTypes) {
                if (!pType.isAssignableFrom(clazz)) {
                    return null
                }
            }
        }
        val result = MatchResult(clazz)
        if (methodTemplate.size == 0) {
            return result
        }
        for (t in methodTemplate) {
            for (m in clazz.declaredMethods) {
                if (t.match(m)) {
                    result.addMethod(t, m)
                }
            }
        }
        return if (result.getMatchedTemplates().keys.size != methodTemplate.size) {
            null
        } else {
            result
        }
    }
}

class MethodTemplate constructor(private val classLoader: ClassLoader) {
    private var methodName = ""
    private var methodNameRegex = ".*".toRegex()
    private val orderedParameterList = mutableListOf<ClassType>()
    private val parameterList = mutableListOf<ClassType>()
    private var ignoreParameterCount = false
    private var returnType: ClassType? = null
    private var ignoreReturnType = false
    private var modifiers = 0
    private val primitiveMap = mapOf<String, Class<*>>(
        "boolean" to java.lang.Boolean.TYPE,
        "byte" to java.lang.Byte.TYPE,
        "char" to Character.TYPE,
        "short" to java.lang.Short.TYPE,
        "int" to Integer.TYPE,
        "long" to java.lang.Long.TYPE,
        "double" to java.lang.Double.TYPE,
        "float" to java.lang.Float.TYPE,
        "void" to Void.TYPE
    )
    private val modifierDefs = mapOf(
        "public" to Modifier.PUBLIC,
        "private" to Modifier.PRIVATE,
        "protected" to Modifier.PROTECTED,
        "static" to Modifier.STATIC,
        "final" to Modifier.FINAL,
        "abstract" to Modifier.ABSTRACT,
        "transient" to Modifier.TRANSIENT,
        "volatile" to Modifier.VOLATILE,
        "synchronized" to Modifier.SYNCHRONIZED,
        "native" to Modifier.NATIVE,
        "strictfp" to Modifier.STRICT,
        "interface" to Modifier.INTERFACE
    )

    fun parseMethod(context: ParserContext) {
        parseMethodName(context)
        parseParameterType(context)
        parseMethodReturnType(context)
        if (!context.expectNextChar(';')) {
            throw Exception("expect ';' at ${context.remaining()}")
        }
    }

    private fun parseMethodName(context: ParserContext) {
        var foundMethodName: Boolean
        do {
            foundMethodName = true
            when (context.readNextChar()) {
                '"' -> methodNameRegex = context.nextToken("\"([^\"]+)\"".toRegex()).toRegex()
                '*' -> context.expectNextChar('*')
                else -> {
                    val t = context.nextToken("[a-zA-Z0-9_]+".toRegex())
                    if (t in modifierDefs) {
                        modifiers = modifiers or modifierDefs[t]!!
                        foundMethodName = false
                    } else {
                        methodName = t
                    }
                }
            }
        } while (!foundMethodName)
    }

    private fun parseClassType(context: ParserContext): ClassType {
        var t = context.nextToken("[a-zA-Z0-9_.\$\\[\\]]+".toRegex())
        var transformation = ClassType.Transformation.Invariant
        if (t == "extends") {
            transformation = ClassType.Transformation.Extends
            t = context.nextToken("[a-zA-Z0-9_.\$\\[\\]]+".toRegex())
        } else if (t == "super") {
            transformation = ClassType.Transformation.Super
            t = context.nextToken("[a-zA-Z0-9_.\$\\[\\]]+".toRegex())
        }
        try {
            return ClassType(loadClass(t), transformation)
        } catch (e: ClassNotFoundException) {
            throw Exception("class $t not found")
        }

    }

    private fun parseParameterType(context: ParserContext) {
        if (!context.expectNextChar('(')) {
            throw Exception("expect '(' at ${context.remaining()}")
        }

        do {
            when (context.readNextChar()) {
                ')' -> {
                    break
                }

                '*' -> {
                    context.expectNextChar('*')
                    ignoreParameterCount = true
                    break
                }

                else -> {
                    val classType = parseClassType(context)
                    if (context.expectNextChar('?')) {
                        ignoreParameterCount = true
                        parameterList.add(classType)
                    } else {
                        orderedParameterList.add(classType)
                    }
                }
            }
        } while (context.expectNextChar(','))

        if (!context.expectNextChar(')')) {
            throw Exception("expect ')' at ${context.remaining()}")
        }
    }

    private fun parseMethodReturnType(context: ParserContext) {
        if (context.expectNextChar(':')) {
            returnType = parseClassType(context)
        } else {
            ignoreReturnType = true
        }
    }

    private fun loadClass(name: String): Class<*> {
        var canonicalName = name.trim()
        val prefix = StringBuilder()
        if (canonicalName.endsWith("[]")) {
            while (canonicalName.endsWith("[]")) {
                prefix.append("[")
                canonicalName = canonicalName.dropLast(2)
            }
            return if (canonicalName in primitiveMap) {
                java.lang.reflect.Array.newInstance(primitiveMap[canonicalName]!!, *IntArray(prefix.length))::class.java
            } else {
                Class.forName("${prefix}L$canonicalName;", false, classLoader)
            }
        } else {
            return if (canonicalName in primitiveMap) {
                primitiveMap[canonicalName]!!
            } else {
                Class.forName(canonicalName, false, classLoader)
            }
        }
    }

    private fun matchMethodName(method: Method): Boolean {
        return if (methodName == "") {
            methodNameRegex.matches(method.name)
        } else {
            methodName == method.name
        }
    }

    private fun matchOrderedParameter(method: Method): Boolean {
        if (method.parameterCount < orderedParameterList.size) {
            return false
        }
        if (!ignoreParameterCount) {
            if (method.parameterCount != orderedParameterList.size) {
                return false
            }
        }
        for (i in 0 until orderedParameterList.size) {
            if (!orderedParameterList[i].match(method.parameterTypes[i])) {
                return false
            }
        }
        return true
    }

    private fun matchParameter(method: Method): Boolean {
        for (classType in parameterList) {
            var matched = false
            for (t in method.parameterTypes) {
                if (classType.match(t)) {
                    matched = true
                }
            }
            if (!matched) {
                return false
            }
        }
        return true
    }

    fun match(method: Method): Boolean {
        if (modifiers != 0 && (method.modifiers and modifiers != modifiers)) {
            return false
        }
        if (!matchMethodName(method)) {
            return false
        }
        if (!matchOrderedParameter(method)) {
            return false
        }
        if (!matchParameter(method)) {
            return false
        }
        if (!ignoreReturnType) {
            if (!returnType!!.match(method.returnType)) {
                return false
            }
        }
        return true
    }
}

class ClassType constructor(private val clazz: Class<*>, private val transformation: Transformation) {
    enum class Transformation { Extends, Super, Invariant }

    fun match(other: Class<*>): Boolean {
        return when (transformation) {
            Transformation.Invariant -> {
                other == clazz
            }

            Transformation.Extends -> {
                clazz.isAssignableFrom(other)
            }

            Transformation.Super -> {
                other.isAssignableFrom(clazz)
            }
        }
    }
}