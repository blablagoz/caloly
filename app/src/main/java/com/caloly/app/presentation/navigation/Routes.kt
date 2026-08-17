package com.caloly.app.presentation.navigation

object Routes {
    const val AUTH_GATE = "auth_gate"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val OTP = "otp"
    const val FORGOT_PASSWORD = "forgot_password"
    const val CHANGE_PASSWORD = "change_password"
    const val ACCOUNT = "account"
    const val ONBOARDING = "onboarding"
    const val SHARING_SETTINGS = "sharing_settings"
    const val HOME = "home"
    const val ADD_FOOD = "add_food"
    const val SOCIAL = "social"
    fun addFood(dateKey: String) = "$ADD_FOOD/$dateKey"
}
