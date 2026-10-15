package com.glasssms.data

import com.glasssms.otp.OtpParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorizerTest {
    private fun cat(address: String, body: String) = Categorizer.categorize(address, body, OtpParser.parse(body, address))

    @Test fun traiPromotional() = assertEquals(Category.Offers, cat("VM-MYNTRA-P", "New arrivals just dropped"))
    @Test fun traiService() = assertEquals(Category.Transactions, cat("JX-HDFCBK-S", "Spent Rs.1357.26 On HDFC Bank Card 9474"))
    @Test fun traiTransactional() = assertEquals(Category.Transactions, cat("AD-IRCTC-T", "PNR 4558368310 booked"))
    @Test fun traiGovernment() = assertEquals(Category.Transactions, cat("VK-UIDAI-G", "Your Aadhaar was updated"))
    @Test fun otpWinsOverSuffix() = assertEquals(Category.Otps, cat("JX-SWIGGY-T", "Use OTP 266243 to log into your Swiggy account"))
    @Test fun personNumber() = assertEquals(Category.Personal, cat("+919876543210", "Reached home?"))
    @Test fun oldHeaderPromoByText() = assertEquals(Category.Offers, cat("AX-AJIO", "Flat 50% off on everything this weekend"))
    @Test fun oldHeaderService() = assertEquals(Category.Transactions, cat("AD-AIRTEL", "Your bill of Rs 499 is generated"))

    @Test fun senderKeyStripsPrefixAndSuffix() = assertEquals("HDFCBK", Categorizer.senderKey("JX-HDFCBK-S"))
    @Test fun senderKeySameAcrossOperators() = assertEquals(Categorizer.senderKey("AD-HDFCBK-T"), Categorizer.senderKey("VM-HDFCBK"))
    @Test fun senderKeyNumberLast10() = assertEquals("9876543210", Categorizer.senderKey("+91 98765 43210"))

    @Test fun promoHeaderDetected() = assertTrue(Categorizer.isPromotionalHeader("VM-MYNTRA-P"))
    @Test fun serviceHeaderNotPromo() = assertFalse(Categorizer.isPromotionalHeader("JX-HDFCBK-S"))
}
