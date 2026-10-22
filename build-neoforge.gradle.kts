plugins { id("dev.isxander.modstitch.base") version "0.8.5" }

val mcVersion = property("deps.minecraft").toString()
val targetNeoForgeVersion = property("deps.neoforge").toString()
val artifactVersion = "${property("mod.version")}+$mcVersion"
group = property("mod.group").toString()
version = artifactVersion
base.archivesName = "slashblade_neomotions-neoforge"
modstitch {
    minecraftVersion = mcVersion
    moddevgradle { neoForgeVersion = targetNeoForgeVersion; defaultRuns() }
}
repositories {
    mavenCentral()
    maven("https://cursemaven.com") { content { includeGroup("curse.maven") } }
    maven("https://maven.kosmx.dev") { content { includeGroup("dev.kosmx.player-anim") } }
}
dependencies {
    implementation("curse.maven:slashblade-resharped-1022428:${property("deps.slashblade")}")
    implementation("dev.kosmx.player-anim:player-animation-lib-forge:${property("deps.playeranim")}")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("com.google.code.gson:gson:2.10.1")
}
tasks {
    withType<JavaCompile>().configureEach {
        dependsOn("stonecutterGenerate")
        options.encoding = "UTF-8"
        options.release = 21
    }
    withType<Jar>().configureEach {
        exclude(".cache/**")
        archiveVersion = artifactVersion
        exclude("com/tonywww/slashblade_neomotions/gametest/**")
        exclude("com/tonywww/slashblade_neomotions/clientqa/**")
        exclude("data/slashblade_neomotions/structure/empty.nbt")
        from(rootProject.file("LICENSE"))
    }
    processResources {
        dependsOn("stonecutterGenerate")
        val props = mapOf("version" to project.property("mod.version"), "packFormat" to 34)
        inputs.properties(props)
        filesMatching(listOf("META-INF/neoforge.mods.toml", "pack.mcmeta")) { expand(props) }
        exclude("META-INF/mods.toml", "data/*/recipes/**")
    }
    test {
        useJUnitPlatform()
        // SlashBlade's OBJ parser uses Minecraft Vec3 and JOML even without a game window.
        classpath += sourceSets.main.get().compileClasspath
    }
}
java { withSourcesJar(); toolchain.languageVersion = JavaLanguageVersion.of(21) }
// Native rig tests use Minecraft's JOML matrices at compile time as well as runtime.
sourceSets.test { compileClasspath += sourceSets.main.get().compileClasspath }

val generatedData = rootProject.file("src/generated/${project.name}/resources")
sourceSets.main { resources.srcDir(generatedData) }
tasks.processResources { exclude(".cache/**") }
extensions.configure<net.neoforged.moddevgradle.dsl.NeoForgeExtension> {
    runs.create("data") {
        type = "data"
        gameDirectory = rootProject.file("build/datagen-neoforge")
        programArguments.addAll("--mod", project.property("mod.id").toString(), "--all", "--output", generatedData.absolutePath,
            "--existing", layout.buildDirectory.dir("resources/main").get().asFile.absolutePath)
    }
}
tasks.matching { it.name == "runData" }.configureEach {
    doLast {
        for (name in listOf("greatsword_prototype", "dualblade_prototype", "switchaxe_prototype")) {
            check(generatedData.resolve("data/slashblade_neomotions/slashblade/named_blades/$name.json").isFile)
            check(generatedData.resolve("data/slashblade_neomotions/recipe/$name.json").isFile)
        }
    }
}

if (providers.gradleProperty("neomotionsGameTest").isPresent) {
    sourceSets.main {
        java.srcDir(rootProject.file("src/gametest-neoforge/java"))
        resources.srcDir(rootProject.file("src/gametest-neoforge/resources"))
    }
    extensions.configure<net.neoforged.moddevgradle.dsl.NeoForgeExtension> {
        runs.create("gameTestServer") {
            type = "gameTestServer"
            gameDirectory = rootProject.file("build/gametest-neoforge")
            systemProperty("neoforge.enabledGameTestNamespaces", "slashblade_neomotions")
        }
    }
}

if (providers.gradleProperty("neomotionsClientQa").isPresent) {
    sourceSets.main { java.srcDir(rootProject.file("src/clientqa-neoforge/java")) }
    extensions.configure<net.neoforged.moddevgradle.dsl.NeoForgeExtension> {
        runs.create("rigClient") {
            type = "client"
            gameDirectory = rootProject.file("build/rig-client-neoforge")
            systemProperty("neomotions.clientQa", "true")
            programArguments.addAll("--width", "1280", "--height", "900")
        }
    }
}

