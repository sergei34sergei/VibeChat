
package com.vibe.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.initialize
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val Purple = Color(0xFF9D4EDD)
private val Cyan = Color(0xFF48E5FF)
private val Dark = Color(0xFF090B1A)
private val Panel = Color(0xFF191C35)
private val PanelLight = Color(0xFF232746)
private val Pink = Color(0xFFFF4FA3)
private val Blue = Color(0xFF4169FF)
private val Muted = Color(0xFFABB4D1)

private val VibeGradient =
    Brush.linearGradient(listOf(Purple, Blue, Cyan))

private val VibeBackground =
    Brush.verticalGradient(
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
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Purple,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF343650)
        )
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun VibeAvatar(
    name: String,
    size: Int = 44,
    bitmap: Bitmap? = null
) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Аватар",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
        )
    } else {
        Box(
            Modifier
                .size(size.dp)
                .background(VibeGradient, CircleShape)
                .padding(2.dp)
                .background(Panel, CircleShape),
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
}

@Composable
private fun VibeSectionTitle(title: String, subtitle: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 5.dp, bottom = 10.dp)
    ) {
        Text(
            title,
            color = Color.White,
            fontSize = 23.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = Muted, fontSize = 12.sp)
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
                    surface = Panel
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
        status = "Отправляем SMS-код..."

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
            status = "Сначала нажми «Получить SMS-код»."
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
        Modifier
            .fillMaxSize()
            .background(VibeBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Panel, RoundedCornerShape(26.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VibeAvatar("VIBE", 76)

            Text(
                "VIBE CHAT",
                color = Color.White,
                fontSize = 29.sp,
                fontWeight = FontWeight.Black
            )

            Text(
                "ТВОЙ МИР ОБЩЕНИЯ",
                color = Cyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Общайся. Находи друзей. Будь собой.",
                color = Muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

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
                shape = RoundedCornerShape(14.dp),
                colors = VibeFieldColors
            )

            VibeButton(
                if (busy) "Подожди..." else "Получить SMS-код",
                { onSendCode(phone) },
                Modifier.fillMaxWidth(),
                !busy
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
                shape = RoundedCornerShape(14.dp),
                colors = VibeFieldColors
            )

            VibeButton(
                if (busy) "Проверяем..." else "Войти в Vibe Chat",
                { onVerifyCode(code) },
                Modifier.fillMaxWidth(),
                !busy
            )

            if (busy) {
                CircularProgressIndicator(color = Cyan)
            }

            if (status.isNotBlank()) {
                Text(
                    status,
                    color = if (status.startsWith("Ошибка")) Pink else Cyan,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private data class VibeUser(
    val id: String,
    val name: String,
    val phone: String,
    val bio: String = ""
)

private data class ConversationPreview(
    val chatId: String,
    val otherUserId: String,
    val otherUserName: String,
    val lastText: String,
    val lastAt: Timestamp?
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
    var tab by remember { mutableStateOf("Чаты") }
    var privateChatUserId by remember { mutableStateOf<String?>(null) }
    var privateChatUserName by remember { mutableStateOf("") }

    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val currentUser = auth.currentUser
    val myId = currentUser?.uid.orEmpty()

    var savedName by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var bioInput by remember { mutableStateOf("") }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var avatarBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var profileLoaded by remember { mutableStateOf(false) }
    var profileMessage by remember { mutableStateOf("") }
    var savingProfile by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Некоторые провайдеры не поддерживают сохранение разрешения.
            }

            avatarUri = uri
            profileMessage = "Фото выбрано. Нажми «Сохранить профиль»."
        }
    }

    DisposableEffect(myId) {
        var registration: ListenerRegistration? = null

        if (myId.isNotBlank()) {
            registration = db.collection("users")
                .document(myId)
                .addSnapshotListener { snapshot, exception ->
                    if (exception != null) {
                        profileMessage =
                            "Ошибка профиля: ${exception.localizedMessage}"
                    } else if (snapshot != null) {
                        savedName = snapshot.getString("name").orEmpty()

                        if (!profileLoaded) {
                            nameInput = savedName
                            bioInput = snapshot.getString("bio").orEmpty()

                            val savedUri = snapshot.getString("avatarUri")
                            if (!savedUri.isNullOrBlank()) {
                                avatarUri = try {
                                    Uri.parse(savedUri)
                                } catch (_: Exception) {
                                    null
                                }
                            }

                            profileLoaded = true
                        }
                    }
                }
        }

        onDispose {
            registration?.remove()
        }
    }

    LaunchedEffect(avatarUri) {
        avatarBitmap = null
        val uri = avatarUri ?: return@LaunchedEffect

        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                avatarBitmap = BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) {
            avatarBitmap = null
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(VibeBackground)
            .statusBarsPadding()
    ) {
        if (privateChatUserId == null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 10.dp,
                        top = 8.dp,
                        bottom = 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(VibeGradient, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "V",
                        color = Color.White,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        "VIBE CHAT",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )

                    Text(
                        when (tab) {
                            "Чаты" -> "Твои переписки"
                            "Люди" -> "Найди собеседника"
                            "Алекс" -> "Твой AI-помощник"
                            "Общий чат" -> "Общение со всеми"
                            else -> "Твой аккаунт"
                        },
                        color = Cyan,
                        fontSize = 11.sp
                    )
                }

                TextButton(onClick = onLogout) {
                    Text("Выйти", color = Pink, fontSize = 12.sp)
                }
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (privateChatUserId != null) {
                PrivateChatScreen(
                    otherUserId = privateChatUserId!!,
                    otherUserName = privateChatUserName,
                    onBack = { privateChatUserId = null }
                )
            } else {
                when (tab) {
                    "Чаты" -> VibeChatsListScreen(
                        myId = myId,
                        onOpenChat = { id, name ->
                            privateChatUserId = id
                            privateChatUserName = name
                        },
                        onOpenGeneralChat = { tab = "Общий чат" },
                        onOpenUsers = { tab = "Люди" }
                    )

                    "Люди" -> VibeUsers(
                        db = db,
                        myId = myId,
                        onOpenProfile = { tab = "Профиль" },
                        onOpenChat = { id, name ->
                            privateChatUserId = id
                            privateChatUserName = name
                        }
                    )

                    "Алекс" -> AlexAiScreen()

                    "Общий чат" -> VibeGeneralChat(phone)

                    else -> VibeProfile(
                        phone = phone,
                        savedName = savedName,
                        nameInput = nameInput,
                        bioInput = bioInput,
                        avatarBitmap = avatarBitmap,
                        onNameChange = { nameInput = it.take(40) },
                        onBioChange = { bioInput = it.take(160) },
                        onPickAvatar = {
                            avatarPicker.launch(arrayOf("image/*"))
                        },
                        onSave = {
                            val uid = currentUser?.uid
                            val cleanName = nameInput.trim()

                            if (uid == null) {
                                profileMessage = "Войди в аккаунт."
                            } else if (cleanName.isBlank()) {
                                profileMessage = "Введи имя."
                            } else {
                                savingProfile = true
                                profileMessage = ""

                                val data = hashMapOf<String, Any>(
                                    "name" to cleanName,
                                    "bio" to bioInput.trim(),
                                    "phone" to (currentUser.phoneNumber ?: ""),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )

                                avatarUri?.let {
                                    data["avatarUri"] = it.toString()
                                }

                                db.collection("users")
                                    .document(uid)
                                    .set(data, SetOptions.merge())
                                    .addOnSuccessListener {
                                        savedName = cleanName
                                        savingProfile = false
                                        profileMessage = "Профиль сохранён!"
                                    }
                                    .addOnFailureListener { e ->
                                        savingProfile = false
                                        profileMessage =
                                            "Не удалось сохранить: ${
                                                e.localizedMessage ?: "ошибка Firebase"
                                            }"
                                    }
                            }
                        },
                        saving = savingProfile,
                        message = profileMessage
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
                val tabs = listOf("Чаты", "Люди", "Алекс", "Профиль")
                val icons = listOf("💬", "👥", "✨", "👤")

                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == item ||
                            (item == "Чаты" && tab == "Общий чат"),
                        onClick = { tab = item },
                        icon = {
                            Text(icons[index], fontSize = 20.sp)
                        },
                        label = {
                            Text(
                                item,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
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
    bioInput: String,
    avatarBitmap: Bitmap?,
    onNameChange: (String) -> Unit,
    onBioChange: (String) -> Unit,
    onPickAvatar: () -> Unit,
    onSave: () -> Unit,
    saving: Boolean,
    message: String
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        VibeSectionTitle("Мой профиль", "Твой аккаунт Vibe Chat")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF30205A), Color(0xFF192D55))
                            ),
                            RoundedCornerShape(24.dp)
                        )
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    VibeAvatar(
                        nameInput.ifBlank { savedName.ifBlank { "Vibe" } },
                        100,
                        avatarBitmap
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        nameInput.ifBlank { savedName.ifBlank { "Твоё имя" } },
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        phone.ifBlank { "Телефон не указан" },
                        color = Muted,
                        fontSize = 12.sp
                    )

                    if (bioInput.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            bioInput,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    VibeButton(
                        "📷 Выбрать фотографию",
                        onPickAvatar,
                        Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        "Фотография выбирается из галереи телефона.",
                        color = Muted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Panel, RoundedCornerShape(20.dp))
                        .padding(15.dp)
                ) {
                    Text(
                        "ТВОИ ДАННЫЕ",
                        color = Cyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = onNameChange,
                        label = { Text("Имя") },
                        placeholder = { Text("Как тебя называть?") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = VibeFieldColors
                    )

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = bioInput,
                        onValueChange = onBioChange,
                        label = { Text("О себе") },
                        placeholder = { Text("Напиши пару слов о себе") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 4,
                        colors = VibeFieldColors
                    )

                    Spacer(Modifier.height(12.dp))

                    VibeButton(
                        if (saving) "Сохраняем..." else "Сохранить профиль",
                        onSave,
                        Modifier.fillMaxWidth(),
                        !saving
                    )

                    if (message.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            message,
                            color = if (message == "Профиль сохранён!") Cyan else Pink,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Text(
                    "Vibe Chat — общайся в своём стиле ✨",
                    color = Muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun VibeUsers(
    db: FirebaseFirestore,
    myId: String,
    onOpenProfile: () -> Unit,
    onOpenChat: (String, String) -> Unit
) {
    var users by remember { mutableStateOf<List<VibeUser>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    DisposableEffect(myId) {
        loading = true

        val registration = db.collection("users")
            .addSnapshotListener { snapshot, exception ->
                loading = false

                if (exception != null) {
                    error =
                        "Не удалось загрузить пользователей: ${exception.localizedMessage}"
                } else if (snapshot != null) {
                    users = snapshot.documents.mapNotNull { doc ->
                        val name = doc.getString("name")?.trim().orEmpty()

                        if (name.isBlank()) {
                            null
                        } else {
                            VibeUser(
                                id = doc.id,
                                name = name,
                                phone = doc.getString("phone").orEmpty(),
                                bio = doc.getString("bio").orEmpty()
                            )
                        }
                    }.sortedBy {
                        it.name.lowercase(Locale.getDefault())
                    }

                    error = ""
                }
            }

        onDispose { registration.remove() }
    }

    val visibleUsers = users.filter {
        it.id != myId &&
            (
                it.name.contains(search.trim(), true) ||
                    it.phone.contains(search.trim(), true) ||
                    it.bio.contains(search.trim(), true)
                )
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        VibeSectionTitle("Люди", "Найди друзей и начни общение")

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Поиск по имени или номеру") },
            singleLine = true,
            shape = RoundedCornerShape(15.dp),
            colors = VibeFieldColors
        )

        if (error.isNotBlank()) {
            Text(error, color = Pink, fontSize = 12.sp)
        }

        Spacer(Modifier.height(9.dp))

        when {
            loading -> Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Cyan)
            }

            visibleUsers.isEmpty() -> Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("👥", fontSize = 42.sp)
                    Spacer(Modifier.height(10.dp))

                    Text(
                        if (search.isBlank()) {
                            "Пока никого нет.\nПригласи друга в Vibe Chat!"
                        } else {
                            "Никого не нашли."
                        },
                        color = Muted,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(14.dp))
                    VibeButton("Мой профиль", onOpenProfile)
                }
            }

            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(visibleUsers, key = { it.id }) { person ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Panel, RoundedCornerShape(15.dp))
                            .clickable {
                                onOpenChat(person.id, person.name)
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VibeAvatar(person.name, 40)
                        Spacer(Modifier.width(10.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                person.name,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1
                            )

                            Spacer(Modifier.height(3.dp))

                            Text(
                                person.bio.ifBlank {
                                    person.phone.ifBlank {
                                        "Пользователь Vibe Chat"
                                    }
                                },
                                color = Muted,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }

                        Text("›", color = Cyan, fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun VibeChatsListScreen(
    myId: String,
    onOpenChat: (String, String) -> Unit,
    onOpenGeneralChat: () -> Unit,
    onOpenUsers: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }

    var conversations by remember {
        mutableStateOf<List<ConversationPreview>>(emptyList())
    }
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
            loading = true

            chatsRegistration = db.collection("chats")
                .whereArrayContains("participantIds", myId)
                .addSnapshotListener { snapshot, exception ->
                    if (exception != null) {
                        loading = false
                        error =
                            "Не удалось загрузить чаты: ${exception.localizedMessage}"
                    } else if (snapshot != null) {
                        loading = false
                        error = ""

                        val currentIds = snapshot.documents.map { it.id }.toSet()

                        messageRegistrations.keys
                            .filter { it !in currentIds }
                            .toList()
                            .forEach { id ->
                                messageRegistrations.remove(id)?.remove()
                                previews.remove(id)
                                userLoads.remove(id)
                            }

                        snapshot.documents.forEach { chatDoc ->
                            val participants =
                                chatDoc.get("participantIds") as? List<*>
                                    ?: emptyList<Any>()

                            val otherId = participants
                                .filterIsInstance<String>()
                                .firstOrNull {
                                    it.isNotBlank() && it != myId
                                } ?: return@forEach

                            val chatId = chatDoc.id

                            if (chatId !in previews) {
                                previews[chatId] = ConversationPreview(
                                    chatId = chatId,
                                    otherUserId = otherId,
                                    otherUserName = "Пользователь",
                                    lastText = "Пока нет сообщений",
                                    lastAt = chatDoc.getTimestamp("createdAt")
                                )
                            }

                            if (userLoads.add(chatId)) {
                                db.collection("users")
                                    .document(otherId)
                                    .get()
                                    .addOnSuccessListener { userDoc ->
                                        val old = previews[chatId]

                                        if (old != null) {
                                            val name = userDoc
                                                .getString("name")
                                                ?.takeIf { it.isNotBlank() }
                                                ?: "Пользователь"

                                            previews[chatId] =
                                                old.copy(otherUserName = name)
                                            publish()
                                        }
                                    }
                                    .addOnFailureListener {
                                        userLoads.remove(chatId)
                                    }
                            }

                            if (chatId !in messageRegistrations) {
                                val registration = db.collection("chats")
                                    .document(chatId)
                                    .collection("messages")
                                    .orderBy(
                                        "createdAt",
                                        com.google.firebase.firestore.Query.Direction.DESCENDING
                                    )
                                    .limit(1)
                                    .addSnapshotListener { messages, messageError ->
                                        if (messageError != null) {
                                            error =
                                                "Ошибка обновления сообщений: ${messageError.localizedMessage}"
                                        } else {
                                            val latest =
                                                messages?.documents?.firstOrNull()
                                            val old = previews[chatId]

                                            if (old != null) {
                                                previews[chatId] = old.copy(
                                                    lastText = latest
                                                        ?.getString("text")
                                                        ?: "Пока нет сообщений",
                                                    lastAt = latest
                                                        ?.getTimestamp("createdAt")
                                                        ?: old.lastAt
                                                )
                                                publish()
                                            }
                                        }
                                    }

                                messageRegistrations[chatId] = registration
                            }
                        }

                        previews.keys
                            .filter { it !in currentIds }
                            .toList()
                            .forEach { previews.remove(it) }

                        publish()
                    }
                }
        }

        onDispose {
            chatsRegistration?.remove()
            messageRegistrations.values.forEach { it.remove() }
            messageRegistrations.clear()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        VibeSectionTitle("Чаты", "Твои личные переписки")

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onOpenGeneralChat,
                modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Cyan
                ),
                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 6.dp)
            ) {
                Text("🌐 Общий чат", fontSize = 11.sp)
            }

            VibeButton(
                "＋ Новая переписка",
                onOpenUsers,
                Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        if (error.isNotBlank()) {
            Text(error, color = Pink, fontSize = 11.sp)
        }

        when {
            loading -> Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Cyan)
            }

            conversations.isEmpty() -> Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💬", fontSize = 42.sp)
                    Spacer(Modifier.height(12.dp))

                    Text(
                        "Здесь будут твои переписки",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(7.dp))

                    Text(
                        "Найди собеседника и напиши первое сообщение.",
                        color = Muted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(14.dp))
                    VibeButton("Найти собеседника", onOpenUsers)
                }
            }

            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(conversations, key = { it.chatId }) { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Panel, RoundedCornerShape(14.dp))
                            .clickable {
                                onOpenChat(
                                    item.otherUserId,
                                    item.otherUserName
                                )
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VibeAvatar(item.otherUserName, 42)
                        Spacer(Modifier.width(10.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                item.otherUserName,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )

                            Spacer(Modifier.height(3.dp))

                            Text(
                                item.lastText,
                                color = Muted,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }

                        Spacer(Modifier.width(6.dp))

                        Column(horizontalAlignment = Alignment.End) {
                            item.lastAt?.let { time ->
                                Text(
                                    SimpleDateFormat(
                                        "HH:mm",
                                        Locale.getDefault()
                                    ).format(Date(time.toDate().time)),
                                    color = Cyan,
                                    fontSize = 10.sp
                                )
                            }

                            Text("›", color = Purple, fontSize = 22.sp)
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
            registration = db.collection("chats")
                .document("general")
                .collection("messages")
                .orderBy("createdAt")
                .addSnapshotListener { snapshot, exception ->
                    if (exception != null) {
                        error =
                            "Не удалось загрузить сообщения: ${exception.localizedMessage}"
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

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        VibeSectionTitle("Общий чат", "Общение со всеми")

        if (error.isNotBlank()) {
            Text(error, color = Pink, fontSize = 11.sp)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val own = msg.authorId == user?.uid

                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment =
                        if (own) Alignment.End else Alignment.Start
                ) {
                    Text(
                        if (own) "Ты" else msg.author,
                        color = if (own) Cyan else Pink,
                        fontSize = 10.sp
                    )

                    Text(
                        msg.text,
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .background(
                                if (own) {
                                    Brush.linearGradient(listOf(Purple, Blue))
                                } else {
                                    Brush.linearGradient(listOf(PanelLight, Panel))
                                },
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    )
                }
            }
        }

        Row(
            Modifier.padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = {
                    if (it.length <= 1000) messageText = it
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Напиши сообщение...") },
                shape = RoundedCornerShape(14.dp),
                colors = VibeFieldColors
            )

            Spacer(Modifier.width(7.dp))

            VibeButton(
                if (sending) "…" else "➤",
                onClick = {
                    val text = messageText.trim()
                    val current = auth.currentUser

                    if (text.isNotBlank() && current != null && !sending) {
                        sending = true

                        db.collection("chats")
                            .document("general")
                            .collection("messages")
                            .add(
                                hashMapOf(
                                    "text" to text,
                                    "authorId" to current.uid,
                                    "author" to (
                                        current.phoneNumber ?: "Пользователь"
                                    ),
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
                                error =
                                    "Не удалось отправить: ${e.localizedMessage}"
                            }
                    }
                },
                enabled = messageText.isNotBlank() && !sending && user != null
            )
        }
    }
}

private data class AlexMessage(
    val fromUser: Boolean,
    val text: String
)

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
                    "Привет! Я Алекс AI ✨\n\n" +
                        "Я твой помощник внутри Vibe Chat. " +
                        "Напиши вопрос — постараюсь помочь."
                )
            )
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF30205A), Color(0xFF192D55))
                    ),
                    RoundedCornerShape(18.dp)
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VibeAvatar("A", 44)
            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    "Алекс",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    if (isLoading) "Думаю..." else "Твой AI-помощник",
                    color = Cyan,
                    fontSize = 11.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages) { message ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        if (message.fromUser) Arrangement.End
                        else Arrangement.Start
                ) {
                    Text(
                        message.text,
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .widthIn(max = 310.dp)
                            .background(
                                if (message.fromUser) {
                                    Brush.linearGradient(listOf(Purple, Blue))
                                } else {
                                    Brush.linearGradient(listOf(PanelLight, Panel))
                                },
                                RoundedCornerShape(17.dp)
                            )
                            .padding(horizontal = 13.dp, vertical = 10.dp)
                    )
                }
            }

            if (isLoading) {
                item {
                    CircularProgressIndicator(
                        Modifier.size(22.dp),
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
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 5.dp)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(1000) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Спроси Алекса...") },
                shape = RoundedCornerShape(14.dp),
                maxLines = 4,
                colors = VibeFieldColors
            )

            Spacer(Modifier.width(7.dp))

            VibeButton(
                if (isLoading) "…" else "➤",
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
                                        "Помогай писать промпты для фото и видео.\n\n" +
                                        "Сообщение пользователя: $question"

                                val response = model.generateContent(prompt)

                                val answer = response.text
                                    ?.takeIf { it.isNotBlank() }
                                    ?: "Gemini не вернул текст. Попробуй ещё раз."

                                messages = messages + AlexMessage(false, answer)
                            } catch (e: Exception) {
                                error =
                                    "Не удалось получить ответ: ${
                                        e.localizedMessage
                                            ?: "проверь Firebase AI Logic"
                                    }"
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                }
            )
        }
    }
}
