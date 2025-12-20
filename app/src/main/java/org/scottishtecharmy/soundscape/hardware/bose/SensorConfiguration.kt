package org.scottishtecharmy.soundscape.hardware.bose

import androidx.compose.ui.input.key.type
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SensorConfigurationEntry(val type: String, var samplePeriod: Int) {

    companion object {
        val sensorTypes = listOf(
            "accelerometer",
            "gyroscope",
            "rotation",
            "gameRotation",
            "orientation",
            "magnetometer",
            "uncalibratedMagnetometer",
        )

        fun parse(buffer: ByteBuffer) : SensorConfigurationEntry? {
            val sensorId = buffer.get().toInt()
            if(sensorId >= sensorTypes.size) {
                return null
            }
            val sensor = sensorTypes[sensorId]
            val samplePeriod = buffer.getShort().toUShort().toInt()

            return SensorConfigurationEntry(sensor, samplePeriod);
        }
    }

    fun write(buffer: ByteBuffer) {
        val sensorId = sensorTypes.indexOf(type).takeIf { it != -1 } ?: 0
        buffer.put(sensorId.toByte())
        buffer.putShort(samplePeriod.toShort())
    }

    val isEnabled: Boolean
        get() = this.samplePeriod != 0
}

class SensorConfiguration(val entries: List<SensorConfigurationEntry>) {

    companion object {
        fun parse(buffer: ByteBuffer): SensorConfiguration {
            val length = 3
            val parsedEntries = mutableListOf<SensorConfigurationEntry>()

            while (buffer.remaining() >= length) {
                val entry = SensorConfigurationEntry.parse(buffer)
                if(entry != null) {
                    parsedEntries.add(entry)
                }
            }

            return SensorConfiguration(parsedEntries)
        }
    }

    fun enableAll() {
        // TODO: This is just enabling the rotation sensor for now
        entries[2].samplePeriod = 40
    }

    fun data() : ByteArray {
        val buffer = ByteBuffer.allocate(3 * entries.size)
        buffer.order(ByteOrder.BIG_ENDIAN)
        for(entry in entries) {
            entry.write(buffer)
        }
        return buffer.array()
    }
}