/*
 *     KTelegram (Telegram MTProto client library)
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
}

repositories {
    mavenCentral()
    jcenter()
    maven(url = "https://jitpack.io")
}

val napierVersion = "1.1.0"
val ktorVersion = "1.2.6"
val kotlinxIoVersion = "0.1.16"
val ktMathVersion = "0.0.6"
val serializationVersion = "0.14.0"
val klockVersion = "1.7.3"
val kryptoVersion = "1.9.1"
val bouncyCastleVersion = "1.64"

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("generated/commonMain")
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-common:1.3.3")
                implementation("com.github.aakira:napier:$napierVersion")
                implementation("io.github.gciatto:kt-math-metadata:$ktMathVersion")
                implementation("com.soywiz.korlibs.krypto:krypto:$kryptoVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-io:$kotlinxIoVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-common:$serializationVersion")
                implementation("com.soywiz.korlibs.klock:klock:$klockVersion")

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
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.3")
                implementation("com.github.aakira:napier-jvm:$napierVersion")
                implementation("io.ktor:ktor-network:$ktorVersion")
                implementation("io.github.gciatto:kt-math-jvm:$ktMathVersion")
                implementation("com.soywiz.korlibs.krypto:krypto-jvm:$kryptoVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-io-jvm:$kotlinxIoVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationVersion")
                implementation("com.soywiz.korlibs.klock:klock-jvm:$klockVersion")
                implementation("org.bouncycastle:bcprov-jdk15on:$bouncyCastleVersion")
                implementation("org.bouncycastle:bcpkix-jdk15on:$bouncyCastleVersion")
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


tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions.freeCompilerArgs += listOf("-Xuse-experimental=kotlin.Experimental")
    kotlinOptions.freeCompilerArgs += listOf("-Xuse-experimental=kotlin.UseExperimental")
}