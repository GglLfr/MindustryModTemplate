pluginManagement{
    repositories{
        gradlePluginPortal()
        maven("https://raw.githubusercontent.com/GglLfr/EntityAnnoMaven/main")
        mavenLocal()
    }

    plugins{
        val entVersion: String = providers.gradleProperty("entVersion").get()
        id("com.github.GglLfr.EntityAnno") version(entVersion)
    }
}

if(JavaVersion.current().ordinal < JavaVersion.VERSION_17.ordinal){
    throw IllegalStateException("JDK 17 is a required minimum version. Yours: ${System.getProperty("java.version")}")
}

val modName: String = providers.gradleProperty("modName").get()
rootProject.name = modName
