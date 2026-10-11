package com.vibe.chat

import android.app.Activity
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.ai
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

private val VibeBg = Color(0xFF070A18)
private val VibePanel = Color(0xFF11162B)
private val VibePanel2 = Color(0xFF171D38)
private val VibePurple = Color(0xFF9B5CFF)
private val VibeCyan = Color(0xFF35E7FF)
private val VibeText = Color(0xFFF4F3FF)
private val VibeMuted = Color(0xFFA8B0D0)
private val VibePink = Color(0xFFFF63C3)

private fun vibeGradient() = Brush.linearGradient(listOf(Color(0xFF15152E), Color(0xFF090D20), Color(0xFF101B31)))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = VibePurple,
                secondary = VibeCyan,
                background = VibeBg,
                surface = VibePanel,
                onPrimary = Color.White,
                onBackground = VibeText,
                onSurface = VibeText
            )) {
                Surface(modifier = Modifier.fillMaxSize(), color = VibeBg) {
                    VibeRoot()
                }
            }
        }
    }
}

@Composable
private fun VibeRoot() {
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    if (user == null) VibeLogin(onSignedIn = { user = auth.currentUser })
    else VibeHome(userId = user!!.uid, phone = user!!.phoneNumber.orEmpty(), onSignOut = { auth.signOut(); user = null })
}

@Composable
private fun VibeBackground(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(vibeGradient()), content = content)
}

@Composable
private fun VibeLogo() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(VibePurple, VibeCyan))), contentAlignment = Alignment.Center) {
            Text("V", fontSize = 29.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
        Column {
            Text("VIBE CHAT", fontSize = 22.sp, fontWeight = FontWeight.Black, color = VibeText, letterSpacing = 1.5.sp)
            Text("ТВОЁ ОБЩЕНИЕ • ТВОЯ ВСЕЛЕННАЯ", fontSize = 9.sp, color = VibeCyan, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun VibeButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 50.dp),
        shape = RoundedCornerShape(17.dp),
        colors = ButtonDefaults.buttonColors(containerColor = VibePurple, contentColor = Color.White, disabledContainerColor = Color(0xFF45405D))
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
}

@Composable
private fun VibeField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Text, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label, color = VibeMuted) },
        modifier = modifier.fillMaxWidth(), singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VibeCyan, unfocusedBorderColor = Color(0xFF353D60),
            focusedTextColor = VibeText, unfocusedTextColor = VibeText,
            cursorColor = VibeCyan, focusedLabelColor = VibeCyan,
            unfocusedLabelColor = VibeMuted, focusedContainerColor = VibePanel,
            unfocusedContainerColor = VibePanel
        )
    )
}

@Composable
private fun VibeLogin(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Войди по номеру телефона — код придёт по SMS") }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }

    val callbacks = remember {
        object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                auth.signInWithCredential(credential).addOnCompleteListener { task ->
                    busy = false
                    if (task.isSuccessful) onSignedIn() else status = task.exception?.localizedMessage ?: "Не удалось войти"
                }
            }
            override fun onVerificationFailed(e: FirebaseException) {
                busy = false
                status = e.localizedMessage ?: "Не удалось отправить SMS. Проверь номер и настройки Firebase."
            }
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                verificationId = id
                resendToken = token
                busy = false
                status = "Код отправлен. Введи 6 цифр из SMS."
            }
        }
    }

    VibeBackground {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(70.dp))
            VibeLogo()
            Spacer(Modifier.height(42.dp))
            Text("ТВОЯ ВОЛНА. ТВОИ ЛЮДИ.", fontSize = 23.sp, fontWeight = FontWeight.Black, color = VibeText)
            Spacer(Modifier.height(8.dp))
            Text("Общайся, делись моментами и спрашивай Алекса", color = VibeMuted, fontSize = 13.sp)
            Spacer(Modifier.height(30.dp))
            Card(colors = CardDefaults.cardColors(containerColor = VibePanel.copy(alpha = 0.96f)), shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(if (verificationId == null) "ВХОД ПО SMS" else "ПОДТВЕРЖДЕНИЕ НОМЕРА", color = VibeCyan, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    VibeField(phone, { phone = it }, "Номер с кодом страны, например +49…", keyboardType = KeyboardType.Phone)
                    if (verificationId != null) VibeField(code, { code = it }, "Код из SMS", keyboardType = KeyboardType.Number)
                    VibeButton(if (busy) "Подожди…" else if (verificationId == null) "Получить код" else "Войти", enabled = !busy) {
                        if (verificationId == null) {
                            val normalized = phone.trim().replace(" ", "")
                            if (!normalized.startsWith("+") || normalized.length < 8) {
                                status = "Введи номер в международном формате, начиная с +"
                            } else {
                                busy = true
                                status = "Отправляем SMS…"
                                val options = PhoneAuthOptions.newBuilder(auth)
                                    .setPhoneNumber(normalized)
                                    .setTimeout(60L, TimeUnit.SECONDS)
                                    .setActivity(context as Activity)
                                    .setCallbacks(callbacks)
                                    .build()
                                PhoneAuthProvider.verifyPhoneNumber(options)
                            }
                        } else {
                            val id = verificationId
                            if (id == null || code.trim().length < 6) status = "Введи код из SMS"
                            else {
                                busy = true
                                auth.signInWithCredential(PhoneAuthProvider.getCredential(id, code.trim()))
                                    .addOnCompleteListener { task ->
                                        busy = false
                                        if (task.isSuccessful) onSignedIn() else status = task.exception?.localizedMessage ?: "Неверный код"
                                    }
                            }
                        }
                    }
                    Text(status, color = if (status.contains("не удалось", true) || status.contains("проверь", true)) VibePink else VibeMuted, fontSize = 12.sp)
                    Text("Если SMS не приходит, проверь Phone Authentication в Firebase и SHA-1/SHA-256 приложения.", color = VibeMuted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("AURORA NEON • VIBE CHAT", color = Color(0xFF6F789C), fontSize = 10.sp, letterSpacing = 2.sp)
        }
    }
}

private data class VibeUser(val id: String, val name: String, val phone: String, val bio: String, val avatarBase64: String)

private enum class VibeTab(val title: String, val symbol: String) {
    Chats("Чаты", "◉"), People("Люди", "♙"), Alex("Алекс", "✦"), Photos("Фото", "▧"), Profile("Профиль", "◎")
}

@Composable
private fun VibeHome(userId: String, phone: String, onSignOut: () -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    var tab by remember { mutableStateOf(VibeTab.Chats) }
    var openPrivateChat by remember { mutableStateOf<Pair<String, String>?>(null) }
    var openGeneral by remember { mutableStateOf(false) }
    var profileTarget by remember { mutableStateOf<VibeUser?>(null) }

    when {
        openPrivateChat != null -> {
            val target = openPrivateChat!!
            PrivateChatScreen(target.first, target.second) { openPrivateChat = null }
        }
        openGeneral -> VibeGeneralChatReplacement(phone = phone, onBack = { openGeneral = false })
        profileTarget != null -> VibeProfile(user = profileTarget!!, isMe = profileTarget!!.id == userId, onBack = { profileTarget = null }, onSave = { profileTarget = it })
        else -> VibeBackground {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(VibePurple, VibeCyan))), contentAlignment = Alignment.Center) { Text("V", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp) }
                        Column { Text("VIBE CHAT", fontWeight = FontWeight.Black, color = VibeText, letterSpacing = 1.sp); Text("AURORA NEON", color = VibeCyan, fontSize = 9.sp, letterSpacing = 1.5.sp) }
                    }
                    Text("● ONLINE", color = VibeCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        VibeTab.Chats -> VibeChatsListScreen(myId = userId, onOpenChat = { id, name -> openPrivateChat = id to name }, onOpenGeneralChat = { openGeneral = true }, onOpenUsers = { tab = VibeTab.People })
                        VibeTab.People -> VibePeopleScreen(db, userId, onOpenProfile = { profileTarget = it }, onOpenChat = { id, name -> openPrivateChat = id to name })
                        VibeTab.Alex -> AlexAiScreen()
                        VibeTab.Photos -> VibePhotoStudioScreen()
                        VibeTab.Profile -> VibeMyProfileScreen(db, userId, phone, onSignOut = onSignOut, onOpenProfile = { profileTarget = it })
                    }
                }
                VibeBottomBar(tab = tab, onSelect = { tab = it })
            }
        }
    }
}

@Composable
private fun VibeBottomBar(tab: VibeTab, onSelect: (VibeTab) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF10162D)).border(1.dp, Color(0xFF343A62), RoundedCornerShape(24.dp)).padding(5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        VibeTab.values().forEach { item ->
            val selected = item == tab
            Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(if (selected) Brush.linearGradient(listOf(VibePurple.copy(alpha = .9f), Color(0xFF24516A))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)).let { if (selected) it else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)) }).clickable { onSelect(item) }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(item.symbol, color = if (selected) Color.White else VibeMuted, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(item.title, color = if (selected) Color.White else VibeMuted, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun VibeSectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text(title, color = VibeText, fontWeight = FontWeight.Black, fontSize = 23.sp)
        if (subtitle != null) Text(subtitle, color = VibeMuted, fontSize = 12.sp)
    }
}

@Composable
private fun VibePeopleScreen(db: FirebaseFirestore, myId: String, onOpenProfile: (VibeUser) -> Unit, onOpenChat: (String, String) -> Unit) {
    var users by remember { mutableStateOf<List<VibeUser>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    DisposableEffect(db, myId) {
        val registration = db.collection("users").addSnapshotListener { snapshot, error ->
            loading = false
            if (error == null && snapshot != null) {
                users = snapshot.documents.filter { it.id != myId }.map { doc ->
                    VibeUser(doc.id, doc.getString("name").orEmpty().ifBlank { doc.getString("phone").orEmpty().ifBlank { "Пользователь" } }, doc.getString("phone").orEmpty(), doc.getString("bio").orEmpty(), doc.getString("avatarBase64").orEmpty())
                }
            }
        }
        onDispose { registration.remove() }
    }
    Column(Modifier.fillMaxSize()) {
        VibeSectionTitle("Люди", "Находи друзей в Vibe Chat")
        VibeField(search, { search = it }, "Поиск по имени или номеру", modifier = Modifier.padding(horizontal = 16.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = VibeCyan) }
            users.isEmpty() -> VibeEmptyState("Пока никого нет", "Когда пользователи зарегистрируются, они появятся здесь.")
            else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(users.filter { it.name.contains(search, true) || it.phone.contains(search, true) }, key = { it.id }) { user ->
                    VibeUserCard(user, onClick = { onOpenProfile(user) }, onChat = { onOpenChat(user.id, user.name) })
                }
            }
        }
    }
}

@Composable
private fun VibeUserCard(user: VibeUser, onClick: () -> Unit, onChat: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp).clip(RoundedCornerShape(20.dp)).background(VibePanel).border(1.dp, Color(0xFF2D3457), RoundedCornerShape(20.dp)).clickable { onClick() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        VibeAvatar(user.name, user.avatarBase64, 48)
        Column(Modifier.weight(1f)) {
            Text(user.name, color = VibeText, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(user.bio.ifBlank { user.phone.ifBlank { "Пользователь Vibe Chat" } }, color = VibeMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("Чат", color = VibeCyan, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF1B2D46)).clickable { onChat() }.padding(horizontal = 12.dp, vertical = 9.dp))
    }
}

@Composable
private fun VibeMyProfileScreen(db: FirebaseFirestore, userId: String, phone: String, onSignOut: () -> Unit, onOpenProfile: (VibeUser) -> Unit) {
    var user by remember { mutableStateOf(VibeUser(userId, "", phone, "", "")) }
    DisposableEffect(db, userId) {
        val registration = db.collection("users").document(userId).addSnapshotListener { doc, _ ->
            if (doc != null && doc.exists()) user = VibeUser(userId, doc.getString("name").orEmpty(), doc.getString("phone").orEmpty().ifBlank { phone }, doc.getString("bio").orEmpty(), doc.getString("avatarBase64").orEmpty())
        }
        onDispose { registration.remove() }
    }
    Column(Modifier.fillMaxSize()) {
        VibeSectionTitle("Мой профиль", "Твоя страница в Vibe Chat")
        Column(Modifier.fillMaxWidth().padding(18.dp).clip(RoundedCornerShape(28.dp)).background(VibePanel).border(1.dp, Color(0xFF3B3765), RoundedCornerShape(28.dp)).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            VibeAvatar(user.name.ifBlank { "V" }, user.avatarBase64, 94)
            Spacer(Modifier.height(14.dp))
            Text(user.name.ifBlank { "Имя не указано" }, color = VibeText, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(user.phone.ifBlank { phone }, color = VibeCyan, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Text(user.bio.ifBlank { "Добавь описание о себе" }, color = VibeMuted, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            VibeButton("Редактировать профиль") { onOpenProfile(user) }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = VibePink)) { Text("Выйти из аккаунта") }
        }
    }
}

@Composable
private fun VibeProfile(user: VibeUser, isMe: Boolean, onBack: () -> Unit, onSave: (VibeUser) -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current
    var name by remember(user.id) { mutableStateOf(user.name) }
    var bio by remember(user.id) { mutableStateOf(user.bio) }
    var avatar by remember(user.id) { mutableStateOf(user.avatarBase64) }
    var saving by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readBytes()
                    avatar = Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            }.onFailure { Toast.makeText(context, "Не удалось открыть изображение", Toast.LENGTH_SHORT).show() }
        }
    }
    VibeBackground {
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("‹ Назад", color = VibeCyan, modifier = Modifier.clickable { onBack() }.padding(8.dp))
                Spacer(Modifier.width(12.dp))
                Text(if (isMe) "Редактировать профиль" else "Профиль", color = VibeText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(18.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(VibePanel).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                VibeAvatar(name.ifBlank { "V" }, avatar, 92)
                if (isMe) Text("Изменить фото", color = VibeCyan, modifier = Modifier.clickable { picker.launch("image/*") }.padding(12.dp))
                else Text(user.phone, color = VibeMuted)
                Spacer(Modifier.height(12.dp))
                if (isMe) {
                    VibeField(name, { name = it }, "Имя")
                    Spacer(Modifier.height(12.dp))
                    VibeField(bio, { bio = it }, "О себе", singleLine = false)
                    Spacer(Modifier.height(18.dp))
                    VibeButton(if (saving) "Сохраняем…" else "Сохранить профиль", enabled = !saving) {
                        saving = true
                        val updated = mapOf("name" to name.trim(), "bio" to bio.trim(), "phone" to user.phone.ifBlank { FirebaseAuth.getInstance().currentUser?.phoneNumber.orEmpty() }, "avatarBase64" to avatar, "updatedAt" to FieldValue.serverTimestamp())
                        db.collection("users").document(user.id).set(updated, com.google.firebase.firestore.SetOptions.merge()).addOnCompleteListener { task ->
                            saving = false
                            if (task.isSuccessful) {
                                val saved = user.copy(name = name.trim(), bio = bio.trim(), avatarBase64 = avatar)
                                onSave(saved)
                                Toast.makeText(context, "Профиль сохранён", Toast.LENGTH_SHORT).show()
                                onBack()
                            } else Toast.makeText(context, task.exception?.localizedMessage ?: "Не удалось сохранить профиль", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Text(name.ifBlank { "Пользователь" }, color = VibeText, fontSize = 23.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    Text(bio.ifBlank { "Пока без описания" }, color = VibeMuted)
                }
            }
        }
    }
}

@Composable
private fun VibeAvatar(name: String, base64: String, size: Int) {
    val bitmap = remember(base64) {
        runCatching { if (base64.isBlank()) null else BitmapFactory.decodeByteArray(Base64.decode(base64, Base64.DEFAULT), 0, Base64.decode(base64, Base64.DEFAULT).size)?.asImageBitmap() }.getOrNull()
    }
    Box(Modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(listOf(VibePurple, VibeCyan))).border(2.dp, Color(0xFF6BDAFF), CircleShape), contentAlignment = Alignment.Center) {
        if (bitmap != null) androidx.compose.foundation.Image(bitmap, contentDescription = "Фото профиля", modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        else Text(name.trim().firstOrNull()?.uppercase() ?: "V", color = Color.White, fontSize = (size / 2.5).sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun VibeChatsListScreen(myId: String, onOpenChat: (String, String) -> Unit, onOpenGeneralChat: () -> Unit, onOpenUsers: () -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    var previews by remember { mutableStateOf<List<ConversationPreview>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    DisposableEffect(db, myId) {
        val registration = db.collection("chats").whereArrayContains("participantIds", myId).addSnapshotListener { snapshot, error ->
            loading = false
            if (error == null && snapshot != null) {
                previews = snapshot.documents.map { doc ->
                    val ids = doc.get("participantIds") as? List<*> ?: emptyList<Any>()
                    val otherId = ids.filterIsInstance<String>().firstOrNull { it != myId }.orEmpty()
                    ConversationPreview(doc.id, otherId, "", "", doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L)
                }
            }
        }
        onDispose { registration.remove() }
    }
    Column(Modifier.fillMaxSize()) {
        VibeSectionTitle("Твои чаты", "Общайся без границ")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Color(0xFF452A78), Color(0xFF15475A)))).clickable { onOpenGeneralChat() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                VibeAvatar("Vibe", "", 48)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text("Общий чат Vibe", color = Color.White, fontWeight = FontWeight.Bold); Text("Общайся со всеми", color = Color(0xFFD0D8FF), fontSize = 12.sp) }
                Text("›", color = Color.White, fontSize = 28.sp)
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(VibePanel).clickable { onOpenUsers() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("＋", color = VibeCyan, fontSize = 24.sp); Spacer(Modifier.width(10.dp)); Text("Найти людей", color = VibeText, fontWeight = FontWeight.Bold)
            }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = VibeCyan) }
            previews.isEmpty() -> VibeEmptyState("Личных чатов пока нет", "Открой «Люди», выбери пользователя и начни переписку.")
            else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(previews, key = { it.id }) { preview ->
                    var otherName by remember(preview.otherId) { mutableStateOf("Пользователь") }
                    DisposableEffect(preview.otherId) {
                        val listener = if (preview.otherId.isNotBlank()) db.collection("users").document(preview.otherId).addSnapshotListener { doc, _ -> otherName = doc?.getString("name").orEmpty().ifBlank { doc?.getString("phone").orEmpty().ifBlank { "Пользователь" } } } else null
                        onDispose { listener?.remove() }
                    }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(VibePanel).clickable { onOpenChat(preview.otherId, otherName) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        VibeAvatar(otherName, "", 48); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(otherName, color = VibeText, fontWeight = FontWeight.Bold); Text("Открой переписку", color = VibeMuted, fontSize = 12.sp) }; Text("›", color = VibeCyan, fontSize = 25.sp)
                    }
                }
            }
        }
    }
}

private data class ConversationPreview(val id: String, val otherId: String, val name: String, val lastMessage: String, val updatedAt: Long)

@Composable
private fun VibeEmptyState(title: String, description: String) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✦", color = VibePurple, fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(title, color = VibeText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(description, color = VibeMuted, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun VibeGeneralChatReplacement(phone: String, onBack: () -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current
    var messages by remember { mutableStateOf<List<GeneralMessage>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var myName by remember { mutableStateOf(phone.ifBlank { "Пользователь" }) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    DisposableEffect(auth.currentUser?.uid) {
        val uid = auth.currentUser?.uid.orEmpty()
        var userListener: ListenerRegistration? = null
        if (uid.isNotBlank()) {
            userListener = db.collection("users").document(uid).addSnapshotListener { doc, _ ->
                myName = doc?.getString("name").orEmpty().ifBlank { phone.ifBlank { "Пользователь" } }
            }
        }
        val registration = db.collection("chats").document("general").collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(150)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(context, "Не удалось загрузить общий чат", Toast.LENGTH_SHORT).show()
                } else if (snapshot != null) {
                    messages = snapshot.documents.map { doc ->
                        GeneralMessage(
                            id = doc.id,
                            authorId = doc.getString("authorId").orEmpty(),
                            author = doc.getString("author").orEmpty().ifBlank { "Пользователь" },
                            text = doc.getString("text").orEmpty(),
                            time = doc.getTimestamp("createdAt")?.toDate()?.let {
                                java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(it)
                            }.orEmpty()
                        )
                    }
                }
            }
        onDispose { registration.remove(); userListener?.remove() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹ Назад", color = VibeCyan, modifier = Modifier.clickable { onBack() }.padding(8.dp))
            Spacer(Modifier.width(10.dp))
            Column { Text("Общий чат", color = VibeText, fontSize = 21.sp, fontWeight = FontWeight.Black); Text("Все пользователи Vibe Chat", color = VibeMuted, fontSize = 12.sp) }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages, key = { it.id }) { message ->
                val mine = message.authorId == auth.currentUser?.uid
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                    if (!mine) Text(message.author, color = VibeCyan, fontSize = 10.sp, modifier = Modifier.padding(start = 8.dp, bottom = 3.dp))
                    Column(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp)).background(if (mine) Color(0xFF4D347A) else VibePanel).padding(horizontal = 13.dp, vertical = 9.dp)) {
                        Text(message.text, color = VibeText, fontSize = 14.sp)
                        if (message.time.isNotBlank()) Text(message.time, color = VibeMuted, fontSize = 9.sp, modifier = Modifier.align(Alignment.End))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("Сообщение…", color = VibeMuted) },
                shape = RoundedCornerShape(18.dp), maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VibeCyan, unfocusedBorderColor = Color(0xFF353D60), focusedTextColor = VibeText, unfocusedTextColor = VibeText, cursorColor = VibeCyan)
            )
            Button(onClick = {
                val textToSend = draft.trim()
                val uid = auth.currentUser?.uid.orEmpty()
                if (uid.isBlank()) Toast.makeText(context, "Сначала войди в аккаунт", Toast.LENGTH_SHORT).show()
                else if (textToSend.isNotBlank()) {
                    draft = ""
                    db.collection("chats").document("general").collection("messages").add(
                        mapOf("text" to textToSend, "authorId" to uid, "author" to myName, "createdAt" to FieldValue.serverTimestamp())
                    ).addOnFailureListener { e -> Toast.makeText(context, e.localizedMessage ?: "Не удалось отправить", Toast.LENGTH_LONG).show() }
                }
            }, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = VibePurple), contentPadding = PaddingValues(horizontal = 15.dp, vertical = 13.dp)) { Text("➤", fontSize = 18.sp) }
        }
    }
}

private data class GeneralMessage(val id: String, val authorId: String, val author: String, val text: String, val time: String)

@Composable
private fun AlexAiScreen() {
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("Привет! Я Алекс — AI-помощник Vibe Chat. Спроси меня о чём угодно.") }
    var busy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        VibeSectionTitle("Алекс AI", "Твой умный помощник")
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp)).background(VibePanel).border(1.dp, Color(0xFF34345F), RoundedCornerShape(24.dp)).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(VibePurple, VibeCyan))), contentAlignment = Alignment.Center) { Text("✦", color = Color.White, fontSize = 28.sp) }
                Spacer(Modifier.width(12.dp)); Column { Text("АЛЕКС", color = VibeText, fontWeight = FontWeight.Black, fontSize = 18.sp); Text(if (busy) "ДУМАЮ…" else "В СЕТИ", color = VibeCyan, fontSize = 10.sp, letterSpacing = 1.5.sp) }
            }
            Spacer(Modifier.height(18.dp))
            Text(answer, color = VibeText, fontSize = 14.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(10.dp))
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = VibeCyan, trackColor = VibePanel2)
        }
        Spacer(Modifier.height(12.dp))
        VibeField(prompt, { prompt = it }, "Напиши Алексу…", modifier = Modifier.padding(horizontal = 16.dp), singleLine = false)
        Spacer(Modifier.height(8.dp))
        VibeButton("Отправить Алексу", modifier = Modifier.padding(horizontal = 16.dp), enabled = !busy && prompt.isNotBlank()) {
            val question = prompt.trim()
            prompt = ""
            busy = true
            scope.launch {
                try {
                    val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel("gemini-3.8-flash")
                    val response = model.generateContent(question)
                    answer = response.text ?: "Не получилось получить текстовый ответ. Попробуй ещё раз."
                } catch (e: Exception) {
                    answer = "Не удалось подключиться к Алексу: ${e.localizedMessage ?: "ошибка AI"}. Проверь Firebase AI Logic, доступ к модели и App Check."
                    Toast.makeText(context, "Ошибка AI-помощника", Toast.LENGTH_SHORT).show()
                } finally { busy = false }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun VibePhotoStudioScreen() {
    var prompt by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        VibeSectionTitle("Фото AI", "Твоя творческая студия")
        Column(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(26.dp)).background(VibePanel).border(1.dp, Color(0xFF39365F), RoundedCornerShape(26.dp)).padding(20.dp)) {
            Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Color(0xFF45266C), Color(0xFF123B55), Color(0xFF1B2140)))), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("✧", color = VibeCyan, fontSize = 58.sp); Text("AI PHOTO STUDIO", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 2.sp); Text("Идея начинается здесь", color = Color(0xFFD3D9FF), fontSize = 12.sp) }
            }
            Spacer(Modifier.height(18.dp))
            Text("Опиши изображение", color = VibeText, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            VibeField(prompt, { prompt = it }, "Например: футуристический город под дождём", singleLine = false)
            Spacer(Modifier.height(12.dp))
            Text("Поле для идеи готово. Генерация и скачивание изображений потребуют подключения отдельного сервиса генерации; этот экран пока не создаёт картинку самостоятельно.", color = VibeMuted, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { prompt = "Кинематографичный портрет в стиле Aurora Neon, фиолетово-голубое свечение, высокая детализация" }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = VibeCyan)) { Text("Вставить пример промпта") }
        }
    }
}
