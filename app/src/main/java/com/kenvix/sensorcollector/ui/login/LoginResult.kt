package com.kenvix.sensorcollector.ui.login

import com.kenvix.sensorcollector.ui.login.LoggedInUserView

/**
 * Authentication result : success (user details) or error message.
 */
data class LoginResult(
    val success: LoggedInUserView? = null,
    val error: Int? = null
)
