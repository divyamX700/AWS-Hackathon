package com.sankatsetu.app.payments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpiQrParserTest {

    @Test
    fun `a well-formed upi uri with amount and payee name parses correctly`() {
        val result = UpiQrParser.parse("upi://pay?pa=shop@okbank&pn=Ramesh%20Store&am=150.00&cu=INR&tn=Groceries")

        assertTrue(result is UpiQrParser.ParseResult.Valid)
        val data = (result as UpiQrParser.ParseResult.Valid).data
        assertEquals("shop@okbank", data.vpa)
        assertEquals("Ramesh Store", data.payeeName)
        assertEquals("150.00", data.amount)
        assertEquals("Groceries", data.transactionNote)
    }

    @Test
    fun `a upi uri with no amount parses with an empty amount, not a rejection`() {
        // Many real merchant QRs fix the payee but let the customer type
        // the amount themselves — an empty `am` is valid, not malformed.
        val result = UpiQrParser.parse("upi://pay?pa=shop@okbank&pn=Ramesh%20Store")

        assertTrue(result is UpiQrParser.ParseResult.Valid)
        assertEquals("", (result as UpiQrParser.ParseResult.Valid).data.amount)
    }

    @Test
    fun `a bare VPA with no upi wrapper is accepted`() {
        val result = UpiQrParser.parse("shop@okbank")

        assertTrue(result is UpiQrParser.ParseResult.Valid)
        assertEquals("shop@okbank", (result as UpiQrParser.ParseResult.Valid).data.vpa)
    }

    @Test
    fun `an empty scan is rejected as EMPTY, not NOT_A_UPI_QR`() {
        val result = UpiQrParser.parse("   ")
        assertEquals(UpiQrParser.Reason.EMPTY, (result as UpiQrParser.ParseResult.Invalid).reason)
    }

    @Test
    fun `a random non-payment QR is rejected, never treated as a payee`() {
        // The real bug this guards against: an earlier, cruder parser
        // regex-fished any "@"-containing string out of arbitrary text,
        // so scanning e.g. a poster with an email address on it would
        // silently start a payment flow to that address.
        val result = UpiQrParser.parse("https://example.com/some-poster-with-an-email-admin@example.com")
        assertEquals(UpiQrParser.Reason.NOT_A_UPI_QR, (result as UpiQrParser.ParseResult.Invalid).reason)
    }

    @Test
    fun `a upi uri missing the payee address is rejected as NO_PAYEE_ADDRESS`() {
        val result = UpiQrParser.parse("upi://pay?pn=Ramesh%20Store&am=150")
        assertEquals(UpiQrParser.Reason.NO_PAYEE_ADDRESS, (result as UpiQrParser.ParseResult.Invalid).reason)
    }

    @Test
    fun `a upi uri with a structurally invalid VPA is rejected as INVALID_PAYEE_ADDRESS`() {
        val result = UpiQrParser.parse("upi://pay?pa=not-a-vpa-no-at-sign")
        assertEquals(UpiQrParser.Reason.INVALID_PAYEE_ADDRESS, (result as UpiQrParser.ParseResult.Invalid).reason)
    }

    @Test
    fun `a upi uri with a zero or negative amount is rejected as INVALID_AMOUNT`() {
        val zero = UpiQrParser.parse("upi://pay?pa=shop@okbank&am=0")
        assertEquals(UpiQrParser.Reason.INVALID_AMOUNT, (zero as UpiQrParser.ParseResult.Invalid).reason)

        val negative = UpiQrParser.parse("upi://pay?pa=shop@okbank&am=-50")
        assertEquals(UpiQrParser.Reason.INVALID_AMOUNT, (negative as UpiQrParser.ParseResult.Invalid).reason)
    }

    @Test
    fun `a upi uri with an amount above the QR ceiling is rejected as INVALID_AMOUNT`() {
        val result = UpiQrParser.parse("upi://pay?pa=shop@okbank&am=999999")
        assertEquals(UpiQrParser.Reason.INVALID_AMOUNT, (result as UpiQrParser.ParseResult.Invalid).reason)
    }

    @Test
    fun `a upi uri with more than two decimal places on the amount is rejected`() {
        val result = UpiQrParser.parse("upi://pay?pa=shop@okbank&am=150.999")
        assertEquals(UpiQrParser.Reason.INVALID_AMOUNT, (result as UpiQrParser.ParseResult.Invalid).reason)
    }

    @Test
    fun `a payee name is truncated at 99 characters and control characters are stripped`() {
        val longName = "A".repeat(150)
        val result = UpiQrParser.parse("upi://pay?pa=shop@okbank&pn=$longName")
        val data = (result as UpiQrParser.ParseResult.Valid).data
        assertEquals(99, data.payeeName.length)
    }
}
