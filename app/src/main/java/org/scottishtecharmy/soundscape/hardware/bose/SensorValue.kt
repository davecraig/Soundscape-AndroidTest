package org.scottishtecharmy.soundscape.hardware.bose

import android.util.Range
import androidx.compose.ui.input.key.type
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.pow

abstract class SensorValue() {
    abstract fun getLength() : Int
    abstract fun getString() : String
}

class SensorVector(val x: Double, val y: Double, val z: Double) : SensorValue() {
    companion object {
        fun parse(buffer: ByteBuffer): SensorVector {
            fun getComponent(): Double {
                return buffer.getShort().toDouble() / 2.0.pow(12)
            }

            val x = getComponent()
            val y = getComponent()
            val z = getComponent()

            val vector = SensorVector(x, y, z)
            //vector.applyMatrix4(correctionMatrix)
            return vector
        }
    }
    override fun getLength() : Int { return 6 }
    override fun getString() : String {
        return "($x, $y, $z)"
    }
}

class SensorQuaternion(val x: Double, val y: Double, val z: Double, val w: Double) : SensorValue() {
    companion object {
        fun parse(buffer: ByteBuffer): SensorQuaternion {
            fun getComponent(): Double {
                return buffer.getShort().toDouble() / 2.0.pow(14)
            }

            val x = getComponent()
            val y = getComponent()
            val z = getComponent()
            val w = getComponent()

            val quaternion = SensorQuaternion(x, y, z, w)
            //quaternion.correctOrientationForTHREEjs(correctionMatrix)
            return quaternion
        }
    }

    fun getYaw(): Double {
        // Standard formula for converting a quaternion to yaw (Z-axis rotation).
        val yawInRadians = atan2(2.0 * (w * z + x * y), 1.0 - 2.0 * (y * y + z * z))

        // Convert radians to degrees for easier use.
        return yawInRadians * (180.0 / PI)
    }

    override fun getLength() : Int { return 8 }
    override fun getString() : String {
        return "(${getYaw()} $x, $y, $z, $w)"
    }
}

abstract class SensorAccuracy() {
    abstract fun getLength() : Int
    abstract fun getString() : String
}

class VectorAccuracy(val accuracy: Int) : SensorAccuracy() {
    companion object {
        fun parse(buffer: ByteBuffer): VectorAccuracy {
            val accuracy = buffer.get().toInt()
            return VectorAccuracy(accuracy)
        }
    }
    override fun getLength() : Int { return 1 }
    override fun getString() : String {
        return accuracy.toString()
    }
}

class QuaternionAccuracy(val accuracy: Short) : SensorAccuracy() {
    companion object {
        fun parse(buffer: ByteBuffer): QuaternionAccuracy {
            val accuracy = buffer.getShort()
            return QuaternionAccuracy(accuracy)
        }
    }
    override fun getLength() : Int { return 2 }
    override fun getString() : String {
        return accuracy.toString()
    }
}

class SensorSample(val type: Int, val value: SensorValue, val accuracy: SensorAccuracy?, val bias: Boolean) {

    companion object {
        enum class ValueType {
            Vector,
            Quaternion,
        }

        enum class ValueAccuracy {
            VectorAccuracy,
            QuaternionAccuracy,
            UnknownAccuracy
        }

        class SensorMetadata(
            val value: ValueType,
            val accuracy: ValueAccuracy = ValueAccuracy.UnknownAccuracy,
            val bias: Boolean = false
        )

        // It's placed inside the companion object.
        private val metadata = mapOf(
            0 to SensorMetadata(ValueType.Vector, ValueAccuracy.VectorAccuracy),
            1 to SensorMetadata(ValueType.Vector, ValueAccuracy.VectorAccuracy),
            2 to SensorMetadata(ValueType.Quaternion, ValueAccuracy.QuaternionAccuracy),
            3 to SensorMetadata(ValueType.Quaternion),
            4 to SensorMetadata(ValueType.Vector, ValueAccuracy.VectorAccuracy),
            5 to SensorMetadata(ValueType.Vector, ValueAccuracy.VectorAccuracy),
            6 to SensorMetadata(ValueType.Vector, ValueAccuracy.UnknownAccuracy, bias = true)
        )

        fun parse(sensorType: Int, buffer: ByteBuffer): SensorSample? {

            val metadata = metadata[sensorType]
            if (metadata != null) {
                val value = when (metadata.value) {
                    ValueType.Vector -> {
                        SensorVector.parse(buffer)
                    }

                    ValueType.Quaternion -> {
                        SensorQuaternion.parse(buffer)
                    }
                }

                val accuracy = when (metadata.accuracy) {
                    ValueAccuracy.VectorAccuracy -> {
                        VectorAccuracy.parse(buffer)
                    }

                    ValueAccuracy.QuaternionAccuracy -> {
                        QuaternionAccuracy.parse(buffer)
                    }

                    else -> null
                }

//                if (metadata.bias) {
//                    bias = metadata.bias.parse(dataView, offset);
//                }

                return SensorSample(sensorType, value, accuracy, false)
            }
            return null
        }
    }
    fun dump(timestamp: SensorTimestamp) {
        println("type $type, ${timestamp.timestamp}, ${value.getString()}, ${accuracy?.getString() ?: ""}")
    }
}

class SensorTimestamp(val timestamp: Short) {
    companion object {
        fun parse(buffer: ByteBuffer): SensorTimestamp {
            val timestamp = buffer.getShort()
            return SensorTimestamp(timestamp)
        }
    }
}

class SampleData() {
    companion object {
        fun parse(buffer: ByteBuffer) : Double? {
            var lastSample: Double? = null
            while(buffer.remaining() > 0) {
                val sensorId = buffer.get().toInt()
                val timestamp = SensorTimestamp.parse(buffer)
                val sample = SensorSample.parse(sensorId, buffer)
                lastSample = (sample?.value as SensorQuaternion?)?.getYaw()
            }
            return lastSample
        }
    }
}
