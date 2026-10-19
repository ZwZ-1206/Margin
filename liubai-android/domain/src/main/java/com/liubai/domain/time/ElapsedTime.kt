package com.liubai.domain.time

/**
 * 单调时钟抽象。引擎只依赖本接口，因此可在 JVM 单测中替换为假时钟。
 * 生产实现（SystemClock.elapsedRealtime）位于 :data 层，避免 domain 依赖 Android。
 */
fun interface ElapsedTimeProvider {
    fun elapsedRealtime(): Long
}
