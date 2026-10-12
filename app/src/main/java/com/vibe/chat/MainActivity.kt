package com.vibe.chat

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Base64
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseApp
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.FirebaseException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private val VibeBg = Color(0xFFFAFAF9)
private val VibePanel = Color(0xFFFFFFFF)
private val VibePanel2 = Color(0xFFF4F6F4)
private val VibeCoral = Color(0xFFE85B58)
private val VibeMint = Color(0xFF75BDA7)
private val VibeText = Color(0xFF252B2A)
private val VibeMuted = Color(0xFF7A8581)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = VibeCoral,
                    onPrimary = Color.White,
                    secondary = VibeMint,
                    onSecondary = Color.White,
                    background = VibeBg,
                    onBackground = VibeText,
                    surface = VibePanel,
                    onSurface = VibeText,
                    surfaceVariant = VibePanel2,
                    onSurfaceVariant = VibeMuted
                )
            ) {
                VibeRoot()
            }
        }
    }
}

@Composable
private fun VibeRoot() {
    val auth = remember { FirebaseAuth.getInstance() }
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener {
            currentUser = it.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    Surface(Modifier.fillMaxSize(), color = VibeBg) {
        val user = currentUser
        if (user == null) {
            VibeLogin()
        } else {
            VibeHome(
                userId = user.uid,
                phone = user.phoneNumber.orEmpty(),
                onSignOut = { auth.signOut() }
            )
        }
    }
}

@Composable
private fun VibeLogin() {
    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current
    val activity = context as? Activity

    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember {
        mutableStateOf("Войди по номеру телефона — общение уже рядом")
    }

    val callbacks = remember {
        object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(
                credential: PhoneAuthCredential
            ) {
                auth.signInWithCredential(credential)
                    .addOnCompleteListener { task ->
                        loading = false
                        if (!task.isSuccessful) {
                            message = task.exception?.localizedMessage
                                ?: "Не удалось выполнить вход"
                        }
                    }
            }

            override fun onVerificationFailed(exception: FirebaseException) {
                loading = false
                message = exception.localizedMessage
                    ?: "Не удалось отправить SMS. Проверь настройки Firebase."
            }

            override fun onCodeSent(
                id: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                loading = false
                verificationId = id
                message = "Код отправлен. Введи код из SMS."
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF5F3))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(VibeCoral),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.SmartToy,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            "Vibe Chat",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = VibeText
        )
        Text("Твой мир общения", color = VibeMint)

        Spacer(Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = VibePanel)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    if (verificationId == null) "Добро пожаловать"
                    else "Подтверди номер",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))
                Text(message, color = VibeMuted, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Номер с кодом страны") },
                    placeholder = { Text("+49…") },
                    singleLine = true,
                    enabled = verificationId == null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone
                    )
                )

                if (verificationId != null) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = {
                            code = it.filter(Char::isDigit).take(6)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Код из SMS") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        )
                    )
                }

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = {
                        if (activity == null) {
                            message = "Не удалось открыть авторизацию"
                        } else if (verificationId == null) {
                            val normalizedPhone = phone.trim()
                            if (!normalizedPhone.startsWith("+") ||
                                normalizedPhone.length < 8
                            ) {
                                message =
                                    "Введи номер в международном формате, например +49…"
                            } else {
                                loading = true
                                message = "Отправляем код…"
                                val options = PhoneAuthOptions.newBuilder(auth)
                                    .setPhoneNumber(normalizedPhone)
                                    .setTimeout(60L, TimeUnit.SECONDS)
                                    .setActivity(activity)
                                    .setCallbacks(callbacks)
                                    .build()
                                PhoneAuthProvider.verifyPhoneNumber(options)
                            }
                        } else {
                            val id = verificationId
                            if (id == null || code.length != 6) {
                                message = "Введи шестизначный код из SMS"
                            } else {
                                loading = true
                                val credential =
                                    PhoneAuthProvider.getCredential(id, code)
                                auth.signInWithCredential(credential)
                                    .addOnCompleteListener { task ->
                                        loading = false
                                        if (!task.isSuccessful) {
                                            message =
                                                task.exception?.localizedMessage
                                                    ?: "Ошибка входа"
                                        }
                                    }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VibeCoral
                    )
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            if (verificationId == null) "Получить код"
                            else "Войти"
                        )
                    }
                }

                if (verificationId != null) {
                    TextButton(
                        onClick = {
                            verificationId = null
                            code = ""
                            message = "Проверь номер и запроси код ещё раз"
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Изменить номер", color = VibeMint)
                    }
                }
            }
        }
    }
}

private enum class VibeTab(val label: String) {
    Chats("Чаты"),
    People("Люди"),
    Alex("Алекс"),
    Profile("Профиль")
}

private data class VibeUser(
    val id: String,
    val name: String,
    val phone: String,
    val bio: String = "",
    val avatarBase64: String = ""
)

@Composable
private fun VibeHome(
    userId: String,
    phone: String,
    onSignOut: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var tab by remember { mutableStateOf(VibeTab.Chats) }
    var openedChat by remember { mutableStateOf<VibeUser?>(null) }
    var showGeneralChat by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showVoice by remember { mutableStateOf(false) }

    when {
        showHistory -> VibeCreationHistoryScreen {
            showHistory = false
        }

        showSettings -> VibeSettingsScreen(
            onBack = { showSettings = false },
            onHistory = { showHistory = true }
        )

        showVoice -> AlexVoiceScreen(
            onOpenChat = { showVoice = false },
            onBack = { showVoice = false }
        )

        openedChat != null -> {
            val target = openedChat!!
            PrivateChatScreen(
                otherUserId = target.id,
                otherUserName = target.name,
                onBack = { openedChat = null }
            )
        }

        showGeneralChat -> VibeGeneralChatScreen(
            userId = userId,
            phone = phone,
            onBack = { showGeneralChat = false }
        )

        else -> Column(
            Modifier
                .fillMaxSize()
                .background(VibeBg)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(43.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(VibeCoral),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Forum,
                        contentDescription = null,
                        tint = Color.White
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        "Vibe Chat",
                        fontSize = 23.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = VibeText
                    )
                    Text(
                        when (tab) {
                            VibeTab.Chats -> "Твои разговоры"
                            VibeTab.People -> "Найди своих людей"
                            VibeTab.Alex -> "Твой AI-помощник"
                            VibeTab.Profile -> "Твой аккаунт"
                        },
                        color = VibeMuted,
                        fontSize = 12.sp
                    )
                }

                IconButton(onClick = { showSettings = true }) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Настройки",
                        tint = VibeMint
                    )
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    VibeTab.Chats -> VibeChatsScreen(
                        userId = userId,
                        onOpenChat = { openedChat = it },
                        onGeneralChat = { showGeneralChat = true },
                        onPeople = { tab = VibeTab.People }
                    )

                    VibeTab.People -> VibePeopleScreen(
                        myId = userId,
                        onOpenChat = { openedChat = it }
                    )

                    VibeTab.Alex -> AlexTextScreen(
                        onVoice = { showVoice = true }
                    )

                    VibeTab.Profile -> VibeProfileScreen(
                        userId = userId,
                        phone = phone,
                        onSettings = { showSettings = true },
                        onHistory = { showHistory = true },
                        onSignOut = onSignOut
                    )
                }
            }

            NavigationBar(
                containerColor = Color.White
            ) {
                VibeTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            Icon(
                                when (item) {
                                    VibeTab.Chats -> Icons.Default.ChatBubble
                                    VibeTab.People -> Icons.Default.People
                                    VibeTab.Alex -> Icons.Default.AutoAwesome
                                    VibeTab.Profile -> Icons.Default.Person
                                },
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = VibeCoral,
                            selectedTextColor = VibeCoral,
                            indicatorColor = Color(0xFFFFE7E4),
                            unselectedIconColor = VibeMuted,
                            unselectedTextColor = VibeMuted
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun VibeChatsScreen(
    userId: String,
    onOpenChat: (VibeUser) -> Unit,
    onGeneralChat: () -> Unit,
    onPeople: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var chats by remember { mutableStateOf<List<VibeUser>>(emptyList()) }
    var users by remember { mutableStateOf<Map<String, VibeUser>>(emptyMap()) }

    DisposableEffect(userId) {
        val userListener = db.collection("users")
            .addSnapshotListener { snapshot, _ ->
                users = snapshot?.documents?.associate { doc ->
                    doc.id to VibeUser(
                        id = doc.id,
                        name = doc.getString("name") ?: "Пользователь",
                        phone = doc.getString("phone").orEmpty(),
                        bio = doc.getString("bio").orEmpty(),
                        avatarBase64 = doc.getString("avatarBase64").orEmpty()
                    )
                }.orEmpty()
            }

        val chatListener = db.collection("chats")
            .whereArrayContains("participantIds", userId)
            .addSnapshotListener { snapshot, _ ->
                chats = snapshot?.documents
                    ?.filter { it.id != "general" }
                    ?.mapNotNull { doc ->
                        val otherId = (doc.get("participantIds") as? List<*>)
                            ?.filterIsInstance<String>()
                            ?.firstOrNull { it != userId }
                        otherId?.let {
                            VibeUser(it, "Пользователь", "")
                        }
                    }.orEmpty()
            }

        onDispose {
            userListener.remove()
            chatListener.remove()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        VibeSectionTitle("Сообщения", "Общайся без границ")

        VibeActionCard(
            icon = Icons.Default.Public,
            title = "Общий чат",
            subtitle = "Общение со всеми пользователями",
            onClick = onGeneralChat
        )

        Spacer(Modifier.height(9.dp))

        VibeActionCard(
            icon = Icons.Default.PersonSearch,
            title = "Найти людей",
            subtitle = "Посмотри пользователей Vibe Chat",
            onClick = onPeople
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "Личные разговоры",
            fontWeight = FontWeight.Bold,
            color = VibeText
        )
        Spacer(Modifier.height(8.dp))

        val resolved = chats.map { chat ->
            users[chat.id] ?: chat
        }.distinctBy { it.id }

        if (resolved.isEmpty()) {
            VibeEmptyState(
                Icons.Default.ChatBubbleOutline,
                "Здесь пока пусто",
                "Найди человека во вкладке «Люди», чтобы начать разговор."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(resolved, key = { it.id }) { user ->
                    VibeUserRow(user) { onOpenChat(user) }
                }
            }
        }
    }
}

@Composable
private fun VibePeopleScreen(
    myId: String,
    onOpenChat: (VibeUser) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var users by remember { mutableStateOf<List<VibeUser>>(emptyList()) }
    var search by remember { mutableStateOf("") }

    DisposableEffect(myId) {
        val registration = db.collection("users")
            .addSnapshotListener { snapshot, _ ->
                users = snapshot?.documents
                    ?.filter { it.id != myId }
                    ?.map { doc ->
                        VibeUser(
                            id = doc.id,
                            name = doc.getString("name") ?: "Пользователь",
                            phone = doc.getString("phone").orEmpty(),
                            bio = doc.getString("bio").orEmpty(),
                            avatarBase64 = doc.getString("avatarBase64").orEmpty()
                        )
                    }.orEmpty()
            }
        onDispose { registration.remove() }
    }

    val filtered = users.filter {
        it.name.contains(search, ignoreCase = true) ||
            it.phone.contains(search, ignoreCase = true)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        VibeSectionTitle("Люди", "Знакомься и общайся")

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Поиск пользователей") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            VibeEmptyState(
                Icons.Default.People,
                "Пользователей не найдено",
                "Когда пользователи появятся, ты увидишь их здесь."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { user ->
                    VibeUserRow(user) { onOpenChat(user) }
                }
            }
        }
    }
}

@Composable
private fun VibeUserRow(
    user: VibeUser,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VibeAvatar(user.avatarBase64, user.name)

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                user.name,
                color = VibeText,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                user.bio.ifBlank { user.phone.ifBlank { "Пользователь Vibe Chat" } },
                color = VibeMuted,
                fontSize = 12.sp,
                maxLines = 1
            )
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = "Открыть чат",
            tint = VibeMint
        )
    }
}

@Composable
private fun VibeAvatar(
    avatarBase64: String,
    name: String,
    size: Int = 48
) {
    val bitmap = remember(avatarBase64) {
        try {
            if (avatarBase64.isBlank()) null
            else {
                val bytes = Base64.decode(avatarBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            null
        }
    }

    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(0xFFFFE7E4)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Аватар $name",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                name.trim().firstOrNull()?.uppercase() ?: "V",
                color = VibeCoral,
                fontWeight = FontWeight.Bold,
                fontSize = (size / 2).sp
            )
        }
    }
}

@Composable
private fun VibeGeneralChatScreen(
    userId: String,
    phone: String,
    onBack: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var myName by remember { mutableStateOf("Пользователь") }

    DisposableEffect(userId) {
        val userListener = db.collection("users").document(userId)
            .addSnapshotListener { doc, _ ->
                myName = doc?.getString("name") ?: "Пользователь"
            }

        val chatListener = db.collection("chats")
            .document("general")
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(150)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(
                        context,
                        "Не удалось загрузить сообщения",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                messages = snapshot?.documents?.mapNotNull { it.data }.orEmpty()
            }

        onDispose {
            userListener.remove()
            chatListener.remove()
        }
    }

    Column(Modifier.fillMaxSize().background(VibeBg)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Назад")
            }
            Column {
                Text("Общий чат", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text("Vibe Chat", color = VibeMuted, fontSize = 12.sp)
            }
        }

        HorizontalDivider(color = VibePanel2)

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { item ->
                val authorId = item["authorId"] as? String ?: ""
                val author = item["author"] as? String ?: "Пользователь"
                val text = item["text"] as? String ?: ""

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (authorId == userId) {
                        Arrangement.End
                    } else {
                        Arrangement.Start
                    }
                ) {
                    Column(
                        Modifier
                            .widthIn(max = 290.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (authorId == userId) {
                                    Color(0xFFFFE7E4)
                                } else {
                                    Color.White
                                }
                            )
                            .padding(11.dp)
                    ) {
                        if (authorId != userId) {
                            Text(
                                author,
                                color = VibeCoral,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(text, color = VibeText)
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .navigationBarsPadding()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение…") },
                shape = RoundedCornerShape(22.dp),
                maxLines = 4
            )

            IconButton(
                onClick = {
                    val text = message.trim()
                    if (text.isNotEmpty()) {
                        db.collection("chats")
                            .document("general")
                            .collection("messages")
                            .add(
                                mapOf(
                                    "text" to text,
                                    "authorId" to userId,
                                    "author" to myName,
                                    "phone" to phone,
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                            )
                            .addOnFailureListener {
                                Toast.makeText(
                                    context,
                                    "Не удалось отправить сообщение",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        message = ""
                    }
                }
            ) {
                Icon(Icons.Default.Send, "Отправить", tint = VibeCoral)
            }
        }
    }
}

@Composable
private fun AlexTextScreen(
    onVoice: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val model = remember {
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel("gemini-3.8-flash")
    }

    var input by remember { mutableStateOf("") }
    var answer by remember {
        mutableStateOf("Привет! Я Алекс, твой AI-помощник. Чем помочь?")
    }
    var loading by remember { mutableStateOf(false) }
    var history by remember {
        mutableStateOf(listOf<Pair<String, String>>())
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        VibeSectionTitle("Алекс AI", "Твой помощник в Vibe Chat")

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFE7E4)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            null,
                            tint = VibeCoral,
                            modifier = Modifier.size(25.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Алекс", fontWeight = FontWeight.Bold)
                        Text(
                            if (loading) "Думаю…" else "AI-помощник",
                            color = VibeMuted,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onVoice) {
                        Icon(Icons.Default.Mic, "Голосовой режим", tint = VibeMint)
                    }
                }

                HorizontalDivider(
                    Modifier.padding(vertical = 12.dp),
                    color = VibePanel2
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        VibeMessageBubble("Алекс", answer, false)
                    }
                    items(history) { pair ->
                        VibeMessageBubble("Ты", pair.first, true)
                        VibeMessageBubble("Алекс", pair.second, false)
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Напиши Алексу…") },
                    shape = RoundedCornerShape(17.dp),
                    maxLines = 5
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        val prompt = input.trim()
                        if (prompt.isEmpty() || loading) return@Button

                        input = ""
                        loading = true

                        scope.launch {
                            try {
                                val result = model.generateContent(prompt)
                                val response = result.text
                                    ?: "Не получилось сформировать ответ."
                                history = history + (prompt to response)
                                answer = response
                            } catch (e: Exception) {
                                answer = "Не удалось получить ответ. Проверь интернет и настройки Firebase AI."
                                Toast.makeText(
                                    context,
                                    "Ошибка AI: ${e.localizedMessage ?: "неизвестная ошибка"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            } finally {
                                loading = false
                            }
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VibeCoral
                    )
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Send, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Отправить")
                    }
                }
            }
        }
    }
}

@Composable
private fun VibeMessageBubble(
    author: String,
    text: String,
    mine: Boolean
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(
                    if (mine) Color(0xFFFFE7E4) else VibePanel2
                )
                .padding(11.dp)
        ) {
            Text(
                author,
                fontSize = 11.sp,
                color = if (mine) VibeCoral else VibeMint,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(3.dp))
            Text(text, color = VibeText)
        }
    }
}

@Composable
private fun VibeProfileScreen(
    userId: String,
    phone: String,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
    onSignOut: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var name by remember { mutableStateOf("Мой профиль") }
    var bio by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }

    DisposableEffect(userId) {
        val listener = db.collection("users").document(userId)
            .addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    name = doc.getString("name") ?: "Мой профиль"
                    bio = doc.getString("bio").orEmpty()
                }
            }
        onDispose { listener.remove() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(18.dp)
    ) {
        VibeSectionTitle("Мой профиль", "Твой аккаунт Vibe Chat")

        Spacer(Modifier.height(20.dp))

        Box(
            Modifier
                .size(94.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFE7E4))
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Person,
                null,
                tint = VibeCoral,
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            name,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            phone,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = VibeMuted
        )

        Spacer(Modifier.height(20.dp))

        VibeActionCard(
            Icons.Default.Edit,
            "Редактировать профиль",
            "Измени имя и описание"
        ) {
            editing = true
        }

        Spacer(Modifier.height(9.dp))

        VibeActionCard(
            Icons.Default.Settings,
            "Настройки приложения",
            "Оформление и информация",
            onSettings
        )

        Spacer(Modifier.height(9.dp))

        VibeActionCard(
            Icons.Default.History,
            "История создания",
            "Как создавался Vibe Chat",
            onHistory
        )

        Spacer(Modifier.height(9.dp))

        VibeActionCard(
            Icons.Default.Logout,
            "Выйти из аккаунта",
            "Завершить текущий сеанс",
            onSignOut
        )
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Редактировать профиль") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Имя") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text("О себе") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        db.collection("users").document(userId)
                            .set(
                                mapOf(
                                    "name" to name.trim().ifBlank { "Пользователь" },
                                    "bio" to bio.trim(),
                                    "phone" to phone,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                ),
                                com.google.firebase.firestore.SetOptions.merge()
                            )
                        editing = false
                    }
                ) {
                    Text("Сохранить", color = VibeCoral)
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun VibeSettingsScreen(
    onBack: () -> Unit,
    onHistory: () -> Unit
) {
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Назад")
            }
            Text(
                "Настройки",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(16.dp))

        VibeActionCard(
            Icons.Default.History,
            "История создания",
            "Информация о разработке проекта",
            onHistory
        )

        Spacer(Modifier.height(10.dp))

        VibeActionCard(
            Icons.Default.Android,
            "Версия приложения",
            "Vibe Chat 2.0.0"
        ) {
            Toast.makeText(
                context,
                "Vibe Chat 2.0.0",
                Toast.LENGTH_SHORT
            ).show()
        }

        Spacer(Modifier.height(10.dp))

        VibeActionCard(
            Icons.Default.Settings,
            "Системные настройки",
            "Разрешения и настройки приложения"
        ) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }

        Spacer(Modifier.height(18.dp))

        Text(
            "Vibe Chat использует Firebase для авторизации и работы с данными. Доступность функций зависит от конфигурации проекта.",
            color = VibeMuted,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun VibeCreationHistoryScreen(
    onBack: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(VibeBg)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Назад")
            }
            Text(
                "История создания",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = VibeText
            )
        }

        Spacer(Modifier.height(18.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(18.dp)) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = VibeCoral,
                    modifier = Modifier.size(34.dp)
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    "Vibe Chat",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = VibeText
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    "Vibe Chat — проект разработчика приложения. Цель проекта — создать удобное пространство для общения с личными чатами, общим чатом и AI-помощником.",
                    color = VibeText,
                    lineHeight = 22.sp
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    "Роль разработчика",
                    fontWeight = FontWeight.Bold,
                    color = VibeCoral
                )
                Text(
                    "Разработчик отвечает за идею, направление развития, решения по функциям и развитие проекта.",
                    color = VibeMuted,
                    lineHeight = 21.sp
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    "Использование искусственного интеллекта",
                    fontWeight = FontWeight.Bold,
                    color = VibeCoral
                )
                Text(
                    "ChatGPT от OpenAI использовался как AI-помощник при разработке: для обсуждения идей, проектирования интерфейса и подготовки кода. ChatGPT не является единственным автором или владельцем приложения.",
                    color = VibeMuted,
                    lineHeight = 21.sp
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    "Версия проекта: 2.0.0",
                    fontWeight = FontWeight.SemiBold,
                    color = VibeText
                )
            }
        }
    }
}

@Composable
private fun VibeSectionTitle(
    title: String,
    subtitle: String
) {
    Column(Modifier.padding(top = 6.dp, bottom = 14.dp)) {
        Text(
            title,
            fontSize = 23.sp,
            fontWeight = FontWeight.ExtraBold,
            color = VibeText
        )
        Text(subtitle, color = VibeMuted, fontSize = 13.sp)
    }
}

@Composable
private fun VibeActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xFFFFE7E4)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = VibeCoral)
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(title, color = VibeText, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = VibeMuted, fontSize = 12.sp)
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = VibeMint
        )
    }
}

@Composable
private fun VibeEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = VibeMint,
            modifier = Modifier.size(42.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            fontWeight = FontWeight.Bold,
            color = VibeText
        )
        Spacer(Modifier.height(5.dp))
        Text(
            subtitle,
            color = VibeMuted,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
