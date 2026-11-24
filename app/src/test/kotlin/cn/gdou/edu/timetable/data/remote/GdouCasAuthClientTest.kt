package cn.gdou.edu.timetable.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GdouCasAuthClientTest {

    @Test
    fun isAlreadyInJwSystem_rejectsIntermediateSsoLogoutAndLoginUrls() {
        assertFalse(
            GdouCasAuthClient.isAlreadyInJwSystem(
                "https://jw.gdou.edu.cn/sso/jziotlogin?ticket=ST-12345",
                "<html><body>Redirecting...</body></html>"
            )
        )
        assertFalse(
            GdouCasAuthClient.isAlreadyInJwSystem(
                "https://jw.gdou.edu.cn/logout",
                "<html><body>已退出登录</body></html>"
            )
        )
        assertFalse(
            GdouCasAuthClient.isAlreadyInJwSystem(
                "https://jw.gdou.edu.cn/xtgl/login_slogin.html",
                "<html><body>教学管理信息服务平台</body></html>"
            )
        )
        assertFalse(
            GdouCasAuthClient.isAlreadyInJwSystem(
                "https://authserver.gdou.edu.cn/authserver/login?service=https%3A%2F%2Fjw.gdou.edu.cn%2Fsso%2Fjziotlogin",
                "<html><body><input id=\"pwdEncryptSalt\" value=\"abc123\" /></body></html>"
            )
        )
    }

    @Test
    fun isAlreadyInJwSystem_acceptsAuthenticatedJwMenuPage() {
        assertTrue(
            GdouCasAuthClient.isAlreadyInJwSystem(
                "https://jw.gdou.edu.cn/xtgl/index_initMenu.html?jsdm=xs",
                "<html><body>教学管理信息服务平台</body></html>"
            )
        )
    }

    @Test
    fun parseCasForm_errorPageHasBlankExecutionAndNotAlreadyAuthenticated() {
        val form = GdouCasAuthClient.parseCasForm("<html><body>502 Bad Gateway</body></html>")
        assertFalse(form.alreadyAuthenticated)
        assertEquals("", form.execution)
        assertEquals("", form.pwdEncryptSalt)
    }

    @Test
    fun parseCasForm_extractsPasswordFormFieldsAndCaptchaFlag() {
        val html = """
            <html>
            <body>
              <form id="pwdFromId" action="/authserver/login">
                <input type="hidden" name="lt" value="LT-1" />
                <input type="hidden" name="execution" value="e3s1" />
                <input type="hidden" name="_eventId" value="submit" />
                <input type="hidden" name="dllt" value="userNamePasswordLogin" />
                <input type="hidden" id="pwdEncryptSalt" value="Ig44SE7ZZhlbx9fB" />
              </form>
              <script>var needCaptcha = "true";</script>
            </body>
            </html>
        """.trimIndent()
        val form = GdouCasAuthClient.parseCasForm(html)
        assertFalse(form.alreadyAuthenticated)
        assertEquals("LT-1", form.lt)
        assertEquals("e3s1", form.execution)
        assertEquals("Ig44SE7ZZhlbx9fB", form.pwdEncryptSalt)
        assertTrue(form.needCaptcha)
    }
}
