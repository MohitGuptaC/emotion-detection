package com.example.emotiondetection.audio

/**
 * Aggregates emotion detections over a sliding time window and computes a stable winner.
 */
class EmotionAggregator(
    private val windowMs: Long = 10_000L,
    private val minValidSamples: Int = 7,
    private val confidenceThreshold: Float = 0.55f
) {
    data class WindowSummary(val topLabel: String, val topShare: Float, val totalSamples: Int)
    private data class Sample(val label: String, val confidence: Float, val timestampMs: Long)
    private val samples = ArrayDeque<Sample>()
    private var lastWinner: String? = null

    fun add(label: String, confidence: Float, nowMs: Long = System.currentTimeMillis()) {
        if (label.isBlank() || confidence < confidenceThreshold) return
        samples.addLast(Sample(label, confidence, nowMs))
        trim(nowMs)
    }

    private fun trim(nowMs: Long) {
        while (samples.isNotEmpty() && nowMs - samples.first().timestampMs > windowMs) {
            samples.removeFirst()
        }
    }

    fun windowSummary(nowMs: Long = System.currentTimeMillis()): WindowSummary? {
        trim(nowMs)
        if (samples.size < minValidSamples) return null

        val counts = mutableMapOf<String, Int>()
        for (sample in samples) {
            counts[sample.label] = (counts[sample.label] ?: 0) + 1
        }
        if (counts.isEmpty()) return null

        val sorted = counts.entries.sortedByDescending { it.value }
        val top = sorted.first()
        val totalCount = counts.values.sum().takeIf { it > 0 } ?: return null
        val topShare = (top.value.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
        return WindowSummary(top.key, topShare, samples.size)
    }

    fun reset() {
        samples.clear()
        lastWinner = null
    }
}


