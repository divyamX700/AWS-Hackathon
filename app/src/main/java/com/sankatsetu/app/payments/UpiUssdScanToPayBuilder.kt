package com.sankatsetu.app.payments

/**
 * Builds the `*99#` USSD string for NPCI's "send money to a VPA" menu
 * branch, from a scanned QR's [UpiQrPayload] — pure Kotlin, unit tested
 * directly, same discipline as [UpiQrParser]. `*99*1*3*<vpa>*<amount>#` is
 * `*99#`'s published menu path: `1` = Send Money, `3` = "To VPA". This is
 * the mechanism Flowpay's own README describes for its "Scan QR" entry
 * point ("Scan QR → dials `*99*1*3#`, the USSD scan-to-pay branch") — this
 * project could not find Flowpay's own builder for that exact string in
 * its public source (only `Upi123CallStringBuilder`, the DTMF-call path for
 * manual entry, was present at the paths this project could read), so this
 * is a fresh implementation of the same publicly-documented NPCI menu
 * structure, not a direct port. **Caveat honestly**: the exact digit
 * sequence a live carrier's `*99#` gateway accepts is not verified against
 * a real network by this project — there was no live USSD session to test
 * against. [UssdDialer] already requires the person's own final tap in the
 * system dialer before anything is sent to the network, which is the real
 * safety boundary regardless of whether this exact string needs a later
 * correction.
 */
object UpiUssdScanToPayBuilder {

    private val WHOLE_OR_DECIMAL_RUPEES_REGEX = Regex("^[0-9]{1,6}(\\.[0-9]{1,2})?$")
    private const val MIN_AMOUNT_RUPEES = 1.0
    private const val MAX_AMOUNT_RUPEES = 100_000.0

    enum class Reason { MISSING_VPA, INVALID_VPA, MISSING_AMOUNT, AMOUNT_NOT_A_NUMBER, AMOUNT_BELOW_MINIMUM, AMOUNT_ABOVE_CAP }

    sealed class Result {
        data class Valid(val ussdCode: String) : Result()
        data class Invalid(val reason: Reason) : Result()
    }

    // Same VPA shape UpiQrParser already validated a scanned VPA against —
    // re-checked here too since this builder also accepts a manually
    // corrected/typed VPA, not only an already-validated scanned one.
    private val VPA_REGEX = Regex("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z][a-zA-Z0-9]{1,64}$")

    /** [amountRupees] as a plain decimal string, e.g. "150" or "150.50" — never paise here, USSD dials rupees. */
    fun build(vpa: String, amountRupees: String): Result {
        val trimmedVpa = vpa.trim()
        if (trimmedVpa.isEmpty()) return Result.Invalid(Reason.MISSING_VPA)
        if (!VPA_REGEX.matches(trimmedVpa)) return Result.Invalid(Reason.INVALID_VPA)

        val trimmedAmount = amountRupees.trim()
        if (trimmedAmount.isEmpty()) return Result.Invalid(Reason.MISSING_AMOUNT)
        if (!WHOLE_OR_DECIMAL_RUPEES_REGEX.matches(trimmedAmount)) return Result.Invalid(Reason.AMOUNT_NOT_A_NUMBER)
        val value = trimmedAmount.toDoubleOrNull() ?: return Result.Invalid(Reason.AMOUNT_NOT_A_NUMBER)
        if (value < MIN_AMOUNT_RUPEES) return Result.Invalid(Reason.AMOUNT_BELOW_MINIMUM)
        if (value > MAX_AMOUNT_RUPEES) return Result.Invalid(Reason.AMOUNT_ABOVE_CAP)

        return Result.Valid("*99*1*3*$trimmedVpa*$trimmedAmount#")
    }
}
