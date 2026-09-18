package com.sankatsetu.app.payments

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens the system dialer pre-filled with a USSD/IVR code — for `*99#`
 * (NPCI's USSD-based UPI, works on any phone with no internet, see
 * docs/PRD.md §F3) and UPI 123Pay's IVR numbers.
 *
 * Deliberately uses [Intent.ACTION_DIAL], never [Intent.ACTION_CALL]:
 * `ACTION_DIAL` opens the dialer with the number/code already typed in and
 * requires the person to press the call button themselves, exactly like
 * looking up a number and dialing it by hand. `ACTION_CALL` would place the
 * call immediately with no further confirmation and needs the
 * `CALL_PHONE` runtime permission — this app asks for neither, because
 * initiating a real USSD session against someone's actual bank account is a
 * financial action that must always be a deliberate, physical tap from the
 * account holder, never something software fires on their behalf.
 */
object UssdDialer {
    /** [ussdCode] like `*99#`; `#` must stay literal in the dialer, so it's percent-encoded as required for a `tel:` URI. */
    fun openDialer(context: Context, ussdCode: String) {
        val encoded = Uri.encode(ussdCode)
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$encoded")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
