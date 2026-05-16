package se.lublin.humla.net

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer

class PacketBuffer(private val buffer: ByteBuffer) {
    constructor(data: ByteArray, len: Int) : this(ByteBuffer.wrap(data)) {
        buffer.limit(len)
    }

    fun size(): Int = buffer.position()

    fun capacity(): Int = buffer.limit()

    fun left(): Int = buffer.limit() - buffer.position()

    fun append(value: Long) {
        buffer.put(value.toByte())
    }

    fun append(data: ByteArray, len: Int) {
        buffer.put(data, 0, len)
    }

    fun skip(len: Int) {
        buffer.position(buffer.position() + len)
    }

    fun next(): Int = buffer.get().toInt() and 0xFF

    fun bufferBlock(size: Int): ByteBuffer {
        if (size > buffer.remaining()) {
            throw BufferUnderflowException()
        }
        val slicedBuffer = buffer.slice()
        slicedBuffer.limit(size)
        skip(size)
        return slicedBuffer
    }

    fun dataBlock(size: Int): ByteArray {
        val block = ByteArray(size)
        buffer.get(block, 0, size)
        return block
    }

    fun readBool(): Boolean = readLong().toInt() > 0

    fun readDouble(): Double {
        if (left() < 8) {
            throw BufferUnderflowException()
        }
        val bits = next().toLong() or
            (next().toLong() shl 8) or
            (next().toLong() shl 16) or
            (next().toLong() shl 24) or
            (next().toLong() shl 32) or
            (next().toLong() shl 40) or
            (next().toLong() shl 48) or
            (next().toLong() shl 56)
        return Double.fromBits(bits)
    }

    fun readFloat(): Float {
        if (left() < 4) {
            throw BufferUnderflowException()
        }
        val bits = next() or (next() shl 8) or (next() shl 16) or (next() shl 24)
        return Float.fromBits(bits)
    }

    fun readLong(): Long {
        var result = 0L
        val value = next().toLong()
        if ((value and 0x80) == 0x00L) {
            result = value and 0x7F
        } else if ((value and 0xC0) == 0x80L) {
            result = (value and 0x3F) shl 8 or next().toLong()
        } else if ((value and 0xF0) == 0xF0L) {
            when ((value and 0xFC).toInt()) {
                0xF0 -> result = (next().toLong() shl 24) or
                    (next().toLong() shl 16) or
                    (next().toLong() shl 8) or
                    next().toLong()
                0xF4 -> result = (next().toLong() shl 56) or
                    (next().toLong() shl 48) or
                    (next().toLong() shl 40) or
                    (next().toLong() shl 32) or
                    (next().toLong() shl 24) or
                    (next().toLong() shl 16) or
                    (next().toLong() shl 8) or
                    next().toLong()
                0xF8 -> result = readLong().inv()
                0xFC -> result = (value and 0x03).inv()
                else -> throw BufferUnderflowException()
            }
        } else if ((value and 0xF0) == 0xE0L) {
            result = (value and 0x0F) shl 24 or (next().toLong() shl 16) or (next().toLong() shl 8) or next().toLong()
        } else if ((value and 0xE0) == 0xC0L) {
            result = (value and 0x1F) shl 16 or (next().toLong() shl 8) or next().toLong()
        }
        return result
    }

    fun rewind() {
        buffer.rewind()
    }

    fun writeBool(value: Boolean) {
        writeLong(if (value) 1 else 0)
    }

    fun writeDouble(value: Double) {
        val bits = value.toBits()
        append(bits and 0xFF)
        append((bits shr 8) and 0xFF)
        append((bits shr 16) and 0xFF)
        append((bits shr 24) and 0xFF)
        append((bits shr 32) and 0xFF)
        append((bits shr 40) and 0xFF)
        append((bits shr 48) and 0xFF)
        append((bits shr 56) and 0xFF)
    }

    fun writeFloat(value: Float) {
        val bits = value.toBits()
        append((bits and 0xFF).toLong())
        append(((bits shr 8) and 0xFF).toLong())
        append(((bits shr 16) and 0xFF).toLong())
        append(((bits shr 24) and 0xFF).toLong())
    }

    fun writeLong(value: Long) {
        var output = value
        if ((output and Long.MIN_VALUE) > 0 && output.inv() < 0x100000000L) {
            output = output.inv()
            if (output <= 0x3) {
                append(0xFCL or output)
                return
            } else {
                append(0xF8)
            }
        }
        if (output < 0x80) {
            append(output)
        } else if (output < 0x4000) {
            append((output shr 8) or 0x80)
            append(output and 0xFF)
        } else if (output < 0x200000) {
            append((output shr 16) or 0xC0)
            append((output shr 8) and 0xFF)
            append(output and 0xFF)
        } else if (output < 0x10000000) {
            append((output shr 24) or 0xE0)
            append((output shr 16) and 0xFF)
            append((output shr 8) and 0xFF)
            append(output and 0xFF)
        } else if (output < 0x100000000L) {
            append(0xF0)
            append((output shr 24) and 0xFF)
            append((output shr 16) and 0xFF)
            append((output shr 8) and 0xFF)
            append(output and 0xFF)
        } else {
            append(0xF4)
            append((output shr 56) and 0xFF)
            append((output shr 48) and 0xFF)
            append((output shr 40) and 0xFF)
            append((output shr 32) and 0xFF)
            append((output shr 24) and 0xFF)
            append((output shr 16) and 0xFF)
            append((output shr 8) and 0xFF)
            append(output and 0xFF)
        }
    }

    companion object {
        @JvmStatic
        fun allocate(len: Int): PacketBuffer = PacketBuffer(ByteBuffer.allocate(len))

        @JvmStatic
        fun allocateDirect(len: Int): PacketBuffer = PacketBuffer(ByteBuffer.allocateDirect(len))
    }
}
