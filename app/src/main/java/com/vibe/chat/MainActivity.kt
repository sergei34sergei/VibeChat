
package com.vibe.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private var verificationId by mutableStateOf<String?>(null)
    private var status by mutableStateOf("")
    private var busy by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        setContent {
            VibeLogin(
                status = status,
                busy = busy,
                onSendCode = { phone -> sendCode(phone) },
                onVerifyCode = { code -> verifyCode(code) }
            )
        }
    }

    private fun sendCode(phone: String) {
        if (phone.isBlank() || !phone.startsWith("+")) {
            status = "Введи номер с кодом страны, например +1..."
            return
        }

        busy = true
        status = "Проверяем номер..."

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone.trim())
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object :
                PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                override fun onVerificationCompleted(
                    credential: com.google.firebase.auth.PhoneAuthCredential
                ) {
                    auth.signInWithCredential(credential)
                        .addOnCompleteListener { task ->
                            busy = false
                            status = if (task.isSuccessful) {
                                "Успешный вход! Добро пожаловать в VIBE CHAT 💜"
                            } else {
                                "Не удалось войти: ${task.exception?.localizedMessage}"
                            }
                        }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    busy = false
                    status = "Ошибка: ${e.localizedMessage ?: "проверь номер и Firebase"}"
                }

                override fun onCodeSent(
                    id: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    verificationId = id
                    busy = false
                    status = "Код запрошен. Введи код из SMS или тестовый код Firebase."
                }
            })
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun verifyCode(code: String) {
        val id = verificationId

        if (id == null) {
            status = "Сначала запроси код."
            return
        }

        if (code.length != 6) {
            status = "Код должен содержать 6 цифр."
            return
        }

        busy = true
        status = "Проверяем код..."

        val credential = PhoneAuthProvider.getCredential(id, code)

        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                busy = false
                status = if (task.isSuccessful) {
                    "Ты вошёл в VIBE CHAT! 💜"
                } else {
                    "Ошибка входа: ${task.exception?.localizedMessage ?: "неверный код"}"
                }
            }
    }
}

@Composable
fun VibeLogin(
    status: String,
    busy: Boolean,
    onSendCode: (String) -> Unit,
    onVerifyCode: (String) -> Unit
) {
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    val background = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF100B25),
            Color(0xFF211044),
            Color(0xFF071B30)
        )
    )

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text("VIBE", color = Color(0xFFB99AFF),
                    fontSize = 48.sp, fontWeight = FontWeight.Black)
                Text(
                    "CHAT",
                    color = Color(0xFF62E9FF),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Твоё общение. Твоя атмосфера.",
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Номер с кодом страны") },
                    placeholder = { Text("+16505553434") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                Button(
                    onClick = { onSendCode(phone) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8A4DFF)
                    )
                ) {
                    Text("Получить код")
                }

                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it.filter(Char::isDigit).take(6)
                    },
                    label = { Text("Шестизначный код") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                Button(
                    onClick = { onVerifyCode(code) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00AFC8)
                    )
                ) {
                    Text("Войти", color = Color.White)
                }

                if (busy) {
                    CircularProgressIndicator(color = Color(0xFF62E9FF))
                }

                if (status.isNotBlank()) {
                    Text(
                        status,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                }

                Text(
                    "Безопасное общение начинается здесь",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
