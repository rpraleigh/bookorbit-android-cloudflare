package com.bookorbit.core.model

import kotlinx.serialization.Serializable

/** The authenticated user as returned by the server. */
@Serializable
data class AuthUser(
    val id: Int,
    val username: String,
    val name: String? = null,
    val email: String? = null,
    val active: Boolean = true,
    val isSuperuser: Boolean = false,
    val isDefaultPassword: Boolean = false,
    val avatarUrl: String? = null,
    val provisioningMethod: String = "local",
    val permissions: List<String> = emptyList(),
)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginResponse(val accessToken: String, val user: AuthUser)

/** `POST /auth/refresh` response. The refresh token itself rides as an HTTP-only cookie. */
@Serializable
data class RefreshResponse(val accessToken: String)

@Serializable
data class SetupStatus(
    val needsSetup: Boolean,
    /** True when the server lets people create their own account (`POST /auth/register`). */
    val allowRegistration: Boolean = false,
)

/** `POST /auth/register` body. The server also enforces these limits; see [com.bookorbit.feature.auth.RegistrationRules]. */
@Serializable
data class RegisterRequest(
    val username: String,
    val name: String,
    val email: String,
    val password: String,
)

/** `POST /auth/register` response. It carries no token: the new user signs in afterwards. */
@Serializable
data class RegisterResponse(val id: Int, val username: String, val name: String? = null)

@Serializable
data class OidcProviderPublic(
    val slug: String,
    val displayName: String,
    val enabled: Boolean,
    val iconUrl: String? = null,
    val clientId: String,
    val scopes: String,
)

@Serializable
data class OidcStateResponse(
    val state: String,
    val authorizationEndpoint: String,
)

@Serializable
data class OidcCallbackRequest(
    val code: String,
    val codeVerifier: String,
    val redirectUri: String,
    val nonce: String,
    val state: String,
)

@Serializable
data class OidcCallbackResponse(
    val mode: String = "login",
    val accessToken: String,
    val user: AuthUser,
)
