package com.astrixforge.devicemasker.common.util

internal object Luhn {
    private const val DECIMAL_RADIX = 10
    private const val DOUBLE_THRESHOLD = 9

    fun appendCheckDigit(partial: String): String = partial + calculateCheckDigit(partial)

    fun calculateCheckDigit(partial: String): Int {
        require(partial.isNotEmpty()) { "Luhn input must not be empty" }
        require(partial.all(Char::isDigit)) { "Luhn input must contain digits only" }

        var sum = 0
        var shouldDouble = true
        for (index in partial.length - 1 downTo 0) {
            var digit = partial[index].digitToInt()
            if (shouldDouble) {
                digit *= 2
                if (digit > DOUBLE_THRESHOLD) digit -= DOUBLE_THRESHOLD
            }
            sum += digit
            shouldDouble = !shouldDouble
        }
        return (DECIMAL_RADIX - (sum % DECIMAL_RADIX)) % DECIMAL_RADIX
    }

    fun isValid(value: String): Boolean {
        if (value.isEmpty() || value.any { !it.isDigit() }) return false

        var sum = 0
        var shouldDouble = false
        for (index in value.length - 1 downTo 0) {
            var digit = value[index].digitToInt()
            if (shouldDouble) {
                digit *= 2
                if (digit > DOUBLE_THRESHOLD) digit -= DOUBLE_THRESHOLD
            }
            sum += digit
            shouldDouble = !shouldDouble
        }
        return sum % DECIMAL_RADIX == 0
    }
}
