/*
 *     TeleKat (Telegram MTProto client library)
 *     Copyright (C) 2020 Hackintosh Five
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.3.60"
    id("org.jetbrains.dokka") version "0.10.0"
    `maven-publish`
}

group = "tk.hack5.telekat"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
    mavenLocal()
    maven(url = "https://jitpack.io")
}

val coroutinesVersion = "1.3.3"
val napierVersion = "1.1.0"

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("generated/commonMain")
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-common:$coroutinesVersion")
                implementation("com.github.aakira:napier:$napierVersion")
                implementation("com.github.aakira:napier-jvm:$napierVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:$coroutinesVersion")
                implementation(project(":core"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        jvm().compilations["main"].defaultSourceSet {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                implementation(project(":core"))
            }
            dependsOn(commonMain)
        }
        jvm().compilations["test"].defaultSourceSet {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("org.junit.jupiter:junit-jupiter:5.5.2")
            }
            dependsOn(commonTest)
        }
    }
}

tasks.dokka {
    outputFormat = "html"
    outputDirectory = "$buildDir/javadoc"
}

val dokkaJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles Kotlin docs with Dokka"
    classifier = "javadoc"
    from(tasks.dokka)
}

publishing {
    repositories {
        mavenLocal()
    }
}