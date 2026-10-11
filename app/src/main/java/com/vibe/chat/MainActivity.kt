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
import com.google.firebase.firestore.FirebaseFirestoreException
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

// =====================================================
// VIBE NEON — ЦВЕТА И ФОН
// =====================================================

private val Purple = Color(0xFFAC55FF)
private val Cyan = Color(0xFF45E8FF)
private val Dark = Color(0xFF080914)
private val Panel = Color(0xFF15182D)
private val PanelLight = Color(0xFF232746)
private val Pink = Color(0xFFFF4FA3)
private val Blue = Color(0xFF4169FF)
private val Muted = Color(0xFFABB4D1)
private val BorderNeon = Color(0xFF41466C)

private val VibeGradient = Brush.linearGradient(
    colors = listOf(Purple, Blue, Cyan)
)

private val VibeBackground = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF080914),
        Color(0xFF11152D),
        Color(0xFF101B35),
        Color(0xFF080914)
    )
)

private val VibeCardGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF1B2040),
        Color(0xFF14172B)
    )
)

private val VibeFieldColors
    @Composable get() = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedContainerColor = Color(0xFF101326),
        unfocusedContainerColor = Color(0xFF101326),
        focusedBorderColor = Cyan,
        unfocusedBorderColor = BorderNeon,
        focusedLabelColor = Cyan,
        unfocusedLabelColor = Muted,
        focusedPlaceholderColor = Muted,
        unfocusedPlaceholderColor = Muted,
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
    resized.compress(Bitmap.CompressFormat.JPEG, 60, output)

    if (resized !== bitmap) {
        resized.recycle()
    }

    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
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

    return SimpleDateFormat("HH:mm", Locale.getDefault())
        .format(Date(timestamp.toDate().time))
}

private fun firestoreErrorDetails(
    title: String,
    error: Exception,
    uid: String
): String {
    val firestoreCode =
        (error as? FirebaseFirestoreException)?.code?.name
            ?: "не FirestoreException"

    return "$title\nКод: $firestoreCode\nUID: $uid\nПричина: ${
        error.localizedMessage ?: error.javaClass.simpleName
    }"
}

// =====================================================
// НЕОНОВАЯ КНОПКА
// =====================================================

@Composable
private fun VibeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (enabled) {
                    VibeGradient
                } else {
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF30334B),
                            Color(0xFF25283E)
                        )
                    )
                }
            )
            .clickable(
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

// =====================================================
// НЕОНОВЫЙ АВАТАР
// =====================================================

@Composable
private fun VibeAvatar(
    name: String,
    size: Int = 44,
    bitmap: Bitmap? = null
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(VibeGradient)
            .padding(2.dp)
            .clip(CircleShape)
            .background(Panel),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Аватар",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "V",
                color = Color.White,
                fontSize = (size * 0.38f).sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

// =====================================================
// ЗАГОЛОВКИ
// =====================================================

@Composable
private fun VibeSectionTitle(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = subtitle,
            color = Cyan,
            fontSize = 12.sp
        )
    }
}

// =====================================================
// FIREBASE И ВХОД ПО SMS
// =====================================================

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
                    surface = Panel,
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

                    override fun onVerificationFailed(e: FirebaseException) {
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
                        task.exception?.localizedMessage ?: "неверный код"
                    }"
            }
        }
    }
}

// =====================================================
// ЭКРАН ВХОДА
// =====================================================

@Composable
fun VibeLogin(
    status: String,
    busy: Boolean,
    onSendCode: (String) -> Unit,
    onVerifyCode: (String) -> Unit
) {
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val codeSent = status.contains("Код отправлен", true) || status.contains("Введи код вручную", true)

    Box(Modifier.fillMaxSize().background(VibeBackground).statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.align(Alignment.TopEnd).padding(top = 26.dp, end = 8.dp).size(170.dp).clip(CircleShape).background(Color(0x222DDBFF)))
        Box(Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 46.dp).size(210.dp).clip(CircleShape).background(Color(0x22AC55FF)))
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(VibeGradient).padding(2.dp).clip(RoundedCornerShape(26.dp)).background(Panel), contentAlignment = Alignment.Center) {
                Text("V", color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(18.dp))
            Text("Твой мир ближе", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("V I B E   C H A T", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text("Общайся по-настоящему.\nНаходи своих людей.", color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 21.sp)
            Spacer(Modifier.height(24.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(VibeCardGradient).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x223EDCFF)), contentAlignment = Alignment.Center) {
                        Text(if (codeSent) "✓" else "✦", color = Cyan, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (codeSent) "Проверь свой телефон" else "Рады тебя видеть", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(if (codeSent) "Введи код из SMS ниже" else "Войди по номеру телефона", color = Muted, fontSize = 12.sp)
                    }
                }
                OutlinedTextField(value = phone, onValueChange = { phone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(16) }, label = { Text("Номер телефона") }, placeholder = { Text("+49 123 456789") }, leadingIcon = { Text("☎", color = Cyan, fontSize = 18.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = VibeFieldColors)
                VibeButton(text = if (busy && !codeSent) "Отправляем код…" else "Получить SMS-код  →", onClick = { onSendCode(phone) }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(1.dp).background(BorderNeon))
                    Text("  ПОДТВЕРЖДЕНИЕ  ", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Box(Modifier.weight(1f).height(1.dp).background(BorderNeon))
                }
                OutlinedTextField(value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) }, label = { Text("6-значный код") }, placeholder = { Text("• • • • • •") }, leadingIcon = { Text("⌑", color = Cyan, fontSize = 22.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = VibeFieldColors)
                VibeButton(text = if (busy && codeSent) "Проверяем…" else "Войти в Vibe Chat  ✨", onClick = { onVerifyCode(code) }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                if (busy) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Cyan, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(9.dp)); Text("Секундочку, мы рядом…", color = Muted, fontSize = 11.sp)
                }
                if (status.isNotBlank()) Text(status, color = if (status.startsWith("Ошибка")) Pink else Cyan, textAlign = TextAlign.Center, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(16.dp))
            Text("Твоё общение. Твои люди. Твой Vibe.", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}
// =====================================================
// МОДЕЛИ ДАННЫХ
// =====================================================

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


// =====================================================
// ГЛАВНЫЙ ЭКРАН И НИЖНЕЕ МЕНЮ
// =====================================================

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
                        profileMessage = firestoreErrorDetails(
                            "Ошибка загрузки профиля",
                            exception,
                            myId
                        )
                    } else if (snapshot != null) {
                        savedName = snapshot.getString("name").orEmpty()

                        if (!profileLoaded) {
                            nameInput = savedName
                            bioInput = snapshot.getString("bio").orEmpty()
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
            context.contentResolver.openInputStream(uri)?.use {
                avatarBitmap = BitmapFactory.decodeStream(it)
            }
        } catch (_: Exception) {
            avatarBitmap = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VibeBackground)
            .statusBarsPadding()
    ) {
        if (privateChatUserId == null) {
            Row(
                modifier = Modifier
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
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(VibeGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "V",
                        color = Color.White,
                        fontSize = 27.sp,
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
                            else -> "Твой аккаунт"
                        },
                        color = Cyan,
                        fontSize = 11.sp
                    )
                }

                TextButton(onClick = onLogout) {
                    Text(
                        "Выйти",
                        color = Pink,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (privateChatUserId != null) {
                PrivateChatScreen(
                    otherUserId = privateChatUserId!!,
                    otherUserName = privateChatUserName,
                    onBack = {
                        privateChatUserId = null
                    }
                )
            } else {
                when (tab) {
                    "Чаты" -> VibeChatsListScreen(
                        myId = myId,
                        onOpenChat = { id, name ->
                            privateChatUserId = id
                            privateChatUserName = name
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
                                        profileMessage = firestoreErrorDetails(
                                            "Ошибка сохранения профиля",
                                            e,
                                            uid
                                        )
                                    }
                            }
                        }
                    )
                }
            }
        }

        if (privateChatUserId == null) {
            NavigationBar(
                containerColor = Color(0xFF0B0D20),
                contentColor = Color.White,
                tonalElevation = 0.dp
            ) {
                val menuItems = listOf(
                    Triple("Чаты", "✉", "Переписки"),
                    Triple("Люди", "◎", "Пользователи"),
                    Triple("Алекс", "✦", "AI-помощник"),
                    Triple("Профиль", "◉", "Профиль")
                )

                menuItems.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item.first,
                        onClick = {
                            tab = item.first
                        },
                        icon = {
                            Text(
                                text = item.second,
                                color = if (tab == item.first) Cyan else Muted,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        label = {
                            Text(item.first, fontSize = 9.sp)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Cyan,
                            selectedTextColor = Cyan,
                            indicatorColor = Color(0xFF29254C),
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    }
}

// =====================================================
// ПРОФИЛЬ
// =====================================================

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
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        VibeSectionTitle(
            "Мой профиль",
            "Как тебя видят другие пользователи"
        )

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(VibeGradient)
                .padding(3.dp)
        ) {
            VibeAvatar(
                nameInput.ifBlank { savedName.ifBlank { "V" } },
                104,
                avatarBitmap
            )
        }

        TextButton(onClick = onPickAvatar) {
            Text("＋ Выбрать фото профиля", color = Cyan)
        }

        OutlinedTextField(
            value = nameInput,
            onValueChange = onNameChange,
            label = { Text("Имя") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
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
            shape = RoundedCornerShape(16.dp),
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
            shape = RoundedCornerShape(16.dp),
            colors = VibeFieldColors
        )

        Spacer(Modifier.height(16.dp))

        VibeButton(
            text = if (savingProfile) {
                "Сохраняем..."
            } else {
                "Сохранить профиль"
            },
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = !savingProfile
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
                text = profileMessage,
                color = if (profileMessage.startsWith("Ошибка")) Pink else Cyan,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

// =====================================================
// СПИСОК ЛЮДЕЙ
// =====================================================

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
                    error = firestoreErrorDetails(
                        "Ошибка загрузки пользователей",
                        e,
                        myId
                    )
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
                                avatarBase64 = doc.getString("avatarBase64")
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
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                VibeSectionTitle("Люди", "Пользователи Vibe Chat")
            }

            TextButton(onClick = onOpenProfile) {
                Text("Мой профиль", color = Cyan)
            }
        }

        if (error.isNotBlank()) {
            Text(error, color = Pink, fontSize = 12.sp)
        }

        if (users.isEmpty() && error.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("◎", color = Cyan, fontSize = 42.sp)

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Пока других пользователей нет.",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "Пригласи друзей в Vibe Chat!",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(users, key = { it.id }) { person ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(VibeCardGradient)
                        .clickable {
                            onOpenChat(person.id, person.name)
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VibeAvatar(
                        person.name,
                        50,
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

                    Text("➤", color = Cyan, fontSize = 18.sp)
                }
            }
        }
    }
}

// =====================================================
// СПИСОК ЛИЧНЫХ ЧАТОВ
// =====================================================

@Composable
private fun VibeChatsListScreen(
    myId: String,
    onOpenChat: (String, String) -> Unit,
    onOpenUsers: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }

    var previews by remember {
        mutableStateOf<List<ConversationPreview>>(emptyList())
    }
    var error by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    DisposableEffect(myId) {
        var chatsReg: ListenerRegistration? = null
        val messageRegs = mutableMapOf<String, ListenerRegistration>()
        val userRegs = mutableMapOf<String, ListenerRegistration>()
        val participantsByChat = mutableMapOf<String, String>()
        val latestMessages = mutableMapOf<String, Pair<String, Timestamp?>>()
        val userNames = mutableMapOf<String, String>()
        var activeChatIds = emptySet<String>()

        fun refreshPreviews() {
            previews = activeChatIds.mapNotNull { chatId ->
                val otherId = participantsByChat[chatId]
                    ?: return@mapNotNull null
                val last = latestMessages[chatId]

                ConversationPreview(
                    chatId = chatId,
                    otherUserId = otherId,
                    otherUserName = userNames[otherId] ?: "Собеседник",
                    lastText = last?.first.orEmpty(),
                    lastAt = last?.second
                )
            }.sortedWith(
                compareByDescending<ConversationPreview> {
                    it.lastAt?.toDate()?.time ?: 0L
                }.thenBy {
                    it.otherUserName.lowercase(Locale.getDefault())
                }
            )
        }

        if (myId.isBlank()) {
            error = "Не удалось определить аккаунт. Перезайди в приложение."
        } else {
            chatsReg = db.collection("chats")
                .whereArrayContains("participantIds", myId)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        error = firestoreErrorDetails(
                            "Ошибка загрузки списка чатов",
                            e,
                            myId
                        )
                        return@addSnapshotListener
                    }

                    if (snapshot == null) return@addSnapshotListener

                    error = ""
                    val documents = snapshot.documents
                    activeChatIds = documents.map { it.id }.toSet()

                    (messageRegs.keys - activeChatIds)
                        .toList()
                        .forEach { chatId ->
                            messageRegs.remove(chatId)?.remove()
                            participantsByChat.remove(chatId)
                            latestMessages.remove(chatId)
                        }

                    val currentOtherIds = documents.mapNotNull { doc ->
                        val ids = doc.get("participantIds")
                            as? List<*> ?: emptyList<Any>()
                        ids.filterIsInstance<String>()
                            .firstOrNull { it != myId }
                    }.toSet()

                    (userRegs.keys - currentOtherIds)
                        .toList()
                        .forEach { uid ->
                            userRegs.remove(uid)?.remove()
                            userNames.remove(uid)
                        }

                    documents.forEach { doc ->
                        val ids = doc.get("participantIds")
                            as? List<*> ?: emptyList<Any>()

                        val otherId = ids.filterIsInstance<String>()
                            .firstOrNull { it != myId }.orEmpty()

                        if (otherId.isBlank()) return@forEach

                        participantsByChat[doc.id] = otherId

                        if (otherId !in userRegs) {
                            userRegs[otherId] =
                                db.collection("users")
                                    .document(otherId)
                                    .addSnapshotListener userListener@{
                                            userDoc, userError ->
                                        if (userError != null) {
                                            error = firestoreErrorDetails(
                                                "Ошибка чтения профиля собеседника",
                                                userError,
                                                myId
                                            )
                                            return@userListener
                                        }

                                        userNames[otherId] =
                                            userDoc?.getString("name")
                                                ?.takeIf { it.isNotBlank() }
                                                ?: "Пользователь"

                                        refreshPreviews()
                                    }
                        }

                        if (doc.id !in messageRegs) {
                            val chatId = doc.id

                            messageRegs[chatId] =
                                db.collection("chats")
                                    .document(chatId)
                                    .collection("messages")
                                    .orderBy(
                                        "createdAt",
                                        Query.Direction.DESCENDING
                                    )
                                    .limit(1)
                                    .addSnapshotListener messageListener@{
                                            messages, messageError ->
                                        if (messageError != null) {
                                            error = firestoreErrorDetails(
                                                "Ошибка чтения сообщений чата $chatId",
                                                messageError,
                                                myId
                                            )
                                            return@messageListener
                                        }

                                        val lastDoc =
                                            messages?.documents?.firstOrNull()

                                        latestMessages[chatId] = Pair(
                                            lastDoc?.getString("text").orEmpty(),
                                            lastDoc?.getTimestamp("createdAt")
                                        )

                                        refreshPreviews()
                                    }
                        }
                    }

                    refreshPreviews()
                }
        }

        onDispose {
            chatsReg?.remove()
            messageRegs.values.forEach { it.remove() }
            userRegs.values.forEach { it.remove() }
        }
    }

    val filteredPreviews = previews.filter { chat ->
        chat.otherUserName.contains(searchQuery.trim(), ignoreCase = true) ||
            chat.lastText.contains(searchQuery.trim(), ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Сообщения",
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "Твои разговоры в одном месте",
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(VibeGradient),
                contentAlignment = Alignment.Center
            ) {
                Text("✦", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Поиск по чатам", color = Muted) },
            leadingIcon = { Text("⌕", color = Cyan, fontSize = 25.sp) },
            shape = RoundedCornerShape(18.dp),
            colors = VibeFieldColors
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Личные чаты", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (previews.isEmpty()) "Начни новый разговор" else "${previews.size} переписок",
                    color = Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            TextButton(onClick = onOpenUsers) {
                Text("＋ Новый чат", color = Cyan, fontWeight = FontWeight.Bold)
            }
        }

        if (error.isNotBlank()) {
            Text(
                text = error,
                color = Pink,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        if (previews.isEmpty() && error.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 18.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(VibeCardGradient)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(82.dp)
                            .clip(CircleShape)
                            .background(VibeGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✉", color = Color.White, fontSize = 38.sp)
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Пока здесь тихо",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Найди друзей в Vibe Chat и отправь первое сообщение. Твои переписки появятся здесь.",
                        color = Muted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    VibeButton(
                        text = "Найти людей  →",
                        onClick = onOpenUsers,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else if (filteredPreviews.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("Ничего не найдено", color = Muted, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 4.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredPreviews, key = { it.chatId }) { chat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(VibeCardGradient)
                            .clickable {
                                if (chat.otherUserId.isNotBlank()) {
                                    onOpenChat(chat.otherUserId, chat.otherUserName)
                                }
                            }
                            .padding(horizontal = 13.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VibeAvatar(chat.otherUserName, 54)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                chat.otherUserName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                chat.lastText.ifBlank { "Начни общение ✨" },
                                color = Muted,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(readableTime(chat.lastAt), color = Cyan, fontSize = 10.sp)
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x332DDBFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("›", color = Cyan, fontSize = 22.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}


// =====================================================
// АЛЕКС AI
// =====================================================

private data class AlexBubble(val fromUser: Boolean, val text: String)

@Composable
private fun AlexAiScreen() {
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val conversation = remember {
        mutableStateListOf(AlexBubble(false, "Привет! Я Алекс 💜\n\nЯ твой AI-помощник внутри Vibe Chat. Можем придумать идею, разобраться в сложной ситуации, написать сообщение или просто поговорить.\n\nС чего начнём?"))
    }
    val model = remember { Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel("gemini-3.8-flash") }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(VibeCardGradient).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp).clip(CircleShape).background(VibeGradient).padding(2.dp).clip(CircleShape).background(Panel), contentAlignment = Alignment.Center) {
                Text("✦", color = Cyan, fontSize = 31.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Алекс", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (loading) "Собираю мысли…" else "Твой собеседник и помощник", color = Cyan, fontSize = 11.sp)
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (loading) Purple else Color(0xFF54E39B)))
                    Spacer(Modifier.width(5.dp)); Text(if (loading) "Сейчас отвечу" else "На связи", color = Muted, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Чем займёмся сегодня?", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("💡 Идея", "❤️ Поддержка", "✍️ Текст").forEach { suggestion ->
                Surface(onClick = { if (!loading) prompt = when {
                    suggestion.contains("Идея") -> "Помоги придумать интересную идею для моего проекта"
                    suggestion.contains("Поддержка") -> "Мне хочется поговорить. Поддержи меня по-доброму"
                    else -> "Помоги написать красивое сообщение"
                } }, shape = RoundedCornerShape(14.dp), color = PanelLight, contentColor = Color.White) {
                    Text(suggestion, modifier = Modifier.padding(horizontal = 9.dp, vertical = 10.dp), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF0E1122)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(conversation.size) { index ->
                val message = conversation[index]
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
                    if (!message.fromUser) {
                        Box(Modifier.padding(top = 3.dp).size(30.dp).clip(CircleShape).background(VibeGradient), contentAlignment = Alignment.Center) { Text("✦", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(7.dp))
                    }
                    Column(Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (message.fromUser) 20.dp else 5.dp, bottomEnd = if (message.fromUser) 5.dp else 20.dp)).background(if (message.fromUser) Brush.linearGradient(listOf(Color(0xFF6B42D8), Color(0xFF385BE8))) else VibeCardGradient).padding(13.dp)) {
                        Text(if (message.fromUser) "Ты" else "Алекс ✦", color = if (message.fromUser) Color(0xFFDCD3FF) else Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp)); Text(message.text, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
            if (loading) item { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(color = Cyan, strokeWidth = 2.dp, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Алекс обдумывает ответ…", color = Muted, fontSize = 12.sp) } }
            if (error.isNotBlank()) item { Text(error, color = Pink, fontSize = 12.sp) }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel).padding(7.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(value = prompt, onValueChange = { prompt = it.take(2000) }, placeholder = { Text("Расскажи Алексу…", color = Muted) }, modifier = Modifier.weight(1f), maxLines = 4, shape = RoundedCornerShape(17.dp), colors = VibeFieldColors)
            Spacer(Modifier.width(7.dp))
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(if (loading || prompt.isBlank()) Brush.linearGradient(listOf(PanelLight, PanelLight)) else VibeGradient).clickable(enabled = !loading && prompt.isNotBlank()) {
                val question = prompt.trim()
                if (question.isNotBlank() && !loading) {
                    conversation.add(AlexBubble(true, question)); prompt = ""; loading = true; error = ""
                    val history = conversation.dropLast(1).takeLast(8).joinToString("\n") { (if (it.fromUser) "Пользователь" else "Алекс") + ": " + it.text }
                    val fullPrompt = "Ты Алекс — тёплый, внимательный и дружелюбный AI-компаньон в мессенджере Vibe Chat. Общайся естественно, с эмпатией. Не притворяйся человеком и не утверждай, что испытываешь настоящие чувства. Отвечай на русском, если пользователь пишет по-русски. Не будь сухим и не злоупотребляй списками и эмодзи. Учитывай недавний контекст.\n\nИстория разговора:\n$history\n\nНовое сообщение пользователя:\n$question"
                    scope.launch {
                        try {
                            val response = model.generateContent(fullPrompt)
                            conversation.add(AlexBubble(false, response.text?.takeIf { it.isNotBlank() } ?: "Я рядом. Попробуй сформулировать вопрос немного иначе 💜"))
                        } catch (e: Exception) {
                            error = "Не удалось получить ответ. Проверь подключение и настройки Firebase AI."
                            conversation.add(AlexBubble(false, "Кажется, сейчас я не смог подключиться к AI-сервису. Попробуй ещё раз чуть позже 💜"))
                        } finally { loading = false }
                    }
                }
            }, contentAlignment = Alignment.Center) { Text("➤", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(4.dp))
        Text("Алекс — AI-помощник. Он может ошибаться, важное стоит перепроверять.", color = Muted, fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
