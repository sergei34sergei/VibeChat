
package com.vibe.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.Timestamp
import com.google.firebase.initialize
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
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
        listOf(
            Color(0xFF090B1A),
            Color(0xFF11152D),
            Color(0xFF101B35)
        )
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

private fun bitmapToAvatarBase64(bitmap: Bitmap): String {
    val maxSide = 256

    val scale = minOf(
        maxSide.toFloat() / bitmap.width,
        maxSide.toFloat() / bitmap.height,
        1f
    )

    val resized = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    } else {
        bitmap
    }

    val output = ByteArrayOutputStream()

    resized.compress(
        Bitmap.CompressFormat.JPEG,
        60,
        output
    )

    if (resized !== bitmap) {
        resized.recycle()
    }

    return Base64.encodeToString(
        output.toByteArray(),
        Base64.NO_WRAP
    )
}

private fun avatarBase64ToBitmap(value: String?): Bitmap? {
    if (value.isNullOrBlank()) return null

    return try {
        val bytes = Base64.decode(value, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) {
        null
    }
}

private fun readableTime(timestamp: Timestamp?): String {
    if (timestamp == null) return ""

    return SimpleDateFormat(
        "HH:mm",
        Locale.getDefault()
    ).format(Date(timestamp.toDate().time))
}

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
        Text(
            text,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
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
            contentScale = ContentScale.Crop,
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
private fun VibeSectionTitle(
    title: String,
    subtitle: String
) {
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

        Text(
            subtitle,
            color = Muted,
            fontSize = 12.sp
        )
    }
}

class MainActivity : ComponentActivity() {

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

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
            status =
                "Введи номер с кодом страны, например +49123456789"
            return
        }

        busy = true
        status = "Отправляем SMS-код..."

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(number)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(
                object :
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
                                    status =
                                        "Не удалось войти: ${
                                            task.exception?.localizedMessage
                                                ?: "ошибка"
                                        }"
                                }
                            }
                    }

                    override fun onVerificationFailed(
                        e: FirebaseException
                    ) {
                        busy = false
                        status =
                            "Ошибка: ${e.localizedMessage ?: "проверь номер"}"
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
                }
            )
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
                status =
                    "Ошибка входа: ${
                        task.exception?.localizedMessage
                            ?: "неверный код"
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
    val bio: String = "",
    val avatarBase64: String? = null
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
fun VibeHome(
    phone: String,
    onLogout: () -> Unit
) {
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
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Некоторые источники не поддерживают сохранение разрешения.
            }

            avatarUri = uri
            profileMessage =
                "Фото выбрано. Нажми «Сохранить профиль»."
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
                        savedName =
                            snapshot.getString("name").orEmpty()

                        if (!profileLoaded) {
                            nameInput = savedName
                            bioInput =
                                snapshot.getString("bio").orEmpty()

                            avatarBitmap = avatarBase64ToBitmap(
                                snapshot.getString("avatarBase64")
                            )

                            avatarUri = null
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
                        .background(
                            VibeGradient,
                            RoundedCornerShape(13.dp)
                        ),
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
                        onOpenGeneralChat = {
                            tab = "Общий чат"
                        },
                        onOpenUsers = {
                            tab = "Люди"
                        }
                    )

                    "Люди" -> VibeUsers(
                        db = db,
                        myId = myId,
                        onOpenProfile = {
                            tab = "Профиль"
                        },
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
                        profileMessage = profileMessage,
                        savingProfile = savingProfile,
                        onNameChange = {
                            nameInput = it.take(40)
                        },
                        onBioChange = {
                            bioInput = it.take(160)
                        },
                        onPickAvatar = {
                            avatarPicker.launch(arrayOf("image/*"))
                        },
                        onSave = {
                            val uid = currentUser?.uid
                            val cleanName = nameInput.trim()

                            if (uid == null) {
                                profileMessage = "Сначала войди в аккаунт."
                            } else if (cleanName.isBlank()) {
                                profileMessage = "Введи имя."
                            } else {
                                savingProfile = true
                                profileMessage = "Сохраняем профиль..."

                                val data = hashMapOf<String, Any>(
                                    "name" to cleanName,
                                    "bio" to bioInput.trim(),
                                    "phone" to phone,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )

                                avatarBitmap?.let {
                                    data["avatarBase64"] =
                                        bitmapToAvatarBase64(it)
                                }

                                db.collection("users")
                                    .document(uid)
                                    .set(data, SetOptions.merge())
                                    .addOnSuccessListener {
                                        savingProfile = false
                                        savedName = cleanName
                                        profileMessage = "Профиль сохранён!"
                                    }
                                    .addOnFailureListener { e ->
                                        savingProfile = false
                                        profileMessage =
                                            "Ошибка сохранения: ${e.localizedMessage}"
                                    }
                            }
                        }
                    )
                }
            }
        }

        if (privateChatUserId == null) {
            NavigationBar(
                containerColor = Color(0xFF0D1024)
            ) {
                listOf(
                    "Чаты",
                    "Люди",
                    "Общий чат",
                    "Алекс",
                    "Профиль"
                ).forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            Text(
                                when (item) {
                                    "Чаты" -> "✉"
                                    "Люди" -> "👥"
                                    "Общий чат" -> "🌐"
                                    "Алекс" -> "✦"
                                    else -> "☺"
                                },
                                fontSize = 18.sp
                            )
                        },
                        label = {
                            Text(item, fontSize = 9.sp)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Cyan,
                            selectedTextColor = Cyan,
                            indicatorColor = PanelLight,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun VibeProfile(
    phone: String,
    savedName: String,
    nameInput: String,
    bioInput: String,
    avatarBitmap: Bitmap?,
    profileMessage: String,
    savingProfile: Boolean,
    onNameChange: (String) -> Unit,
    onBioChange: (String) -> Unit,
    onPickAvatar: () -> Unit,
    onSave: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        VibeSectionTitle(
            "Мой профиль",
            "Как тебя видят другие пользователи"
        )

        Spacer(Modifier.height(12.dp))

        VibeAvatar(
            nameInput.ifBlank { savedName.ifBlank { "V" } },
            104,
            avatarBitmap
        )

        TextButton(onClick = onPickAvatar) {
            Text("Выбрать фото профиля", color = Cyan)
        }

        OutlinedTextField(
            value = nameInput,
            onValueChange = onNameChange,
            label = { Text("Имя") },
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
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 4,
            shape = RoundedCornerShape(14.dp),
            colors = VibeFieldColors
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = {},
            readOnly = true,
            label = { Text("Телефон") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = VibeFieldColors
        )

        Spacer(Modifier.height(16.dp))

        VibeButton(
            if (savingProfile) "Сохраняем..." else "Сохранить профиль",
            onSave,
            Modifier.fillMaxWidth(),
            !savingProfile
        )

        if (savedName.isNotBlank()) {
            Text(
                "Текущее имя: $savedName",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        if (profileMessage.isNotBlank()) {
            Text(
                profileMessage,
                color = if (profileMessage.startsWith("Ошибка")) Pink else Cyan,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun VibeUsers(
    db: FirebaseFirestore,
    myId: String,
    onOpenProfile: () -> Unit,
    onOpenChat: (String, String) -> Unit
) {
    var users by remember { mutableStateOf<List<VibeUser>>(emptyList()) }
    var error by remember { mutableStateOf("") }

    DisposableEffect(myId) {
        val reg = db.collection("users")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    error = e.localizedMessage ?: "Ошибка загрузки"
                } else if (snapshot != null) {
                    users = snapshot.documents
                        .filter { it.id != myId }
                        .map { doc ->
                            VibeUser(
                                id = doc.id,
                                name = doc.getString("name")
                                    ?.takeIf { it.isNotBlank() }
                                    ?: "Пользователь",
                                phone = doc.getString("phone").orEmpty(),
                                bio = doc.getString("bio").orEmpty(),
                                avatarBase64 =
                                    doc.getString("avatarBase64")
                            )
                        }
                        .sortedBy {
                            it.name.lowercase(Locale.getDefault())
                        }
                }
            }

        onDispose {
            reg.remove()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                VibeSectionTitle(
                    "Люди",
                    "Пользователи Vibe Chat"
                )
            }

            TextButton(onClick = onOpenProfile) {
                Text("Мой профиль", color = Cyan)
            }
        }

        if (error.isNotBlank()) {
            Text(error, color = Pink, fontSize = 12.sp)
        }

        if (users.isEmpty() && error.isBlank()) {
            Text(
                "Пока других пользователей нет. Пригласи друзей!",
                color = Muted,
                fontSize = 13.sp
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(users, key = { it.id }) { person ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Panel, RoundedCornerShape(18.dp))
                        .clickable {
                            onOpenChat(person.id, person.name)
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VibeAvatar(
                        person.name,
                        48,
                        avatarBase64ToBitmap(person.avatarBase64)
                    )

                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            person.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            person.bio.ifBlank { person.phone },
                            color = Muted,
                            fontSize = 12.sp,
                            maxLines = 2
                        )
                    }

                    Text(
                        "Написать",
                        color = Cyan,
                        fontSize = 11.sp
                    )
                }
            }
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

    var previews by remember {
        mutableStateOf<List<ConversationPreview>>(emptyList())
    }

    var error by remember { mutableStateOf("") }

    DisposableEffect(myId) {
        var profileReg: ListenerRegistration? = null
        var chatsReg: ListenerRegistration? = null

        if (myId.isNotBlank()) {
            profileReg = db.collection("users")
                .document(myId)
                .addSnapshotListener { _, _ -> }

            chatsReg = db.collection("chats")
                .whereArrayContains("members", myId)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        error =
                            e.localizedMessage ?: "Не удалось загрузить чаты"
                    } else if (snapshot != null) {
                        previews = snapshot.documents.map { doc ->
                            val members =
                                doc.get("members") as? List<*>
                                    ?: emptyList<Any>()

                            val other = members
                                .filterIsInstance<String>()
                                .firstOrNull { it != myId }
                                .orEmpty()

                            ConversationPreview(
                                chatId = doc.id,
                                otherUserId = other,
                                otherUserName =
                                    doc.getString("otherName_$myId")
                                        ?: "Собеседник",
                                lastText =
                                    doc.getString("lastText").orEmpty(),
                                lastAt = doc.getTimestamp("lastAt")
                            )
                        }.sortedByDescending {
                            it.lastAt?.toDate()?.time ?: 0L
                        }
                    }
                }
        }

        onDispose {
            profileReg?.remove()
            chatsReg?.remove()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        VibeSectionTitle(
            "Мои чаты",
            "Личные сообщения и общение"
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VibeButton(
                "Общий чат",
                onOpenGeneralChat,
                Modifier.weight(1f)
            )

            VibeButton(
                "Найти людей",
                onOpenUsers,
                Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        if (error.isNotBlank()) {
            Text(error, color = Pink, fontSize = 12.sp)
        }

        if (previews.isEmpty() && error.isBlank()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 30.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("✉", fontSize = 40.sp)

                    Text(
                        "Здесь появятся твои переписки",
                        color = Muted,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Открой «Люди» и выбери собеседника.",
                        color = Muted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(previews, key = { it.chatId }) { chat ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Panel, RoundedCornerShape(16.dp))
                        .clickable {
                            if (chat.otherUserId.isNotBlank()) {
                                onOpenChat(
                                    chat.otherUserId,
                                    chat.otherUserName
                                )
                            }
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VibeAvatar(chat.otherUserName, 46)

                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            chat.otherUserName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            chat.lastText.ifBlank { "Начни общение" },
                            color = Muted,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    Text(
                        readableTime(chat.lastAt),
                        color = Cyan,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun VibeGeneralChat(phone: String) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val uid = auth.currentUser?.uid.orEmpty()

    var myName by remember { mutableStateOf("Пользователь") }
    var text by remember { mutableStateOf("") }
    var messages by remember {
        mutableStateOf<List<ChatMessage>>(emptyList())
    }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    DisposableEffect(uid) {
        var profileReg: ListenerRegistration? = null

        if (uid.isNotBlank()) {
            profileReg = db.collection("users")
                .document(uid)
                .addSnapshotListener { snapshot, _ ->
                    myName = snapshot?.getString("name")
                        ?.ifBlank { null }
                        ?: phone.ifBlank { "Пользователь" }
                }
        }

        val messagesReg = db.collection("generalChat")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(200)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    error =
                        e.localizedMessage ?: "Ошибка загрузки общего чата"
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
                }
            }

        onDispose {
            profileReg?.remove()
            messagesReg.remove()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        VibeSectionTitle(
            "Общий чат",
            "Сообщения для всех пользователей"
        )

        if (error.isNotBlank()) {
            Text(error, color = Pink, fontSize = 11.sp)
        }

        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val mine = msg.authorId == uid

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (mine) 38.dp else 0.dp,
                            end = if (mine) 0.dp else 38.dp
                        )
                        .background(
                            if (mine) Color(0xFF343064) else Panel,
                            RoundedCornerShape(14.dp)
                        )
                        .padding(10.dp)
                ) {
                    Text(
                        if (mine) "Ты"
                        else msg.author.ifBlank { "Пользователь" },
                        color = Cyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        msg.text,
                        color = Color.White,
                        fontSize = 14.sp
                    )

                    Text(
                        readableTime(msg.time),
                        color = Muted,
                        fontSize = 9.sp,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(1000) },
                placeholder = { Text("Напиши сообщение...") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                colors = VibeFieldColors
            )

            Spacer(Modifier.width(8.dp))

            VibeButton(
                if (sending) "…" else "➤",
                {
                    val clean = text.trim()

                    if (clean.isNotBlank() &&
                        uid.isNotBlank() &&
                        !sending
                    ) {
                        sending = true

                        db.collection("generalChat")
                            .add(
                                hashMapOf(
                                    "authorId" to uid,
                                    "author" to myName,
                                    "text" to clean,
                                    "phone" to phone,
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                            )
                            .addOnSuccessListener {
                                text = ""
                                sending = false
                            }
                            .addOnFailureListener { e ->
                                sending = false
                                error = e.localizedMessage
                                    ?: "Не удалось отправить"
                            }
                    }
                },
                enabled = text.isNotBlank() && !sending
            )
        }
    }
}

@Composable
private fun AlexAiScreen() {
    val scope = rememberCoroutineScope()

    var prompt by remember { mutableStateOf("") }
    var answer by remember {
        mutableStateOf(
            "Привет! Я Алекс — AI-помощник Vibe Chat. Чем помочь?"
        )
    }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val model = remember {
        Firebase.ai(
            backend = GenerativeBackend.googleAI()
        ).generativeModel("gemini-3.8-flash")
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        VibeSectionTitle(
            "Алекс AI",
            "Твой помощник прямо в Vibe Chat"
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Panel, RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text(
                        answer,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }

                if (error.isNotBlank()) {
                    item {
                        Text(
                            error,
                            color = Pink,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it.take(2000) },
            placeholder = { Text("Спроси Алекса...") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4,
            shape = RoundedCornerShape(14.dp),
            colors = VibeFieldColors
        )

        Spacer(Modifier.height(8.dp))

        VibeButton(
            if (loading) "Алекс думает..." else "Отправить",
            {
                val question = prompt.trim()

                if (question.isNotBlank() && !loading) {
                    loading = true
                    error = ""
                    answer = "Ты: $question\n\nАлекс печатает ответ..."
                    prompt = ""

                    scope.launch {
                        try {
                            val response = model.generateContent(question)

                            answer =
                                "Ты: $question\n\nАлекс: ${
                                    response.text ?: "Не удалось получить ответ."
                                }"
                        } catch (e: Exception) {
                            error =
                                "Ошибка AI: ${
                                    e.localizedMessage
                                        ?: "проверь настройки Firebase AI"
                                }"
                            answer = "Не удалось получить ответ."
                        } finally {
                            loading = false
                        }
                    }
                }
            },
            Modifier.fillMaxWidth(),
            enabled = !loading && prompt.isNotBlank()
        )
    }
}
