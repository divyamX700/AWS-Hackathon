package com.sankatsetu.app.payments

import org.junit.Assert.assertEquals
import org.junit.Test

class UpiUssdScanToPayBuilderTest {

    @Test
    fun `a valid VPA and amount builds the expected star-99 menu string`() {
        val result = UpiUssdScanToPayBuilder.build("shop@okbank", "150")
        assertEquals(UpiUssdScanToPayBuilder.Result.Valid("*99*1*3*shop@okbank*150#"), result)
    }

    @Test
    fun `a decimal amount is carried through as-is`() {
        val result = UpiUssdScanToPayBuilder.build("shop@okbank", "150.50")
        assertEquals(UpiUssdScanToPayBuilder.Result.Valid("*99*1*3*shop@okbank*150.50#"), result)
    }

    @Test
    fun `a blank VPA is rejected as MISSING_VPA`() {
        val result = UpiUssdScanToPayBuilder.build("  ", "150")
        assertEquals(UpiUssdScanToPayBuilder.Result.Invalid(UpiUssdScanToPayBuilder.Reason.MISSING_VPA), result)
    }

    @Test
    fun `a structurally invalid VPA is rejected as INVALID_VPA`() {
        val result = UpiUssdScanToPayBuilder.build("not-a-vpa", "150")
        assertEquals(UpiUssdScanToPayBuilder.Result.Invalid(UpiUssdScanToPayBuilder.Reason.INVALID_VPA), result)
    }

    @Test
    fun `a blank amount is rejected as MISSING_AMOUNT`() {
        val result = UpiUssdScanToPayBuilder.build("shop@okbank", "")
        assertEquals(UpiUssdScanToPayBuilder.Result.Invalid(UpiUssdScanToPayBuilder.Reason.MISSING_AMOUNT), result)
    }

    @Test
    fun `a non-numeric amount is rejected as AMOUNT_NOT_A_NUMBER`() {
        val result = UpiUssdScanToPayBuilder.build("shop@okbank", "abc")
        assertEquals(UpiUssdScanToPayBuilder.Result.Invalid(UpiUssdScanToPayBuilder.Reason.AMOUNT_NOT_A_NUMBER), result)
    }

    @Test
    fun `a zero amount is rejected as AMOUNT_BELOW_MINIMUM`() {
        val result = UpiUssdScanToPayBuilder.build("shop@okbank", "0")
        assertEquals(UpiUssdScanToPayBuilder.Result.Invalid(UpiUssdScanToPayBuilder.Reason.AMOUNT_BELOW_MINIMUM), result)
    }

    @Test
    fun `an amount above the cap is rejected as AMOUNT_ABOVE_CAP`() {
        val result = UpiUssdScanToPayBuilder.build("shop@okbank", "999999")
        assertEquals(UpiUssdScanToPayBuilder.Result.Invalid(UpiUssdScanToPayBuilder.Reason.AMOUNT_ABOVE_CAP), result)
    }
}
