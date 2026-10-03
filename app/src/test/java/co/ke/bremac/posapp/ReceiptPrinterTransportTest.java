package co.ke.bremac.posapp;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ReceiptPrinterTransportTest {
    @Test
    public void acceptsIpAndHostNamesAndStandardPort() {
        assertEquals("192.168.1.20", ReceiptPrinterTransport.validateHost(" 192.168.1.20 "));
        assertEquals("printer.local", ReceiptPrinterTransport.validateHost("printer.local"));
        assertEquals("printer-2", ReceiptPrinterTransport.validateHost("printer-2"));
        assertEquals("2001:db8::1", ReceiptPrinterTransport.validateHost("2001:db8::1"));
        assertEquals("fe80::1%wlan0", ReceiptPrinterTransport.validateHost("fe80::1%wlan0"));
        assertEquals(9100, ReceiptPrinterTransport.validatePort("9100"));
        assertEquals(1, ReceiptPrinterTransport.validatePort("1"));
        assertEquals(65535, ReceiptPrinterTransport.validatePort("65535"));
    }

    @Test
    public void rejectsUrlsPortsAndInvalidHostInputs() {
        for (String input : new String[]{"", "http://printer.local", "printer.local:9100",
                "192.168.1.20:9100", "printer/path", "host name", "host\ninjected",
                ".", "..", "999.1.2.3", "1.2.3", "-printer", "printer..local"}) {
            try {
                ReceiptPrinterTransport.validateHost(input);
                fail("Should reject " + input);
            } catch (IllegalArgumentException expected) {
                // Input stays in the settings dialog with a useful validation message.
            }
        }
    }

    @Test
    public void rejectsInvalidPorts() {
        for (String input : new String[]{"", "0", "65536", "-1", "9100x", "2147483648"}) {
            try {
                ReceiptPrinterTransport.validatePort(input);
                fail("Should reject " + input);
            } catch (IllegalArgumentException expected) {
                assertEquals("Printer port must be from 1 to 65535 (usually 9100).", expected.getMessage());
            }
        }
    }
}
