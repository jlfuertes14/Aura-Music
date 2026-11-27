plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.ksp) apply false
}

subprojects {
    configurations.all {
        resolutionStrategy {
            force("com.github.TeamNewPipe:nanojson:c7a6c1c08d16b6d5ecded34758e6415e07be2166")
        }
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }
}
