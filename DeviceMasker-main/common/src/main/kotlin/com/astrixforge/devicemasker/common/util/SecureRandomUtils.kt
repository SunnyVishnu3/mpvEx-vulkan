package com.astrixforge.devicemasker.common.util

import java.security.SecureRandom

/** Global secure random instance. */
val secureRandom = SecureRandom()

/** Returns a random integer between 0 (inclusive) and [bound] (exclusive) using [SecureRandom]. */
fun nextInt(bound: Int): Int = secureRandom.nextInt(bound)

/**
 * Returns a random element from this collection using [SecureRandom].
 *
 * @throws NoSuchElementException if the collection is empty.
 */
fun <T> Collection<T>.secureRandom(): T {
    if (isEmpty()) throw NoSuchElementException("Collection is empty.")
    val index = secureRandom.nextInt(size)
    return elementAt(index)
}

/**
 * Returns a random element from this array using [SecureRandom].
 *
 * @throws NoSuchElementException if the array is empty.
 */
fun <T> Array<T>.secureRandom(): T {
    if (isEmpty()) throw NoSuchElementException("Array is empty.")
    val index = secureRandom.nextInt(size)
    return get(index)
}

/** Returns a random element from this IntRange using [SecureRandom]. */
fun IntRange.secureRandom(): Int {
    val size = last - first + 1
    return first + secureRandom.nextInt(size)
}

/** Returns a random double between start and endInclusive using [SecureRandom]. */
fun ClosedFloatingPointRange<Double>.secureRandom(): Double {
    return start + (endInclusive - start) * secureRandom.nextDouble()
}

/** Returns a random character from this string using [SecureRandom]. */
fun String.secureRandom(): Char {
    if (isEmpty()) throw NoSuchElementException("String is empty.")
    return get(secureRandom.nextInt(length))
}
