package org.scottishtecharmy.soundscape.hardware.bose

import android.util.Range
import androidx.compose.ui.input.key.type
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SamplePeriod(val rawValue: Short, val milliseconds: Int) {

    companion object {
        fun set(rawValue: Short) : SamplePeriod {
            return SamplePeriod(rawValue, 0)
        }
    }
    fun all() : Array<Int> {
        return arrayOf(
            320,
            160,
            80,
            40,
            20,
        )
    }

    fun from(milliseconds: Int) : Int {
        val index = all().indexOf(milliseconds)
        return index
    }
}

class SensorInformationEntry(val sensorId:Int,
                             val scaledValueRange: Range<Short>,
                             val rawValueRange: Range<Short>,
                             val availableSamplePeriods: SamplePeriod,
                             val sampleLength: Int
) {

    companion object {
        fun parse(buffer: ByteBuffer) : SensorInformationEntry {
            val sensorId = buffer.get().toInt()

            val minScaled = buffer.getShort()
            val maxScaled = buffer.getShort()

            val minRaw = buffer.getShort()
            val maxRaw = buffer.getShort()

            val samplePeriodBitmask = buffer.getShort()
            val availableSamplePeriods = SamplePeriod.set(samplePeriodBitmask)

            val sampleLength = buffer.get().toInt()

            val scaledValueRange = Range(minScaled, maxScaled)
            val rawValueRange = Range(minRaw, maxRaw)

            return SensorInformationEntry(
                sensorId,
                scaledValueRange,
                rawValueRange,
                availableSamplePeriods,
                sampleLength
            )
        }
    }

    fun dump() {
        println("SensorId: $sensorId, scaledValueRange: $scaledValueRange, rawValueRange: $rawValueRange, samplePeriod: $availableSamplePeriods, sampleLength: $sampleLength")
    }
}

class SensorInformation(val entries: List<SensorInformationEntry>) {

    companion object {
        fun parse(buffer: ByteBuffer): SensorInformation {
            val length = 12
            val parsedEntries = mutableListOf<SensorInformationEntry>()

            while (buffer.remaining() >= length) {
                val entry = SensorInformationEntry.parse(buffer)
                parsedEntries.add(entry)
            }

            return SensorInformation(parsedEntries)
        }
    }
}