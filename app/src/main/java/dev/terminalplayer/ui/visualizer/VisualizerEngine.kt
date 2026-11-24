package dev.terminalplayer.ui.visualizer

import android.media.audiofx.Visualizer
import android.os.SystemClock
import android.util.Log
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Owns the `android.media.audiofx.Visualizer` effect attached to the ExoPlayer audio session and
 * turns raw FFT / waveform buffers into a render friendly model.
 *
 * ## Why two clocks
 * The platform caps `Visualizer` capture at [Visualizer.getMaxCaptureRate] milliHertz, i.e. ~20 Hz.
 * Drawing directly from those callbacks would produce a 20 fps slideshow. Instead:
 *
 *  - capture callbacks only write *target* values (fast, allocation free, main thread),
 *  - [advance] is called once per VSYNC from the Compose frame clock (60/90/120 Hz) and moves the
 *    *display* values towards the targets with asymmetric attack/release envelopes.
 *
 * The result is a 60+ fps animation that still reacts to real audio, with the classic
 * "fast attack, slow decay" look of hardware spectrum analysers.
 */
class VisualizerEngine(val audioSessionId: Int) : Visualizer.OnDataCaptureListener {

    private var visualizer: Visualizer? = null
    private var bandEdges = IntArray(BAND_COUNT + 1)

    /** Smoothed, render ready spectrum in `0f..1f`, low frequencies first. */
    val bands = FloatArray(BAND_COUNT)

    /** Peak hold envelope used for the `*` markers on the rays. */
    val peaks = FloatArray(BAND_COUNT)

    private val targets = FloatArray(BAND_COUNT)

    @Volatile
    var rms: Float = 0f
        private set

    @Volatile
    var peak: Float = 0f
        private set

    @Volatile
    var bass: Float = 0f
        private set

    @Volatile
    var fps: Float = 0f
        private set

    /** Radar sweep angle in radians, wrapped to `0..2π`. */
    @Volatile
    var sweep: Float = 0f
        private set

    @Volatile
    var frames: Long = 0L
        private set

    @Volatile
    var captureSize: Int = 0
        private set

    @Volatile
    var sampleRate: Int = 0
        private set

    private var targetRms = 0f
    private var targetBass = 0f
    private var lastCaptureAt = 0L

    val isRunning: Boolean get() = visualizer != null

    /**
     * Attaches the effect. Returns `null` on success or a short terminal style error message.
     * Never throws: some OEM ROMs refuse `Visualizer` entirely.
     */
    fun start(): String? {
        if (visualizer != null) return null
        return try {
            val effect = Visualizer(audioSessionId)
            val range = Visualizer.getCaptureSizeRange()
            var size = range[1]
            if ((size and (size - 1)) != 0) size = Integer.highestOneBit(size)
            size = size.coerceIn(range[0], range[1])

            effect.captureSize = size
            captureSize = size
            buildBandEdges(size / 2)

            val status = effect.setDataCaptureListener(
                this,
                Visualizer.getMaxCaptureRate(),
                /* waveform = */ true,
                /* fft = */ true,
            )
            if (status != Visualizer.SUCCESS) {
                runCatching { effect.release() }
                return "VISUALIZER INIT FAILED // STATUS $status"
            }

            effect.enabled = true
            visualizer = effect
            Log.i(TAG, "capture attached: session=$audioSessionId size=$size")
            null
        } catch (throwable: Throwable) {
            Log.w(TAG, "visualizer unavailable for session $audioSessionId", throwable)
            "VISUALIZER UNAVAILABLE // ${throwable.javaClass.simpleName.uppercase()}"
        }
    }

    fun release() {
        val effect = visualizer ?: return
        visualizer = null
        runCatching { effect.enabled = false }
        runCatching { effect.release() }
        targets.fill(0f)
        bands.fill(0f)
        peaks.fill(0f)
        targetRms = 0f
        rms = 0f
        peak = 0f
        bass = 0f
        lastCaptureAt = 0L
    }

    // ------------------------------------------------------------ capture callbacks

    override fun onWaveFormDataCapture(
        visualizer: Visualizer?,
        waveform: ByteArray?,
        samplingRate: Int,
    ) {
        if (waveform == null || waveform.isEmpty()) return
        if (samplingRate > 0) this.sampleRate = samplingRate

        var sum = 0f
        var count = 0
        var index = 0
        val step = (waveform.size / RMS_POINTS).coerceAtLeast(1)
        while (index < waveform.size) {
            val sample = waveform[index] / 128f
            sum += sample * sample
            count++
            index += step
        }
        targetRms = if (count > 0) sqrt(sum / count).coerceIn(0f, 1f) else 0f
        lastCaptureAt = SystemClock.elapsedRealtime()
    }

    override fun onFftDataCapture(
        visualizer: Visualizer?,
        fft: ByteArray?,
        samplingRate: Int,
    ) {
        if (fft == null) return
        if (samplingRate > 0) this.sampleRate = samplingRate

        val bins = min(captureSize / 2, fft.size / 2)
        if (bins <= 1) return

        var bassSum = 0f
        var bassBands = 0

        for (band in 0 until BAND_COUNT) {
            val start = bandEdges[band].coerceIn(1, bins - 1)
            val end = bandEdges[band + 1].coerceIn(start + 1, bins)
            var magnitude = 0f
            var index = start
            while (index < end) {
                val real = fft[index * 2].toFloat()
                val imaginary = fft[index * 2 + 1].toFloat()
                magnitude += hypot(real, imaginary)
                index++
            }
            magnitude /= (end - start) * 128f

            // dBFS -> normalised 0..1, then a perceptual curve so quiet passages stay readable.
            val decibels = 20f * log10(magnitude + 1e-5f)
            val normalised = ((decibels + 62f) / 62f).coerceIn(0f, 1f)
            targets[band] = normalised * normalised

            if (band < BASS_BANDS) {
                bassSum += targets[band]
                bassBands++
            }
        }

        targetBass = if (bassBands > 0) bassSum / bassBands else 0f
        lastCaptureAt = SystemClock.elapsedRealtime()
    }

    // ------------------------------------------------------------------ frame clock

    /**
     * Advances the animation by [frameDeltaNanos] (one VSYNC interval) and updates every smoothed
     * value. Must be called from the main thread, once per frame.
     */
    fun advance(frameDeltaNanos: Long) {
        val deltaSeconds = (frameDeltaNanos / 1_000_000_000f).coerceIn(0.001f, 0.2f)
        frames++

        val instantaneousFps = 1f / deltaSeconds
        fps = if (frames <= 1L) instantaneousFps else fps + (instantaneousFps - fps) * 0.06f
        sweep = (sweep + deltaSeconds * 0.9f) % TWO_PI_F

        // Fall back to silence if the effect stops delivering data (paused / released).
        if (lastCaptureAt > 0L && SystemClock.elapsedRealtime() - lastCaptureAt > CAPTURE_TIMEOUT_MS) {
            for (index in 0 until BAND_COUNT) targets[index] *= 0.92f
            targetRms *= 0.92f
            targetBass *= 0.92f
        }

        val attack = 1f - exp(-deltaSeconds / ATTACK_SECONDS)
        val release = 1f - exp(-deltaSeconds / RELEASE_SECONDS)

        for (index in 0 until BAND_COUNT) {
            val target = targets[index]
            val current = bands[index]
            val next = current + (target - current) * if (target > current) attack else release
            bands[index] = next
            val previousPeak = peaks[index]
            peaks[index] = if (next >= previousPeak) next else max(0f, previousPeak - deltaSeconds * 0.45f)
        }

        rms += (targetRms - rms) * (1f - exp(-deltaSeconds / 0.06f))
        bass += (targetBass - bass) * (1f - exp(-deltaSeconds / 0.09f))
        peak = max(targetRms, peak - deltaSeconds * 0.35f)
    }

    /** Dominant band index, used by the HUD and by the "loudest ray" highlight. */
    fun dominantBand(): Int {
        var index = 0
        var value = -1f
        for (band in 0 until BAND_COUNT) {
            if (bands[band] > value) {
                value = bands[band]
                index = band
            }
        }
        return index
    }

    /** Smooth 0..1 level of the loudest band. */
    fun dominantLevel(): Float = bands.maxOrNull() ?: 0f

    private fun buildBandEdges(binCount: Int) {
        val minBin = 1
        val maxBin = max(BAND_COUNT + 2, (binCount * 0.82f).roundToInt())
        var previous = minBin
        bandEdges[0] = previous
        for (band in 1..BAND_COUNT) {
            val fraction = band.toFloat() / BAND_COUNT
            val edge = (minBin * (maxBin.toFloat() / minBin).pow(fraction)).roundToInt()
                .coerceIn(previous + 1, maxBin)
            bandEdges[band] = edge
            previous = edge
        }
    }

    companion object {
        const val BAND_COUNT = 40

        private const val TAG = "AsciiVisualizer"
        private const val BASS_BANDS = 6
        private const val RMS_POINTS = 96
        private const val CAPTURE_TIMEOUT_MS = 450L
        private const val ATTACK_SECONDS = 0.035f
        private const val RELEASE_SECONDS = 0.18f
        private val TWO_PI_F = (Math.PI * 2.0).toFloat()
    }
}
