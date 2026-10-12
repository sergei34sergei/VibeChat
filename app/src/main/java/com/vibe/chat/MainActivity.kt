package com.vibe.chat

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// VIBE CHAT — светлая мятно-белая палитра.
private val VibeBg = Color(0xFFFAFAF9)
private val VibePanel = Color(0xFFFFFFFF)
private val VibePanel2 = Color(0xFFF4F6F4)
private val VibePurple = Color(0xFFE85B58)
private val VibeCyan = Color(0xFF75BDA7)
private val VibePink = Color(0xFFF2A19A)
private val VibeText = Color(0xFF252B2A)
private val VibeMuted = Color(0xFF7A8581)
private val VibeGreen = Color(0xFF75BDA7)

private fun vibeGradient() = Brush.linearGradient(
    listOf(
        Color(0xFFFF8178),
        Color(0xFFE85B58),
        Color(0xFFD94C50)
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = VibePurple,
                    onPrimary = Color.White,
                    secondary = VibeCyan,
                    onSecondary = Color.White,
                    background = VibeBg,
                    onBackground = VibeText,
                    surface = VibePanel,
                    onSurface = VibeText,
                    surfaceVariant = VibePanel2,
                    onSurfaceVariant = VibeMuted,
                    primaryContainer = Color(0xFFFFE7E4),
                    onPrimaryContainer = Color(0xFF7C2929),
                    secondaryContainer = Color(0xFFEAF5F0),
                    onSecondaryContainer = Color(0xFF294A40),
                    error = Color(0xFFB94357)
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

        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = VibeBg
    ) {
        if (currentUser == null) {
            VibeLogin()
        } else {
            VibeHome(
                userId = currentUser!!.uid,
                phone = currentUser!!.phoneNumber.orEmpty(),
                onSignOut = { auth.signOut() }
            )
        }
    }
}

@Composable
private fun VibeLogin() {
    val auth = remember { FirebaseAuth.getInstance() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? Activity

    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }

    var resendToken by remember {
        mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null)
    }

    var loading by remember { mutableStateOf(false) }

    var message by remember {
        mutableStateOf("Войди по номеру телефона — общение уже рядом")
    }

    val callbacks = remember {
        object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(
                credential: PhoneAuthCredential
            ) {
                loading = true

                auth.signInWithCredential(credential)
                    .addOnCompleteListener {
                        loading = false

                        if (!it.isSuccessful) {
                            message = it.exception?.localizedMessage
                                ?: "Не удалось выполнить вход"
                        }
                    }
            }

            override fun onVerificationFailed(
                exception: FirebaseException
            ) {
                loading = false

                message = exception.localizedMessage
                    ?: "Не удалось отправить SMS. Проверь номер и настройки Firebase."
            }

            override fun onCodeSent(
                id: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                loading = false
                verificationId = id
                resendToken = token
                message = "Код отправлен. Введи код из SMS."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFFF0EE),
                        VibeBg,
                        Color(0xFFFAFAF9)
                    )
                )
            )
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFFFFF0EE)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "Робот VibeChat",
                modifier = Modifier.size(96.dp)
            )
        }

        Spacer(Modifier.height(22.dp))

        Text(
            "Vibe Chat",
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = VibeText
        )

        Text(
            "Твой мир общения",
            color = VibeCyan,
            fontSize = 15.sp
        )

        Spacer(Modifier.height(32.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(25.dp))
                .background(VibePanel)
                .padding(20.dp)
        ) {
            Text(
                if (verificationId == null) {
                    "Добро пожаловать"
                } else {
                    "Подтверди номер"
                },
                color = VibeText,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                message,
                color = VibeMuted,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Номер с кодом страны") },
                placeholder = { Text("+49…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone
                ),
                enabled = verificationId == null,
                shape = RoundedCornerShape(15.dp)
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
                    ),
                    shape = RoundedCornerShape(15.dp)
                )
            }

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = {
                    if (activity == null) {
                        message = "Не удалось открыть экран авторизации"
                    } else if (verificationId == null) {
                        val normalizedPhone = phone.trim()

                        if (
                            !normalizedPhone.startsWith("+") ||
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

                        if (id == null || code.length < 6) {
                            message = "Введи шестизначный код из SMS"
                        } else {
                            loading = true

                            val credential =
                                PhoneAuthProvider.getCredential(id, code)

                            auth.signInWithCredential(credential)
                                .addOnCompleteListener {
                                    loading = false

                                    if (!it.isSuccessful) {
                                        message =
                                            it.exception?.localizedMessage
                                                ?: "Неверный код или ошибка входа"
                                    }
                                }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !loading,
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VibePurple,
                    contentColor = Color.White
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
                        if (verificationId == null) {
                            "Получить код"
                        } else {
                            "Войти"
                        },
                        fontWeight = FontWeight.Bold
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
                    Text("Изменить номер", color = VibeCyan)
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

@Composable
private fun VibeHome(
    userId: String,
    phone: String,
    onSignOut: () -> Unit
) {
    var tab by remember { mutableStateOf(VibeTab.Chats) }
    var privateChat by remember { mutableStateOf<Pair<String, String>?>(null) }
    var generalChat by remember { mutableStateOf(false) }
    var profileTarget by remember { mutableStateOf<VibeUser?>(null) }
    var alexChat by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val db = remember { FirebaseFirestore.getInstance() }

    when {
        showSettings -> VibeAppSettingsScreen(
            onBack = { showSettings = false }
        )

        tab == VibeTab.Alex && alexChat -> AlexChatScreen(
            onOpenVoice = { alexChat = false },
            onBack = {
                alexChat = false
                tab = VibeTab.Chats
            }
        )

        privateChat != null -> {
            val target = privateChat!!

            PrivateChatScreen(
                otherUserId = target.first,
                otherUserName = target.second,
                onBack = { privateChat = null }
            )
        }

        generalChat -> VibeGeneralChatReplacement(
            phone = phone,
            onBack = { generalChat = false }
        )

        profileTarget != null -> VibeProfile(
            user = profileTarget!!,
            isMe = profileTarget!!.id == userId,
            onBack = { profileTarget = null },
            onSave = { name, bio, avatar ->
                db.collection("users").document(userId)
                    .set(
                        mapOf(
                            "name" to name,
                            "bio" to bio,
                            "phone" to phone,
                            "avatarBase64" to avatar,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
            }
        )

        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .background(VibeBg)
        ) {
            VibeHeader(
                userId = userId,
                tab = tab,
                onProfile = {
                    db.collection("users").document(userId).get()
                        .addOnSuccessListener { doc ->
                            profileTarget = VibeUser(
                                id = userId,
                                name = doc.getString("name") ?: "Мой профиль",
                                phone = doc.getString("phone") ?: phone,
                                bio = doc.getString("bio").orEmpty(),
                                avatarBase64 = doc.getString("avatarBase64").orEmpty()
                            )
                        }
                }
            )

            AnimatedContent(
                targetState = tab,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "vibe-tab"
            ) { selected ->
                Box(Modifier.fillMaxSize()) {
                    when (selected) {
                        VibeTab.Chats -> VibeChatsListScreen(
                            myId = userId,
                            onOpenChat = { id, name ->
                                privateChat = id to name
                            },
                            onOpenGeneralChat = {
                                generalChat = true
                            },
                            onOpenUsers = {
                                tab = VibeTab.People
                            }
                        )

                        VibeTab.People -> VibePeopleScreen(
                            db = db,
                            myId = userId,
                            onOpenProfile = { profileTarget = it },
                            onOpenChat = { id, name ->
                                privateChat = id to name
                            }
                        )

                        VibeTab.Alex -> AlexVoiceScreen(
                            onOpenChat = { alexChat = true },
                            onBack = {
                                alexChat = false
                                tab = VibeTab.Chats
                            }
                        )

                        VibeTab.Profile -> VibeMyProfileScreen(
                            db = db,
                            userId = userId,
                            phone = phone,
                            onSignOut = onSignOut,
                            onOpenProfile = { profileTarget = it },
                            onOpenSettings = { showSettings = true }
                        )
                    }
                }
            }

            VibeBottomBar(
                selected = tab,
                onSelect = {
                    tab = it
                    if (it != VibeTab.Alex) alexChat = false
                }
            )
        }
    }
}

@Composable
private fun VibeHeader(
    userId: String,
    tab: VibeTab,
    onProfile: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VibeBg)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(43.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(vibeGradient()),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.SmartToy,
                contentDescription = null,
                tint = Color.White
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                "Vibe Chat",
                fontSize = 22.sp,
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

        IconButton(onClick = onProfile) {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = "Профиль",
                tint = VibeCyan,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun VibeBottomBar(
    selected: VibeTab,
    onSelect: (VibeTab) -> Unit
) {
    val items = listOf(
        VibeTab.Chats to Icons.Default.ChatBubble,
        VibeTab.People to Icons.Default.People,
        VibeTab.Alex to Icons.Default.AutoAwesome,
        VibeTab.Profile to Icons.Default.Person
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VibePanel)
            .navigationBarsPadding()
            .padding(horizontal = 5.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (item, icon) ->
            val active = selected == item

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        if (active) {
                            Color(0xFFDDF5E8)
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable { onSelect(item) }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    icon,
                    contentDescription = item.label,
                    tint = if (active) VibePurple else VibeMuted,
                    modifier = Modifier.size(21.dp)
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    item.label,
                    fontSize = 10.sp,
                    color = if (active) VibePurple else VibeMuted,
                    fontWeight = if (active) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Normal
                    }
                )
            }
        }
    }
}

private data class VibeUser(
    val id: String,
    val name: String,
    val phone: String,
    val bio: String,
    val avatarBase64: String
)

@Composable
private fun VibePeopleScreen(
    db: FirebaseFirestore,
    myId: String,
    onOpenProfile: (VibeUser) -> Unit,
    onOpenChat: (String, String) -> Unit
) {
    var users by remember {
        mutableStateOf<List<VibeUser>>(emptyList())
    }

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

        onDispose {
            registration.remove()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        VibeSectionTitle("Люди", "Знакомься и общайся")

        if (users.isEmpty()) {
            VibeEmptyState(
                icon = Icons.Default.People,
                title = "Пока никого нет",
                subtitle = "Когда другие пользователи появятся в Vibe Chat, ты увидишь их здесь."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(9.dp),
                contentPadding = PaddingValues(bottom = 18.dp)
            ) {
                items(users, key = { it.id }) { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(17.dp))
                            .background(VibePanel)
                            .clickable {
                                onOpenProfile(user)
                            }
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
                                user.bio.ifBlank { user.phone },
                                color = VibeMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = {
                                onOpenChat(user.id, user.name)
                            }
                        ) {
                            Icon(
                                Icons.Default.Chat,
                                contentDescription = "Написать",
                                tint = VibeCyan
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VibeMyProfileScreen(
    db: FirebaseFirestore,
    userId: String,
    phone: String,
    onSignOut: () -> Unit,
    onOpenProfile: (VibeUser) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var user by remember {
        mutableStateOf(
            VibeUser(userId, "Мой профиль", phone, "", "")
        )
    }

    DisposableEffect(userId) {
        val registration = db.collection("users").document(userId)
            .addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    user = VibeUser(
                        id = userId,
                        name = doc.getString("name") ?: "Мой профиль",
                        phone = doc.getString("phone") ?: phone,
                        bio = doc.getString("bio").orEmpty(),
                        avatarBase64 = doc.getString("avatarBase64").orEmpty()
                    )
                }
            }

        onDispose {
            registration.remove()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        VibeSectionTitle("Мой профиль", "Настрой Vibe под себя")

        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(vibeGradient()),
            contentAlignment = Alignment.Center
        ) {
            VibeAvatar(user.avatarBase64, user.name, size = 108)
        }

        Spacer(Modifier.height(15.dp))

        Text(
            user.name,
            color = VibeText,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Text(user.phone, color = VibeMuted)

        if (user.bio.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(user.bio, color = VibeMuted)
        }

        Spacer(Modifier.height(24.dp))

        VibeActionCard(
            icon = Icons.Default.Edit,
            title = "Редактировать профиль",
            subtitle = "Имя, описание и аватар",
            onClick = {
                onOpenProfile(user)
            }
        )

        Spacer(Modifier.height(10.dp))

        VibeActionCard(
            icon = Icons.Default.Settings,
            title = "Настройки приложения",
            subtitle = "Оформление, звук, уведомления и приватность",
            onClick = onOpenSettings
        )

        Spacer(Modifier.height(10.dp))

        VibeActionCard(
            icon = Icons.Default.Security,
            title = "Аккаунт защищён",
            subtitle = "Вход выполняется через Firebase",
            onClick = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                ).apply {
                    data = android.net.Uri.parse(
                        "package:" + context.packageName
                    )
                }

                context.startActivity(intent)
            }
        )

        Spacer(Modifier.weight(1f))

        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFFB94357)
            )
        ) {
            Icon(
                Icons.Default.Logout,
                contentDescription = null
            )

            Spacer(Modifier.width(8.dp))

            Text("Выйти из аккаунта")
        }
    }
}

@Composable
private fun VibeAppSettingsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val prefs = remember {
        context.getSharedPreferences(
            "vibechat_settings",
            android.content.Context.MODE_PRIVATE
        )
    }

    var soundEnabled by remember {
        mutableStateOf(prefs.getBoolean("sound_enabled", true))
    }

    var vibrationEnabled by remember {
        mutableStateOf(prefs.getBoolean("vibration_enabled", true))
    }

    var notificationsEnabled by remember {
        mutableStateOf(prefs.getBoolean("notifications_enabled", true))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Назад",
                    tint = VibeText
                )
            }

            Column {
                Text(
                    "Настройки",
                    color = VibeText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Настрой VibeChat под себя",
                    color = VibeMuted,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(VibePanel)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFFFE7E4)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Palette,
                        contentDescription = null,
                        tint = VibePurple
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column {
                    Text(
                        "Оформление",
                        color = VibeText,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        "Светлая тема · белый, мятный и коралловый",
                        color = VibeMuted,
                        fontSize = 12.sp
                    )
                }
            }

            HorizontalDivider(
                Modifier.padding(vertical = 14.dp),
                color = Color(0xFFECEFED)
            )

            SettingsSwitchRow(
                "Уведомления",
                "Сохранённое предпочтение; управление уведомлениями Android доступно ниже",
                notificationsEnabled
            ) {
                notificationsEnabled = it
                prefs.edit()
                    .putBoolean("notifications_enabled", it)
                    .apply()
            }

            SettingsSwitchRow(
                "Звук",
                "Сохраняет выбор для звуковых оповещений VibeChat",
                soundEnabled
            ) {
                soundEnabled = it
                prefs.edit()
                    .putBoolean("sound_enabled", it)
                    .apply()
            }

            SettingsSwitchRow(
                "Вибрация",
                "Сохраняет выбор для виброоповещений VibeChat",
                vibrationEnabled
            ) {
                vibrationEnabled = it
                prefs.edit()
                    .putBoolean("vibration_enabled", it)
                    .apply()
            }
        }

        Spacer(Modifier.height(12.dp))

        VibeActionCard(
            icon = Icons.Default.Notifications,
            title = "Системные уведомления",
            subtitle = "Открыть настройки уведомлений Android",
            onClick = {
                val intent = Intent(
                    Settings.ACTION_APP_NOTIFICATION_SETTINGS
                ).putExtra(
                    Settings.EXTRA_APP_PACKAGE,
                    context.packageName
                )

                context.startActivity(intent)
            }
        )

        Spacer(Modifier.height(10.dp))

        VibeActionCard(
            icon = Icons.Default.Security,
            title = "Приватность и разрешения",
            subtitle = "Управление
