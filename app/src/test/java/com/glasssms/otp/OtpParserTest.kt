package com.glasssms.otp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class OtpParserTest {
    private fun code(body: String, sender: String? = null) = OtpParser.parse(body, sender)?.code

    @Test fun googleStyle() = assertEquals("482913", code("G-482913 is your Google verification code."))
    @Test fun otpIs() = assertEquals("4821", code("Your OTP is 4821. Valid for 5 minutes. Do not share."))
    @Test fun skipsAmounts() = assertEquals("773311", code("Rs 2500.00 debited from A/c XX1234 on 12-03. OTP 773311 for txn."))
    @Test fun codeFirst() = assertEquals("482913", code("482913 is your Amazon OTP. Do not share it with anyone."))
    @Test fun splitCode() = assertEquals("123456", code("Your WhatsApp code: 123-456"))
    @Test fun ignoresChat() = assertNull(code("Meeting moved to 3pm, call me on 9876543210"))
    @Test fun ignoresPromo() = assertNull(code("Use code SAVE20 for 20% off your next order!"))
    @Test fun ignoresCardSpend() = assertNull(code("INR 1500 spent on card 4455 at AMAZON. Not you? call 18002586161"))

    @Test fun validity() = assertEquals(
        TimeUnit.MINUTES.toMillis(5),
        OtpParser.parse("Your OTP is 4821. Valid for 5 minutes.")?.validForMillis,
    )

    @Test fun serviceFromSenderId() = assertEquals("HDFCBK", OtpParser.parse("482913 is the OTP for txn", "VM-HDFCBK-S")?.service)
    @Test fun serviceFromBody() = assertEquals("Uber", OtpParser.parse("Your Uber code: 1234")?.service)
    @Test fun prettySix() = assertEquals("482 913", OtpParser.parse("Your OTP is 482913")?.pretty)
}
