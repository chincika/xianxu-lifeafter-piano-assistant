package dev.gamepiano.autoplay

import dev.gamepiano.autoplay.legal.RiskDisclaimer
import kotlin.test.Test
import kotlin.test.assertTrue

class RiskDisclaimerTest {
    @Test
    fun `agreement states detection privacy responsibility and refusal consequences`() {
        val statement = buildString {
            append(RiskDisclaimer.SUMMARY)
            RiskDisclaimer.sections.forEach {
                append(it.title)
                append(it.body)
            }
        }

        assertTrue(statement.contains("反作弊"))
        assertTrue(statement.contains("账号违规处罚"))
        assertTrue(statement.contains("不会收集用户导入的乐谱"))
        assertTrue(statement.contains("不依赖联网权限"))
        assertTrue(statement.contains("不同意并退出"))
    }
}
