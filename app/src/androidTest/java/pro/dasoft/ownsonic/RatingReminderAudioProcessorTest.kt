package pro.dasoft.ownsonic

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class RatingReminderAudioProcessorTest {
    @Test fun musicRemainsUnchangedUntilReminderActuallyReachesPcm() {
        val processor = configured(48000, 2)
        val samples = ShortArray(9600) { (it % 2000 - 1000).toShort() }
        assertArrayEquals(samples, process(processor, samples))
        var starts = 0
        assertTrue(processor.request { starts++ })
        assertEquals(0, starts)
        assertFalse(processor.request { fail("The reminder must not be queued twice") })
        val output = process(processor, ShortArray(4800))
        assertEquals(1, starts)
        assertTrue(output.any { abs(it.toInt()) > 10000 })
        for (index in output.indices step 2) assertEquals(output[index], output[index + 1])
    }

    @Test fun twoToneReminderDucksMusicThenRestoresItAtDifferentSampleRates() {
        for (rate in listOf(8000, 44100, 48000, 96000)) {
            val processor = configured(rate, 1)
            var starts = 0
            processor.request { starts++ }
            val input = ShortArray(rate) { 20000 }
            // Feed multiple decoder buffers to verify phase and duration continuity.
            val output = input.asList().chunked(1024).flatMap {
                process(processor, it.toShortArray()).asList()
            }.toShortArray()
            assertEquals(1, starts)
            assertEquals(input.size, output.size)
            assertTrue(output.take(rate / 4).any { it < 0 })
            assertTrue(output.slice(rate * 36 / 100 until rate * 59 / 100).any { it < 0 })
            assertTrue(output.slice(rate * 26 / 100 until rate * 34 / 100).all { it == 20000.toShort() })
            assertTrue(output.drop(rate * 60 / 100).all { it == 20000.toShort() })
        }
    }

    @Test fun pipelineCanQueueAnAlreadyConsumedOutputBuffer() {
        val processor = configured(48000, 1)
        val input = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder())
        processor.queueInput(input)
        val consumed = processor.output
        consumed.position(consumed.limit())
        processor.queueInput(consumed)
        assertFalse(processor.output.hasRemaining())
        assertArrayEquals(ShortArray(8), process(processor, ShortArray(8)))
    }

    @Test fun seekOrQueueFlushDiscardsAnUnplayedReminder() {
        val processor = configured(48000, 1)
        processor.request { fail("A flushed reminder must not be confirmed") }
        processor.flush()
        assertArrayEquals(ShortArray(4800), process(processor, ShortArray(4800)))
        var started = false
        assertTrue(processor.request { started = true })
        process(processor, ShortArray(4800))
        assertTrue(started)
        processor.cancel()
        assertArrayEquals(ShortArray(4800), process(processor, ShortArray(4800)))
    }

    private fun configured(rate: Int, channels: Int) = RatingReminderAudioProcessor().apply {
        configure(AudioFormat(rate, channels, C.ENCODING_PCM_16BIT))
        flush()
    }

    private fun process(processor: RatingReminderAudioProcessor, samples: ShortArray): ShortArray {
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach { input.putShort(it) }
        input.flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())
        return ShortArray(output.remaining() / 2) { output.short }
    }
}
