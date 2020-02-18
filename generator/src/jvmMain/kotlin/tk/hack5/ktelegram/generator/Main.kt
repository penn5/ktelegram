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

package tk.hack5.ktelegram.generator

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import java.io.BufferedWriter
import java.io.File

@ExperimentalUnsignedTypes
fun parseAndSave(inputPath: String, outputDir: String, packageName: String) {
    val inputFile = File(inputPath)
    val data = Json(JsonConfiguration.Stable).parse(TLData.serializer(), inputFile.readText())
    println(data)
    var file: File
    var bufferedWriter: BufferedWriter? = null
    var writer: KtWriter
    for (constructor in data.constructors) {
        println("=====")
        writer = NormalKtWriter({ bufferedWriter!!.write(it) }, constructor, packageName)
        if (" " in writer.tlName || "<" in writer.tlName)
            continue
        file = File("$outputDir/${writer.tlName.capitalize()}Object.kt")
        file.parentFile.mkdirs()
        bufferedWriter = file.bufferedWriter()
        writer.build()
        bufferedWriter.close()
    }
    for (method in data.methods) {
        writer = NormalKtWriter({ bufferedWriter!!.write(it) }, method, packageName)
        file = File("$outputDir/${writer.tlName.capitalize()}Request.kt")
        if (" " in writer.tlName || "<" in writer.tlName)
            continue
        file.parentFile.mkdirs()
        bufferedWriter = file.bufferedWriter()
        writer.build()
        bufferedWriter.close()
    }
    for (type in data.types) {
        writer = TypeKtWriter({ bufferedWriter!!.write(it) }, type, packageName)
        file = File("$outputDir/${writer.tlName.capitalize()}Type.kt")
        if (" " in writer.tlName || "<" in writer.tlName)
            continue
        file.parentFile.mkdirs()
        bufferedWriter = file.bufferedWriter()
        writer.build()
        bufferedWriter.close()
    }
    file = File("$outputDir/TlMappings.kt")
    bufferedWriter = file.bufferedWriter()
    MapKtWriter({ bufferedWriter.write(it) }, data, packageName).build()
    bufferedWriter.close()
}

fun writeErrors(input: String, outputPath: String, packageName: String) {
    val file = File(outputPath)
    file.parentFile.mkdirs()
    val writer = file.bufferedWriter()
    ErrorsWriter({ writer.write(it) }, packageName, File(input).readLines().drop(1).map { Error(it) }).build()
    writer.close()
}

@ExperimentalUnsignedTypes
fun main() {
    parseAndSave(
        "resources/schema.json",
        "../core/generated/commonMain/tk/hack5/ktelegram/core/tl",
        "tk.hack5.ktelegram.core.tl"
    )
    parseAndSave(
        "resources/schema-mtproto.json",
        "../core/generated/commonMain/tk/hack5/ktelegram/core/mtproto",
        "tk.hack5.ktelegram.core.mtproto"
    )
    writeErrors(
        "resources/errors.csv",
        "../core/generated/commonMain/tk/hack5/ktelegram/core/errors/Errors.kt",
        "tk.hack5.ktelegram.core.errors"
    )
}