package co.ke.bremac.posapp;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EscPosRasterTest {
    @Test
    public void packsBlackPixelsInWebsiteOrder() {
        byte[] data = EscPosRaster.encode(384, 1, (x, y) -> x == 0 || x == 7 ? 0xff000000 : 0xffffffff, false);
        assertEquals(29, data[5] & 255);
        assertEquals(118, data[6] & 255);
        assertEquals(48, data[9] & 255);
        assertEquals(1, data[11] & 255);
        assertEquals(129, data[13] & 255);
        assertEquals(0, data[14]);
        assertEquals(13 + 48 + 3, data.length);
    }

    @Test
    public void transparentPixelsPrintWhite() {
        byte[] data = EscPosRaster.encode(384, 1, (x, y) -> 0x00000000, false);
        assertEquals(0, data[13]);
    }

    @Test
    public void splitsLongReceiptsAndCutsOnlyWhenRequested() {
        byte[] data = EscPosRaster.encode(576, 129, (x, y) -> y == 128 ? 0xff000000 : 0xffffffff, true);
        int second = 13 + 72 * 128;
        assertEquals(29, data[second] & 255);
        assertEquals(72, data[second + 4] & 255);
        assertEquals(1, data[second + 6] & 255);
        assertEquals(255, data[second + 8] & 255);
        assertEquals(29, data[data.length - 3] & 255);
        assertEquals(86, data[data.length - 2] & 255);
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesTruncatedOrOversizedReceipts() {
        EscPosRaster.encode(384, 20001, (x, y) -> 0xffffffff, false);
    }

    @Test
    public void supportsBothPaperWidths() {
        assertEquals(384, EscPosRaster.width(58));
        assertEquals(576, EscPosRaster.width(80));
    }
}
