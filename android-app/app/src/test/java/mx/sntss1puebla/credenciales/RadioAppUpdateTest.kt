package mx.sntss1puebla.credenciales

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioAppUpdateTest {
    private val json = """{"versionCode":28,"versionName":"0.10.18","apkUrl":"https://raw.githubusercontent.com/cncdigital/AndroidRadio/apk/RadioSindical.apk"}"""

    @Test fun parsesPublishedVersionAndDownloadLink() {
        val update = RadioAppUpdate.parse(json)
        assertNotNull(update)
        assertEquals(28, update?.versionCode)
        assertEquals("0.10.18", update?.versionName)
    }

    @Test fun updateIsOfferedOnlyForHigherVersionCode() {
        val update = RadioAppUpdate.parse(json)!!
        assertTrue(update.isNewerThan(27))
        assertFalse(update.isNewerThan(28))
    }

    @Test fun rejectsUntrustedDownloadLinksAndMalformedMetadata() {
        assertNull(RadioAppUpdate.parse(json.replace("raw.githubusercontent.com", "example.com")))
        assertNull(RadioAppUpdate.parse("""{"versionCode":0,"versionName":"bad","apkUrl":"http://raw.githubusercontent.com/cncdigital/AndroidRadio/apk/RadioSindical.apk"}"""))
    }
}
