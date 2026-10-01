package co.ke.bremac.posapp.api;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class EnvelopeParserTest {
    @Test
    public void parsesSuccessEnvelope() throws Exception {
        JSONObject env = EnvelopeParser.parse(
                "{\"success\":true,\"data\":{\"id\":7},\"meta\":{\"current_page\":1}}",
                200);
        assertEquals(7, env.getJSONObject("data").getInt("id"));
    }

    @Test
    public void parsesErrorEnvelope() {
        try {
            EnvelopeParser.parse(
                    "{\"success\":false,\"message\":\"No auth\",\"code\":\"unauthenticated\","
                            + "\"errors\":{\"username\":[\"Required\"]}}",
                    401);
            fail("Expected exception");
        } catch (ApiException e) {
            assertEquals(401, e.httpStatus);
            assertEquals("unauthenticated", e.code);
            assertEquals("No auth", e.getMessage());
            assertEquals("Required", e.errors.get("username").get(0));
        }
    }
}
