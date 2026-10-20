package pro.dasoft.ownsonic

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Mixes the reminder into music PCM so both use the same volume and output device. */
@androidx.annotation.OptIn(UnstableApi::class)
class RatingReminderAudioProcessor : BaseAudioProcessor() {
    private data class Request(val generation: Int, val onStarted: () -> Unit)
    private val generation = AtomicInteger()
    private val pending = AtomicReference<Request?>()
    private val busy = AtomicBoolean()
    private var active: Request? = null
    private var frame = 0
    private var started = false
    private var toneSampleRate = 0
    private var tone = ShortArray(0)
    private var envelope = FloatArray(0)

    fun request(onStarted: () -> Unit): Boolean {
        if (!busy.compareAndSet(false, true)) return false
        pending.set(Request(generation.get(), onStarted))
        return true
    }

    fun cancel() {
        generation.incrementAndGet()
        pending.set(null)
        busy.set(false)
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }

    override fun onFlush() {
        cancel()
        active = null
        frame = 0
        val rate = inputAudioFormat.sampleRate
        if (rate <= 0 || rate == toneSampleRate) return
        toneSampleRate = rate
        tone = ShortArray(rate * 600 / 1000)
        envelope = FloatArray(tone.size)
        for (index in tone.indices) {
            val seconds = index.toDouble() / rate
            val local = if (seconds < 0.25) seconds else seconds - 0.35
            if (local !in 0.0..0.25) continue
            val fade = min(1.0, min(local, 0.25 - local) / 0.012).coerceAtLeast(0.0)
            val frequency = if (seconds < 0.25) 880.0 else 1174.66
            envelope[index] = fade.toFloat()
            tone[index] = (sin(2 * PI * frequency * local) * fade * 0.4 * Short.MAX_VALUE).toInt().toShort()
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val output = replaceOutputBuffer(inputBuffer.remaining())
        inputBuffer.order(ByteOrder.nativeOrder())
        if (active?.generation != generation.get()) active = null
        if (active == null) {
            active = pending.getAndSet(null)?.takeIf { it.generation == generation.get() }
            frame = 0
            started = false
        }
        val request = active
        if (request == null || tone.isEmpty()) {
            output.put(inputBuffer)
        } else {
            while (inputBuffer.hasRemaining()) {
                val mixing = frame < tone.size
                val gain = if (mixing) 1f - 0.65f * envelope[frame] else 1f
                val beep = if (mixing) tone[frame].toInt() else 0
                repeat(inputAudioFormat.channelCount) {
                    val sample = inputBuffer.short
                    output.putShort(if (mixing) (sample * gain + beep).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort() else sample)
                }
                if (!started && beep != 0) {
                    started = true
                    request.onStarted()
                }
                frame++
            }
            if (frame >= tone.size) {
                active = null
                busy.set(false)
            }
        }
        output.flip()
    }
}
