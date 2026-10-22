plugins { id("dev.architectury.loom") version "1.11.458" }

val mcVersion = property("deps.minecraft").toString()
group = property("mod.group").toString()
version = "${property("mod.version")}+$mcVersion"
base.archivesName = "slashblade_neomotions-forge"

loom {
    silentMojangMappingsLicense()
    runConfigs.all { runDir("../../run/forge"); ideConfigGenerated(true) }
}
repositories {
    mavenCentral()
    maven("https://cursemaven.com") { content { includeGroup("curse.maven") } }
    maven("https://maven.kosmx.dev") { content { includeGroup("dev.kosmx.player-anim") } }
}
dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings(loom.officialMojangMappings())
    forge("net.minecraftforge:forge:$mcVersion-${property("deps.forge")}")
    modImplementation("curse.maven:slashblade-resharped-1022428:${property("deps.slashblade")}")
    modImplementation("dev.kosmx.player-anim:player-animation-lib-forge:${property("deps.playeranim")}")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks {
    withType<Jar>().configureEach {
        exclude(".cache/**")
        exclude("com/tonywww/slashblade_neomotions/gametest/**")
        exclude("com/tonywww/slashblade_neomotions/clientqa/**")
        exclude("data/slashblade_neomotions/structures/empty.nbt")
        from(rootProject.file("LICENSE"))
    }
    configureEach { if (name == "createMinecraftArtifacts") dependsOn("stonecutterGenerate") }
    withType<JavaCompile>().configureEach {
        dependsOn("stonecutterGenerate")
        options.encoding = "UTF-8"
        options.release = 17
    }
    processResources {
        dependsOn("stonecutterGenerate")
        val props = mapOf("version" to project.property("mod.version"), "packFormat" to 15)
        inputs.properties(props)
        filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) { expand(props) }
        exclude("META-INF/neoforge.mods.toml", "data/*/recipe/**")
    }
    test { useJUnitPlatform() }
}
java { withSourcesJar(); toolchain.languageVersion = JavaLanguageVersion.of(17) }

val generatedData = rootProject.file("src/generated/${project.name}/resources")
sourceSets.main { resources.srcDir(generatedData) }
tasks.processResources { exclude(".cache/**") }
loom.runs.create("data") {
    data()
    runDir("../../build/datagen-forge")
    programArgs("--mod", project.property("mod.id").toString(), "--all", "--output", generatedData.absolutePath,
        "--existing", layout.buildDirectory.dir("resources/main").get().asFile.absolutePath)
}
tasks.matching { it.name == "runData" }.configureEach {
    doLast {
        for (name in listOf("greatsword_prototype", "dualblade_prototype", "switchaxe_prototype")) {
            check(generatedData.resolve("data/slashblade_neomotions/slashblade/named_blades/$name.json").isFile)
            check(generatedData.resolve("data/slashblade_neomotions/recipes/$name.json").isFile)
        }
    }
}

if (providers.gradleProperty("neomotionsGameTest").isPresent) {
    sourceSets.main {
        java.srcDir(rootProject.file("src/gametest/java"))
        resources.srcDir(rootProject.file("src/gametest/resources"))
    }
    loom.runs.create("gameTestServer") {
        server()
        runDir("../../build/gametest-forge")
        vmArg("-Dforge.gameTestServer=true")
        vmArg("-Dforge.enabledGameTestNamespaces=slashblade_neomotions")
    }
}

if (providers.gradleProperty("neomotionsClientQa").isPresent) {
    sourceSets.main { java.srcDir(rootProject.file("src/clientqa/java")) }
    loom.runs.create("rigClient") {
        client()
        runDir("../../build/rig-client-forge")
        vmArg("-Dneomotions.clientQa=true")
        programArgs("--width", "1280", "--height", "900")
    }
}

