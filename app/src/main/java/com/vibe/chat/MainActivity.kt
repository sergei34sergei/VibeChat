package com.vibe.chat

import android.app.Activity
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseApp
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

// VIBE CLASSIC — спокойная графитовая палитра
private val VibeBg = Color(0xFF101113)
private val VibePanel = Color(0xFF1B1C20)
private val VibePanel2 = Color(0xFF25262C)
private val VibePurple = Color(0xFF9B86D8)
private val VibeCyan = Color(0xFFB9A2F5)
private val VibePink = Color(0xFFE59AAE)
private val VibeText = Color(0xFFF4F2F8)
private val VibeMuted = Color(0xFFA5A5AF)
private val VibeGreen = Color(0xFF83CFA7)

private fun vibeGradient() = Brush.linearGradient(
    listOf(
        Color(0xFF7865AE),
        Color(0xFF51466F),
        Color(0xFF34313E)
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = VibePurple,
                    secondary = VibeCyan,
                    background = VibeBg,
                    surface = VibePanel,
                    onBackground = VibeText,
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
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
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

            override fun onVerificationFailed(exception: FirebaseException) {
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
                        Color(0xFF211D2B),
                        VibeBg,
                        Color(0xFF17171B)
                    )
                )
            )
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(86.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(vibeGradient()),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Forum,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(Modifier.height(22.dp))

        Text(
            "Vibe Chat",
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = VibeText
        )
        Text("Твой мир общения", color = VibeCyan, fontSize = 15.sp)

        Spacer(Modifier.height(32.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(25.dp))
                .background(VibePanel)
                .padding(20.dp)
        ) {
            Text(
                if (verificationId == null) "Добро пожаловать" else "Подтверди номер",
                color = VibeText,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))
            Text(message, color = VibeMuted, fontSize = 13.sp)

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Номер с кодом страны") },
                placeholder = { Text("+49…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                enabled = verificationId == null,
                shape = RoundedCornerShape(15.dp)
            )

            if (verificationId != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Код из SMS") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                        if (!normalizedPhone.startsWith("+") ||
                            normalizedPhone.length < 8
                        ) {
                            message = "Введи номер в международном формате, например +49…"
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
                            val credential = PhoneAuthProvider.getCredential(id, code)
                            auth.signInWithCredential(credential)
                                .addOnCompleteListener {
                                    loading = false
                                    if (!it.isSuccessful) {
                                        message = it.exception?.localizedMessage
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
                        if (verificationId == null) "Получить код" else "Войти",
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
    Photos("Фото"),
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

    val db = remember { FirebaseFirestore.getInstance() }

    when {
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
                            onOpenChat = { id, name -> privateChat = id to name },
                            onOpenGeneralChat = { generalChat = true },
                            onOpenUsers = { tab = VibeTab.People }
                        )

                        VibeTab.People -> VibePeopleScreen(
                            db = db,
                            myId = userId,
                            onOpenProfile = { profileTarget = it },
                            onOpenChat = { id, name -> privateChat = id to name }
                        )

                        VibeTab.Alex -> AlexVoiceScreen()

                        VibeTab.Photos -> VibePhotoStudioScreen()

                        VibeTab.Profile -> VibeMyProfileScreen(
                            db = db,
                            userId = userId,
                            phone = phone,
                            onSignOut = onSignOut,
                            onOpenProfile = { profileTarget = it }
                        )
                    }
                }
            }

            VibeBottomBar(
                selected = tab,
                onSelect = { tab = it }
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
                Icons.Default.Forum,
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
                    VibeTab.Photos -> "Идеи для творчества"
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
        VibeTab.Photos to Icons.Default.Palette,
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
                        if (active) VibePurple.copy(alpha = 0.12f)
                        else Color.Transparent
                    )
                    .clickable { onSelect(item) }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    icon,
                    contentDescription = item.label,
                    tint = if (active) VibeCyan else VibeMuted,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    item.label,
                    fontSize = 10.sp,
                    color = if (active) VibeCyan else VibeMuted,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
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
    var users by remember { mutableStateOf<List<VibeUser>>(emptyList()) }

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
                            .clickable { onOpenProfile(user) }
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
                        IconButton(onClick = { onOpenChat(user.id, user.name) }) {
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
    onOpenProfile: (VibeUser) -> Unit
) {
    var user by remember {
        mutableStateOf(VibeUser(userId, "Мой профиль", phone, "", ""))
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
        onDispose { registration.remove() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
            onClick = { onOpenProfile(user) }
        )

        Spacer(Modifier.height(10.dp))

        VibeActionCard(
            icon = Icons.Default.Security,
            title = "Аккаунт защищён",
            subtitle = "Вход выполняется через Firebase",
            onClick = {}
        )

        Spacer(Modifier.weight(1f))

        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = VibePink
            )
        ) {
            Icon(Icons.Default.Logout, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Выйти из аккаунта")
        }
    }
}

@Composable
private fun VibeProfile(
    user: VibeUser,
    isMe: Boolean,
    onBack: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var name by remember(user.id) { mutableStateOf(user.name) }
    var bio by remember(user.id) { mutableStateOf(user.bio) }
    var avatar by remember(user.id) { mutableStateOf(user.avatarBase64) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readBytes()
                    avatar = Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            }.onFailure {
                Toast.makeText(
                    context,
                    "Не удалось загрузить изображение",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Назад",
                    tint = VibeText
                )
            }
            Text(
                if (isMe) "Редактирование" else "Профиль",
                color = VibeText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(25.dp))

        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VibeAvatar(avatar, name, size = 104)
            if (isMe) {
                TextButton(onClick = { imagePicker.launch("image/*") }) {
                    Text("Изменить фотографию", color = VibeCyan)
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        if (isMe) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Имя") },
                singleLine = true,
                shape = RoundedCornerShape(15.dp)
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it.take(160) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("О себе") },
                minLines = 3,
                shape = RoundedCornerShape(15.dp)
            )

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = {
                    onSave(
                        name.trim().ifBlank { "Пользователь" },
                        bio.trim(),
                        avatar
                    )
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(15.dp)
            ) {
                Text("Сохранить изменения")
            }
        } else {
            Text(
                name,
                color = VibeText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(user.phone, color = VibeMuted)
            Spacer(Modifier.height(12.dp))
            Text(bio.ifBlank { "Пользователь Vibe Chat" }, color = VibeMuted)
        }
    }
}

@Composable
private fun VibeAvatar(
    avatarBase64: String,
    name: String,
    size: Int = 52
) {
    val bitmap = remember(avatarBase64) {
        runCatching {
            if (avatarBase64.isBlank()) null
            else {
                val bytes = Base64.decode(avatarBase64, Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size
                )
            }
        }.getOrNull()
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = name,
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(vibeGradient()),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontSize = (size / 2.2f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun VibeChatsListScreen(
    myId: String,
    onOpenChat: (String, String) -> Unit,
    onOpenGeneralChat: () -> Unit,
    onOpenUsers: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var chats by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    DisposableEffect(myId) {
        val registration = db.collection("chats")
            .whereArrayContains("participantIds", myId)
            .addSnapshotListener { snapshot, _ ->
                chats = snapshot?.documents
                    ?.filter { it.id != "general" }
                    ?.map { doc ->
                        val participants = doc.get("participantIds") as? List<*>
                        val otherId = participants
                            ?.filterIsInstance<String>()
                            ?.firstOrNull { it != myId }
                            ?: doc.id
                        otherId to otherId
                    }.orEmpty()
            }
        onDispose { registration.remove() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        VibeSectionTitle("Твои чаты", "Общайся без границ")

        VibeActionCard(
            icon = Icons.Default.Public,
            title = "Общий чат",
            subtitle = "Общайся со всеми участниками",
            accent = VibeCyan,
            onClick = onOpenGeneralChat
        )

        Spacer(Modifier.height(10.dp))

        VibeActionCard(
            icon = Icons.Default.PersonSearch,
            title = "Найти людей",
            subtitle = "Открыть список пользователей",
            accent = VibePurple,
            onClick = onOpenUsers
        )

        Spacer(Modifier.height(18.dp))

        Text(
            "ЛИЧНЫЕ ДИАЛОГИ",
            color = VibeMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))

        if (chats.isEmpty()) {
            VibeEmptyState(
                icon = Icons.Default.ChatBubbleOutline,
                title = "Здесь начнутся разговоры",
                subtitle = "Найди пользователя и отправь первое сообщение."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(chats, key = { it.first }) { (otherId, _) ->
                    var displayName by remember(otherId) {
                        mutableStateOf("Загрузка профиля…")
                    }
                    DisposableEffect(otherId) {
                        val registration = db.collection("users").document(otherId)
                            .addSnapshotListener { doc, _ ->
                                displayName = doc?.getString("name")
                                    ?: doc?.getString("phone")
                                    ?: "Пользователь"
                            }
                        onDispose { registration.remove() }
                    }

                    VibeActionCard(
                        icon = Icons.Default.Chat,
                        title = displayName,
                        subtitle = "Открыть диалог",
                        onClick = { onOpenChat(otherId, displayName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VibeGeneralChatReplacement(
    phone: String,
    onBack: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val myId = auth.currentUser?.uid.orEmpty()

    var myName by remember { mutableStateOf(phone.ifBlank { "Участник" }) }
    var draft by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<GeneralMessage>>(emptyList()) }
    var sending by remember { mutableStateOf(false) }

    DisposableEffect(myId) {
        val registration = db.collection("users").document(myId)
            .addSnapshotListener { doc, _ ->
                myName = doc?.getString("name")
                    ?: doc?.getString("phone")
                    ?: phone.ifBlank { "Участник" }
            }
        onDispose { registration.remove() }
    }

    DisposableEffect(Unit) {
        val registration = db.collection("chats").document("general")
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(150)
            .addSnapshotListener { snapshot, _ ->
                messages = snapshot?.documents?.map { doc ->
                    GeneralMessage(
                        id = doc.id,
                        authorId = doc.getString("authorId").orEmpty(),
                        author = doc.getString("author") ?: "Участник",
                        text = doc.getString("text").orEmpty(),
                        createdAt = doc.getTimestamp("createdAt")
                    )
                }.orEmpty()
            }
        onDispose { registration.remove() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(VibeBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VibePanel)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Назад",
                    tint = VibeText
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Общий чат",
                    color = VibeText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
                Text("Сообщество Vibe", color = VibeMuted, fontSize = 12.sp)
            }
            Icon(
                Icons.Default.Public,
                contentDescription = null,
                tint = VibeCyan
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                val mine = message.authorId == myId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (mine) 42.dp else 0.dp,
                            end = if (mine) 0.dp else 42.dp
                        )
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (mine) VibePanel2 else VibePanel)
                        .padding(11.dp)
                ) {
                    Text(
                        if (mine) "Ты" else message.author,
                        color = if (mine) VibeCyan else VibePurple,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(message.text, color = VibeText)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VibePanel)
                .navigationBarsPadding()
                .padding(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(2000) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Написать сообщение…") },
                maxLines = 4,
                shape = RoundedCornerShape(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                enabled = draft.isNotBlank() && !sending,
                onClick = {
                    val text = draft.trim()
                    if (text.isNotEmpty() && myId.isNotEmpty()) {
                        sending = true
                        db.collection("chats").document("general")
                            .collection("messages")
                            .add(
                                mapOf(
                                    "text" to text,
                                    "authorId" to myId,
                                    "author" to myName,
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                            )
                            .addOnSuccessListener {
                                draft = ""
                                sending = false
                            }
                            .addOnFailureListener {
                                sending = false
                            }
                    }
                }
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Отправить",
                    tint = VibeCyan
                )
            }
        }
    }
}

private data class GeneralMessage(
    val id: String,
    val authorId: String,
    val author: String,
    val text: String,
    val createdAt: Timestamp?
)

@Composable
private fun VibePhotoStudioScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var subject by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("Кинематографичный реализм") }
    var format by remember { mutableStateOf("Вертикальный 9:16") }
    var extra by remember { mutableStateOf("") }

    val prompt = buildString {
        append("Создай качественное изображение: ")
        append(subject.ifBlank { "красивую выразительную сцену" })
        append(". Стиль: ")
        append(style)
        append(". Формат: ")
        append(format)
        if (extra.isNotBlank()) append(". Дополнительно: $extra")
        append(". Детализированная композиция, выразительный свет, чистая анатомия, высокая чёткость.")
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        VibeSectionTitle("Фото-студия", "Преврати идею в промпт")

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(VibePanel)
                .padding(15.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(vibeGradient()),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(Modifier.height(15.dp))

            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Что изображаем?") },
                placeholder = { Text("Например, спорткар в ночном городе") },
                minLines = 2,
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(Modifier.height(10.dp))
            Text("Стиль", color = VibeMuted, fontSize = 12.sp)
            Spacer(Modifier.height(5.dp))

            listOf(
                "Кинематографичный реализм",
                "Аниме",
                "3D-мультфильм",
                "Фэнтези",
                "Минимализм"
            ).forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { style = option }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = style == option,
                        onClick = { style = option },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = VibeCyan
                        )
                    )
                    Text(option, color = VibeText, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Формат", color = VibeMuted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Вертикальный 9:16", "Квадрат 1:1").forEach {
                    FilterChip(
                        selected = format == it,
                        onClick = { format = it },
                        label = { Text(it) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = extra,
                onValueChange = { extra = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Дополнительные пожелания") },
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = {
                    val clipboard = context.getSystemService(
                        android.content.Context.CLIPBOARD_SERVICE
                    ) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText("Vibe prompt", prompt)
                    )
                    Toast.makeText(
                        context,
                        "Промпт скопирован",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VibePurple
                )
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Создать и скопировать промпт")
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Эта версия формирует текстовый промпт. Для настоящей генерации изображений нужно отдельно подключить сервис генерации.",
                color = VibeMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun VibeSectionTitle(title: String, subtitle: String) {
    Column(
        modifier = Modifier.padding(top = 8.dp, bottom = 15.dp)
    ) {
        Text(
            title,
            color = VibeText,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = VibeMuted, fontSize = 13.sp)
    }
}

@Composable
private fun VibeActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: Color = VibePurple,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(VibePanel)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(accent.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = VibeText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                color = VibeMuted,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = VibeMuted
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(VibePanel2),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = VibeCyan,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(15.dp))
        Text(
            title,
            color = VibeText,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            color = VibeMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}
