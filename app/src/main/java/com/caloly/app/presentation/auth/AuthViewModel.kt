package com.caloly.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caloly.app.domain.auth.AuthRepository
import com.caloly.app.domain.auth.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthActionState(
    val loading: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val otpEmail: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
) : ViewModel() {
    val authState: StateFlow<AuthState> = repository.authState.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Loading
    )

    private val _action = MutableStateFlow(AuthActionState())
    val action = _action.asStateFlow()

    fun clearFeedback() { _action.value = _action.value.copy(message = null, error = null) }

    fun sendOtp(email: String, createUser: Boolean = true) = runAction {
        require(email.contains('@')) { "Geçerli bir e-posta adresi girin." }
        repository.sendEmailOtp(email, createUser)
        _action.value = AuthActionState(
            message = "Giriş bağlantısı e-posta adresinize gönderildi.",
            otpEmail = email.trim()
        )
    }

    fun verifyOtp(email: String, token: String, isSignup: Boolean = false, onSuccess: () -> Unit) = runAction {
        require(token.length >= 6) { "E-postadaki doğrulama kodunu girin." }
        repository.verifyEmailOtp(email, token, isSignup)
        _action.value = AuthActionState(message = "Giriş başarılı.")
        onSuccess()
    }

    fun signUp(email: String, password: String, displayName: String, username: String, onSubmitted: () -> Unit) = runAction {
        require(email.contains('@')) { "Geçerli bir e-posta adresi girin." }
        validatePassword(password)
        require(displayName.isNotBlank()) { "Adınızı girin." }
        require(username.matches(Regex("[a-zA-Z0-9._]{3,24}"))) { "Kullanıcı adı 3-24 karakter; harf, sayı, nokta ve alt çizgi içerebilir." }
        repository.signUp(email, password, displayName, username)
        _action.value = AuthActionState(
            message = "Hesap oluşturuldu. E-postadaki doğrulama bağlantısına dokunun.",
            otpEmail = email.trim()
        )
        onSubmitted()
    }

    fun signIn(email: String, password: String) = runAction {
        repository.signIn(email, password)
    }

    fun googleSignIn() = runAction { repository.signInWithGoogle() }

    fun forgotPassword(email: String) = runAction {
        require(email.contains('@')) { "Geçerli e-posta adresi girin." }
        repository.sendPasswordReset(email)
        _action.value = AuthActionState(message = "Şifre yenileme bağlantısı e-postanıza gönderildi.")
    }

    fun changePassword(password: String, onDone: () -> Unit) = runAction {
        validatePassword(password)
        repository.changePassword(password)
        _action.value = AuthActionState(message = "Şifreniz değiştirildi.")
        onDone()
    }

    fun updateProfile(displayName: String, username: String, onDone: () -> Unit) = runAction {
        require(displayName.isNotBlank()) { "Ad alanı boş bırakılamaz." }
        require(username.matches(Regex("[a-zA-Z0-9._]{3,24}"))) { "Geçerli bir kullanıcı adı girin." }
        repository.updateProfile(displayName, username)
        _action.value = AuthActionState(message = "Profil güncellendi.")
        onDone()
    }

    fun uploadAvatar(bytes: ByteArray, contentType: String = "image/jpeg") = runAction {
        repository.uploadAvatar(bytes, contentType)
        _action.value = AuthActionState(message = "Profil fotoğrafı güncellendi.")
    }

    fun signOut() = runAction { repository.signOut() }

    private fun runAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            _action.value = _action.value.copy(loading = true, error = null, message = null)
            runCatching { block() }
                .onFailure { _action.value = _action.value.copy(loading = false, error = humanize(it)) }
            if (_action.value.loading) _action.value = _action.value.copy(loading = false)
        }
    }

    private fun validatePassword(password: String) {
        require(password.length >= 8) { "Şifre en az 8 karakter olmalı." }
        require(password.any(Char::isLetter) && password.any(Char::isDigit)) { "Şifre en az bir harf ve bir rakam içermeli." }
    }

    private fun humanize(t: Throwable): String = t.message?.takeIf { it.isNotBlank() } ?: "İşlem sırasında bir hata oluştu."
}
