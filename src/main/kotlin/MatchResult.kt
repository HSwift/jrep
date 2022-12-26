import java.lang.reflect.Method

class MatchResult constructor(val clazz: Class<*>) {
    private val methods = mutableListOf<Method>()
    private val templateToMethod = mutableMapOf<MethodTemplate, Method>()
    fun addMethod(template: MethodTemplate, method: Method) {
        templateToMethod[template] = method
        methods.add(method)
    }

    fun getMethods() = methods
    fun getMatchedTemplates() = templateToMethod
    fun getMethodsCount() = methods.size

    override fun toString(): String {
        return buildString {
            append("$clazz { \n")
            methods.forEach {
                append("\t$it\n")
            }
            append("}\n")
        }
    }
}