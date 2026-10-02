package co.ke.bremac.posapp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class OtpCodeTest {
    @Test
    public void findsSixDigitCodeInMessage() {
        assertEquals("482913", OtpCode.extract("Your BreMac360 login code is 482913. It expires in 5 minutes."));
    }

    @Test
    public void ignoresLongerNumbers() {
        assertEquals("120456", OtpCode.extract("Call 0712345678 for help. Code: 120456"));
    }

    @Test
    public void returnsNullWithoutCode() {
        assertNull(OtpCode.extract("No code here 12345"));
        assertNull(OtpCode.extract(null));
    }
}
