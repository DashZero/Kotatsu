package org.koitharu.kotatsu.gdrive

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.time.Instant
import java.time.format.DateTimeParseException

class InstantAdapter : TypeAdapter<Instant>() {
    override fun write(out: JsonWriter, value: Instant?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value.toString())
        }
    }

    override fun read(reader: JsonReader): Instant? {
        return try {
            reader.nextString()?.let { Instant.parse(it) }
        } catch (e: DateTimeParseException) {
            null
        }
    }
}
