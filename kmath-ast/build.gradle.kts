plugins { id("scientifik.mpp") }

kotlin.sourceSets {
    all { languageSettings.useExperimentalAnnotation("kotlin.contracts.ExperimentalContracts") }

    commonMain {
        dependencies {
            api(project(":kmath-core"))
            api("com.github.h0tk3y.betterParse:better-parse:0.4.0")
        }
    }

    jvmMain {
        dependencies {
            implementation("org.ow2.asm:asm:8.0.1")
            implementation("org.ow2.asm:asm-commons:8.0.1")
            implementation(kotlin("reflect"))
        }
    }
}