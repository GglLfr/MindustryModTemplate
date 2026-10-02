import arc.util.*
import ent.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.Serializable
import java.util.*
import java.util.jar.*

buildscript{
    val (mindustry, mindustryVersion, mindustrySource) = when(val version = providers.gradleProperty("mindustryVersion").get()){
        "latest" -> Triple("Mindustry", "latest", "Anuken/Mindustry/releases/latest/download/dependencies.jar")
        "be" -> Triple("MindustryBuilds", "latest", "Anuken/MindustryBuilds/releases/download/master/latest.jar")
        else -> Triple("Mindustry", version, "Anuken/Mindustry/releases/download/[revision]/dependencies.jar")
    }

    dependencies{
        classpath("Anuken:$mindustry:$mindustryVersion")
    }

    configurations.configureEach{
        // Resolve the correct Mindustry dependency.
        resolutionStrategy.eachDependency{
            if(requested.group == "Anuken" && requested.name.startsWith("Mindustry")){
                useTarget("Anuken:$mindustry:$mindustryVersion")
            }
        }
    }

    repositories{
        ivy{
            url = uri("https://github.com")
            patternLayout{
                artifact(mindustrySource)
                metadataSources{artifact()}
            }
            content{
                includeVersion("Anuken", mindustry, mindustryVersion)
            }
        }
    }
}

plugins{
    java
    id("com.github.GglLfr.EntityAnno") apply false
}

val (mindustry, mindustryVersion, mindustrySource) = when(val version = providers.gradleProperty("mindustryVersion").get()){
    "latest" -> Triple("Mindustry", "latest", "Anuken/Mindustry/releases/latest/download/dependencies.jar")
    "be" -> Triple("MindustryBuilds", "latest", "Anuken/MindustryBuilds/releases/download/master/latest.jar")
    else -> Triple("Mindustry", version, "Anuken/Mindustry/releases/download/[revision]/dependencies.jar")
}
val entVersion = providers.gradleProperty("entVersion").get()

val modArtifact = providers.gradleProperty("modArtifact").get()
val modFetch = providers.gradleProperty("modFetch").get()
val modGenSrc = providers.gradleProperty("modGenSrc").get()
val modGen = providers.gradleProperty("modGen").get()

val mindustryIgnoreSteam = providers.gradleProperty("mindustryIgnoreSteam").orElse("false").map{it.toBoolean()}
val mindustryPath = providers.gradleProperty("mindustryPath").map(::File)

val clientProvider = gradle.sharedServices.registerIfAbsent("clientService", ClientService::class.java){
    parameters.ignoreSteam = mindustryIgnoreSteam
    parameters.path = mindustryPath
}

allprojects{
    apply(plugin = "java")
    sourceSets["main"].java.setSrcDirs(listOf(layout.projectDirectory.dir("src")))

    dependencies{
        registerTransform(TrimSources::class){
            from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
            to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar-stripped")
        }
    }

    configurations.configureEach{
        // Resolve the correct Mindustry dependency.
        resolutionStrategy.eachDependency{
            if(requested.group == "Anuken" && requested.name.startsWith("Mindustry")){
                useTarget("Anuken:$mindustry:$mindustryVersion")
            }
        }
    }

    configurations.matching{it.isCanBeResolved}.configureEach{
        attributes{
            attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar-stripped")
        }
    }

    repositories{
        // Use Ivy repository for Mindustry builds.
        ivy{
            url = uri("https://github.com")
            patternLayout{
                artifact(mindustrySource)
                metadataSources{artifact()}
            }
            content{
                includeVersion("Anuken", mindustry, mindustryVersion)
            }
        }

        // Necessary Maven repositories to pull dependencies from.
        mavenLocal()
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://oss.sonatype.org/content/repositories/releases/")
        maven("https://raw.githubusercontent.com/GglLfr/EntityAnnoMaven/main")
    }

    tasks.withType<JavaCompile>().configureEach{
        options.apply{
            compilerArgs.add("-Xlint:-options")
            compilerArgs.add("-implicit:none")
            compilerArgs.addAll(providers.gradleProperty("org.gradle.jvmargs").get()
                .split(Regex("\\s+"))
                .filter{it.startsWith("--add-opens")}
                .map{"--add-exports=${it.substring("--add-opens=".length)}"}
            )

            isIncremental = true
            isFork = false
            encoding = "UTF-8"
        }

        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
}

project(":"){
    apply(plugin = "com.github.GglLfr.EntityAnno")

    configure<EntityAnnoExtension>{
        revisionDir = layout.projectDirectory.dir("revisions").asFile
        fetchPackage = modFetch
        genSrcPackage = modGenSrc
        genPackage = modGen
    }

    dependencies{
        // Use the entity generation annotation processor.
        compileOnly("com.github.GglLfr.EntityAnno:entity:$entVersion")
        annotationProcessor("com.github.GglLfr.EntityAnno:entity:$entVersion")

        compileOnly("Anuken:$mindustry:$mindustryVersion")
    }

    val jar = tasks.named<Jar>("jar"){
        archiveFileName = "${modArtifact}Desktop.jar"

        // Deliberately check if the mod meta is actually written in HJSON, since, well, some people actually use
        // it. But this is also not mentioned in the `README.md`, for the mischievous reason of driving beginners
        // into using JSON instead.
        val metaJson = layout.projectDirectory.file("mod.json")
        val metaHjson = layout.projectDirectory.file("mod.hjson")

        if(metaJson.asFile.exists() && metaHjson.asFile.exists()){
            throw IllegalStateException("Ambiguous mod meta: both `mod.json` and `mod.hjson` exist.")
        }else if(!metaJson.asFile.exists() && !metaHjson.asFile.exists()){
            throw IllegalStateException("Missing mod meta: neither `mod.json` nor `mod.hjson` exist.")
        }

        val isJson = metaJson.asFile.exists()
        val usedMeta = if(isJson) metaJson else metaHjson

        from(
            files(sourceSets["main"].output.classesDirs),
            files(sourceSets["main"].output.resourcesDir),
            configurations.runtimeClasspath.map{conf -> conf.map{if(it.isDirectory) it else zipTree(it)}},

            files(layout.projectDirectory.dir("assets")),
            layout.projectDirectory.file("icon.png"),
            usedMeta
        )

        metaInf.from(layout.projectDirectory.file("LICENSE"))
    }

    val dex = tasks.register<Jar>("dex"){
        description = "Builds an Android-compatible JAR from the desktop-only JAR. Use this file for GitHub release."
        inputs.files(jar)

        archiveFileName = "$modArtifact.jar"

        val desktopJar = jar.flatMap{it.archiveFile}
        val dexJar = File(temporaryDir, "Dex.jar")

        val androidSdkVersion = providers.gradleProperty("androidSdkVersion").get()
        val androidBuildVersion = providers.gradleProperty("androidBuildVersion").get()
        val androidMinVersion = providers.gradleProperty("androidMinVersion").get()

        val classpaths = configurations.compileClasspath.get().toList() + configurations.runtimeClasspath.get().toList()
        val providers = project.providers

        from(zipTree(desktopJar), zipTree(dexJar))
        doFirst{
            // Find Android SDK root.
            val sdkRoot = File(
                OS.env("ANDROID_HOME") ?: OS.env("ANDROID_SDK_ROOT")
                ?: throw GradleException("Neither `ANDROID_HOME` nor `ANDROID_SDK_ROOT` are set")
            )

            // Find `d8`.
            val d8 = File(sdkRoot, "build-tools/$androidBuildVersion/${if(OS.isWindows) "d8.bat" else "d8"}")
            if(!d8.exists()) throw GradleException("Android SDK `build-tools;$androidBuildVersion` isn't installed or is corrupted")

            // Initialize a release build.
            val input = desktopJar.get().asFile
            val command = arrayListOf("$d8", "--release", "--min-api", androidMinVersion, "--output", "$dexJar", "$input")

            // Include all compile and runtime classpath.
            classpaths.forEach{
                if(it.exists()) command.addAll(arrayOf("--classpath", it.path))
            }

            // Include Android platform as library.
            val androidJar = File(sdkRoot, "platforms/android-$androidSdkVersion/android.jar")
            if(!androidJar.exists()) throw GradleException("Android SDK `platforms;android-$androidSdkVersion` isn't installed or is corrupted")

            command.addAll(arrayOf("--lib", "$androidJar"))
            if(OS.isWindows) command.addAll(0, arrayOf("cmd", "/c").toList())

            // Run `d8`.
            providers.exec{commandLine(command)}.result.get().rethrowFailure()
        }
    }

    val client = clientProvider.map{it.detected}
    val install = tasks.register<DefaultTask>("install"){
        description = "Installs the desktop JAR to your `mods/` folder."

        val desktopJar = jar.flatMap{it.archiveFile}
        val dexJar = dex.flatMap{it.archiveFileName}

        inputs.files(desktopJar)

        doLast{
            val mods = client.get().getModsDirectory()
            mods.parentFile?.mkdirs()
            mods.resolve(dexJar.get()).delete()

            val input = desktopJar.get().asFile
            val output = mods.resolve(input.name)
            FileInputStream(input).use{input -> FileOutputStream(output).use{output -> input.copyTo(output)}}

            logger.lifecycle("Copied :jar output to ${mods}.")
        }
    }

    val installClient = tasks.register<InstallClientTask>("installClient"){
        description = "Installs a Mindustry client compatible with `mindustryVersion` from `gradle.properties`."

        val versionProperties = buildscript.classLoader.getResourceAsStream("version.properties").use{
            val props = Properties()
            props.load(it)
            props
        }

        buildNumber.set(versionProperties.getProperty("build"))
        buildType.set(versionProperties.getProperty("type"))
    }

    tasks.register<RunClientTask>("run"){
        description = "Installs the mod and runs Mindustry."
        dependsOn(install)

        clientClasspaths.from(installClient.flatMap{it.clientFile})
    }
}

abstract class TrimSources : TransformAction<TransformParameters.None>{
    @get:InputArtifact
    abstract val file: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs){
        val input = file.get().asFile
        val classes = outputs.file(input.name)

        JarFile(input).use{jar ->
            val entries = jar.entries()
            JarOutputStream(FileOutputStream(classes)).use{classes ->
                for(entry in entries){
                    if(entry.name.endsWith(".java")) continue

                    classes.putNextEntry(JarEntry(entry.name))
                    jar.getInputStream(entry).use{it.copyTo(classes)}
                    classes.closeEntry()
                }
            }
        }
    }
}

data class ClientInfo(
    var ignoreSteam: Boolean,
    // `[...]/steamapps/common/Mindustry` directory.
    var steamPath: File?,
    // `Mindustry[.exe|.app]` executable path.
    var path: File?,
    var dataDirectory: File
) : Serializable{
    fun getModsDirectory(): File = dataDirectory.resolve("mods")

    fun isSteam(): Boolean = steamPath != null && (!ignoreSteam || steamPath?.absoluteFile?.let{path?.absoluteFile?.startsWith(it)} ?: false);
}

interface ClientParams : BuildServiceParameters{
    val ignoreSteam: Property<Boolean>
    val path: Property<File>
}

abstract class ClientService : BuildService<ClientParams>{
    private val logger = Logging.getLogger(ClientService::class.java)
    val detected = detectClient(parameters.ignoreSteam.get(), parameters.path.orNull)

    fun detectClient(ignoreSteam: Boolean, path: File?): ClientInfo{
        val steamDirs = mutableListOf<File>()
        val dataFolder = File(OS.getAppDataDirectoryString("Mindustry"))
        val out = ClientInfo(ignoreSteam, null, null, dataFolder)

        if(OS.isWindows){
            steamDirs.add(File("/Program Files (x86)/Steam"))
            steamDirs.add(File("/Program Files/Steam"))

            OS.env("PROGRAMFILES(X86)")?.let{steamDirs.add(File(it, "Steam")) }
            OS.env("PROGRAMFILES")?.let{steamDirs.add(File(it, "Steam")) }
        }else if(OS.isMac){
            steamDirs.add(File(OS.userHome, "Library/Application Support/Steam"))
        }else if(OS.isLinux){
            steamDirs.add(File(OS.userHome, ".local/share/Steam"))
            steamDirs.add(File(OS.userHome, ".steam/steam"))
            steamDirs.add(File(OS.userHome, ".var/app/com.valvesoftware.Steam/.local/share/Steam"))
        }

        val steamRoot = steamDirs.firstOrNull{it.exists()}
        if(steamRoot != null){
            val libraryPaths = mutableSetOf(steamRoot.resolve("steamapps"))
            val vdfFile = steamRoot.resolve("steamapps/libraryfolders.vdf")
            if(vdfFile.exists()){
                try{
                    """"path"\s+"([^"]+)"""".toRegex().findAll(vdfFile.readText(Charsets.UTF_8)).forEach{match ->
                        val dir = File(match.groupValues[1].replace("\\\\", "\\"))
                        if(dir.exists()) libraryPaths.add(dir.resolve("steamapps"))
                    }
                }catch(_: IOException){}
            }

            val steamPath = libraryPaths
                .mapNotNull{steamapps ->
                    val acf = steamapps.resolve("appmanifest_1127400.acf")
                    if(!acf.exists()) return@mapNotNull null

                    val name = try{
                        val match = """"installdir"\s+"([^"]+)"""".toRegex().find(acf.readText(Charsets.UTF_8))
                        match?.groupValues[1] ?: return@mapNotNull null
                    }catch(_: IOException){
                        return@mapNotNull null
                    }

                    val dir = steamapps.resolve("common/$name")
                    if(dir.exists()) dir else null
                }.firstOrNull()

            if(steamPath != null) {
                if(!ignoreSteam) logger.lifecycle("Found a Steam Mindustry installation at `$steamPath`.")
                out.steamPath = steamPath
                out.dataDirectory = steamPath.resolve("saves")
            }
        }

        if(path != null && (out.steamPath == null || ignoreSteam)) {
            if(path.exists()){
                val path = if(OS.isMac) path.resolve("Contents/MacOS/Mindustry") else path
                logger.lifecycle("Using explicitly provided Mindustry executable at `$path`.")

                out.path = path
            }else{
                logger.warn("Provided Mindustry executable path `$path` does not exist.")
            }
        }

        if(!out.isSteam()) out.dataDirectory = dataFolder
        return out
    }

    companion object {
        fun isJar(file: File?): Boolean =
            file != null && file.exists() && try{
                JarFile(file).use{
                    it.manifest?.mainAttributes?.getValue("Main-Class") == "mindustry.desktop.DesktopLauncher"
                }
            }catch(_: IOException){
                false
            }
    }
}

abstract class InstallClientTask @Inject constructor(
    layout: ProjectLayout
) : DefaultTask(){
    @get:Input
    abstract val buildNumber: Property<String>
    @get:Input
    abstract val buildType: Property<String>
    @get:Input
    abstract val client: Property<ClientInfo>

    @get:OutputFile
    abstract val clientFile: RegularFileProperty

    @get:ServiceReference("clientService")
    abstract val clientService: Property<ClientService>

    init{
        clientFile.convention(buildNumber.flatMap{num -> buildType.flatMap{type -> layout.buildDirectory.file("clients/Mindustry-$type-$num.jar")}})
        client.set(clientService.map{it.detected})
        client.disallowChanges()
    }

    @TaskAction
    fun install(){
        val client = clientService.get().detected
        if(client.path != null || !client.ignoreSteam && client.steamPath != null) return

        val dest = clientFile.get().asFile
        dest.parentFile?.mkdirs()

        if(dest.exists() && ClientService.isJar(dest)) return
        logger.lifecycle("Installing client...")

        val num = buildNumber.get()
        val type = buildType.get()
        Http.get(when(type){
            "official" -> "https://github.com/Anuken/Mindustry/releases/download/v$num/Mindustry.jar"
            "bleeding-edge" -> "https://github.com/Anuken/MindustryBuilds/releases/download/$num/Mindustry-BE-Desktop-$num.jar"
            else -> {
                throw GradleException("Invalid Mindustry version type `$type`; cannot install and run client from Gradle")
            }
        })
            .error{throw GradleException("Couldn't install client", it)}
            .block{
                val totalBytes = it.contentLength
                it.resultAsStream.use{input ->
                    FileOutputStream(dest).use{output ->
                        val buf = ByteArray(65536)
                        var totalRead = 0

                        print("Downloading client file...")
                        while(true){
                            System.out.flush()

                            val read = input.read(buf)
                            if(read == -1) break

                            output.write(buf, 0, read)
                            totalRead += read

                            print("\rDownloading client file: " +
                                "${"%.2f".format(totalRead / (1024f * 1024f))} MiB / " +
                                "${"%.2f".format(totalBytes / (1024f * 1024f))} MiB " +
                                "(${"%.0f".format((totalRead * 100f) / totalBytes)}%)"
                            )
                        }
                        println()
                    }
                }
            }

        logger.lifecycle("Installed $type client version $num!")
    }
}

abstract class RunClientTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask(){
    @get:InputFiles
    abstract val clientClasspaths: ConfigurableFileCollection
    @get:Input
    abstract val client: Property<ClientInfo>

    @get:ServiceReference("clientService")
    abstract val clientService: Property<ClientService>

    init{
        client.set(clientService.map{it.detected})
        client.disallowChanges()
    }

    @TaskAction
    fun run(){
        val client = client.get()
        val jvmArgs = arrayOf(
            // Match the ones in native Mindustry client json file.
            "-Dhttps.protocols=TLSv1.2,TLSv1.1,TLSv1",
            "-XX:+ShowCodeDetailsInExceptionMessages",
            "-XX:+UseCompactObjectHeaders",
            "--enable-native-access=ALL-UNNAMED"
        )

        if(client.steamPath != null && (!client.ignoreSteam || client.steamPath?.absoluteFile?.let{client.path?.absoluteFile?.startsWith(it)} ?: false)){
            logger.lifecycle("Running Mindustry via Steam, so stdin/stdout is not captured.")
            logger.lifecycle("This Gradle task will exit immediately, but Mindustry is being run at the background.")
            logger.lifecycle("Give Steam some time to boot Mindustry up.")

            val uri = "steam://run/1127400"
            execOperations.exec{
                when{
                    OS.isWindows -> commandLine("cmd", "/c", "start", uri)
                    OS.isMac -> commandLine("open", uri)
                    OS.isLinux -> commandLine("xdg-open", uri)
                    else -> throw GradleException("Unsupported host OS ${OS.osName}")
                }
            }
        }else if(client.path != null){
            if(ClientService.isJar(client.path)){
                execOperations.javaexec{
                    mainClass = "mindustry.desktop.DesktopLauncher"
                    classpath(client.path)
                    jvmArgs(*jvmArgs)
                }
            }else{
                execOperations.exec{
                    when{
                        OS.isWindows -> commandLine("cmd", "/c", client.path)
                        OS.isMac || OS.isLinux -> commandLine(client.path)
                        else -> throw GradleException("Unsupported host OS ${OS.osName}")
                    }
                }
            }
        }else{
            execOperations.javaexec{
                mainClass = "mindustry.desktop.DesktopLauncher"
                classpath(clientClasspaths)
                jvmArgs(*jvmArgs)
            }
        }
    }
}
