package com.venera.compose.gallery

import com.venera.compose.data.platform.PreferenceKeys
import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 登录那几颗"**看起来像"登录、实际不是"的件，以及它们必须带着的那句话（2026-10-17 第四轮）。
 *
 * 用户当时的原话是「我的要求是加上个原生谷歌登录（能拉起手机的谷歌账号那种的）」。
 * 查完 pixez-miuix（它自己也没有原生 Google 登录）之后定的是 B：**A 全量**（站方 App 登录页 +
 * 深链回跳 + 粘贴兜底）+ 一枚**系统账号选择器**，但后者只作"本机 Google 身份"，
 * 界面上必须写明它不等于 pixiv 登录。
 *
 * 为什么值得单钉：那一枚按钮上写着 Google、又长在登录弹层里 —— 不写明就一定会被读成
 * "点它能登进 pixiv"，而它换了任何人也换不到一张 pixiv 的票据。这是本仓最忌讳的那一类：
 * 开关在、看着像、按下去什么也不会发生（假开关）。
 *
 * 判据只读源码文本（`app/` 没有 Robolectric，composable 组合不出来），扫描口径与
 * `GalleryPixivAccountWiringTest` 同一把：注释行不参与匹配。
 */
class PixivLoginPathsTest {

    private fun codeLines(dir: String, name: String): List<IndexedValue<String>> {
        val file = RepoSources.kotlinFiles(dir).firstOrNull { it.name == name }
            ?: error("找不到 $name（目录 $dir，尝试过的根：${RepoSources.root.absolutePath}）")
        return file.readLines().withIndex().filter { (_, line) ->
            val t = line.trim()
            !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
        }
    }

    private val uiDir = "com/venera/compose/gallery/ui"
    private val dataDir = "com/venera/compose/gallery/data"

    /** 原始文本（**不**滤注释）：`/* allowableAccountTypes = */` 这种行首块注释会被 [codeLines] 滤掉。 */
    private fun rawText(dir: String, name: String): String {
        val file = RepoSources.kotlinFiles(dir).firstOrNull { it.name == name }
            ?: error("找不到 $name（目录 $dir）")
        return file.readText()
    }

    private val identity = codeLines(uiDir, "GoogleAccountIdentity.kt")
    private val identityStore = codeLines("$dataDir/android", "GoogleIdentityAndroid.kt")
    private val account = codeLines(uiDir, "GalleryPixivAccount.kt")

    @Test
    fun `本机 Google 账号走系统账号选择器，不走要 client id 的 Credential Manager`() {
        // 两边的区别是"能不能零配置拉起手机里那几个账号"：系统选择器能，Credential Manager 要有
        // 一枚 Google OAuth client id（还要包名+签名在 Google Cloud 登记过）。本仓没有那一枚，
        // 也不该替用户申请一个 —— 于是选了前者，而它换回来的是邮箱、不是票据（正好对上 B 的口径）。
        assertTrue(
            "要用系统账号选择器：\n" + identity.joinToString("\n") { "${it.index}:${it.value}" },
            identity.any { it.value.contains("newChooseAccountIntent(") },
        )
        for (banned in listOf("CredentialManager", "GetGoogleIdOption", "SignInWithGoogleOption")) {
            val hits = identity.filter { it.value.contains(banned) }
            assertTrue("这一件里不许出现 $banned（那一路要有 Google OAuth client id）：$hits", hits.isEmpty())
        }
        // 只认 Google 那一类账号，别把整机所有账号都列出来。
        // 这一条要读**原文**：`/* allowableAccountTypes = */` 在行首，[codeLines] 会把整行当注释滤掉。
        val raw = rawText(uiDir, "GoogleAccountIdentity.kt")
        assertTrue(
            "allowableAccountTypes 要钉在 com.google 上（现在一个都没找到）",
            raw.contains("arrayOf(GOOGLE_ACCOUNT_TYPE)") && raw.contains("\"com.google\""),
        )
    }

    @Test
    fun `本机 Google 账号不联网、不碰票据`() {
        // 它是一枚**身份标记**：取 token / 打网络 / 读登录态，一项都不许有。
        // 有了一项，界面那句"不等于 pixiv 登录"就开始撒谎了。两段（选择器 + 落盘）一起扫。
        for (banned in listOf("OkHttpClient", "bearerOrNull", "access_token", "refresh_token", "HttpUrl", "VeneraNetworkClient")) {
            val hits = (identity + identityStore).filter { it.value.contains(banned) }
            assertTrue("本题里出现了 $banned：\n" + hits.joinToString("\n") { "${it.index}:${it.value}" }, hits.isEmpty())
        }
    }

    @Test
    fun `本机 Google 账号落在一份自己的配置里，退出 pixiv 不会顺手把它删掉`() {
        // `PixivCredentialState.signOut()` 走 `store.clear()`：两者同文件的话，
        // "退出 pixiv"会把这枚与登录态毫无关系的身份一起抹掉，而屏上只会表现为"怎么又没了"。
        assertNotEquals(
            "两枚配置名不许相同",
            PreferenceKeys.PREFS_GOOGLE_IDENTITY,
            PreferenceKeys.PREFS_PIXIV_ACCOUNT,
        )
        assertTrue(
            "落盘那一半要读自己的那份配置：\n" + identityStore.joinToString("\n") { "${it.index}:${it.value}" },
            identityStore.any { it.value.contains("PreferenceKeys.PREFS_GOOGLE_IDENTITY") },
        )
        // 反向一：选择器那一半（界面目录）不许自己碰存储 —— 那是 `BusinessApiBoundaryTest` 的红线。
        for (banned in listOf("AndroidKeyValueStore", "getSharedPreferences", "PREFS_GOOGLE_IDENTITY")) {
            val hits = identity.filter { it.value.contains(banned) }
            assertTrue("界面那一件里出现了 $banned：\n" + hits.joinToString("\n") { "${it.index}:${it.value}" }, hits.isEmpty())
        }
        // 反向二：pixiv 那两件里不许出现这枚配置名（出现了就是两处各读一份，迟早分叉）。
        val pixivFiles = RepoSources.kotlinFiles(dataDir) + RepoSources.kotlinFiles("$dataDir/android")
        for (file in pixivFiles.filter { it.name.startsWith("Pixiv") }) {
            assertTrue(
                "${file.name} 里不许读 PREFS_GOOGLE_IDENTITY",
                file.readText().lines().none { it.contains("PREFS_GOOGLE_IDENTITY") },
            )
        }
    }

    @Test
    fun `登录弹层里那枚 Google 按钮旁边必须写着它不等于 pixiv 登录`() {
        // 这一条是本轮唯一真正的"文案即判据"：那一枚按钮的一切风险都在被误读上，
        // 而误读的代价是"用户以为点了它就该登进去，结果什么都没发生"。
        val honest = account.filter { it.value.contains("不等于") && it.value.contains("pixiv") }
        assertEquals(
            "屏幕说明只许有一句（多写一句就会与另一句分叉）：\n" +
                honest.joinToString("\n") { "${it.index}:${it.value}" },
            1,
            honest.size,
        )
        // 那句说明要排在 Google 那一行的动作之后 —— 写成"先说不是、再给按钮"也不行：
        // 用户读的顺序是"按钮→说明"，而说明得紧跟着那一枚。
        val pickerAction = account.firstOrNull { it.value.contains("GoogleAccountIdentity.pickerIntent(") }
        assertTrue("找不到拉起选择器那一处", pickerAction != null)
        assertTrue(
            "说明（第 ${honest[0].index} 行）必须排在拉起选择器（第 ${pickerAction!!.index} 行）之后",
            honest[0].index > pickerAction.index,
        )
        // 也不许把"能登进 pixiv"这种话写进去（那正是要防的那一种读法）。
        val lie = honest[0].value.contains("登进 pixiv") || honest[0].value.contains("可以登")
        assertTrue("说明里出现了会误导的话：${honest[0].value}", !lie)
    }

    @Test
    fun `粘贴兜底真的接到判据件上，不是一颗摆着不动的按钮`() {
        // 件写好了没人调 = 屏上什么都没有，而测试全绿（本仓那一批 notWired 的教训）。
        assertTrue(
            "弹层里要真调 account.signInWithPasted(：\n" + account.joinToString("\n") { "${it.index}:${it.value}" },
            account.any { it.value.contains("account.signInWithPasted(") },
        )
        // 空内容不许发出去：那颗按钮的可用性要跟着输入框能不能用一起变
        // （否则点下去只会得到一句"空的"）。
        // 判据取"那颗按钮那几行"：`text = if (…) … else "用这一条登录"` 与 `enabled = …` 不在同一行。
        val buttonAt = account.indexOfFirst { it.value.contains("\"用这一条登录\"") }
        assertTrue("找不到粘贴那颗按钮", buttonAt >= 0)
        val buttonBlock = account.drop(buttonAt).take(8).map { it.value }.joinToString("\n")
        assertTrue(
            "按钮的可用性要跟着粘贴框走（`enabled = pasted.isNotBlank()`）：\n$buttonBlock",
            buttonBlock.contains("enabled = pasted.isNotBlank()"),
        )
    }

    @Test
    fun `起登录页那一跳用的是站方那个 client 标签，不是我们自己的 client_id`() {
        // 判据在算式件里（`PixivPkce`），这里只钉"别处不许再拼一份登录页地址"：
        // 界面里第二次拼 URL 就是第二把尺子，改了算式只坏一半。
        val urlLiterals = account.filter {
            it.value.contains("app-api.pixiv.net") || it.value.contains("accounts.pixiv.net")
        }
        assertTrue(
            "界面里不许自己拼登录页地址：\n" + urlLiterals.joinToString("\n") { "${it.index}:${it.value}" },
            urlLiterals.isEmpty(),
        )
    }

    @Test
    fun `回跳失败要在屏上当面说，不许只丢给「关注画师」那一行`() {
        // `LoginRejected` 不落盘，屏上原本唯一那句在「关注画师」那一行 —— 而那一行常在屏幕外。
        // 用户刚从浏览器回来，什么都没看见就等于"点完没反应"（本仓"一次失败不许静默"那条）。
        assertTrue(
            "要有一条把 LoginRejected 抬进 notice 的：\n" + account.joinToString("\n") { "${it.index}:${it.value}" },
            account.any {
                it.value.contains("as? PixivAuthState.LoginRejected") && it.value.contains("notice = ")
            },
        )
    }

    @Test
    fun `交浏览器那一跳先试 Custom Tab 再退回裸 VIEW，顺序不许反`() {
        // 2026-10-17 第五轮（用户「我还是要 google 这样登录 pixiv 试试」）：pixiv 的 Google 登录
        // 只能发生在站方网页里，唯一能改的是"把哪份浏览器会话交给它"。Google 的账号列表跟着
        // **浏览器自己的会话**走，系统默认浏览器未必是 Chrome、那份会话里未必有 Google。
        // ⇒ Custom Tab（固定 Chrome）必须排在裸 ACTION_VIEW 之前；反了的话，凡是有默认浏览器的
        // 机器就永远走不到 Chrome，这一改直接作废（而且屏上完全看不出来）。
        val tabAt = account.firstOrNull { it.value.contains("CustomTabsIntent") }
        val plainAt = account.firstOrNull { it.value.contains("Intent(Intent.ACTION_VIEW") }
        assertTrue("找不到 Custom Tab 那一跳：\n" + account.joinToString("\n") { "${it.index}:${it.value}" }, tabAt != null)
        assertTrue("找不到退回裸 VIEW 那一档（没它就没有兜底）：\n" + account.joinToString("\n") { "${it.index}:${it.value}" }, plainAt != null)
        assertTrue(
            "Custom Tab（第 ${tabAt!!.index} 行）要排在裸 VIEW（第 ${plainAt!!.index} 行）之前",
            tabAt.index < plainAt.index,
        )
        assertTrue("Custom Tab 那一档要真的 launchUrl(", account.any { it.value.contains(".launchUrl(") })
    }
}
