
package com.vibe.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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

private val Purple = Color(0xFF9B6BFF)
private val Cyan = Color(0xFF62E9FF)
private val Dark = Color(0xFF100B25)
private val Panel = Color(0xFF21183B)

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    private var verificationId by mutableStateOf<String?>(null)
    private var status by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var signedIn by mutableStateOf(false)
    private var userPhone by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        signedIn = auth.currentUser != null
        userPhone = auth.currentUser?.phoneNumber.orEmpty()

        setContent {
            MaterialTheme {
                if (signedIn) {
                    VibeChats(
                        phone = userPhone,
                        onLogout = {
                            auth.signOut()
                            signedIn = false
                            userPhone = ""
                            verificationId = null
                            status = "Ты вышел из аккаунта."
                        }
                    )
                } else {
                    VibeLogin(
                        status = status,
                        busy = busy,
                        onSendCode = { sendCode(it) },
                        onVerifyCode = { verifyCode(it) }
                    )
                }
            }
        }
    }

    private fun sendCode(phone: String) {
        val number = phone.trim()

        if (number.isBlank() || !number.startsWith("+")) {
            status = "Введи номер с кодом страны, например +49123456789"
            return
        }

        busy = true
        status = "Проверяем номер..."

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(number)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object :
                PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                override fun onVerificationCompleted(
                    credential: com.google.firebase.auth.PhoneAuthCredential
                ) {
                    auth.signInWithCredential(credential)
                        .addOnCompleteListener(this@MainActivity) { task ->
                            busy = false
                            if (task.isSuccessful) {
                                userPhone = auth.currentUser?.phoneNumber.orEmpty()
                                signedIn = true
                                status = ""
                            } else {
                                status = "Не удалось войти: ${
                                    task.exception?.localizedMessage ?: "ошибка авторизации"
                                }"
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
                    status = "Код отправлен. Введи 6 цифр из SMS."
                }

                override fun onCodeAutoRetrievalTimeOut(id: String) {
                    verificationId = id
                    busy = false
                    status = "Введи код из SMS вручную."
                }
            })
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun verifyCode(code: String) {
        val id = verificationId

        if (id == null) {
            status = "Сначала нажми «Получить код»."
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
            .addOnCompleteListener(this) { task ->
                busy = false
                if (task.isSuccessful) {
                    userPhone = auth.currentUser?.phoneNumber.orEmpty()
                    signedIn = true
                    status = ""
                } else {
                    status = "Ошибка входа: ${
                        task.exception?.localizedMessage ?: "неверный код"
                    }"
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
        listOf(Dark, Color(0xFF211044), Color(0xFF071B30))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("VIBE", color = Color(0xFFB99AFF),
                fontSize = 48.sp, fontWeight = FontWeight.Black)
            Text("CHAT", color = Cyan,
                fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "Твоё общение. Твоя атмосфера.",
                color = Color.White,
                textAlign = TextAlign.Center
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Номер с кодом страны") },
                placeholder = { Text("+49123456789") },
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
                    containerColor = Purple
                )
            ) {
                Text("Получить код")
            }

            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6) },
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
                CircularProgressIndicator(color = Cyan)
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

private data class ChatPreview(
    val title: String,
    val subtitle: String,
    val emoji: String,
    val unread: Int = 0
)

@Composable
fun VibeChats(
    phone: String,
    onLogout: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selectedChat by remember { mutableStateOf<ChatPreview?>(null) }

    val chats = listOf(
        ChatPreview("VIBE команда", "Добро пожаловать в VIBE CHAT!", "💜", 2),
        ChatPreview("Друзья", "Создай свой первый разговор", "👋"),
        ChatPreview("Избранное", "Твои сохранённые сообщения", "⭐")
    )

    val background = Brush.verticalGradient(
        listOf(Dark, Color(0xFF11152F), Color(0xFF071B30))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        if (selectedChat == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "VIBE CHAT",
                            color = Cyan,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "Твоя атмосфера общения",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Panel, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("💜", fontSize = 23.sp)
                    }
                }

                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Поиск чатов") },
                    shape = RoundedCornerShape(18.dp)
                )

                Spacer(Modifier.height(22.dp))

                Text(
                    "ТВОИ ЧАТЫ",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(chats.filter {
                        it.title.contains(search, ignoreCase = true)
                    }) { chat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Panel.copy(alpha = 0.9f),
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { selectedChat = chat }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Purple, Color(0xFF00AFC8))
                                        ),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(chat.emoji, fontSize = 25.sp)
                            }

                            Spacer(Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    chat.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    chat.subtitle,
                                    color = Color.LightGray,
                                    fontSize = 13.sp
                                )
                            }

                            if (chat.unread > 0) {
                                Box(
                                    modifier = Modifier
                                        .background(Cyan, CircleShape)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "${chat.unread}",
                                        color = Dark,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    if (phone.isNotBlank()) "Вы вошли: $phone" else "Вы вошли в аккаунт",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )

                TextButton(
                    onClick = onLogout,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Выйти из аккаунта", color = Cyan)
                }

                Spacer(Modifier.height(8.dp))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(20.dp)
            ) {
                TextButton(onClick = { selectedChat = null }) {
                    Text("‹  Назад к чатам", color = Cyan, fontSize = 16.sp)
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    selectedChat!!.emoji,
                    fontSize = 42.sp
                )
                Text(
                    selectedChat!!.title,
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    selectedChat!!.subtitle,
                    color = Color.LightGray,
                    fontSize = 15.sp
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    "Это пока демонстрационный экран. Следующим шагом подключим отправку сообщений.",
                    color = Color.White,
                    fontSize = 15.sp
                )
            }
        }
    }
}
