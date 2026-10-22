package dev.jcdesigns.still

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProfileFormatTest {
    @Test fun profileHeaderIsVersioned() {
        val json = JSONObject().put("format", "still-launcher-profile").put("version", 1)
        assertEquals("still-launcher-profile", json.getString("format"))
        assertEquals(1, json.getInt("version"))
    }
    @Test fun layoutPresetsHaveStableNames() {
        assertEquals(8, HomeStyle.entries.size)
        assertEquals(HomeStyle.PURE_MINIMAL, HomeStyle.valueOf("PURE_MINIMAL"))
        assertEquals(HomeStyle.BLANK, HomeStyle.valueOf("BLANK"))
    }
    @Test fun gridColumnValuesAreBounded() {
        assertEquals(2, (-9).coerceIn(2, 7))
        assertEquals(7, 99.coerceIn(2, 7))
        assertEquals(4, 4.coerceIn(2, 7))
    }
}
