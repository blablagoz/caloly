package com.caloly.app.presentation.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caloly.app.domain.auth.CalolyUser
import com.caloly.app.presentation.theme.*

@Composable
fun LoginScreen(
    state: AuthActionState,
    onPasswordLogin: (String, String) -> Unit,
    onOtp: (String) -> Unit,
    onGoogle: () -> Unit,
    onRegister: () -> Unit,
    onForgot: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AuthPage(title = "Caloly", subtitle = "Beslenmeni takip et, birlikte ilerle.") {
        CalolyField(email, { email = it }, "E-posta", KeyboardType.Email)
        CalolyField(password, { password = it }, "Şifre", password = true)
        Feedback(state)
        PrimaryButton("Giriş Yap", state.loading) { onPasswordLogin(email, password) }
        OutlinedButton(onClick = { onOtp(email) }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(50)) {
            Icon(Icons.Rounded.Email, null); Spacer(Modifier.width(8.dp)); Text("E-posta bağlantısıyla giriş")
        }
        OutlinedButton(onClick = onGoogle, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(50)) {
            Text("G  Google ile devam et", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onForgot) { Text("Şifremi unuttum") }
        TextButton(onClick = onRegister) { Text("Hesabın yok mu? Kayıt ol", color = CalolyLavender, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun RegisterScreen(state: AuthActionState, onRegister: (String,String,String,String)->Unit, onBack:()->Unit) {
    var name by remember { mutableStateOf("") }; var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    AuthPage("Hesap oluştur", "Caloly hesabınla beslenme ve aktivite verilerini sen yönetirsin.", onBack) {
        CalolyField(name,{name=it},"Ad Soyad")
        CalolyField(username,{username=it},"Kullanıcı adı")
        CalolyField(email,{email=it},"E-posta",KeyboardType.Email)
        CalolyField(password,{password=it},"Şifre (en az 8 karakter)",password=true)
        Feedback(state)
        PrimaryButton("Kayıt Ol",state.loading){ onRegister(email,password,name,username) }
    }
}

@Composable
fun OtpScreen(email:String,state:AuthActionState,onVerify:(String)->Unit,onResend:()->Unit,onBack:()->Unit){
    AuthPage("E-postanı kontrol et", "$email adresine bir doğrulama bağlantısı gönderdik.", onBack) {
        Icon(Icons.Rounded.Email, contentDescription = null, tint = CalolyLavender, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(18.dp))
        Text(
            "E-postadaki “Sign in” bağlantısına dokun. Caloly otomatik olarak açılacak ve hesabın doğrulanacak.",
            color = CalolyMuted,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(18.dp))
        Feedback(state)
        PrimaryButton("E-postayı tekrar gönder",state.loading){ onResend() }
        TextButton(onClick=onBack){Text("Giriş ekranına dön")}
    }
}

@Composable
fun ForgotPasswordScreen(state:AuthActionState,onSend:(String)->Unit,onBack:()->Unit){
    var email by remember { mutableStateOf("") }
    AuthPage("Şifremi unuttum","Şifre yenileme bağlantısını e-posta adresine göndereceğiz.",onBack){
        CalolyField(email,{email=it},"E-posta",KeyboardType.Email)
        Feedback(state); PrimaryButton("Bağlantıyı Gönder",state.loading){onSend(email)}
    }
}

@Composable
fun ChangePasswordScreen(state:AuthActionState,onChange:(String)->Unit,onBack:()->Unit){
    var p1 by remember { mutableStateOf("") }; var p2 by remember { mutableStateOf("") }
    AuthPage("Şifreyi değiştir","Yeni şifren en az 8 karakter olmalı.",onBack){
        CalolyField(p1,{p1=it},"Yeni şifre",password=true); CalolyField(p2,{p2=it},"Yeni şifre tekrar",password=true)
        Feedback(state); PrimaryButton("Şifreyi Kaydet",state.loading){ if(p1==p2) onChange(p1) }
    }
}

@Composable
fun AccountScreen(user:CalolyUser,state:AuthActionState,onSave:(String,String)->Unit,onAvatar:(ByteArray,String)->Unit,onChangePassword:()->Unit,onSignOut:()->Unit,onBack:()->Unit){
    var name by remember(user.displayName){ mutableStateOf(user.displayName.orEmpty()) }
    var username by remember(user.username){ mutableStateOf(user.username.orEmpty()) }
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val type = context.contentResolver.getType(uri) ?: "image/jpeg"
            context.contentResolver.openInputStream(uri)?.use { input -> onAvatar(input.readBytes(), type) }
        }
    }
    AuthPage("Hesabım", user.email ?: "Caloly hesabı",onBack){
        if (!user.avatarUrl.isNullOrBlank()) {
            AsyncImage(model=user.avatarUrl,contentDescription="Profil fotoğrafı",contentScale=ContentScale.Crop,modifier=Modifier.size(88.dp).clip(CircleShape))
        } else {
            Surface(shape=CircleShape,color=CalolyLavender){Box(Modifier.size(88.dp),contentAlignment=Alignment.Center){Text((user.displayName ?: user.username ?: "C").take(1).uppercase(),color=CalolyLavenderWhite,fontSize=30.sp,fontWeight=FontWeight.ExtraBold)}}
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick={photoPicker.launch("image/*")},shape=RoundedCornerShape(50)){Text("Profil fotoğrafını değiştir")}
        Spacer(Modifier.height(18.dp))
        CalolyField(name,{name=it},"Ad Soyad"); CalolyField(username,{username=it},"Kullanıcı adı")
        Feedback(state); PrimaryButton("Profili Kaydet",state.loading){onSave(name,username)}
        OutlinedButton(onClick=onChangePassword,modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(50)){Icon(Icons.Rounded.Lock,null);Spacer(Modifier.width(8.dp));Text("Şifremi değiştir")}
        TextButton(onClick=onSignOut){Text("Çıkış Yap",color=MaterialTheme.colorScheme.error)}
    }
}

@Composable
private fun AuthPage(title:String,subtitle:String,onBack:(()->Unit)?=null,content:@Composable ColumnScope.()->Unit){
    Scaffold(containerColor=CalolyBackground){ pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal=24.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Spacer(Modifier.height(22.dp))
            if(onBack!=null){Row(Modifier.fillMaxWidth()){IconButton(onClick=onBack){Icon(Icons.Rounded.ArrowBack,null)}}}
            Spacer(Modifier.height(24.dp)); Text(title,fontSize=32.sp,fontWeight=FontWeight.ExtraBold,color=CalolyLavender)
            Spacer(Modifier.height(8.dp)); Text(subtitle,color=CalolyMuted)
            Spacer(Modifier.height(30.dp)); content()
        }
    }
}

@Composable private fun CalolyField(value:String,onValue:(String)->Unit,label:String,type:KeyboardType=KeyboardType.Text,password:Boolean=false){
    OutlinedTextField(value,onValue,Modifier.fillMaxWidth(),label={Text(label)},singleLine=true,shape=RoundedCornerShape(22.dp),keyboardOptions=KeyboardOptions(keyboardType=if(password) KeyboardType.Password else type),visualTransformation=if(password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)
    Spacer(Modifier.height(12.dp))
}
@Composable private fun PrimaryButton(text:String,loading:Boolean,onClick:()->Unit){Button(onClick=onClick,enabled=!loading,modifier=Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(50),colors=ButtonDefaults.buttonColors(containerColor=CalolyGreen,contentColor=CalolyLavenderWhite)){if(loading) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp) else Text(text,fontWeight=FontWeight.ExtraBold)}}
@Composable private fun Feedback(state:AuthActionState){state.error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(bottom=10.dp))};state.message?.let{Text(it,color=CalolyGreenDark,modifier=Modifier.padding(bottom=10.dp))}}
