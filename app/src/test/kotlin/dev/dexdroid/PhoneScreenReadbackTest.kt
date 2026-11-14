package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

class PhoneScreenReadbackTest {
    @Test
    fun onlyTheExactPhysicalSectionReportsPower() {
        val text =
            "Display 17\n    powerMode=Off\n\nDisplay 18\n    powerMode=On\n\nVirtual Display 117\n    powerMode=On\n"
        assertEquals(PhoneScreenResult.State.OFF, PhoneScreenReadback.parse(text, 17, true))
        assertEquals(PhoneScreenResult.State.ON, PhoneScreenReadback.parse(text, 18, true))
        assertEquals(PhoneScreenResult.State.UNKNOWN, PhoneScreenReadback.parse(text, 117, true))
        assertEquals(PhoneScreenResult.State.UNKNOWN, PhoneScreenReadback.parse(text, 7, true))
    }

    @Test
    fun duplicateSectionsModesAndUnknownValuesStayUnconfirmed() {
        val samples =
            listOf(
                "Display 17\n    powerMode=On\nDisplay 17\n    powerMode=On\n",
                "Display 17\n    powerMode=On\n    powerMode=Off\n",
                "Display 17\n    powerMode=Doze\n",
                "Display 17\nAnother section\n    powerMode=On\n",
                "Display 17 (HWC display 0):\n    powerMode=On\n",
                "Display 17\n    logicalState=ON\n",
            )
        samples.forEach {
            assertEquals(
                it,
                PhoneScreenResult.State.UNKNOWN,
                PhoneScreenReadback.parse(it, 17, true),
            )
        }
    }

    @Test
    fun timeoutTruncationAndOutputCapsNeverConfirmOn() {
        val valid = "Display 17\n    powerMode=On\n"
        assertEquals(PhoneScreenResult.State.UNKNOWN, PhoneScreenReadback.parse(valid, 17, false))
        assertEquals(
            PhoneScreenResult.State.UNKNOWN,
            PhoneScreenReadback.parse(valid + " ".repeat(PhoneScreenReadback.MAX_BYTES), 17, true),
        )
        assertEquals(
            PhoneScreenResult.State.UNKNOWN,
            PhoneScreenReadback.parse("Display 17\n    powerMode=", 17, false),
        )
    }
}
