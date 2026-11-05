package com.bookorbit.feature.auth

import androidx.annotation.StringRes
import com.bookorbit.R
import com.bookorbit.core.network.CloudflareAccessException
import retrofit2.HttpException
import java.io.IOException

/**
 * Client-side copy of the limits the BookOrbit server enforces on `POST /auth/register`
 * (server/src/modules/auth/dto/register.dto.ts). The server stays the authority; these checks only
 * keep obviously bad input from costing a request, since registration is throttled to 3 per minute.
 */
object RegistrationRules {
    const val USERNAME_MIN = 3
    const val USERNAME_MAX = 100
    const val NAME_MAX = 255
    const val PASSWORD_MIN = 8
    const val PASSWORD_MAX = 1024

    /** Control, zero-width and text-direction characters the server rejects in usernames and names. */
    private val UNSAFE_TEXT = Regex("[\\u0000-\\u001F\\u007F\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]")
    private val LOWER = Regex("[a-z]")
    private val UPPER = Regex("[A-Z]")
    private val DIGIT = Regex("\\d")
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    /** Each check returns a string resource describing the problem, or null when the value is fine. */
    @StringRes
    fun usernameProblem(value: String): Int? = when {
        value.length < USERNAME_MIN -> R.string.register_err_username_short
        value.length > USERNAME_MAX -> R.string.register_err_username_long
        value != value.trim() -> R.string.register_err_trimmed
        UNSAFE_TEXT.containsMatchIn(value) -> R.string.register_err_unsafe_text
        else -> null
    }

    @StringRes
    fun nameProblem(value: String): Int? = when {
        value.isEmpty() -> R.string.register_err_name_empty
        value.length > NAME_MAX -> R.string.register_err_name_long
        value != value.trim() -> R.string.register_err_trimmed
        UNSAFE_TEXT.containsMatchIn(value) -> R.string.register_err_unsafe_text
        else -> null
    }

    @StringRes
    fun emailProblem(value: String): Int? =
        if (EMAIL.matches(value.trim())) null else R.string.register_err_email

    @StringRes
    fun passwordProblem(value: String): Int? = when {
        value.length < PASSWORD_MIN -> R.string.register_err_password_short
        value.length > PASSWORD_MAX -> R.string.register_err_password_long
        !(LOWER.containsMatchIn(value) && UPPER.containsMatchIn(value) && DIGIT.containsMatchIn(value)) ->
            R.string.register_err_password_classes
        else -> null
    }

    @StringRes
    fun confirmProblem(password: String, confirm: String): Int? =
        if (password == confirm) null else R.string.register_err_confirm
}

/** What went wrong with a registration request, without any Android resources attached. */
enum class RegisterFailure {
    /** 409: the username or email is already in use. The server does not say which. */
    TAKEN,

    /** 403: the server is not accepting registrations. */
    CLOSED,

    /** 400 / 422: the server rejected a field. */
    INVALID,

    /** 429: too many attempts; the limit is 3 per minute. */
    THROTTLED,
    CLOUDFLARE_REJECTED,
    CLOUDFLARE_REQUIRED,
    NETWORK,
    HTTP_OTHER,
    UNEXPECTED,
}

data class RegisterOutcome(val failure: RegisterFailure, val httpCode: Int? = null)

/**
 * Sorts a failed registration into one of [RegisterFailure]. Only the documented status codes get a
 * specific meaning; anything else is reported as what it is and never as a credentials problem.
 */
fun classifyRegisterFailure(error: Throwable): RegisterOutcome {
    val causes = generateSequence(error) { it.cause }.toList()
    val access = causes.filterIsInstance<CloudflareAccessException>().firstOrNull()
    if (access != null) {
        return RegisterOutcome(
            if (access.credentialsConfigured) RegisterFailure.CLOUDFLARE_REJECTED else RegisterFailure.CLOUDFLARE_REQUIRED,
        )
    }
    val http = causes.filterIsInstance<HttpException>().firstOrNull()
    if (http != null) {
        val code = http.code()
        return when (code) {
            409 -> RegisterOutcome(RegisterFailure.TAKEN, code)
            403 -> RegisterOutcome(RegisterFailure.CLOSED, code)
            400, 422 -> RegisterOutcome(RegisterFailure.INVALID, code)
            429 -> RegisterOutcome(RegisterFailure.THROTTLED, code)
            else -> RegisterOutcome(RegisterFailure.HTTP_OTHER, code)
        }
    }
    if (causes.any { it is IOException }) return RegisterOutcome(RegisterFailure.NETWORK)
    return RegisterOutcome(RegisterFailure.UNEXPECTED)
}
