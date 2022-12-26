import io.github.classgraph.ClassGraph
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import java.io.File
import java.net.MalformedURLException
import java.net.URL
import java.net.URLClassLoader

fun convertFilesToURLs(files: String): Array<URL> {
    val urls = mutableListOf<URL>()
    files.split(':').forEach {
        try {
            urls.add(URL(it))
        } catch (e: MalformedURLException) {
            val f = File(it)
            if (f.isDirectory) {
                f.listFiles { _, name ->
                    name.endsWith(".jar") || name.endsWith(".jmod")
                }?.map { file ->
                    urls.add(file.toURI().toURL())
                }
            } else if (f.isFile) {
                urls.add(f.toURI().toURL())
            } else {
                println("$it does not exist")
            }
        }
    }
    return urls.toTypedArray()
}

fun startToScan(classLoader: ClassLoader, classTemplate: ClassTemplate, showStatistics: Boolean) {
    val classes = ClassGraph().enableClassInfo().overrideClassLoaders(classLoader)
        .ignoreParentClassLoaders().disableDirScanning().ignoreClassVisibility().enableSystemJarsAndModules()
        .scan()
    val classesFailedToScan = mutableListOf<String>()
    var classesCount = 0
    var methodsCount = 0

    for (i in classes.allClasses) {
        if (i.isInterface) {
            continue
        }
        try {
            val result = classTemplate.match(i.name)
            if (result != null) {
                classesCount += 1
                methodsCount += result.getMethodsCount()
                print(result)
            }
        } catch (e: NoClassDefFoundError) {
            classesFailedToScan.add(i.name)
        } catch (e: ClassNotFoundException) {
            classesFailedToScan.add(i.name)
        } catch (e: Exception) {
            println(e.message)
            classesFailedToScan.add(i.name)
        }
    }
    if (showStatistics) {
        println("")
        println("============= statistics =============")
        println("classes count: $classesCount")
        println("methods count: $methodsCount")
        println("classes that failed to scan:")
        for (i in classesFailedToScan) {
            println(i)
        }
    }
}

fun main(args: Array<String>) {
    val parser = ArgParser("jrep")
    val template by parser.argument(
        ArgType.String,
        "template",
        "jrep will look for the corresponding class and its methods based on the template"
    )
    val files by parser.option(
        ArgType.String,
        fullName = "files",
        shortName = "f",
        description = "files or directories for scanning, spilt by ':'"
    ).required()
    val showStatistics by parser.option(
        ArgType.Boolean,
        fullName = "show-statistics",
        shortName = "s",
        description = "whether to show scan statistics"
    ).default(false)
    parser.parse(args)

    val urls = convertFilesToURLs(files)
    val classLoader = URLClassLoader(urls)
    val context = ParserContext(template)
    val classTemplate = ClassTemplate(classLoader)

    classTemplate.parseClass(context)
    startToScan(classLoader, classTemplate, showStatistics)
}

