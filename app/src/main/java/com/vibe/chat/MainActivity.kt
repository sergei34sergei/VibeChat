
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

private val Purple = Color(0xFF9D4EDD)
private val Cyan = Color(0xFF48E5FF)
private val Dark = Color(0xFF090B1A)
private val Panel = Color(0xFF191C35)
private val PanelLight = Color(0xFF232746)
private val Pink = Color(0xFFFF4FA3)
private val Blue = Color(0xFF4169FF)

private val VibeGradient = Brush.linearGradient(
    listOf(Purple, Blue, Cyan)
)

private val VibeBackground = Brush.verticalGradient(
    listOf(Color(0xFF090B1A), Color(0xFF11152D), Color(0xFF101B35))
)

private val VibeFieldColors
    @Composable get() = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedContainerColor = Panel,
        unfocusedContainerColor = Panel,
        focusedBorderColor = Cyan,
        unfocusedBorderColor = Color(0xFF41466C),
        focusedLabelColor = Cyan,
        unfocusedLabelColor = Color.LightGray,
        focusedPlaceholderColor = Color.Gray,
        unfocusedPlaceholderColor = Color.Gray,
        cursorColor = Cyan
    )

@Composable
private fun VibeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 50.dp),
        shape = RoundedCornerShape(17.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Purple,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF343650),
            disabledContentColor = Color.LightGray
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun VibeAvatar(
    name: String,
    size: Int = 54,
    pink: Boolean = false
) {
    Box(
        Modifier
            .size(size.dp)
            .background(
                if (pink) Brush.linearGradient(listOf(Pink, Purple))
                else VibeGradient,
                CircleShape
            )
            .padding(2.dp)
            .background(Color(0xFF171A31), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.firstOrNull()?.uppercase() ?: "V",
            color = Color.White,
            fontSize = (size * 0.38f).sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun VibeSectionTitle(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp)
    ) {
        Text(
            title,
            color = Color.White,
            fontSize = 23.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = Color(0xFFAEB6D5), fontSize = 13.sp)
    }
}

class MainActivity : ComponentActivity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private var verificationId by mutableStateOf<String?>(null)
    private var status by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var signedIn by mutableStateOf(false)
    private var userPhone by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Firebase.initialize(context = this)
        Firebase.appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )

        signedIn = auth.currentUser != null
        userPhone = auth.currentUser?.phoneNumber.orEmpty()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Purple,
                    secondary = Cyan,
                    background = Dark,
                    surface = Panel,
                    onPrimary = Color.White,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Dark
                ) {
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
    }

    private fun sendCode(phone: String) {
        val number = phone.trim()
        if (number.isBlank() || !number.startsWith("+")) {
            status = "Введи номер с кодом страны, например +49123456789"
            return
        }

        busy = true
        status = "Отправляем код подтверждения..."

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
                                userPhone =
                                    auth.currentUser?.phoneNumber.orEmpty()
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

        auth.signInWithCredential(
            PhoneAuthProvider.getCredential(id, code)
        ).addOnCompleteListener(this) { task ->
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

    Box(
        Modifier.fillMaxSize()
            .background(VibeBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(Panel.copy(alpha = 0.96f), RoundedCornerShape(30.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Box(
                Modifier.size(82.dp)
                    .background(VibeGradient, RoundedCornerShape(27.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "V",
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Text(
                "VIBE CHAT",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "ТВОЙ МИР ОБЩЕНИЯ",
                color = Cyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                "Общайся. Находи друзей. Будь собой.",
                color = Color(0xFFB6BEDD),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(3.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Номер телефона") },
                placeholder = { Text("+49123456789") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(17.dp),
                colors = VibeFieldColors
            )

            VibeButton(
                text = if (busy) "Подожди..." else "Получить SMS-код",
                onClick = { onSendCode(phone) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = code,
                onValueChange = {
                    code = it.filter(Char::isDigit).take(6)
                },
                label = { Text("Код из SMS") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(17.dp),
                colors = VibeFieldColors
            )

            VibeButton(
                text = if (busy) "Проверяем..." else "Войти в Vibe Chat",
                onClick = { onVerifyCode(code) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            )

            if (busy) CircularProgressIndicator(color = Cyan)

            if (status.isNotBlank()) {
                Text(
                    status,
                    color = if (status.startsWith("Ошибка")) Pink else Cyan,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp
                )
            }

            Text(
                "Твоё общение. Твоя атмосфера. ✨",
                color = Color(0xFF969FBE),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
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
            registration = db.collection("users")
                .document(currentUser.uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        profileError =
                            "Ошибка профиля: ${error.localizedMessage}"
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

    Column(
        Modifier.fillMaxSize()
            .background(VibeBackground)
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth()
                .padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(45.dp)
                    .background(VibeGradient, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("V", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
            }

            Spacer(Modifier.width(11.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    "VIBE CHAT",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Text(
                    when (tab) {
                        "Чат" -> "Твои переписки"
                        "Общий чат" -> "Общение со всеми"
                        "Пользователи" -> "Найди собеседника"
                        "Алекс AI" -> "Твой AI-помощник"
                        else -> "Твой профиль"
                    },
                    color = Cyan,
                    fontSize = 12.sp
                )
            }

            TextButton(onClick = onLogout) {
                Text("Выйти", color = Pink, fontSize = 12.sp)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (privateChatUserId != null) {
                PrivateChatScreen(
                    otherUserId = privateChatUserId!!,
                    otherUserName = privateChatUserName,
                    onBack = { privateChatUserId = null }
                )
            } else {
                when (tab) {
                    "Чат" -> VibeChatsListScreen(
                        myId = currentUser?.uid.orEmpty(),
                        onOpenChat = { id, name ->
                            privateChatUserId = id
                            privateChatUserName = name
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
                        onOpenChat = { id, name ->
                            privateChatUserId = id
                            privateChatUserName = name
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

                                val task =
                                    if (profileLoaded && savedName.isNotBlank()) {
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
                                    profileError =
                                        "Не удалось сохранить: ${e.localizedMessage}"
                                }
                            } else {
                                profileError = "Введи имя."
                            }
                        },
                        error = profileError
                    )
                }
            }
        }

        if (privateChatUserId == null) {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = Color(0xFF101329),
                contentColor = Color.White
            ) {
                listOf("Чат", "Пользователи", "Алекс AI", "Профиль")
                    .forEach { item ->
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
                                    fontSize = 21.sp
                                )
                            },
                            label = {
                                Text(item, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Cyan,
                                selectedTextColor = Cyan,
                                indicatorColor = Color(0xFF37305D),
                                unselectedTextColor = Color(0xFF929AB9)
                            )
                        )
                    }
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
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(18.dp))

        Box(
            Modifier.fillMaxWidth()
                .background(Panel, RoundedCornerShape(28.dp))
                .padding(vertical = 26.dp, horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                VibeAvatar(savedName.ifBlank { "Vibe" }, 100)
                Spacer(Modifier.height(15.dp))
                Text(
                    "МОЙ ПРОФИЛЬ",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    savedName.ifBlank { "Добро пожаловать!" },
                    color = Cyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    phone.ifBlank { "Телефон не указан" },
                    color = Color(0xFFABB4D1),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Column(
            Modifier.fillMaxWidth()
                .background(Panel, RoundedCornerShape(22.dp))
                .padding(16.dp)
        ) {
            Text("ТВОИ ДАННЫЕ", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = nameInput,
                onValueChange = onNameChange,
                label = { Text("Твоё имя") },
                placeholder = { Text("Например, Алекс") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = VibeFieldColors
            )

            Spacer(Modifier.height(13.dp))
            VibeButton("Сохранить профиль", onSave, Modifier.fillMaxWidth())

            if (error.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    error,
                    color = if (error == "Имя сохранено!") Cyan else Pink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "Твой профиль — твоя индивидуальность ✨",
            color = Color(0xFF929AB9),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
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
        val registration = db.collection("users")
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

        onDispose { registration.remove() }
    }

    val visibleUsers = users.filter {
        it.id != myId && (
            it.name.contains(search.trim(), ignoreCase = true) ||
                it.phone.contains(search.trim(), ignoreCase = true)
            )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        VibeSectionTitle("Люди Vibe", "Найди друзей и начни общение")

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("🔎  Поиск по имени или номеру") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = VibeFieldColors
        )

        Spacer(Modifier.height(13.dp))

        if (error.isNotBlank()) {
            Text(error, color = Pink, fontSize = 12.sp)
        }

        when {
            loading -> Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Cyan)
            }

            visibleUsers.isEmpty() -> Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("👥", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (search.isBlank())
                            "Пока здесь никого нет.\nПригласи друга в Vibe Chat!"
                        else "Никого не нашли.",
                        color = Color(0xFFB1B9D4),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(15.dp))
                    VibeButton("Настроить профиль", onOpenProfile)
                }
            }

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(visibleUsers, key = { it.id }) { person ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(Panel, RoundedCornerShape(21.dp))
                            .clickable { onOpenChat(person.id, person.name) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VibeAvatar(person.name, 52)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                person.name,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                person.phone.ifBlank { "Пользователь VIBE" },
                                color = Color(0xFFABB4D1),
                                fontSize = 12.sp
                            )
                        }
                        Text("›", color = Cyan, fontSize = 30.sp)
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
        val previews = mutableMapOf<String, ConversationPreview>()
        val userLoads = mutableSetOf<String>()

        fun publish() {
            conversations = previews.values.sortedByDescending {
                it.lastAt?.toDate()?.time ?: 0L
            }
        }

        if (myId.isBlank()) {
            loading = false
            error = "Войди в аккаунт, чтобы увидеть переписки."
        } else {
            chatsRegistration = db.collection("chats")
                .whereArrayContains("participantIds", myId)
                .addSnapshotListener { snapshot, exception ->
                    loading = false

                    if (exception != null) {
                        error = "Не удалось загрузить переписки: ${exception.localizedMessage}"
                    } else if (snapshot != null) {
                        error = ""
                        val currentIds = snapshot.documents.map { it.id }.toSet()

                        messageRegistrations.keys.filter { it !in currentIds }
                            .toList().forEach { id ->
                                messageRegistrations.remove(id)?.remove()
                                previews.remove(id)
                                userLoads.remove(id)
                            }

                        snapshot.documents.forEach { chatDoc ->
                            val participants =
                                chatDoc.get("participantIds") as? List<*>
                                    ?: emptyList<Any>()

                            val otherId = participants.filterIsInstance<String>()
                                .firstOrNull { it != myId } ?: return@forEach

                            val chatId = chatDoc.id

                            if (chatId !in previews) {
                                previews[chatId] = ConversationPreview(
                                    chatId,
                                    otherId,
                                    "Пользователь",
                                    "Загрузка сообщений...",
                                    chatDoc.getTimestamp("createdAt")
                                )
                            }

                            if (userLoads.add(chatId)) {
                                db.collection("users").document(otherId).get()
                                    .addOnSuccessListener { userDoc ->
                                        val current = previews[chatId]
                                        if (current != null) {
                                            val name = userDoc.getString("name")
                                                ?.takeIf { it.isNotBlank() }
                                                ?: userDoc.getString("phone")
                                                    ?.takeIf { it.isNotBlank() }
                                                ?: "Пользователь"
                                            previews[chatId] = current.copy(otherUserName = name)
                                            publish()
                                        }
                                    }
                                    .addOnFailureListener { userLoads.remove(chatId) }
                            }

                            if (chatId !in messageRegistrations) {
                                messageRegistrations[chatId] =
                                    db.collection("chats").document(chatId)
                                        .collection("messages")
                                        .orderBy(
                                            "createdAt",
                                            com.google.firebase.firestore.Query.Direction.DESCENDING
                                        )
                                        .limit(1)
                                        .addSnapshotListener { messages, messageError ->
                                            if (messageError != null) {
                                                error = "Не удалось обновить переписки: ${messageError.localizedMessage}"
                                            } else {
                                                val latest = messages?.documents?.firstOrNull()
                                                val current = previews[chatId]
                                                if (current != null) {
                                                    previews[chatId] = current.copy(
                                                        lastText = latest?.getString("text")
                                                            ?: "Пока нет сообщений",
                                                        lastAt = latest?.getTimestamp("createdAt")
                                                            ?: current.lastAt
                                                    )
                                                    publish()
                                                }
                                            }
                                        }
                            }
                        }

                        previews.keys.filter { it !in currentIds }.toList()
                            .forEach { previews.remove(it) }
                        publish()
                    }
                }
        }

        onDispose {
            chatsRegistration?.remove()
            messageRegistrations.values.forEach { it.remove() }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        VibeSectionTitle("Твои сообщения", "Общайся с друзьями в любое время")

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onOpenGeneralChat,
                modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)
            ) {
                Text("🌐 Общий чат", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            VibeButton(
                text = "＋ Новая переписка",
                onClick = onOpenUsers,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        if (error.isNotBlank()) {
            Text(error, color = Pink, fontSize = 12.sp)
        }

        when {
            loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Cyan)
            }

            conversations.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(90.dp).background(
                            Brush.linearGradient(
                                listOf(Color(0xFF35205F), Color(0xFF183D63))
                            ),
                            CircleShape
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("💬", fontSize = 42.sp)
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Здесь будет твоё общение",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Начни новую переписку\nили загляни в общий чат.",
                        color = Color(0xFFABB4D1),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(17.dp))
                    VibeButton("Найти собеседника", onOpenUsers)
                }
            }

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(11.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(conversations, key = { it.chatId }) { item ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(Panel, RoundedCornerShape(22.dp))
                            .clickable {
                                onOpenChat(item.otherUserId, item.otherUserName)
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VibeAvatar(item.otherUserName, 54)
                        Spacer(Modifier.width(12.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                item.otherUserName,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                item.lastText,
                                color = Color(0xFFABB4D1),
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            item.lastAt?.let { time ->
                                Text(
                                    SimpleDateFormat("HH:mm", Locale.getDefault())
                                        .format(Date(time.toDate().time)),
                                    color = Cyan,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(Modifier.height(5.dp))
                            Text("›", color = Purple, fontSize = 26.sp)
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
                                doc.id,
                                doc.getString("authorId").orEmpty(),
                                doc.getString("author").orEmpty(),
                                doc.getString("text").orEmpty(),
                                doc.getTimestamp("createdAt")
                            )
                        }
                        error = ""
                    }
                }
        }

        onDispose { registration?.remove() }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .background(Panel, RoundedCornerShape(18.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🌐", fontSize = 25.sp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Общий чат", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "Ты: ${phone.ifBlank { "аккаунт" }}",
                    color = Color(0xFFABB4D1),
                    fontSize = 11.sp
                )
            }
        }

        if (error.isNotBlank()) {
            Text(error, color = Pink, fontSize = 12.sp)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val own = msg.authorId == user?.uid

                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = if (own) Alignment.End else Alignment.Start
                ) {
                    Text(
                        if (own) "Ты" else msg.author,
                        color = if (own) Cyan else Pink,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                    )

                    Column(
                        Modifier.widthIn(max = 300.dp)
                            .background(
                                if (own) Brush.linearGradient(listOf(Purple, Blue))
                                else Brush.linearGradient(listOf(PanelLight, Panel)),
                                RoundedCornerShape(
                                    topStart = 18.dp,
                                    topEnd = 18.dp,
                                    bottomStart = if (own) 18.dp else 5.dp,
                                    bottomEnd = if (own) 5.dp else 18.dp
                                )
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(msg.text, color = Color.White, fontSize = 15.sp)
                        msg.time?.let {
                            Text(
                                SimpleDateFormat("HH:mm", Locale.getDefault())
                                    .format(Date(it.toDate().time)),
                                color = Color(0xFFD0D5EA),
                                fontSize = 10.sp,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }

        Row(
            Modifier.padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { if (it.length <= 1000) messageText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Напиши сообщение...") },
                shape = RoundedCornerShape(18.dp),
                colors = VibeFieldColors
            )

            Spacer(Modifier.width(8.dp))

            VibeButton(
                text = if (sending) "…" else "➤",
                enabled = messageText.isNotBlank() && !sending && user != null,
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
                }
            )
        }
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
                    "Привет! Я Алекс AI ✨\n\nЯ твой помощник внутри Vibe Chat. Напиши вопрос — постараюсь помочь."
                )
            )
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF30205A), Color(0xFF192D55))
                    ),
                    RoundedCornerShape(23.dp)
                )
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(53.dp).background(VibeGradient, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("✨", fontSize = 26.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Алекс AI",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    if (isLoading) "Думаю над ответом..."
                    else "Твой AI-помощник",
                    color = Cyan,
                    fontSize = 12.sp
                )
            }
            Box(
                Modifier.size(9.dp).background(
                    if (isLoading) Pink else Color(0xFF5DF2B1),
                    CircleShape
                )
            )
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            items(messages) { message ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.fromUser)
                        Arrangement.End else Arrangement.Start
                ) {
                    Text(
                        message.text,
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.widthIn(max = 310.dp)
                            .background(
                                if (message.fromUser)
                                    Brush.linearGradient(listOf(Purple, Blue))
                                else
                                    Brush.linearGradient(listOf(PanelLight, Panel)),
                                RoundedCornerShape(19.dp)
                            )
                            .padding(horizontal = 15.dp, vertical = 12.dp)
                    )
                }
            }

            if (isLoading) {
                item {
                    CircularProgressIndicator(
                        Modifier.size(23.dp),
                        color = Cyan,
                        strokeWidth = 2.dp
                    )
                }
            }
        }

        if (error.isNotBlank()) {
            Text(
                error,
                color = Pink,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(1000) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Спроси Алекса...") },
                shape = RoundedCornerShape(18.dp),
                maxLines = 4,
                colors = VibeFieldColors
            )

            Spacer(Modifier.width(8.dp))

            VibeButton(
                text = if (isLoading) "…" else "➤",
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
                                val prompt =
                                    "Ты Алекс AI — дружелюбный помощник внутри Vibe Chat. " +
                                    "Отвечай по-русски, понятно и полезно. " +
                                    "Помогай писать промпты для фото и видео, " +
                                    "но не утверждай, что генерация изображений или видео " +
                                    "внутри приложения уже доступна, если она не подключена.\n\n" +
                                    "Сообщение пользователя: $question"

                                val response = model.generateContent(prompt)
                                val answer = response.text
                                    ?.takeIf { it.isNotBlank() }
                                    ?: "Gemini не вернул текст. Попробуй задать вопрос иначе."

                                messages = messages + AlexMessage(false, answer)
                            } catch (e: Exception) {
                                error =
                                    "Не удалось получить ответ: ${e.localizedMessage ?: "проверь интернет и настройки Firebase AI Logic"}"
                                messages = messages + AlexMessage(
                                    false,
                                    "Не получилось связаться с Gemini. Проверь интернет, Firebase AI Logic и App Check."
                                )
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                }
            )
        }

        Spacer(Modifier.height(5.dp))
        Text(
            "Ответы генерируются с помощью Gemini.",
            color = Color(0xFF929AB9),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
