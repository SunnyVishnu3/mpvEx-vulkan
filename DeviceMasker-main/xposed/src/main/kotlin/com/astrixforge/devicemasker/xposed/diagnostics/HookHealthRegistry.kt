package com.astrixforge.devicemasker.xposed.diagnostics

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class HookMethodHealth(
    val attempts: Long = 0,
    val successes: Long = 0,
    val failures: Long = 0,
    val skipped: Long = 0,
    val deoptimizeFailures: Long = 0,
    val lastFailureClass: String? = null,
    val lastSkipReason: String? = null,
)

data class HookHealthSnapshot(
    val registrationAttempts: Long,
    val registrationSuccesses: Long,
    val registrationFailures: Long,
    val skippedMethods: Long,
    val deoptimizeFailures: Long,
    val methods: Map<String, HookMethodHealth>,
    val spoofEvents: Map<String, Long>,
)

data class SpoofEventRecord(val count: Long, val shouldLog: Boolean)

class HookHealthRegistry {
    private val registrationAttempts = AtomicLong(0)
    private val registrationSuccesses = AtomicLong(0)
    private val registrationFailures = AtomicLong(0)
    private val skippedMethods = AtomicLong(0)
    private val deoptimizeFailures = AtomicLong(0)
    private val methods = ConcurrentHashMap<String, MutableMethodHealth>()
    private val spoofEvents = ConcurrentHashMap<String, AtomicLong>()

    fun recordRegistrationAttempt(hooker: String, method: String) {
        registrationAttempts.incrementAndGet()
        methodHealth(hooker, method).attempts.incrementAndGet()
    }

    fun recordRegistrationSuccess(hooker: String, method: String) {
        registrationSuccesses.incrementAndGet()
        methodHealth(hooker, method).successes.incrementAndGet()
    }

    fun recordRegistrationFailure(hooker: String, method: String, failureClass: String) {
        registrationFailures.incrementAndGet()
        methodHealth(hooker, method).apply {
            failures.incrementAndGet()
            lastFailureClass = failureClass
        }
    }

    fun recordSkipped(hooker: String, method: String, reason: String) {
        skippedMethods.incrementAndGet()
        methodHealth(hooker, method).apply {
            skipped.incrementAndGet()
            lastSkipReason = reason
        }
    }

    fun recordDeoptimizeFailure(hooker: String, method: String) {
        deoptimizeFailures.incrementAndGet()
        methodHealth(hooker, method).deoptimizeFailures.incrementAndGet()
    }

    fun recordSpoofEvent(pkg: String, spoofType: String): SpoofEventRecord {
        val count =
            spoofEvents.computeIfAbsent("$pkg/$spoofType") { AtomicLong(0) }.incrementAndGet()
        return SpoofEventRecord(
            count = count,
            shouldLog = count <= 5 || count == 10L || count == 100L || count == 1000L,
        )
    }

    fun snapshot(): HookHealthSnapshot =
        HookHealthSnapshot(
            registrationAttempts = registrationAttempts.get(),
            registrationSuccesses = registrationSuccesses.get(),
            registrationFailures = registrationFailures.get(),
            skippedMethods = skippedMethods.get(),
            deoptimizeFailures = deoptimizeFailures.get(),
            methods = methods.mapValues { (_, value) -> value.toSnapshot() },
            spoofEvents = spoofEvents.mapValues { (_, value) -> value.get() },
        )

    private fun methodHealth(hooker: String, method: String): MutableMethodHealth =
        methods.computeIfAbsent("$hooker.$method") { MutableMethodHealth() }

    private class MutableMethodHealth {
        val attempts = AtomicLong(0)
        val successes = AtomicLong(0)
        val failures = AtomicLong(0)
        val skipped = AtomicLong(0)
        val deoptimizeFailures = AtomicLong(0)
        @Volatile var lastFailureClass: String? = null
        @Volatile var lastSkipReason: String? = null

        fun toSnapshot(): HookMethodHealth =
            HookMethodHealth(
                attempts = attempts.get(),
                successes = successes.get(),
                failures = failures.get(),
                skipped = skipped.get(),
                deoptimizeFailures = deoptimizeFailures.get(),
                lastFailureClass = lastFailureClass,
                lastSkipReason = lastSkipReason,
            )
    }
}
