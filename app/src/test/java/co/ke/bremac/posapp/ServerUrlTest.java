package co.ke.bremac.posapp;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ServerUrlTest {
    @Test
    public void addsHttpsAndRemovesTrailingSlash() throws Exception {
        assertEquals("https://pos.example.com", ServerUrl.normalize("pos.example.com/"));
    }

    @Test
    public void preservesPosSubdirectoryAndPort() throws Exception {
        assertEquals("https://pos.example.com:8443/retail", ServerUrl.normalize(" https://POS.example.com:8443/retail/ "));
    }

    @Test
    public void preservesCaseSensitiveSubdirectory() throws Exception {
        assertEquals("https://pos.example.com/StoreFront", ServerUrl.normalize("POS.example.com/StoreFront"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInsecureHttp() throws Exception {
        ServerUrl.normalize("http://pos.example.com");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsCredentialsInAddress() throws Exception {
        ServerUrl.normalize("https://user:password@pos.example.com");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsQueryStrings() throws Exception {
        ServerUrl.normalize("https://pos.example.com?token=secret");
    }
}
