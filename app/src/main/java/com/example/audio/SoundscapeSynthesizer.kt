package com.example.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Common streaming source abstraction so bundled real-time synthesizers and
 * on-demand cached seamless loop files can be mixed interchangeably with
 * sample-accurate continuity and zero clicks.
 */
interface StereoLayerSource {
    fun nextRawStereoSample(): Pair<Double, Double>
}

class RealtimeLayerSource(
    private val track: SoundTrack,
    private val synthesizer: SoundscapeSynthesizer
) : StereoLayerSource {
    override fun nextRawStereoSample(): Pair<Double, Double> {
        return synthesizer.nextRawStereoSample(track)
    }
}

class CachedLoopLayerSource(
    private val loopPcm: ShortArray
) : StereoLayerSource {
    private val frameCount = (loopPcm.size / 2).coerceAtLeast(1)
    private var frameCursor = 0

    override fun nextRawStereoSample(): Pair<Double, Double> {
        val idx = frameCursor * 2
        val l = loopPcm[idx] / 32767.0
        val r = loopPcm[idx + 1] / 32767.0
        frameCursor = (frameCursor + 1) % frameCount
        return l to r
    }
}

/**
 * Clean 44.1 kHz Data-Driven Stereo Acoustic Synthesizer for Mind Relaxation & Sleep.
 *
 * Uses each [SoundTrack]'s [SynthProfileConfig] so new sounds added to `sound_catalog.json`
 * automatically produce distinct, spike-free, seamlessly loopable stereo soundscapes.
 */
class SoundscapeSynthesizer(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    seed: Long = 42L
) {
    private val rng = Random(seed)

    // Left & right Pink/Brown noise filter banks
    private var lB0 = 0.0; private var lB1 = 0.0; private var lB2 = 0.0
    private var lB3 = 0.0; private var lB4 = 0.0; private var lB5 = 0.0; private var lB6 = 0.0
    private var rB0 = 0.0; private var rB1 = 0.0; private var rB2 = 0.0
    private var rB3 = 0.0; private var rB4 = 0.0; private var rB5 = 0.0; private var rB6 = 0.0
    private var lBrown = 0.0
    private var rBrown = 0.0

    // Dual-pole warm low-pass filter states
    private var lpL1 = 0.0; private var lpL2 = 0.0
    private var lpR1 = 0.0; private var lpR2 = 0.0

    // Oscillators & LFOs
    private var phase1 = 0.0
    private var phase2 = 0.0
    private var phase3 = 0.0
    private var lfo1 = 0.0
    private var lfo2 = 0.0

    // Soft acoustic events (raindrops, thunder swells, birds, bamboo, bowls, owls, ticks)
    private var eventTimer = 0
    private var eventEnv = 0.0
    private var eventFreq = 440.0
    private var eventFreqTarget = 440.0
    private var eventPan = 0.5
    private var eventSubStage = 0
    private var eventPhase = 0.0

    // Stereo echo ring buffer (~180 ms) for spacious woods/water/bowls
    private val delaySamples = (sampleRate * 0.18).toInt().coerceAtLeast(1)
    private val delayBufL = DoubleArray(delaySamples)
    private val delayBufR = DoubleArray(delaySamples)
    private var delayIdx = 0

    private var sampleCounter: Long = 0
    private val fadeInSamples = (sampleRate * 1.5).toLong().coerceAtLeast(1L)

    fun reset() {
        lB0 = 0.0; lB1 = 0.0; lB2 = 0.0; lB3 = 0.0; lB4 = 0.0; lB5 = 0.0; lB6 = 0.0
        rB0 = 0.0; rB1 = 0.0; rB2 = 0.0; rB3 = 0.0; rB4 = 0.0; rB5 = 0.0; rB6 = 0.0
        lBrown = 0.0; rBrown = 0.0
        lpL1 = 0.0; lpL2 = 0.0; lpR1 = 0.0; lpR2 = 0.0
        phase1 = 0.0; phase2 = 0.0; phase3 = 0.0; lfo1 = 0.0; lfo2 = 0.0
        eventTimer = 0; eventEnv = 0.0; eventFreq = 440.0; eventFreqTarget = 440.0
        eventPan = 0.5; eventSubStage = 0; eventPhase = 0.0
        delayBufL.fill(0.0); delayBufR.fill(0.0); delayIdx = 0
        sampleCounter = 0
    }

    /**
     * Generates a self-contained, seamlessly loopable stereo PCM buffer of [durationSeconds]
     * using equal-power raised-cosine crossfade between tail and head so loop wrap-around
     * has zero audible clicks, pops, or gaps.
     */
    fun generateSeamlessLoopPcm(
        track: SoundTrack,
        durationSeconds: Int = DEFAULT_LOOP_SECONDS,
        crossfadeSeconds: Double = 1.5,
        onProgress: ((Float) -> Unit)? = null
    ): ShortArray {
        reset()
        val totalFrames = (durationSeconds * sampleRate).coerceAtLeast(sampleRate)
        val crossfadeFrames = (crossfadeSeconds * sampleRate).toInt().coerceIn(1, totalFrames / 3)
        val rawFramesCount = totalFrames + crossfadeFrames

        val rawL = DoubleArray(rawFramesCount)
        val rawR = DoubleArray(rawFramesCount)

        // Warm up filters to avoid cold-start DC step
        val warmupFrames = (sampleRate * 0.25).toInt()
        for (w in 0 until warmupFrames) {
            nextStereoFrame(track.synthProfile)
        }

        val progressStep = (rawFramesCount / 10).coerceAtLeast(1)
        for (i in 0 until rawFramesCount) {
            val (l, r) = nextStereoFrame(track.synthProfile)
            rawL[i] = l
            rawR[i] = r
            if (onProgress != null && i % progressStep == 0) {
                onProgress((i.toFloat() / rawFramesCount) * 0.7f)
            }
        }

        val out = ShortArray(totalFrames * 2)
        val outStep = (totalFrames / 5).coerceAtLeast(1)
        for (i in 0 until totalFrames) {
            var l = rawL[i]
            var r = rawR[i]

            if (i < crossfadeFrames) {
                val t = i.toDouble() / crossfadeFrames.toDouble()
                val headGain = sin(t * PI * 0.5)
                val tailGain = cos(t * PI * 0.5)
                val tailIdx = totalFrames + i
                l = l * headGain + rawL[tailIdx] * tailGain
                r = r * headGain + rawR[tailIdx] * tailGain
            }

            val finalL = tanh(l * 1.15) * 0.72
            val finalR = tanh(r * 1.15) * 0.72
            out[i * 2] = (finalL.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()
            out[i * 2 + 1] = (finalR.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()

            if (onProgress != null && i % outStep == 0) {
                onProgress(0.7f + (i.toFloat() / totalFrames) * 0.25f)
            }
        }
        onProgress?.invoke(1.0f)
        return out
    }

    fun nextRawStereoSample(track: SoundTrack): Pair<Double, Double> {
        return nextStereoFrame(track.synthProfile)
    }

    private fun nextStereoFrame(p: SynthProfileConfig): Pair<Double, Double> {
        return when (p.engineMode.uppercase()) {
            // Nature & Forest
            "WIND" -> synthWind(p)
            "LEAVES" -> synthLeaves(p)
            "BAMBOO" -> synthBamboo(p)
            "MEADOW" -> synthMeadow(p)

            // Rain & Water
            "RAIN" -> synthRain(p, metallic = false)
            "TIN_ROOF" -> synthRain(p, metallic = true)
            "THUNDERSTORM" -> synthThunderstorm(p)
            "STREAM" -> synthStream(p)
            "OCEAN" -> synthOcean(p)
            "LAKE_RIPPLES" -> synthLakeRipples(p)
            "WATERFALL" -> synthWaterfall(p)

            // Animals & Wildlife
            "BIRDS" -> synthBirds(p, tropical = false)
            "RAINFOREST_BIRDS" -> synthBirds(p, tropical = true)
            "CAT_PURR" -> synthCatPurr(p)
            "CRICKETS" -> synthCrickets(p)
            "OWL" -> synthOwl(p)
            "FROGS" -> synthFrogs(p)
            "SEAGULLS" -> synthSeagulls(p)
            "BEES" -> synthBees(p)
            "WHALE" -> synthWhale(p)

            // Electronics & Colored Noise
            "FAN" -> synthFan(p)
            "AC" -> synthApplianceHum(p, rumbleMod = 0.0)
            "DRYER", "WASHER" -> synthApplianceHum(p, rumbleMod = p.lfoDepth)
            "HAIR_DRYER", "VACUUM" -> synthDenseAirNoise(p)
            "AIRPLANE" -> synthAirplane(p)
            "TRAIN" -> synthTrain(p)
            "RADIO_STATIC" -> synthRadioStatic(p)
            "BROWN_NOISE" -> synthColoredNoise(p, isBrownFocus = true)
            "PINK_NOISE" -> synthColoredNoise(p, isBrownFocus = false)

            // Cozy & Deep Focus
            "FIRE" -> synthFire(p)
            "CLOCK" -> synthClock(p)
            "COFFEE_SHOP" -> synthCoffeeShop(p)
            "SINGING_BOWL" -> synthSingingBowls(p)
            "BINAURAL" -> synthBinaural(p)

            else -> synthColoredNoise(p, isBrownFocus = p.brownWeight >= p.pinkWeight)
        }
    }

    // --- Noise Primitives ---

    private fun nextStereoPink(): Pair<Double, Double> {
        val wL = rng.nextDouble(-1.0, 1.0)
        lB0 = 0.99886 * lB0 + wL * 0.0555179
        lB1 = 0.99332 * lB1 + wL * 0.0750759
        lB2 = 0.96900 * lB2 + wL * 0.1538520
        lB3 = 0.86650 * lB3 + wL * 0.3104856
        lB4 = 0.55000 * lB4 + wL * 0.5329522
        lB5 = -0.7616 * lB5 - wL * 0.0168980
        val pL = (lB0 + lB1 + lB2 + lB3 + lB4 + lB5 + lB6 + wL * 0.5362) * 0.08
        lB6 = wL * 0.115926

        val wR = rng.nextDouble(-1.0, 1.0)
        rB0 = 0.99886 * rB0 + wR * 0.0555179
        rB1 = 0.99332 * rB1 + wR * 0.0750759
        rB2 = 0.96900 * rB2 + wR * 0.1538520
        rB3 = 0.86650 * rB3 + wR * 0.3104856
        rB4 = 0.55000 * rB4 + wR * 0.5329522
        rB5 = -0.7616 * rB5 - wR * 0.0168980
        val pR = (rB0 + rB1 + rB2 + rB3 + rB4 + rB5 + rB6 + wR * 0.5362) * 0.08
        rB6 = wR * 0.115926

        return (pL * 0.85 + pR * 0.15) to (pR * 0.85 + pL * 0.15)
    }

    private fun nextStereoBrown(): Pair<Double, Double> {
        val wL = rng.nextDouble(-1.0, 1.0)
        val wR = rng.nextDouble(-1.0, 1.0)
        lBrown = (lBrown + 0.025 * wL) * 0.994
        rBrown = (rBrown + 0.025 * wR) * 0.994
        return (lBrown * 2.1 * 0.84 + rBrown * 2.1 * 0.16) to (rBrown * 2.1 * 0.84 + lBrown * 2.1 * 0.16)
    }

    private fun blendedNoise(p: SynthProfileConfig): Pair<Double, Double> {
        val (pL, pR) = nextStereoPink()
        val (bL, bR) = nextStereoBrown()
        val sum = (p.pinkWeight + p.brownWeight).coerceAtLeast(0.01)
        val pw = p.pinkWeight / sum
        val bw = p.brownWeight / sum
        return (pL * pw + bL * bw) to (pR * pw + bR * bw)
    }

    private fun lowPassStereo(inL: Double, inR: Double, cutoffCoeff: Double): Pair<Double, Double> {
        val a = cutoffCoeff.coerceIn(0.001, 0.95)
        lpL1 += a * (inL - lpL1)
        lpL2 += a * (lpL1 - lpL2)
        lpR1 += a * (inR - lpR1)
        lpR2 += a * (lpR1 - lpR2)
        return lpL2 to lpR2
    }

    private fun applyStereoDelay(inL: Double, inR: Double, wet: Double = 0.25): Pair<Double, Double> {
        val dL = delayBufL[delayIdx]
        val dR = delayBufR[delayIdx]
        delayBufL[delayIdx] = inL + dR * 0.28
        delayBufR[delayIdx] = inR + dL * 0.28
        delayIdx = (delayIdx + 1) % delaySamples
        return (inL + dR * wet) to (inR + dL * wet)
    }

    private fun advancePhase(current: Double, hz: Double): Double {
        var next = current + (2.0 * PI * hz) / sampleRate
        if (next >= 2.0 * PI) next -= 2.0 * PI
        return next
    }

    // --- 1. Nature & Forest ---

    private fun synthWind(p: SynthProfileConfig): Pair<Double, Double> {
        val (nL, nR) = blendedNoise(p)
        lfo1 = advancePhase(lfo1, p.lfoRateHz)
        lfo2 = advancePhase(lfo2, p.lfoRateHz * 1.55)
        val gustL = 0.5 + 0.5 * sin(lfo1)
        val gustR = 0.5 + 0.5 * sin(lfo2 + 0.7)
        val cutoff = (p.lowPassCutoff + p.lfoDepth * 0.22 * ((gustL + gustR) * 0.5)).coerceIn(0.03, 0.5)
        val (wL, wR) = lowPassStereo(
            nL * (0.52 + 0.45 * gustL),
            nR * (0.52 + 0.45 * gustR),
            cutoff
        )
        var tone = 0.0
        if (p.toneHzLeft > 20.0) {
            phase1 = advancePhase(phase1, p.toneHzLeft + 8.0 * gustL)
            tone = sin(phase1) * 0.045 * gustL
        }
        return applyStereoDelay(wL * 0.88 + tone, wR * 0.88 + tone, wet = 0.24)
    }

    private fun synthLeaves(p: SynthProfileConfig): Pair<Double, Double> {
        val (nL, nR) = blendedNoise(p)
        lfo1 = advancePhase(lfo1, p.lfoRateHz)
        lfo2 = advancePhase(lfo2, p.lfoRateHz * 1.7)
        val shimmerL = 0.45 + 0.55 * ((sin(lfo1) + 1.0) * 0.5)
        val shimmerR = 0.45 + 0.55 * ((sin(lfo2) + 1.0) * 0.5)
        val (leafL, leafR) = lowPassStereo(nL * shimmerL, nR * shimmerR, p.lowPassCutoff)

        if (eventTimer <= 0 && rng.nextDouble() < 0.0012 * p.eventDensity) {
            eventEnv = 0.16 + rng.nextDouble() * 0.1
            eventPan = 0.15 + rng.nextDouble() * 0.7
            eventTimer = (sampleRate * 0.05).toInt()
        }
        var rustleL = 0.0; var rustleR = 0.0
        if (eventEnv > 0.002) {
            val tap = rng.nextDouble(-1.0, 1.0) * eventEnv * 0.18
            rustleL = tap * (1.0 - eventPan)
            rustleR = tap * eventPan
            eventEnv *= 0.992
        }
        if (eventTimer > 0) eventTimer--
        return (leafL * 0.82 + rustleL) to (leafR * 0.82 + rustleR)
    }

    private fun synthBamboo(p: SynthProfileConfig): Pair<Double, Double> {
        val (wL, wR) = synthWind(p)
        if (eventTimer <= 0 && rng.nextDouble() < 0.0005 * p.eventDensity) {
            eventEnv = 0.24 + rng.nextDouble() * 0.12
            eventFreq = p.toneHzLeft + rng.nextDouble() * (p.toneHzRight - p.toneHzLeft).coerceAtLeast(20.0)
            eventPan = 0.2 + rng.nextDouble() * 0.6
            eventTimer = (sampleRate * 0.55).toInt()
        }
        var clackL = 0.0; var clackR = 0.0
        if (eventEnv > 0.001) {
            eventPhase = advancePhase(eventPhase, eventFreq)
            val hollow = sin(eventPhase) * eventEnv * 0.22
            clackL = hollow * (1.0 - eventPan)
            clackR = hollow * eventPan
            eventEnv *= 0.994
        }
        if (eventTimer > 0) eventTimer--
        val (wetL, wetR) = applyStereoDelay(clackL, clackR, wet = 0.36)
        return (wL * 0.8 + wetL) to (wR * 0.8 + wetR)
    }

    private fun synthMeadow(p: SynthProfileConfig): Pair<Double, Double> {
        val (wL, wR) = synthWind(p)
        // Subtle distant birdsong warmth mixed softly into grass breeze
        if (eventTimer <= 0 && rng.nextDouble() < 0.0004 * p.eventDensity) {
            eventEnv = 0.14
            eventFreq = 1100.0 + rng.nextDouble() * 500.0
            eventPan = 0.25 + rng.nextDouble() * 0.5
            eventTimer = (sampleRate * 1.4).toInt()
        }
        var noteL = 0.0; var noteR = 0.0
        if (eventEnv > 0.001) {
            eventPhase = advancePhase(eventPhase, eventFreq)
            val chirp = sin(eventPhase) * eventEnv * 0.12
            noteL = chirp * (1.0 - eventPan)
            noteR = chirp * eventPan
            eventEnv *= 0.997
        }
        if (eventTimer > 0) eventTimer--
        return (wL * 0.88 + noteL) to (wR * 0.88 + noteR)
    }

    // --- 2. Rain & Water ---

    private fun synthRain(p: SynthProfileConfig, metallic: Boolean): Pair<Double, Double> {
        val (nL, nR) = blendedNoise(p)
        val (warmL, warmR) = lowPassStereo(nL, nR, p.lowPassCutoff)
        lfo1 = advancePhase(lfo1, p.lfoRateHz)
        val swell = 0.88 + p.lfoDepth * sin(lfo1)

        val dropChance = 0.0012 * p.eventDensity.coerceAtLeast(0.1)
        if (eventTimer <= 0 && rng.nextDouble() < dropChance) {
            eventEnv = if (metallic) 0.24 else 0.17
            val lo = p.toneHzLeft.coerceAtLeast(350.0)
            val hi = p.toneHzRight.coerceAtLeast(lo + 200.0)
            eventFreq = lo + rng.nextDouble() * (hi - lo)
            eventPan = 0.15 + rng.nextDouble() * 0.7
            eventTimer = (sampleRate * 0.035).toInt()
        }
        var dropL = 0.0; var dropR = 0.0
        if (eventEnv > 0.001) {
            eventPhase = advancePhase(eventPhase, eventFreq)
            val tap = sin(eventPhase) * eventEnv * (if (metallic) 0.24 else 0.19)
            dropL = tap * (1.0 - eventPan)
            dropR = tap * eventPan
            eventEnv *= if (metallic) 0.994 else 0.9965
        }
        if (eventTimer > 0) eventTimer--
        return (warmL * swell * 0.86 + dropL) to (warmR * swell * 0.86 + dropR)
    }

    private fun synthThunderstorm(p: SynthProfileConfig): Pair<Double, Double> {
        val (rainL, rainR) = synthRain(p, metallic = false)
        // Soft distant rolling thunder swells without startling peaks
        if (eventTimer <= 0 && rng.nextDouble() < 0.00018) {
            phase2 = 0.0
            eventFreq = 46.0 + rng.nextDouble() * 16.0
            eventPan = 0.3 + rng.nextDouble() * 0.4
            eventTimer = (sampleRate * 3.8).toInt()
        }
        var thunderL = 0.0; var thunderR = 0.0
        val rumbleLen = sampleRate * 2.8
        if (phase2 < rumbleLen) {
            val t = phase2 / rumbleLen
            val env = sin(PI * t) * (1.0 - 0.55 * t)
            phase3 = advancePhase(phase3, eventFreq)
            val rumble = (sin(phase3) * 0.55 + lBrown * 0.45) * env * 0.38
            thunderL = rumble * (1.0 - eventPan * 0.3)
            thunderR = rumble * (0.7 + eventPan * 0.3)
            phase2 += 1.0
        }
        if (eventTimer > 0) eventTimer--
        val (wetL, wetR) = applyStereoDelay(thunderL, thunderR, wet = 0.35)
        return (rainL * 0.78 + wetL) to (rainR * 0.78 + wetR)
    }

    private fun synthStream(p: SynthProfileConfig): Pair<Double, Double> {
        val (nL, nR) = blendedNoise(p)
        lfo1 = advancePhase(lfo1, p.lfoRateHz)
        val flow = 0.86 + p.lfoDepth * sin(lfo1)
        val (sL, sR) = lowPassStereo(nL, nR, p.lowPassCutoff)

        if (eventTimer <= 0 && rng.nextDouble() < 0.0016 * p.eventDensity) {
            eventEnv = 0.16 + rng.nextDouble() * 0.1
            val lo = p.toneHzLeft.coerceAtLeast(260.0)
            val hi = p.toneHzRight.coerceAtLeast(lo + 200.0)
            eventFreq = lo + rng.nextDouble() * (hi - lo)
            eventFreqTarget = eventFreq * 1.24
            eventPan = 0.15 + rng.nextDouble() * 0.7
            eventTimer = (sampleRate * 0.045).toInt()
        }
        var bubL = 0.0; var bubR = 0.0
        if (eventEnv > 0.001) {
            eventFreq += (eventFreqTarget - eventFreq) * 0.0028
            eventPhase = advancePhase(eventPhase, eventFreq)
            val b = sin(eventPhase) * eventEnv * 0.24
            bubL = b * (1.0 - eventPan)
            bubR = b * eventPan
            eventEnv *= 0.995
        }
        if (eventTimer > 0) eventTimer--
        return (sL * flow * 0.82 + bubL) to (sR * flow * 0.82 + bubR)
    }

    private fun synthOcean(p: SynthProfileConfig): Pair<Double, Double> {
        val (nL, nR) = blendedNoise(p)
        lfo1 = advancePhase(lfo1, p.lfoRateHz)
        lfo2 = advancePhase(lfo2, p.lfoRateHz * 0.43)
        val wave = (sin(lfo1) + 1.0) * 0.5
        val tide = wave * wave * (0.85 + 0.15 * sin(lfo2))
        val cutoff = (0.07 + p.lowPassCutoff * tide).coerceIn(0.04, 0.45)
        val (surfL, surfR) = lowPassStereo(nL, nR, cutoff)
        val pan = 0.12 * sin(lfo2)
        return (surfL * (0.28 + p.lfoDepth * tide) * (1.0 - pan)) to
            (surfR * (0.28 + p.lfoDepth * tide) * (1.0 + pan))
    }

    private fun synthLakeRipples(p: SynthProfileConfig): Pair<Double, Double> {
        val (nL, nR) = blendedNoise(p)
        lfo1 = advancePhase(lfo1, p.lfoRateHz)
        val lap = 0.55 + 0.45 * sin(lfo1)
        val (lL, lR) = lowPassStereo(nL * lap, nR * lap, p.lowPassCutoff)
        return applyStereoDelay(lL * 0.82, lR * 0.82, wet = 0.30)
    }

    private fun synthWaterfall(p: SynthProfileConfig): Pair<Double, Double> {
        val (nL, nR) = blendedNoise(p)
        lfo1 = advancePhase(lfo1, p.lfoRateHz)
        val rush = 0.92 + 0.08 * sin(lfo1)
        val (wL, wR) = lowPassStereo(nL, nR, p.lowPassCutoff)
        return applyStereoDelay(wL * rush * 0.9, wR * rush * 0.9, wet = 0.20)
    }

    // --- 3. Animals & Wildlife ---

    private fun synthBirds(p: SynthProfileConfig, tropical: Boolean): Pair<Double, Double> {
        val (nL, nR) = blendedNoise(p)
        val (forestL, forestR) = lowPassStereo(nL * 0.32, nR * 0.32, p.lowPassCutoff)

        if (eventTimer <= 0) {
            eventSubStage = if (eventSubStage in 1..2) eventSubStage + 1 else 1
            phase2 = 0.0
            val lo = p.toneHzLeft.coerceAtLeast(800.0)
            val hi = p.toneHzRight.coerceAtLeast(lo + 300.0)
            eventFreq = lo + rng.nextDouble() * (hi - lo)
            val glideSpan = if (tropical) 420.0 else 250.0
            eventFreqTarget = eventFreq + (if (rng.nextBoolean()) glideSpan else -glideSpan * 0.8)
            if (eventSubStage == 1) eventPan = 0.2 + rng.nextDouble() * 0.6
            eventTimer = if (eventSubStage < 3) {
                (sampleRate * (0.20 + rng.nextDouble() * 0.12)).toInt()
            } else {
                eventSubStage = 0
                (sampleRate * (1.7 + rng.nextDouble() * 1.4)).toInt()
            }
        }
        eventTimer--

        var birdL = 0.0; var birdR = 0.0
        val noteDur = sampleRate * 0.14
        if (phase2 < noteDur) {
            val progress = phase2 / noteDur
            val env = 0.5 * (1.0 - cos(2.0 * PI * progress))
            val curHz = eventFreq + (eventFreqTarget - eventFreq) * progress
            eventPhase = advancePhase(eventPhase, curHz)
            val tone = sin(eventPhase) * env * 0.14
            birdL = tone * (1.0 - eventPan)
            birdR = tone * eventPan
            phase2 += 1.0
        }
        val (wetL, wetR) = applyStereoDelay(birdL, birdR, wet = 0.38)
        return (forestL + wetL) to (forestR + wetR)
    }

    private fun synthCatPurr(p: SynthProfileConfig): Pair<Double, Double> {
        val (bL, bR) = nextStereoBrown()
        lfo1 = advancePhase(lfo1, p.lfoRateHz)
        val breath = (sin(lfo1) + 1.0) * 0.5
        val purrEnv = 0.32 + p.lfoDepth * (breath * breath)
        val fund = p.toneHzLeft + (p.toneHzRight - p.toneHzLeft).coerceAtLeast(1.5) * breath
        phase1 = advancePhase(phase1, fund)
        phase2 = advancePhase(phase2, fund * 2.0)
        phase3 = advancePhase(phase3, fund * 3.0)
        val purr = (sin(phase1) * 0.55 + sin(phase2) * 0.30 + sin(phase3) * 0.15) * purrEnv * 0.42
        val (fL, fR) = lowPassStereo(bL * 0.24 + purr, bR * 0.24 + purr, p.lowPassCutoff)
        return (fL * 0.95) to (fR * 0.95)
    }

    private fun synthCrickets(p: SynthProfileConfig): Pair<Double, Double> {
        val (nL, nR) = blendedNoise(p)
        val (nightL, nightR) = lowPassStereo(nL * 0.28, nR * 0.28, p.lowPassCutoff)
        lfo1 = advancePhase(lfo1, p.lfoRateHz)
        val phraseGate = ((sin(lfo1) - 0.15) / 0.85).coerceIn(0.0, 1.0)
        lfo2 = advancePhase(lfo2, 16.0)
        val pulse = (sin(lfo2) + 1.0) * 0.5
        phase1 = advancePhase(phase1, p.toneHzLeft.coerceAtLeast(2200.0))
        phase2 = advancePhase(phase2, p.toneHzRight.coerceAtLeast(2300.0))
        val cL = sin(phase1) * phraseGate * pulse * 0.075
        val cR = sin(phase2) * phraseGate * pulse * 0.075
        val (wetL, wetR) = applyStereoDelay(cL * 0.75 + cR * 0.25, cR * 0.75 + cL * 0.25, wet = 0.28)
        return (nightL + wetL) to (nightR + wetR)
    }

    private fun synthOwl(p: SynthProfileConfig): Pair<Double, Double> {
        val (nL, nR) = blendedNoise(p)
        val (woodsL, woodsR) = lowPassStereo(nL * 0.30, nR * 0.30, p.lowPassCutoff)
        if (eventTimer <= 0) {
            eventSubStage = (eventSubStage % 3) + 1
            phase2 = 0.0
            eventFreq = if (eventSubStage == 2) p.toneHzRight else p.toneHzLeft
            if (eventSubStage == 1) eventPan = 0.3 + rng.nextDouble() * 0.4
            eventTimer = when (eventSubStage) {
                1 -> (sampleRate * 0.65).toInt()
                2 -> (sampleRate * 0.32).toInt()
                else -> (sampleRate * (2.5 + rng.nextDouble() * 1.4)).toInt()
            }
        }
        eventTimer--
        var owlL = 0.0; var owlR = 0.0
        val hootLen = if (eventSubStage == 2) sampleRate * 0.20 else sampleRate * 0.42
        if (phase2 < hootLen) {
            val t = phase2 / hootLen
            val env = sin(PI * t) * sin(PI * t)
            eventPhase = advancePhase(eventPhase, eventFreq - 18.0 * t)
            val tone = sin(eventPhase) * env * 0.19
            owlL = tone * (1.0 - eventPan)
            owlR = tone * eventPan
            phase2 += 1.0
        }
        val (wetL, wetR) = applyStereoDelay(owlL, owlR, wet = 0.42)
        return (woodsL + wetL) to (woodsR + wetR)
    }

    private fun synthFrogs(p: SynthProfileConfig): Pair<Double, Double> {
        val (nL, nR) = blendedNoise(p)
        val (marshL, marshR) = lowPassStereo(nL * 0.30, nR * 0.30, p.lowPassCutoff)
        if (eventTimer <= 0) {
            eventSubStage = (eventSubStage % 2) + 1
            phase2 = 0.0
            eventFreq = if (eventSubStage == 1) p.toneHzLeft else p.toneHzRight
            if (eventSubStage == 1) eventPan = 0.2 + rng.nextDouble() * 0.6
            eventTimer = if (eventSubStage == 1) (sampleRate * 0.25).toInt() else (sampleRate * (1.3 + rng.nextDouble())).toInt()
        }
        eventTimer--
        var frogL = 0.0; var frogR = 0.0
        val len = sampleRate * 0.13
        if (phase2 < len) {
            val t = phase2 / len
            val env = sin(PI * t)
            eventPhase = advancePhase(eventPhase, eventFreq + 40.0 * t)
            val ribbit = (sin(eventPhase) * 0.65 + sin(eventPhase * 2.0) * 0.35) * env * 0.15
            frogL = ribbit * (1.0 - eventPan)
            frogR = ribbit * eventPan
            phase2 += 1.0
        }
        val (wetL, wetR) = applyStereoDelay(frogL, frogR, wet = 0.34)
        return (marshL + wetL) to (marshR + wetR)
    }

    private fun synthSeagulls(p: SynthProfileConfig): Pair<Double, Double> {
        val (oceanL, oceanR) = synthOcean(p.copy(lowPassCutoff = 0.20, lfoRateHz = 0.08, lfoDepth = 0.45))
        if (eventTimer <= 0) {
            phase2 = 0.0
            eventFreq = p.toneHzLeft + rng.nextDouble() * 140.0
            eventPan = 0.18 + rng.nextDouble() * 0.64
            eventTimer = (sampleRate * (2.8 + rng.nextDouble() * 1.8)).toInt()
        }
        eventTimer--
        var gullL = 0.0; var gullR = 0.0
        val callLen = sampleRate * 0.45
        if (phase2 < callLen) {
            val t = phase2 / callLen
            val env = sin(PI * t) * sin(PI * t)
            eventPhase = advancePhase(eventPhase, eventFreq - 160.0 * t)
            val call = sin(eventPhase) * env * 0.12
            gullL = call * (1.0 - eventPan)
            gullR = call * eventPan
            phase2 += 1.0
        }
        val (wetL, wetR) = applyStereoDelay(gullL, gullR, wet = 0.40)
        return (oceanL * 0.8 + wetL) to (oceanR * 0.8 + wetR)
    }

    private fun synthBees(p: SynthProfileConfig): Pair<Double, Double> {
        val (nL, nR) = blendedNoise(p)
        val (airL, airR) = lowPassStereo(nL * 0.35, nR * 0.35, p.lowPassCutoff)
        lfo1 = advancePhase(lfo1, p.lfoRateHz)
        lfo2 = advancePhase(lfo2, p.lfoRateHz * 1.37)
        val swell = 0.65 + 0.35 * sin(lfo1)
        val pan = 0.5 + 0.3 * sin(lfo2)
        phase1 = advancePhase(phase1, p.toneHzLeft + 4.0 * sin(lfo1))
        phase2 = advancePhase(phase2, p.toneHzRight + 3.0 * cos(lfo2))
        val buzz = (sin(phase1) * 0.5 + sin(phase1 * 2.0) * 0.2 + sin(phase2) * 0.3) * swell * 0.11
        return (airL + buzz * (1.0 - pan)) to (airR + buzz * pan)
    }

    private fun synthWhale(p: SynthProfileConfig): Pair<Double, Double> {
        val (bL, bR) = nextStereoBrown()
        val (oceanL, oceanR) = lowPassStereo(bL * 0.45, bR * 0.45, p.lowPassCutoff)
        if (eventTimer <= 0) {
            phase2 = 0.0
            eventFreq = p.toneHzLeft + rng.nextDouble() * 35.0
            eventFreqTarget = eventFreq * (0.72 + rng.nextDouble() * 0.15)
            eventPan = 0.32 + rng.nextDouble() * 0.36
            eventTimer = (sampleRate * (2.6 + rng.nextDouble() * 1.4)).toInt()
        }
        eventTimer--
        var whaleL = 0.0; var whaleR = 0.0
        val callDuration = sampleRate * 2.1
        if (phase2 < callDuration) {
            val t = phase2 / callDuration
            val env = sin(PI * t) * sin(PI * t)
            val f = eventFreq + (eventFreqTarget - eventFreq) * (0.5 - 0.5 * cos(PI * t))
            eventPhase = advancePhase(eventPhase, f)
            phase3 = advancePhase(phase3, f * 2.0)
            val song = (sin(eventPhase) * 0.72 + sin(phase3) * 0.28) * env * 0.24
            whaleL = song * (1.0 - eventPan)
            whaleR = song * eventPan
            phase2 += 1.0
        }
        val (wetL, wetR) = applyStereoDelay(whaleL, whaleR, wet = 0.45)
        return (oceanL + wetL) to (oceanR + wetR)
    }

    // --- 4. Electronic Equipment & White/Brown Noise ---

    private fun synthFan(p: SynthProfileConfig): Pair<Double, Double> {
        val (nL, nR) = blendedNoise(p)
        phase1 = advancePhase(phase1, p.toneHzLeft.coerceAtLeast(40.0))
        phase2 = advancePhase(phase2, p.toneHzRight.coerceAtLeast(80.0))
        lfo1 = advancePhase(lfo1, p.lfoRateHz)
        val bladeMod = (1.0 - p.lfoDepth) + p.lfoDepth * sin(lfo1)
        val motor = sin(phase1) * 0.075 + sin(phase2) * 0.028
        val (airL, airR) = lowPassStereo(nL, nR, p.lowPassCutoff)
        return (airL * bladeMod * 0.86 + motor) to (airR * bladeMod * 0.86 + motor)
    }

    private fun synthApplianceHum(p: SynthProfileConfig, rumbleMod: Double): Pair<Double, Double> {
        val (nL, nR) = blendedNoise(p)
        phase1 = advancePhase(phase1, p.toneHzLeft.coerceAtLeast(48.0))
        phase2 = advancePhase(phase2, p.toneHzRight.coerceAtLeast(96.0))
        lfo1 = advancePhase(lfo1, p.lfoRateHz)
        lfo2 = advancePhase(lfo2, p.lfoRateHz * 2.0)
        val mod = if (rumbleMod > 0.0) {
            (1.0 - rumbleMod) + rumbleMod * 0.7 * sin(lfo1) + rumbleMod * 0.3 * sin(lfo2)
        } else {
            1.0
        }
        val motor = sin(phase1) * 0.062 + sin(phase2) * 0.024
        val (flowL, flowR) = lowPassStereo(nL, nR, p.lowPassCutoff)
        return (flowL * mod * 0.88 + motor) to (flowR * mod * 0.88 + motor)
    }

    private fun synthDenseAirNoise(p: SynthProfileConfig): Pair<Double, Double> {
        val (nL, nR) = blendedNoise(p)
        phase1 = advancePhase(phase1, p.toneHzLeft.coerceAtLeast(80.0))
        phase2 = advancePhase(phase2, p.toneHzRight.coerceAtLeast(160.0))
        lfo1 = advancePhase(lfo1, p.lfoRateHz)
        val drift = (1.0 - p.lfoDepth) + p.lfoDepth * sin(lfo1)
        val hum = sin(phase1) * 0.048 + sin(phase2) * 0.022
        val (smoothL, smoothR) = lowPassStereo(nL, nR, p.lowPassCutoff)
        return (smoothL * drift * 0.90 + hum) to (smoothR * drift * 0.90 + hum)
    }

    private fun synthAirplane(p: SynthProfileConfig): Pair<Double, Double> {
        val (nL, nR) = blendedNoise(p)
        phase1 = advancePhase(phase1, p.toneHzLeft.coerceAtLeast(82.0))
        phase2 = advancePhase(phase2, p.toneHzRight.coerceAtLeast(84.0))
        val engine = (sin(phase1) + sin(phase2)) * 0.032
        val (cabL, cabR) = lowPassStereo(nL, nR, p.lowPassCutoff)
        return (cabL * 0.90 + engine) to (cabR * 0.90 + engine)
    }

    private fun synthTrain(p: SynthProfileConfig): Pair<Double, Double> {
        val (nL, nR) = blendedNoise(p)
        val (rollL, rollR) = lowPassStereo(nL, nR, p.lowPassCutoff)
        // Soft sleeper-car rail cadence (~0.62 Hz cycle with muted double-clack)
        val cycleSamples = (sampleRate / p.lfoRateHz.coerceAtLeast(0.4)).toInt()
        val pos = (sampleCounter % cycleSamples).toDouble() / cycleSamples
        val clack1 = if (pos in 0.0..0.06) sin(PI * pos / 0.06) * 0.14 else 0.0
        val clack2 = if (pos in 0.12..0.18) sin(PI * (pos - 0.12) / 0.06) * 0.11 else 0.0
        phase1 = advancePhase(phase1, p.toneHzLeft.coerceAtLeast(60.0))
        val railThump = sin(phase1) * (clack1 + clack2)
        sampleCounter++
        return (rollL * 0.88 + railThump) to (rollR * 0.88 + railThump)
    }

    private fun synthRadioStatic(p: SynthProfileConfig): Pair<Double, Double> {
        val (nL, nR) = blendedNoise(p)
        lfo1 = advancePhase(lfo1, p.lfoRateHz)
        val drift = (1.0 - p.lfoDepth) + p.lfoDepth * sin(lfo1)
        val (tapeL, tapeR) = lowPassStereo(nL, nR, p.lowPassCutoff)
        return (tapeL * drift * 0.88) to (tapeR * drift * 0.88)
    }

    private fun synthColoredNoise(p: SynthProfileConfig, isBrownFocus: Boolean): Pair<Double, Double> {
        val (nL, nR) = blendedNoise(p)
        lfo1 = advancePhase(lfo1, p.lfoRateHz)
        val gentle = (1.0 - p.lfoDepth) + p.lfoDepth * sin(lfo1)
        val (warmL, warmR) = lowPassStereo(nL, nR, p.lowPassCutoff)
        val scale = if (isBrownFocus) 0.96 else 0.88
        return (warmL * gentle * scale) to (warmR * gentle * scale)
    }

    // --- 5. Cozy & Deep Focus ---

    private fun synthFire(p: SynthProfileConfig): Pair<Double, Double> {
        val (nL, nR) = blendedNoise(p)
        lfo1 = advancePhase(lfo1, p.lfoRateHz)
        val swell = 0.80 + p.lfoDepth * sin(lfo1)
        val (hearthL, hearthR) = lowPassStereo(nL, nR, p.lowPassCutoff)
        if (eventTimer <= 0 && rng.nextDouble() < 0.0007 * p.eventDensity) {
            eventEnv = 0.20 + rng.nextDouble() * 0.16
            eventPan = 0.25 + rng.nextDouble() * 0.5
            eventTimer = (sampleRate * 0.03).toInt()
        }
        var crackleL = 0.0; var crackleR = 0.0
        if (eventEnv > 0.002) {
            val n = rng.nextDouble(-1.0, 1.0) * eventEnv * 0.24
            crackleL = n * (1.0 - eventPan)
            crackleR = n * eventPan
            eventEnv *= 0.985
        }
        if (eventTimer > 0) eventTimer--
        return (hearthL * swell * 0.90 + crackleL) to (hearthR * swell * 0.90 + crackleR)
    }

    private fun synthClock(p: SynthProfileConfig): Pair<Double, Double> {
        val (nL, nR) = blendedNoise(p)
        val (roomL, roomR) = lowPassStereo(nL * 0.18, nR * 0.18, p.lowPassCutoff)
        val period = sampleRate // 1 tick/tock per second
        val pos = (sampleCounter % period).toInt()
        val isTick = ((sampleCounter / period) % 2L) == 0L
        sampleCounter++

        val tickLen = (sampleRate * 0.028).toInt().coerceAtLeast(1)
        var click = 0.0
        if (pos < tickLen) {
            val t = pos.toDouble() / tickLen
            val env = (1.0 - t) * (1.0 - t) * sin(PI * t)
            val hz = if (isTick) p.toneHzLeft else p.toneHzRight
            eventPhase = advancePhase(eventPhase, hz.coerceAtLeast(360.0))
            click = sin(eventPhase) * env * 0.34
        }
        val (wetL, wetR) = applyStereoDelay(click * 0.9, click * 0.85, wet = 0.18)
        return (roomL + wetL) to (roomR + wetR)
    }

    private fun synthCoffeeShop(p: SynthProfileConfig): Pair<Double, Double> {
        val (nL, nR) = blendedNoise(p)
        lfo1 = advancePhase(lfo1, p.lfoRateHz)
        lfo2 = advancePhase(lfo2, p.lfoRateHz * 1.63)
        val murmurL = 0.65 + 0.35 * sin(lfo1)
        val murmurR = 0.65 + 0.35 * sin(lfo2 + 0.9)
        val (cafeL, cafeR) = lowPassStereo(nL * murmurL, nR * murmurR, p.lowPassCutoff)

        // Occasional distant porcelain cup clink
        if (eventTimer <= 0 && rng.nextDouble() < 0.00028 * p.eventDensity) {
            eventEnv = 0.14
            eventFreq = 1650.0 + rng.nextDouble() * 450.0
            eventPan = 0.2 + rng.nextDouble() * 0.6
            eventTimer = (sampleRate * 1.8).toInt()
        }
        var clinkL = 0.0; var clinkR = 0.0
        if (eventEnv > 0.001) {
            eventPhase = advancePhase(eventPhase, eventFreq)
            val c = sin(eventPhase) * eventEnv * 0.11
            clinkL = c * (1.0 - eventPan)
            clinkR = c * eventPan
            eventEnv *= 0.996
        }
        if (eventTimer > 0) eventTimer--
        val (wetL, wetR) = applyStereoDelay(cafeL * 0.84 + clinkL, cafeR * 0.84 + clinkR, wet = 0.24)
        return wetL to wetR
    }

    private fun synthSingingBowls(p: SynthProfileConfig): Pair<Double, Double> {
        val (bL, bR) = nextStereoBrown()
        val (roomL, roomR) = lowPassStereo(bL * 0.20, bR * 0.20, p.lowPassCutoff)
        if (eventTimer <= 0) {
            phase2 = 0.0
            eventFreq = p.toneHzLeft * (if (rng.nextBoolean()) 1.0 else 1.25)
            eventPan = 0.35 + rng.nextDouble() * 0.3
            eventTimer = (sampleRate * 3.8).toInt()
        }
        eventTimer--
        var bowlL = 0.0; var bowlR = 0.0
        val ringLen = sampleRate * 3.4
        if (phase2 < ringLen) {
            val t = phase2 / ringLen
            val env = sin(PI * t) * (1.0 - 0.5 * t)
            phase1 = advancePhase(phase1, eventFreq)
            phase3 = advancePhase(phase3, eventFreq * 2.756) // singing bowl partial
            val tone = (sin(phase1) * 0.72 + sin(phase3) * 0.28) * env * 0.22
            bowlL = tone * (1.0 - eventPan * 0.35)
            bowlR = tone * (0.65 + eventPan * 0.35)
            phase2 += 1.0
        }
        val (wetL, wetR) = applyStereoDelay(bowlL, bowlR, wet = 0.40)
        return (roomL + wetL) to (roomR + wetR)
    }

    private fun synthBinaural(p: SynthProfileConfig): Pair<Double, Double> {
        val (nL, nR) = blendedNoise(p)
        val (bedL, bedR) = lowPassStereo(nL * 0.22, nR * 0.22, p.lowPassCutoff)
        phase1 = advancePhase(phase1, p.toneHzLeft.coerceAtLeast(100.0))
        phase2 = advancePhase(phase2, p.toneHzRight.coerceAtLeast(102.0))
        lfo1 = advancePhase(lfo1, p.lfoRateHz)
        val shimmer = 0.86 + p.lfoDepth * sin(lfo1)
        val toneL = sin(phase1) * 0.24 * shimmer
        val toneR = sin(phase2) * 0.24 * shimmer
        return (bedL + toneL) to (bedR + toneR)
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100
        const val DEFAULT_LOOP_SECONDS = 8

        /**
         * Mixes 1 to 3 simultaneous soundscape layers (real-time OR cached seamless loop sources)
         * cleanly with sqrt(N) headroom normalization, shared fade-in gain, and soft-knee tanh limiting.
         */
        fun renderMixedStereo(
            layers: List<Pair<StereoLayerSource, Float>>,
            outBuffer: ShortArray,
            sampleCounterStart: Long,
            sampleRate: Int = DEFAULT_SAMPLE_RATE,
            fadeOutMultiplier: Float = 1.0f
        ): Long {
            if (layers.isEmpty()) {
                outBuffer.fill(0)
                return sampleCounterStart
            }
            val frameCount = outBuffer.size / 2
            val fadeInSamples = (sampleRate * 1.5).toLong().coerceAtLeast(1L)
            val normFactor = 1.0 / sqrt(layers.size.toDouble())
            var counter = sampleCounterStart

            for (frame in 0 until frameCount) {
                var sumL = 0.0
                var sumR = 0.0
                for (i in layers.indices) {
                    val (source, layerVol) = layers[i]
                    if (layerVol > 0.001f) {
                        val (l, r) = source.nextRawStereoSample()
                        val g = layerVol.coerceIn(0f, 1f).toDouble()
                        sumL += l * g
                        sumR += r * g
                    }
                }

                val fadeInGain = if (counter < fadeInSamples) {
                    val t = counter.toDouble() / fadeInSamples.toDouble()
                    0.5 * (1.0 - cos(PI * t))
                } else {
                    1.0
                }
                counter++

                val totalGain = fadeInGain * fadeOutMultiplier.coerceIn(0f, 1f) * normFactor
                val finalL = tanh(sumL * totalGain * 1.15) * 0.72
                val finalR = tanh(sumR * totalGain * 1.15) * 0.72

                outBuffer[frame * 2] = (finalL.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()
                outBuffer[frame * 2 + 1] = (finalR.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()
            }
            return counter
        }
    }
}
