package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * pixiv 登录那几路的接线尺子（2026-10-17 第四轮起，2026-10-19 撤掉本机 Google 那一件后收窄）。
 *
 * 用户当时的原话是「我的要求是加上个原生谷歌登录（能拉起手机的谷歌账号那种的）」。
 * 查完 pixez-miuix（它自己也没有原生 Google 登录）之后定的是 B：**A 全量**（站方 App 登录页 +
 * 深链回跳 + 粘贴兜底）+ 一枚**系统账号选择器**（只作"本机 Google 身份"，界面上写明它不等于 pixiv 登录）。
 *
 * 2026-10-19 用户拍板「本机 Google 登录撤掉，暂时用不上」⇒ 那一件连同 `LocalGoogleIdentity` /
 * `GoogleIdentityAndroid` / `GalleryPorts.googleIdentity` 一起撤了（原件在 `_trash/google-identity-2026-10-19/`，
 * 三条协议层的理由记在 `docs/rounds/gallery-pixiv-account-pkce-2026-10-17.md` §四）。
 * 它留下的那四条用例（选择器形状、不联网、独立配置、那句护栏）跟着一起撤 —— **判据不能比被判的件活得久**，
 * 留着只会在下一轮变成"量具失效"那种红。这里剩下的是浏览器那一路自己的四条。
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

    private val account = codeLines(uiDir, "GalleryPixivAccount.kt")

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
