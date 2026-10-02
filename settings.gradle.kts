pluginManagement{
    repositories{
        gradlePluginPortal()
        mavenLocal()
        maven("https://raw.githubusercontent.com/GglLfr/EntityAnnoMaven/main")
        maven("https://raw.githubusercontent.com/GglLfr/MindustryClientMaven/main")
    }

    plugins{
        val entVersion = providers.gradleProperty("entVersion").get()
        val clientVersion = providers.gradleProperty("clientVersion").get()

        id("com.github.GglLfr.EntityAnno") version(entVersion)
        id("com.github.GglLfr.MindustryClient") version(clientVersion)
    }
}

if(JavaVersion.current().ordinal < JavaVersion.VERSION_17.ordinal){
    throw IllegalStateException("JDK 17 is a required minimum version. Yours: ${System.getProperty("java.version")}")
}
