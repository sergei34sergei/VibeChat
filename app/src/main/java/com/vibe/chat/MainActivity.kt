
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
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
                                    task.exception?.localizedMessage ?: "ошибка"
                                }"
                            }
                        }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    busy = false
                    status = "Ошибка: ${e.localizedMessage ?: "проверь номер"}"
                }

                override fun onCodeSent(
                    id: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    verificationId = id
                    busy = false
                    status = "Код отправлен. Введи 6 цифр."
                }

                override fun onCodeAutoRetrievalTimeOut(id: String) {
                    verificationId = id
                    busy = false
                    status = "Введи код вручную."
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
        auth.signInWithCredential(PhoneAuthProvider.getCredential(id, code))
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
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = Cyan,
        unfocusedLabelColor = Color.LightGray,
        focusedBorderColor = Cyan,
        unfocusedBorderColor = Purple,
        focusedPlaceholderColor = Color.LightGray,
        unfocusedPlaceholderColor = Color.LightGray
    )

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Dark, Color(0xFF211044), Color(0xFF071B30)))
        ).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("VIBE", color = Color(0xFFB99AFF), fontSize = 48.sp,
                fontWeight = FontWeight.Black)
            Text("CHAT", color = Cyan, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Твоё общение. Твоя атмосфера.", color = Color.White,
                textAlign = TextAlign.Center)

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Номер с кодом страны") },
                placeholder = { Text("+49123456789") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors
            )

            Button(
                onClick = { onSendCode(phone) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Purple)
            ) { Text("Получить код") }

            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                label = { Text("Шестизначный код") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors
            )

            Button(
                onClick = { onVerifyCode(code) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00AFC8))
            ) { Text("Войти", color = Color.White) }

            if (busy) CircularProgressIndicator(color = Cyan)
            if (status.isNotBlank()) Text(status, color = Color.White,
                textAlign = TextAlign.Center, fontSize = 14.sp)
            Text("Безопасное общение начинается здесь", color = Color.LightGray,
                fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

private data class ChatMessage(
    val id: String,
    val authorId: String,
    val author: String,
    val text: String,
    val time: Timestamp?
)

@Composable
fun VibeChats(phone: String, onLogout: () -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val user = auth.currentUser
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var messageText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    DisposableEffect(user?.uid) {
        var registration: ListenerRegistration? = null
        if (user != null) {
            registration = db.collection("chats")
                .document("general")
                .collection("messages")
                .orderBy("createdAt")
                .addSnapshotListener { snapshot, exception ->
                    if (exception != null) {
                        error = "Не удалось загрузить сообщения: ${exception.localizedMessage}"
                    } else if (snapshot != null) {
                        messages = snapshot.documents.map { doc ->
                            ChatMessage(
                                id = doc.id,
                                authorId = doc.getString("authorId").orEmpty(),
                                author = doc.getString("author").orEmpty(),
                                text = doc.getString("text").orEmpty(),
                                time = doc.getTimestamp("createdAt")
                            )
                        }
                        error = ""
                    }
                }
        }
        onDispose { registration?.remove() }
    }

    val background = Brush.verticalGradient(
        listOf(Dark, Color(0xFF11152F), Color(0xFF071B30))
    )

    Column(
        Modifier.fillMaxSize().background(background).statusBarsPadding().padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("VIBE CHAT", color = Cyan, fontSize = 26.sp,
                    fontWeight = FontWeight.Black)
                Text("Общий чат", color = Color.LightGray, fontSize = 14.sp)
            }
            TextButton(onClick = onLogout) {
                Text("Выйти", color = Cyan)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Вошёл: ${phone.ifBlank { "аккаунт" }}",
            color = Color.LightGray,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(12.dp))

        if (error.isNotBlank()) {
            Text(error, color = Color(0xFFFFA6A6), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val own = msg.authorId == user?.uid
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = if (own) Alignment.End else Alignment.Start
                ) {
                    Text(
                        if (own) "Ты" else msg.author,
                        color = Cyan,
                        fontSize = 11.sp
                    )
                    Column(
                        Modifier.widthIn(max = 300.dp)
                            .background(
                                if (own) Color(0xFF493078) else Panel,
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(msg.text, color = Color.White, fontSize = 15.sp)
                        msg.time?.let {
                            Text(
                                SimpleDateFormat("HH:mm", Locale.getDefault())
                                    .format(Date(it.toDate().time)),
                                color = Color.LightGray,
                                fontSize = 10.sp,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { if (it.length <= 1000) messageText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Напиши сообщение...") },
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Cyan,
                    unfocusedBorderColor = Purple,
                    focusedPlaceholderColor = Color.LightGray,
                    unfocusedPlaceholderColor = Color.LightGray
                )
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val text = messageText.trim()
                    val currentUser = auth.currentUser
                    if (text.isNotBlank() && currentUser != null && !sending) {
                        sending = true
                        val author = currentUser.phoneNumber ?: "Пользователь"
                        db.collection("chats").document("general")
                            .collection("messages")
                            .add(
                                hashMapOf(
                                    "text" to text,
                                    "authorId" to currentUser.uid,
                                    "author" to author,
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                            )
                            .addOnSuccessListener {
                                messageText = ""
                                sending = false
                                error = ""
                            }
                            .addOnFailureListener { e ->
                                sending = false
                                error = "Не удалось отправить: ${e.localizedMessage}"
                            }
                    }
                },
                enabled = messageText.isNotBlank() && !sending && user != null,
                colors = ButtonDefaults.buttonColors(containerColor = Purple)
            ) {
                Text(if (sending) "…" else "➤")
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}
