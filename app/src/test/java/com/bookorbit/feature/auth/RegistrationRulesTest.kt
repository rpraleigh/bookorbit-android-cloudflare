package com.bookorbit.feature.auth

import com.bookorbit.R
import com.bookorbit.core.network.CloudflareAccessException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class RegistrationRulesTest {

    private fun http(code: Int) =
        HttpException(Response.error<Any>(code, "{}".toResponseBody("application/json".toMediaType())))

    @Test
    fun usernameLimits() {
        assertEquals(R.string.register_err_username_short, RegistrationRules.usernameProblem("ab"))
        assertNull(RegistrationRules.usernameProblem("abc"))
        assertNull(RegistrationRules.usernameProblem("a".repeat(100)))
        assertEquals(R.string.register_err_username_long, RegistrationRules.usernameProblem("a".repeat(101)))
    }

    @Test
    fun usernameAndNameMustBeTrimmedAndDisplaySafe() {
        assertEquals(R.string.register_err_trimmed, RegistrationRules.usernameProblem(" perry"))
        assertEquals(R.string.register_err_trimmed, RegistrationRules.nameProblem("Perry "))
        assertEquals(R.string.register_err_unsafe_text, RegistrationRules.usernameProblem("per​ry"))
        assertEquals(R.string.register_err_unsafe_text, RegistrationRules.nameProblem("Pe‮rry"))
        assertEquals(R.string.register_err_unsafe_text, RegistrationRules.usernameProblem("per\nry"))
        assertNull(RegistrationRules.nameProblem("Perry Raleigh"))
        assertNull(RegistrationRules.usernameProblem("pérry_ラ"))
    }

    @Test
    fun nameLimits() {
        assertEquals(R.string.register_err_name_empty, RegistrationRules.nameProblem(""))
        assertNull(RegistrationRules.nameProblem("a".repeat(255)))
        assertEquals(R.string.register_err_name_long, RegistrationRules.nameProblem("a".repeat(256)))
    }

    @Test
    fun emailShape() {
        assertNull(RegistrationRules.emailProblem("a@b.co"))
        assertNull(RegistrationRules.emailProblem("  a@b.co "))
        assertEquals(R.string.register_err_email, RegistrationRules.emailProblem(""))
        assertEquals(R.string.register_err_email, RegistrationRules.emailProblem("a@b"))
        assertEquals(R.string.register_err_email, RegistrationRules.emailProblem("a b@c.de"))
    }

    @Test
    fun passwordRules() {
        assertEquals(R.string.register_err_password_short, RegistrationRules.passwordProblem("Ab1"))
        assertEquals(R.string.register_err_password_classes, RegistrationRules.passwordProblem("alllowercase1"))
        assertEquals(R.string.register_err_password_classes, RegistrationRules.passwordProblem("ALLUPPERCASE1"))
        assertEquals(R.string.register_err_password_classes, RegistrationRules.passwordProblem("NoDigitsHere"))
        assertNull(RegistrationRules.passwordProblem("Abcdefg1"))
        assertEquals(R.string.register_err_password_long, RegistrationRules.passwordProblem("Aa1" + "x".repeat(1022)))
    }

    @Test
    fun confirmMustMatch() {
        assertNull(RegistrationRules.confirmProblem("Abcdefg1", "Abcdefg1"))
        assertEquals(R.string.register_err_confirm, RegistrationRules.confirmProblem("Abcdefg1", "abcdefg1"))
    }

    @Test
    fun statusCodesMapToSpecificFailures() {
        assertEquals(RegisterFailure.TAKEN, classifyRegisterFailure(http(409)).failure)
        assertEquals(RegisterFailure.CLOSED, classifyRegisterFailure(http(403)).failure)
        assertEquals(RegisterFailure.INVALID, classifyRegisterFailure(http(400)).failure)
        assertEquals(RegisterFailure.INVALID, classifyRegisterFailure(http(422)).failure)
        assertEquals(RegisterFailure.THROTTLED, classifyRegisterFailure(http(429)).failure)
    }

    @Test
    fun otherStatusCodesAreReportedAsTheirCode() {
        val outcome = classifyRegisterFailure(http(502))
        assertEquals(RegisterFailure.HTTP_OTHER, outcome.failure)
        assertEquals(502, outcome.httpCode)
        // A 401 is never reported as bad credentials during registration.
        assertEquals(RegisterFailure.HTTP_OTHER, classifyRegisterFailure(http(401)).failure)
    }

    @Test
    fun cloudflareFailuresBeatOtherCauses() {
        assertEquals(
            RegisterFailure.CLOUDFLARE_REJECTED,
            classifyRegisterFailure(CloudflareAccessException(credentialsConfigured = true)).failure,
        )
        assertEquals(
            RegisterFailure.CLOUDFLARE_REQUIRED,
            classifyRegisterFailure(CloudflareAccessException(credentialsConfigured = false)).failure,
        )
        // Wrapped, as Retrofit/coroutines may do.
        assertEquals(
            RegisterFailure.CLOUDFLARE_REJECTED,
            classifyRegisterFailure(RuntimeException("x", CloudflareAccessException(true))).failure,
        )
    }

    @Test
    fun networkAndUnexpectedFailures() {
        assertEquals(RegisterFailure.NETWORK, classifyRegisterFailure(IOException("boom")).failure)
        assertEquals(RegisterFailure.UNEXPECTED, classifyRegisterFailure(IllegalStateException("x")).failure)
    }
}
