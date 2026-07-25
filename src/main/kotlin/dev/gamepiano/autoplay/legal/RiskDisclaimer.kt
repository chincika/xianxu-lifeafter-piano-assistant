package dev.gamepiano.autoplay.legal

import java.util.prefs.Preferences

data class DisclaimerSection(
    val title: String,
    val body: String,
)

object RiskDisclaimer {
    const val ACCEPTANCE_REVISION: Int = 2
    const val DISPLAY_VERSION: String = "1.1"
    const val LAST_UPDATED: String = "2026-07-23"
    const val TITLE: String = "用户协议与隐私声明"

    const val SUMMARY: String =
        "本软件会通过键盘、鼠标或窗口消息向游戏发送自动演奏输入，可能被游戏或其反作弊系统识别为异常、自动化或违规行为。"

    val sections: List<DisclaimerSection> = listOf(
        DisclaimerSection(
            title = "一、功能与检测风险",
            body = "本软件不包含未来也不会开发任何违反公平竞技原则和破坏侵入游戏服务器的功能，但自动演奏的功能依赖向游戏客户端发送虚拟坐标和点击数据，可能被错误检测为违规。因此本软件不会承诺任何输入方式能够避开检测，即使不修改游戏文件，自动化、重复或高频输入仍可能被游戏运营方认定为连点器或鼠标宏违规行为。本软件不对因此产生的账号违规处罚、数据损失或其他间接损失承担任何责任，相关责任由用户自行承担。",
        ),
        DisclaimerSection(
            title = "二、用户责任",
            body = "使用前请自行阅读并遵守游戏用户协议、社区规则及所在地区适用法律。\n\n请勿将本软件用于破坏公平竞技、牟利、骚扰他人或其他未经授权的用途。是否继续使用由用户自行判断并承担相应风险。",
        ),
        DisclaimerSection(
            title = "三、隐私协议",
            body = "本软件所有功能均为本地功能，不依赖联网权限。\n\n本软件不会收集用户导入的乐谱，也不对用户导入的乐谱版权负责。",
        ),
        DisclaimerSection(
            title = "四、其他",
            body = "由于游戏辅助类型软件的特殊性，开发者无法保证本软件始终可用，本软件保留随时停止更新的权利。\n\n开发者保留随时对用户协议进行更新的权利，更新后的协议文本将于软件内公示。\n\n选择“我已阅读并同意”表示你已理解上述风险并自愿继续使用；如果你不同意或无法确认，请选择“不同意并退出”，并停止使用本软件。",
        ),
    )
}

interface DisclaimerAcceptanceStore {
    fun hasAcceptedCurrentVersion(): Boolean
    fun acceptCurrentVersion(): Boolean
}

class PreferencesDisclaimerAcceptanceStore(
    private val preferences: Preferences = Preferences.userNodeForPackage(
        PreferencesDisclaimerAcceptanceStore::class.java,
    ),
) : DisclaimerAcceptanceStore {
    override fun hasAcceptedCurrentVersion(): Boolean = runCatching {
        preferences.getInt(ACCEPTED_VERSION_KEY, 0) >= RiskDisclaimer.ACCEPTANCE_REVISION
    }.getOrDefault(false)

    override fun acceptCurrentVersion(): Boolean = runCatching {
        preferences.putInt(ACCEPTED_VERSION_KEY, RiskDisclaimer.ACCEPTANCE_REVISION)
        preferences.flush()
        true
    }.getOrDefault(false)

    private companion object {
        const val ACCEPTED_VERSION_KEY = "riskDisclaimerAcceptedVersion.v1"
    }
}
