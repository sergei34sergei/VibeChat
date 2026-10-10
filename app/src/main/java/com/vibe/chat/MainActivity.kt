package com.vibe.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.initialize
import com.google.firebase.appcheck.appCheck
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
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
import kotlinx.coroutines.launch

private val Purple = Color(0xFF9B6BFF)
private val Cyan = Color(0xFF62E9FF)
private val Dark = Color(0xFF100B25)
private val Panel = Color(0xFF21183B)

class MainActivity : ComponentActivity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private var verificationId by mutableStateOf<String?>(null)
    private var status by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var signedIn by mutableStateOf(false)
    private var userPhone by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Для разработки: App Check должен быть настроен до обращения к Firebase Auth/AI.
        // Отладочный провайдер нельзя использовать в опубликованной версии приложения.
        Firebase.initialize(context = this)
        // В этой тестовой сборке используется Debug App Check.
        // Не публикуй этот вариант в Play Market без настройки production App Check.
        Firebase.appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )

        signedIn = auth.currentUser != null
        userPhone = auth.currentUser?.phoneNumber.orEmpty()

        setContent {
            MaterialTheme {
                if (signedIn) {
                    VibeHome(
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
        unfocusedBorderColor = Purple
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
            if (status.isNotBlank()) Text(
                status, color = Color.White,
                textAlign = TextAlign.Center, fontSize = 14.sp
            )
            Text("Безопасное общение начинается здесь", color = Color.LightGray,
                fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

private data class VibeUser(
    val id: String,
    val name: String,
    val phone: String
)

private data class ChatMessage(
    val id: String,
    val authorId: String,
    val author: String,
    val text: String,
    val time: Timestamp?
)

@Composable
fun VibeHome(phone: String, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf("Чат") }
    var privateChatUserId by remember { mutableStateOf<String?>(null) }
    var privateChatUserName by remember { mutableStateOf("") }
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val currentUser = auth.currentUser

    var savedName by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var profileLoaded by remember { mutableStateOf(false) }
    var profileError by remember { mutableStateOf("") }

    DisposableEffect(currentUser?.uid) {
        var registration: ListenerRegistration? = null
        if (currentUser != null) {
            registration = db.collection("users").document(currentUser.uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        profileError = "Ошибка профиля: ${error.localizedMessage}"
                    } else if (snapshot != null) {
                        savedName = snapshot.getString("name").orEmpty()
                        if (!profileLoaded) nameInput = savedName
                        profileLoaded = true
                        profileError = ""
                    }
                }
        }
        onDispose { registration?.remove() }
    }

    val background = Brush.verticalGradient(
        listOf(Dark, Color(0xFF11152F), Color(0xFF071B30))
    )

    Column(Modifier.fillMaxSize().background(background).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("VIBE CHAT", color = Cyan, fontSize = 25.sp,
                    fontWeight = FontWeight.Black)
                Text(
                    when (tab) {
                        "Чат" -> "Твои переписки"
                        "Общий чат" -> "Общение со всеми"
                        "Пользователи" -> "Найди собеседника"
                        "Алекс AI" -> "Твой AI-помощник"
                        else -> "Твой профиль"
                    },
                    color = Color.LightGray, fontSize = 13.sp
                )
            }
            TextButton(onClick = onLogout) { Text("Выйти", color = Cyan) }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (privateChatUserId != null) {
                PrivateChatScreen(
                    otherUserId = privateChatUserId!!,
                    otherUserName = privateChatUserName,
                    onBack = { privateChatUserId = null }
                )
            } else when (tab) {
                "Чат" -> VibeChatsListScreen(
                    myId = currentUser?.uid.orEmpty(),
                    onOpenChat = { personId, personName ->
                        privateChatUserId = personId
                        privateChatUserName = personName
                    },
                    onOpenGeneralChat = { tab = "Общий чат" },
                    onOpenUsers = { tab = "Пользователи" }
                )
                "Общий чат" -> VibeGeneralChat(phone)
                "Пользователи" -> VibeUsers(
                    db = db,
                    myId = currentUser?.uid.orEmpty(),
                    myName = savedName,
                    onOpenProfile = { tab = "Профиль" },
                    onOpenChat = { personId, personName ->
                        privateChatUserId = personId
                        privateChatUserName = personName
                    }
                )
                "Алекс AI" -> AlexAiScreen()
                else -> VibeProfile(
                    phone = phone,
                    savedName = savedName,
                    nameInput = nameInput,
                    onNameChange = { nameInput = it.take(40) },
                    onSave = {
                        val uid = currentUser?.uid
                        val cleanName = nameInput.trim()
                        if (uid != null && cleanName.isNotBlank()) {
                            profileError = ""
                            val doc = db.collection("users").document(uid)
                            val task = if (profileLoaded && savedName.isNotBlank()) {
                                doc.update("name", cleanName)
                            } else {
                                doc.set(
                                    hashMapOf(
                                        "name" to cleanName,
                                        "phone" to (currentUser.phoneNumber ?: ""),
                                        "createdAt" to FieldValue.serverTimestamp()
                                    )
                                )
                            }
                            task.addOnSuccessListener {
                                savedName = cleanName
                                profileError = "Имя сохранено!"
                            }.addOnFailureListener { e ->
                                profileError = "Не удалось сохранить: ${e.localizedMessage}"
                            }
                        } else {
                            profileError = "Введи имя."
                        }
                    },
                    error = profileError
                )
            }
        }

        if (privateChatUserId == null) NavigationBar(containerColor = Color(0xFF17112A)) {
            listOf("Чат", "Пользователи", "Алекс AI", "Профиль").forEach { item ->
                NavigationBarItem(
                    selected = tab == item,
                    onClick = { tab = item },
                    icon = {
                        Text(
                            when (item) {
                                "Чат" -> "💬"
                                "Пользователи" -> "👥"
                                "Алекс AI" -> "✨"
                                else -> "👤"
                            },
                            fontSize = 20.sp
                        )
                    },
                    label = { Text(item, fontSize = 10.sp) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Cyan,
                        selectedTextColor = Cyan,
                        indicatorColor = Color(0xFF493078),
                        unselectedTextColor = Color.LightGray
                    )
                )
            }
        }
    }
}

@Composable
fun VibeProfile(
    phone: String,
    savedName: String,
    nameInput: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    error: String
) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.size(100.dp).background(
                Brush.linearGradient(listOf(Purple, Cyan)),
                RoundedCornerShape(50.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                savedName.firstOrNull()?.uppercase() ?: "V",
                color = Dark, fontSize = 42.sp, fontWeight = FontWeight.Black
            )
        }
        Spacer(Modifier.height(14.dp))
        Text("МОЙ ПРОФИЛЬ", color = Cyan, fontSize = 23.sp,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(phone.ifBlank { "Телефон не указан" },
            color = Color.LightGray, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = nameInput,
            onValueChange = onNameChange,
            label = { Text("Твоё имя") },
            placeholder = { Text("Например, Алекс") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Cyan,
                unfocusedBorderColor = Purple,
                focusedLabelColor = Cyan,
                unfocusedLabelColor = Color.LightGray
            )
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Purple)
        ) { Text("Сохранить профиль") }
        if (error.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                error,
                color = if (error == "Имя сохранено!") Cyan else Color(0xFFFFA6A6),
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Позже добавим фотографию и статус.",
            color = Color.LightGray, fontSize = 12.sp
        )
    }
}

@Composable
fun VibeUsers(
    db: FirebaseFirestore,
    myId: String,
    myName: String,
    onOpenProfile: () -> Unit,
    onOpenChat: (String, String) -> Unit
) {
    var users by remember { mutableStateOf<List<VibeUser>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    DisposableEffect(myId) {
        var registration: ListenerRegistration? = null
        registration = db.collection("users")
            .addSnapshotListener { snapshot, exception ->
                loading = false
                if (exception != null) {
                    error = "Не удалось загрузить пользователей: ${exception.localizedMessage}"
                } else if (snapshot != null) {
                    users = snapshot.documents.mapNotNull { doc ->
                        val name = doc.getString("name")?.trim().orEmpty()
                        if (name.isBlank()) null
                        else VibeUser(
                            id = doc.id,
                            name = name,
                            phone = doc.getString("phone").orEmpty()
                        )
                    }.sortedBy { it.name.lowercase(Locale.getDefault()) }
                    error = ""
                }
            }
        onDispose { registration?.remove() }
    }

    val visibleUsers = users.filter {
        it.id != myId &&
            (it.name.contains(search.trim(), ignoreCase = true) ||
                it.phone.contains(search.trim(), ignoreCase = true))
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Поиск по имени...") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Cyan,
                unfocusedBorderColor = Purple,
                focusedPlaceholderColor = Color.LightGray,
                unfocusedPlaceholderColor = Color.LightGray
            )
        )
        Spacer(Modifier.height(12.dp))
        if (error.isNotBlank()) {
            Text(error, color = Color(0xFFFFA6A6), fontSize = 12.sp)
        }
        when {
            loading -> Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Cyan) }
            visibleUsers.isEmpty() -> Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("👥", fontSize = 42.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (search.isBlank())
                            "Пока нет других пользователей.\nПопроси друга зарегистрироваться."
                        else "Никого не нашли.",
                        color = Color.LightGray, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onOpenProfile) {
                        Text("Настроить мой профиль")
                    }
                }
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(visibleUsers, key = { it.id }) { person ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(Panel, RoundedCornerShape(16.dp))
                            .clickable { onOpenChat(person.id, person.name) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(48.dp)
                                .background(
                                    Brush.linearGradient(listOf(Purple, Cyan)),
                                    RoundedCornerShape(24.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                person.name.firstOrNull()?.uppercase() ?: "?",
                                color = Dark, fontSize = 21.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(person.name, color = Color.White,
                                fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                person.phone.ifBlank { "Пользователь VIBE" },
                                color = Color.LightGray, fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ConversationPreview(
    val chatId: String,
    val otherUserId: String,
    val otherUserName: String,
    val lastText: String,
    val lastAt: Timestamp?
)

@Composable
fun VibeChatsListScreen(
    myId: String,
    onOpenChat: (String, String) -> Unit,
    onOpenGeneralChat: () -> Unit,
    onOpenUsers: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var conversations by remember { mutableStateOf<List<ConversationPreview>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    DisposableEffect(myId) {
        var chatsRegistration: ListenerRegistration? = null
        val messageRegistrations = mutableMapOf<String, ListenerRegistration>()

        if (myId.isNotBlank()) {
            chatsRegistration = db.collection("chats")
                .whereArrayContains("participantIds", myId)
                .addSnapshotListener { snapshot, exception ->
                    loading = false
                    if (exception != null) {
                        error = "Не удалось загрузить переписки: ${exception.localizedMessage}"
                    } else if (snapshot != null) {
                        error = ""
                        val currentIds = snapshot.documents.map { it.id }.toSet()
                        messageRegistrations.keys.filter { it !in currentIds }.forEach { staleId ->
                            messageRegistrations.remove(staleId)?.remove()
                        }
                        val existingIds = conversations.map { it.chatId }.toSet()
                        snapshot.documents.forEach { chatDoc ->
                            val participants = chatDoc.get("participantIds") as? List<*> ?: emptyList<Any>()
                            val otherId = participants.filterIsInstance<String>().firstOrNull { it != myId }
                                ?: return@forEach
                            val chatId = chatDoc.id
                            if (chatId !in existingIds) {
                                conversations = conversations + ConversationPreview(
                                    chatId = chatId,
                                    otherUserId = otherId,
                                    otherUserName = "Пользователь",
                                    lastText = "Откройте переписку",
                                    lastAt = chatDoc.getTimestamp("createdAt")
                                )
                                db.collection("users").document(otherId).get()
                                    .addOnSuccessListener { userDoc ->
                                        val name = userDoc.getString("name")?.takeIf { it.isNotBlank() }
                                            ?: userDoc.getString("phone")?.takeIf { it.isNotBlank() }
                                            ?: "Пользователь"
                                        conversations = conversations.map { item ->
                                            if (item.chatId == chatId) item.copy(otherUserName = name) else item
                                        }
                                    }
                            }
                            if (!messageRegistrations.containsKey(chatId)) {
                                messageRegistrations[chatId] = db.collection("chats").document(chatId)
                                    .collection("messages")
                                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                                    .limit(1)
                                    .addSnapshotListener { messages, messageError ->
                                        if (messageError != null) {
                                            error = "Не удалось обновить переписки: ${messageError.localizedMessage}"
                                        } else {
                                            val latest = messages?.documents?.firstOrNull()
                                            conversations = conversations.map { item ->
                                                if (item.chatId == chatId) item.copy(
                                                    lastText = latest?.getString("text") ?: "Пока нет сообщений",
                                                    lastAt = latest?.getTimestamp("createdAt") ?: item.lastAt
                                                ) else item
                                            }.sortedByDescending { it.lastAt?.toDate()?.time ?: 0L }
                                        }
                                    }
                            }
                        }
                        conversations = conversations.filter { it.chatId in currentIds }
                            .sortedByDescending { it.lastAt?.toDate()?.time ?: 0L }
                    }
                }
        } else {
            loading = false
            error = "Войди в аккаунт, чтобы увидеть переписки."
        }
        onDispose {
            chatsRegistration?.remove()
            messageRegistrations.values.forEach { it.remove() }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenGeneralChat, modifier = Modifier.weight(1f)) {
                Text("🌐 Общий чат")
            }
            Button(onClick = onOpenUsers, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Purple)) {
                Text("＋ Новая переписка")
            }
        }
        Spacer(Modifier.height(12.dp))
        if (error.isNotBlank()) Text(error, color = Color(0xFFFFA6A6), fontSize = 12.sp)
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Cyan)
            }
            conversations.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💬", fontSize = 46.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Пока нет личных переписок", color = Color.White,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text("Нажми «Новая переписка» и выбери пользователя.",
                        color = Color.LightGray, textAlign = TextAlign.Center)
                }
            }
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(9.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(conversations, key = { it.chatId }) { item ->
                    Row(
                        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp))
                            .clickable { onOpenChat(item.otherUserId, item.otherUserName) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(50.dp).background(
                            Brush.linearGradient(listOf(Purple, Cyan)), RoundedCornerShape(25.dp)),
                            contentAlignment = Alignment.Center) {
                            Text(item.otherUserName.firstOrNull()?.uppercase() ?: "V",
                                color = Dark, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.otherUserName, color = Color.White, fontSize = 16.sp,
                                fontWeight = FontWeight.Bold)
                            Text(item.lastText, color = Color.LightGray, fontSize = 13.sp,
                                maxLines = 1)
                        }
                        item.lastAt?.let { time ->
                            Text(SimpleDateFormat("HH:mm", Locale.getDefault())
                                .format(Date(time.toDate().time)), color = Cyan, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VibeGeneralChat(phone: String) {
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
            registration = db.collection("chats").document("general")
                .collection("messages").orderBy("createdAt")
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

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Вошёл: ${phone.ifBlank { "аккаунт" }}",
            color = Color.LightGray, fontSize = 11.sp)
        if (error.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(error, color = Color(0xFFFFA6A6), fontSize = 12.sp)
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
                    Text(if (own) "Ты" else msg.author,
                        color = Cyan, fontSize = 11.sp)
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
                                color = Color.LightGray, fontSize = 10.sp,
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
                    val current = auth.currentUser
                    if (text.isNotBlank() && current != null && !sending) {
                        sending = true
                        db.collection("chats").document("general")
                            .collection("messages")
                            .add(
                                hashMapOf(
                                    "text" to text,
                                    "authorId" to current.uid,
                                    "author" to (current.phoneNumber ?: "Пользователь"),
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
            ) { Text(if (sending) "…" else "➤") }
        }
        Spacer(Modifier.height(6.dp))
    }
}

private data class AlexMessage(val fromUser: Boolean, val text: String)

@Composable
fun AlexAiScreen() {
    var input by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val model = remember {
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel("gemini-3.8-flash")
    }
    var messages by remember {
        mutableStateOf(
            listOf(
                AlexMessage(
                    false,
                    "Привет, брат! Я Алекс AI ✨\n\nЯ подключаюсь к Gemini. Напиши вопрос — постараюсь помочь."
                )
            )
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(46.dp).background(
                    Brush.linearGradient(listOf(Purple, Cyan)), RoundedCornerShape(23.dp)
                ),
                contentAlignment = Alignment.Center
            ) { Text("✨", fontSize = 24.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Алекс AI", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(if (isLoading) "Gemini думает..." else "Gemini AI • облачный режим",
                    color = Color.LightGray, fontSize = 11.sp)
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            items(messages) { message ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
                ) {
                    Text(
                        message.text, color = Color.White, fontSize = 14.sp,
                        modifier = Modifier.widthIn(max = 310.dp).background(
                            if (message.fromUser) Color(0xFF493078) else Panel,
                            RoundedCornerShape(18.dp)
                        ).padding(horizontal = 14.dp, vertical = 11.dp)
                    )
                }
            }
            if (isLoading) item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Cyan, strokeWidth = 2.dp)
                }
            }
        }

        if (error.isNotBlank()) Text(
            error, color = Color(0xFFFFA6A6), fontSize = 12.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it.take(1000) },
                modifier = Modifier.weight(1f), placeholder = { Text("Спроси Алекса...") },
                shape = RoundedCornerShape(18.dp), maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Cyan, unfocusedBorderColor = Purple,
                    focusedPlaceholderColor = Color.LightGray, unfocusedPlaceholderColor = Color.LightGray
                )
            )
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = input.isNotBlank() && !isLoading,
                onClick = {
                    val question = input.trim()
                    if (question.isNotBlank() && !isLoading) {
                        messages = messages + AlexMessage(true, question)
                        input = ""
                        error = ""
                        isLoading = true
                        scope.launch {
                            try {
                                val prompt = "Ты Алекс AI — дружелюбный помощник внутри Vibe Chat. " +
                                    "Отвечай по-русски, понятно и полезно. Помогай писать промпты для фото и видео, " +
                                    "но не утверждай, что генерация изображений или видео внутри приложения уже доступна, " +
                                    "если она не подключена.\n\nСообщение пользователя: $question"
                                val response = model.generateContent(prompt)
                                val answer = response.text?.takeIf { it.isNotBlank() }
                                    ?: "Gemini не вернул текст. Попробуй задать вопрос иначе."
                                messages = messages + AlexMessage(false, answer)
                            } catch (e: Exception) {
                                error = "Не удалось получить ответ: ${e.localizedMessage ?: "проверь интернет и настройки Firebase AI Logic"}"
                                messages = messages + AlexMessage(
                                    false,
                                    "Не получилось связаться с Gemini. Проверь интернет, настройку Firebase AI Logic и App Check."
                                )
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Purple),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) { Text(if (isLoading) "…" else "➤") }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Сообщения отправляются в Gemini для генерации ответов.",
            color = Color.LightGray, fontSize = 10.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
