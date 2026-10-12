
package com.vibe.chat

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PrivateMessage(
    val id: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val createdAt: Timestamp?,
    val attachmentUrl: String = "",
    val attachmentName: String = "",
    val attachmentType: String = "",
    val readBy: List<String> = emptyList()
)

private val VibeChatBackground = Color(0xFF0D111A)
private val VibeChatPanel = Color(0xFF151B25)
private val VibeChatIncoming = Color(0xFF1A202B)
private val VibeChatOutgoing = Color(0xFF203630)
private val VibeChatOutgoingDark = Color(0xFF192B2B)
private val VibeChatText = Color(0xFFE8F0F2)
private val VibeChatMuted = Color(0xFF8D9AA6)
private val VibeChatAccent = Color(0xFFB5A0FF)
private val VibeChatGreen = Color(0xFF78E8D0)
private val VibeChatLine = Color(0xFF29343E)

private fun loadVibeImage(url: String): Bitmap? {
    return try {
        val connection = URL(url).openConnection().apply {
            connectTimeout = 10000
            readTimeout = 15000
        }

        connection.getInputStream().use { stream ->
            val bytes = stream.readBytes()
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

            var sampleSize = 1
            while (
                bounds.outWidth / sampleSize > 1200 ||
                bounds.outHeight / sampleSize > 1200
            ) {
                sampleSize *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }

            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                options
            )
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
fun PrivateChatScreen(
    otherUserId: String,
    otherUserName: String,
    onBack: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val currentUser = auth.currentUser
    val context = LocalContext.current
    val myId = currentUser?.uid.orEmpty()

    var myName by remember(myId) {
        mutableStateOf(
            currentUser?.phoneNumber.orEmpty().ifBlank {
                "Пользователь"
            }
        )
    }

    var actualOtherName by remember(otherUserId, otherUserName) {
        mutableStateOf(otherUserName.ifBlank { "Собеседник" })
    }

    var otherAvatarBase64 by remember(otherUserId) {
        mutableStateOf("")
    }

    var messages by remember(myId, otherUserId) {
        mutableStateOf<List<PrivateMessage>>(emptyList())
    }

    var input by remember(myId, otherUserId) {
        mutableStateOf("")
    }

    var showEmojiPicker by remember(myId, otherUserId) {
        mutableStateOf(false)
    }

    var emojiSearch by remember(myId, otherUserId) {
        mutableStateOf("")
    }

    var emojiCategory by remember(myId, otherUserId) {
        mutableStateOf("Vibe")
    }

    var attachmentUploading by remember(myId, otherUserId) {
        mutableStateOf(false)
    }

    var error by remember(myId, otherUserId) {
        mutableStateOf("")
    }

    var sending by remember(myId, otherUserId) {
        mutableStateOf(false)
    }

    var chatReady by remember(myId, otherUserId) {
        mutableStateOf(false)
    }

    var loading by remember(myId, otherUserId) {
        mutableStateOf(true)
    }

    val listState = rememberLazyListState()

    val chatId = remember(myId, otherUserId) {
        listOf(myId, otherUserId).sorted().joinToString("_")
    }

    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && chatReady && myId.isNotBlank()) {
            attachmentUploading = true
            error = ""

            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri)
                ?: "application/octet-stream"

            val fileName = runCatching {
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(
                        android.provider.OpenableColumns.DISPLAY_NAME
                    )
                    if (cursor.moveToFirst() && index >= 0) {
                        cursor.getString(index)
                    } else {
                        null
                    }
                }
            }.getOrNull() ?: "Файл"

            val safeName = fileName
                .replace(Regex("[^A-Za-zА-Яа-я0-9._-]"), "_")
                .take(90)

            val storageRef = FirebaseStorage.getInstance()
                .reference
                .child(
                    "chat_attachments/$chatId/" +
                        "${System.currentTimeMillis()}_$safeName"
                )

            val stream = runCatching {
                resolver.openInputStream(uri)
            }.getOrNull()

            if (stream == null) {
                attachmentUploading = false
                error = "Не удалось открыть выбранный файл."
            } else {
                val metadata =
                    com.google.firebase.storage.StorageMetadata.Builder()
                        .setContentType(mimeType)
                        .build()

                storageRef.putStream(stream, metadata)
                    .continueWithTask { task ->
                        runCatching { stream.close() }

                        if (!task.isSuccessful) {
                            throw (
                                task.exception
                                    ?: Exception("Не удалось загрузить файл")
                            )
                        }

                        storageRef.downloadUrl
                    }
                    .addOnSuccessListener { downloadUri ->
                        db.collection("chats")
                            .document(chatId)
                            .collection("messages")
                            .add(
                                mapOf(
                                    "text" to "",
                                    "authorId" to myId,
                                    "authorName" to myName,
                                    "createdAt" to FieldValue.serverTimestamp(),
                                    "readBy" to listOf(myId),
                                    "attachmentUrl" to downloadUri.toString(),
                                    "attachmentName" to fileName,
                                    "attachmentType" to mimeType
                                )
                            )
                            .addOnSuccessListener {
                                attachmentUploading = false
                            }
                            .addOnFailureListener { exception ->
                                attachmentUploading = false
                                error =
                                    "Файл загружен, но сообщение не отправилось: " +
                                    (exception.localizedMessage ?: "ошибка")
                            }
                    }
                    .addOnFailureListener { exception ->
                        attachmentUploading = false
                        error = "Не удалось загрузить файл: " +
                            (
                                exception.localizedMessage
                                    ?: "проверь Firebase Storage"
                            )
                    }
            }
        } else if (uri != null) {
            error = "Сначала дождись подключения к чату."
        }
    }

    DisposableEffect(otherUserId) {
        var registration: ListenerRegistration? = null

        if (otherUserId.isNotBlank()) {
            registration = db.collection("users")
                .document(otherUserId)
                .addSnapshotListener { snapshot, exception ->
                    if (exception == null && snapshot != null && snapshot.exists()) {
                        actualOtherName = snapshot.getString("name")
                            ?.takeIf { it.isNotBlank() }
                            ?: otherUserName.ifBlank { "Собеседник" }

                        otherAvatarBase64 =
                            snapshot.getString("avatarBase64").orEmpty()
                    }
                }
        }

        onDispose {
            registration?.remove()
        }
    }

    val otherAvatarBitmap = remember(otherAvatarBase64) {
        try {
            if (otherAvatarBase64.isBlank()) {
                null
            } else {
                val bytes = Base64.decode(
                    otherAvatarBase64,
                    Base64.DEFAULT
                )
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            null
        }
    }

    LaunchedEffect(myId, otherUserId) {
        when {
            myId.isBlank() -> {
                error = "Войди в аккаунт, чтобы открыть переписку."
                loading = false
            }

            otherUserId.isBlank() -> {
                error = "Не удалось определить собеседника."
                loading = false
            }

            myId == otherUserId -> {
                error = "Нельзя открыть личный чат с самим собой."
                loading = false
            }
        }
    }

    DisposableEffect(myId) {
        var registration: ListenerRegistration? = null

        if (myId.isNotBlank()) {
            registration = db.collection("users")
                .document(myId)
                .addSnapshotListener { snapshot, exception ->
                    if (exception != null) {
                        error = "Ошибка профиля: " +
                            (
                                exception.localizedMessage
                                    ?: "неизвестная ошибка"
                            )
                    } else {
                        myName = snapshot?.getString("name")
                            ?.takeIf { it.isNotBlank() }
                            ?: currentUser?.phoneNumber.orEmpty()
                                .ifBlank { "Пользователь" }
                    }
                }
        }

        onDispose {
            registration?.remove()
        }
    }

    DisposableEffect(chatId, myId, otherUserId) {
        var messageRegistration: ListenerRegistration? = null
        var active = true

        chatReady = false
        loading = true
        messages = emptyList()

        if (
            myId.isNotBlank() &&
            otherUserId.isNotBlank() &&
            myId != otherUserId
        ) {
            val chatRef = db.collection("chats").document(chatId)

            chatRef.set(
                mapOf(
                    "participantIds" to listOf(myId, otherUserId).sorted(),
                    "createdAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).addOnSuccessListener {
                if (!active) return@addOnSuccessListener

                chatReady = true
                error = ""

                messageRegistration = chatRef.collection("messages")
                    .orderBy("createdAt")
                    .addSnapshotListener { snapshot, exception ->
                        if (!active) return@addSnapshotListener

                        if (exception != null) {
                            error = "Не удалось загрузить сообщения: " +
                                (
                                    exception.localizedMessage
                                        ?: "проверь интернет и правила Firebase"
                                )
                            loading = false
                        } else if (snapshot != null) {
                            messages = snapshot.documents.map { document ->
                                PrivateMessage(
                                    id = document.id,
                                    authorId = document.getString("authorId")
                                        .orEmpty(),
                                    authorName = document.getString("authorName")
                                        .orEmpty(),
                                    text = document.getString("text").orEmpty(),
                                    createdAt = document.getTimestamp("createdAt"),
                                    attachmentUrl = document
                                        .getString("attachmentUrl").orEmpty(),
                                    attachmentName = document
                                        .getString("attachmentName").orEmpty(),
                                    attachmentType = document
                                        .getString("attachmentType").orEmpty(),
                                    readBy = (
                                        document.get("readBy") as? List<*>
                                    )?.filterIsInstance<String>().orEmpty()
                                )
                            }

                            snapshot.documents.forEach { document ->
                                val authorId = document
                                    .getString("authorId").orEmpty()

                                val readBy = (
                                    document.get("readBy") as? List<*>
                                )?.filterIsInstance<String>().orEmpty()

                                if (
                                    authorId.isNotBlank() &&
                                    authorId != myId &&
                                    myId !in readBy
                                ) {
                                    document.reference.update(
                                        "readBy",
                                        FieldValue.arrayUnion(myId)
                                    )
                                }
                            }

                            error = ""
                            loading = false
                        }
                    }
            }.addOnFailureListener { exception ->
                if (!active) return@addOnFailureListener

                chatReady = false
                loading = false
                error = "Не удалось открыть чат: " +
                    (
                        exception.localizedMessage
                            ?: "проверь интернет и Firebase"
                    )
            }
        } else {
            loading = false
        }

        onDispose {
            active = false
            messageRegistration?.remove()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF111622),
                        VibeChatBackground,
                        Color(0xFF101922)
                    )
                )
            )
            .imePadding()
    ) {
        // Компактная верхняя панель.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xEE0D111A))
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Назад",
                    tint = VibeChatText
                )
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF263747),
                                Color(0xFF24243A)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Color(0xFF344B50),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (otherAvatarBitmap != null) {
                    Image(
                        bitmap = otherAvatarBitmap.asImageBitmap(),
                        contentDescription = "Аватар собеседника",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        actualOtherName.firstOrNull()?.uppercase() ?: "?",
                        color = VibeChatGreen,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    actualOtherName,
                    color = VibeChatText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )

                Spacer(Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (chatReady) VibeChatGreen
                                else VibeChatMuted
                            )
                    )

                    Spacer(Modifier.width(6.dp))

                    Text(
                        if (chatReady) "Личная переписка"
                        else "Подключение…",
                        color = VibeChatMuted,
                        fontSize = 11.sp
                    )
                }
            }

            IconButton(
                onClick = {},
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Дополнительно",
                    tint = VibeChatMuted
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            VibeChatLine,
                            Color(0xFF38504F),
                            VibeChatLine,
                            Color.Transparent
                        )
                    )
                )
        )

        if (error.isNotBlank()) {
            Surface(
                color = Color(0xFF30202D),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    error,
                    color = Color(0xFFFFA8C7),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(11.dp)
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = 12.dp,
                vertical = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (messages.isEmpty()) {
                item(key = "chat_welcome") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 54.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(66.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFF203B3C),
                                            Color(0xFF25233C)
                                        )
                                    )
                                )
                                .border(
                                    1.dp,
                                    Color(0xFF38504F),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "✦",
                                color = VibeChatGreen,
                                fontSize = 29.sp
                            )
                        }

                        Spacer(Modifier.height(17.dp))

                        Text(
                            when {
                                loading -> "Подключаем переписку…"
                                chatReady -> "Начни разговор"
                                else -> "Личная переписка"
                            },
                            color = VibeChatText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(7.dp))

                        Text(
                            if (chatReady) {
                                "Напиши $actualOtherName первое сообщение"
                            } else {
                                "Здесь будут ваши сообщения"
                            },
                            color = VibeChatMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            items(messages, key = { it.id }) { message ->
                val mine = message.authorId == myId
                val isImage = message.attachmentType
                    .lowercase(Locale.ROOT)
                    .startsWith("image/")

                val bubbleShape = RoundedCornerShape(
                    topStart = 15.dp,
                    topEnd = 15.dp,
                    bottomStart = if (mine) 15.dp else 4.dp,
                    bottomEnd = if (mine) 4.dp else 15.dp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (mine) {
                        Arrangement.End
                    } else {
                        Arrangement.Start
                    },
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .clip(bubbleShape)
                            .background(
                                if (mine) {
                                    Brush.linearGradient(
                                        listOf(
                                            VibeChatOutgoing,
                                            VibeChatOutgoingDark
                                        )
                                    )
                                } else {
                                    Brush.linearGradient(
                                        listOf(
                                            VibeChatIncoming,
                                            VibeChatIncoming
                                        )
                                    )
                                }
                            )
                            .border(
                                width = 0.5.dp,
                                color = if (mine) {
                                    Color(0xFF34534C)
                                } else {
                                    Color(0xFF2B3441)
                                },
                                shape = bubbleShape
                            )
                            .padding(
                                horizontal = 10.dp,
                                vertical = 6.dp
                            )
                    ) {
                        if (message.text.isNotBlank()) {
                            Text(
                                message.text,
                                color = VibeChatText,
                                fontSize = 14.sp,
                                lineHeight = 19.sp
                            )
                        }

                        if (message.attachmentUrl.isNotBlank()) {
                            if (isImage) {
                                var imageBitmap by remember(
                                    message.attachmentUrl
                                ) {
                                    mutableStateOf<Bitmap?>(null)
                                }

                                var imageLoadFailed by remember(
                                    message.attachmentUrl
                                ) {
                                    mutableStateOf(false)
                                }

                                LaunchedEffect(message.attachmentUrl) {
                                    val result = withContext(Dispatchers.IO) {
                                        loadVibeImage(message.attachmentUrl)
                                    }

                                    imageBitmap = result
                                    imageLoadFailed = result == null
                                }

                                Spacer(Modifier.height(5.dp))

                                if (imageBitmap != null) {
                                    Image(
                                        bitmap = imageBitmap!!.asImageBitmap(),
                                        contentDescription = message.attachmentName
                                            .ifBlank { "Изображение" },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .widthIn(max = 270.dp)
                                            .heightIn(max = 280.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                runCatching {
                                                    val intent = Intent(
                                                        Intent.ACTION_VIEW,
                                                        Uri.parse(
                                                            message.attachmentUrl
                                                        )
                                                    )
                                                    context.startActivity(intent)
                                                }.onFailure {
                                                    error =
                                                        "Не удалось открыть изображение."
                                                }
                                            },
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Surface(
                                        color = Color(0xFF202A35),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(
                                                            Intent.ACTION_VIEW,
                                                            Uri.parse(
                                                                message.attachmentUrl
                                                            )
                                                        )
                                                    )
                                                }.onFailure {
                                                    error =
                                                        "Не удалось открыть вложение."
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment =
                                                Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.AttachFile,
                                                contentDescription = null,
                                                tint = VibeChatGreen,
                                                modifier = Modifier.size(20.dp)
                                            )

                                            Spacer(Modifier.width(8.dp))

                                            Column {
                                                Text(
                                                    message.attachmentName
                                                        .ifBlank {
                                                            "Изображение"
                                                        },
                                                    color = VibeChatText,
                                                    fontSize = 12.sp,
                                                    maxLines = 2
                                                )

                                                Text(
                                                    if (imageLoadFailed) {
                                                        "Нажми, чтобы открыть"
                                                    } else {
                                                        "Загружаем изображение…"
                                                    },
                                                    color = VibeChatMuted,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                if (message.text.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                }
                            } else {
                                Spacer(Modifier.height(5.dp))

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF202A35))
                                        .clickable {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(
                                                        Intent.ACTION_VIEW,
                                                        Uri.parse(
                                                            message.attachmentUrl
                                                        )
                                                    )
                                                )
                                            }.onFailure {
                                                error =
                                                    "Не удалось открыть вложение."
                                            }
                                        }
                                        .padding(
                                            horizontal = 10.dp,
                                            vertical = 9.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.AttachFile,
                                        contentDescription = null,
                                        tint = VibeChatGreen,
                                        modifier = Modifier.size(18.dp)
                                    )

                                    Spacer(Modifier.width(7.dp))

                                    Column(
                                        modifier = Modifier.weight(
                                            1f,
                                            fill = false
                                        )
                                    ) {
                                        Text(
                                            message.attachmentName
                                                .ifBlank { "Вложение" },
                                            color = VibeChatText,
                                            fontSize = 12.sp,
                                            maxLines = 2
                                        )

                                        Text(
                                            "Нажми, чтобы открыть",
                                            color = VibeChatGreen,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            message.createdAt?.let { timestamp ->
                                Text(
                                    SimpleDateFormat(
                                        "HH:mm",
                                        Locale.getDefault()
                                    ).format(
                                        Date(timestamp.toDate().time)
                                    ),
                                    color = VibeChatMuted,
                                    fontSize = 10.sp
                                )
                            }

                            if (mine) {
                                Spacer(Modifier.width(4.dp))

                                Text(
                                    text = if (
                                        otherUserId in message.readBy
                                    ) "✓✓" else "✓",
                                    color = if (
                                        otherUserId in message.readBy
                                    ) {
                                        VibeChatGreen
                                    } else {
                                        VibeChatMuted
                                    },
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Компактная панель выбора эмодзи.
        if (showEmojiPicker) {
            val emojiSets = mapOf(
                "Vibe" to listOf(
                    "🫧", "🪐", "🪼", "🌌", "✨", "💫", "🩵", "💜",
                    "🪽", "🦋", "🌙", "☄️", "🪷", "🧿", "🪄", "🤍",
                    "🥹", "😎", "🫶", "🤝", "🫠", "🙃", "😶‍🌫️", "🤖"
                ),
                "Лица" to listOf(
                    "😀", "😄", "😂", "🥹", "😍", "😎", "🤔", "😴",
                    "🥳", "😭", "😤", "🤗", "🫠", "🙃", "😶‍🌫️", "🤯",
                    "🫡", "🤫"
                ),
                "Сердца" to listOf(
                    "🩵", "💜", "🤍", "🩷", "❤️", "🧡", "💛", "💚",
                    "💙", "🖤", "🤎", "💖", "💗", "💘", "💝", "💞",
                    "💕", "🫶"
                ),
                "Знаки" to listOf(
                    "✨", "💫", "🔥", "⭐", "🌟", "⚡", "☄️", "🌈",
                    "💎", "🎉", "🎧", "🎮", "🚀", "🪄", "🧿", "♾️"
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(VibeChatPanel)
                    .border(
                        1.dp,
                        VibeChatLine,
                        RoundedCornerShape(18.dp)
                    )
                    .padding(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "VIBE EMOJI",
                            color = VibeChatGreen,
                            fontSize = 10.sp,
                            letterSpacing = 1.4.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            "Выбери настроение",
                            color = VibeChatText,
                            fontSize = 14.sp
                        )
                    }

                    IconButton(
                        onClick = { showEmojiPicker = false },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Закрыть Emoji",
                            tint = VibeChatMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(7.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(VibeChatBackground)
                        .padding(horizontal = 9.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = VibeChatMuted,
                        modifier = Modifier.size(17.dp)
                    )

                    Spacer(Modifier.width(8.dp))

                    Box(Modifier.weight(1f)) {
                        if (emojiSearch.isBlank()) {
                            Text(
                                "Найти смайлик…",
                                color = VibeChatMuted,
                                fontSize = 12.sp
                            )
                        }

                        BasicTextField(
                            value = emojiSearch,
                            onValueChange = { emojiSearch = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = VibeChatText,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    emojiSets.keys.forEach { category ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (emojiCategory == category) {
                                        Color(0xFF263B3B)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable {
                                    emojiCategory = category
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                category,
                                color = if (emojiCategory == category) {
                                    VibeChatGreen
                                } else {
                                    VibeChatMuted
                                },
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(7.dp))

                val shownEmojis = (
                    emojiSets[emojiCategory]
                        ?: emojiSets["Vibe"].orEmpty()
                    ).filter {
                        emojiSearch.isBlank() ||
                            it.contains(emojiSearch, ignoreCase = true)
                    }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 135.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    gridItems(shownEmojis) { emoji ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(9.dp))
                                .clickable {
                                    if (input.length < 2000) {
                                        input = (input + emoji).take(2000)
                                    }
                                    showEmojiPicker = false
                                    emojiSearch = ""
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 21.sp)
                        }
                    }
                }
            }
        }

        if (attachmentUploading) {
            Text(
                "Загружаем вложение…",
                color = VibeChatGreen,
                fontSize = 11.sp,
                modifier = Modifier.padding(
                    start = 16.dp,
                    bottom = 4.dp
                )
            )
        }

        // Нижняя панель ввода.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xF20D111A))
                .padding(
                    start = 7.dp,
                    end = 9.dp,
                    top = 5.dp,
                    bottom = 7.dp
                ),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                enabled = chatReady && !attachmentUploading,
                onClick = {
                    attachmentPicker.launch(
                        arrayOf(
                            "image/*",
                            "video/*",
                            "application/pdf",
                            "text/*",
                            "application/*"
                        )
                    )
                },
                modifier = Modifier.size(39.dp)
            ) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = "Прикрепить фото или файл",
                    tint = if (attachmentUploading) {
                        VibeChatMuted
                    } else {
                        VibeChatGreen
                    },
                    modifier = Modifier.size(21.dp)
                )
            }

            IconButton(
                onClick = {
                    showEmojiPicker = !showEmojiPicker
                },
                modifier = Modifier.size(39.dp)
            ) {
                Icon(
                    Icons.Default.EmojiEmotions,
                    contentDescription = "Открыть Emoji",
                    tint = if (showEmojiPicker) {
                        VibeChatAccent
                    } else {
                        VibeChatGreen
                    },
                    modifier = Modifier.size(21.dp)
                )
            }

            Spacer(Modifier.width(3.dp))

            Row(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(VibeChatPanel)
                    .border(
                        1.dp,
                        Color(0xFF35444A),
                        RoundedCornerShape(22.dp)
                    )
                    .padding(
                        horizontal = 13.dp,
                        vertical = 11.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = {
                        input = it.take(2000)
                    },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(
                        color = VibeChatText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    ),
                    cursorBrush = Brush.verticalGradient(
                        listOf(
                            VibeChatGreen,
                            VibeChatAccent
                        )
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (input.isEmpty()) {
                                Text(
                                    "Написать сообщение…",
                                    color = VibeChatMuted,
                                    fontSize = 13.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            Spacer(Modifier.width(7.dp))

            val canSend = input.isNotBlank() &&
                chatReady &&
                myId.isNotBlank() &&
                otherUserId.isNotBlank() &&
                myId != otherUserId &&
                !sending

            Box(
                modifier = Modifier
                    .size(43.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) {
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF3A6960),
                                    Color(0xFF263E46)
                                )
                            )
                        } else {
                            Brush.linearGradient(
                                listOf(
                                    VibeChatPanel,
                                    VibeChatPanel
                                )
                            )
                        }
                    )
                    .border(
                        1.dp,
                        if (canSend) {
                            Color(0xFF477B70)
                        } else {
                            VibeChatLine
                        },
                        CircleShape
                    )
                    .clickable(enabled = canSend) {
                        val textToSend = input.trim()

                        if (textToSend.isNotBlank() && !sending) {
                            sending = true
                            error = ""

                            db.collection("chats")
                                .document(chatId)
                                .collection("messages")
                                .add(
                                    mapOf(
                                        "text" to textToSend,
                                        "authorId" to myId,
                                        "authorName" to myName,
                                        "createdAt" to
                                            FieldValue.serverTimestamp(),
                                        "readBy" to listOf(myId)
                                    )
                                )
                                .addOnSuccessListener {
                                    input = ""
                                    sending = false
                                }
                                .addOnFailureListener { exception ->
                                    error =
                                        "Не удалось отправить сообщение: " +
                                        (
                                            exception.localizedMessage
                                                ?: "ошибка Firebase"
                                        )
                                    sending = false
                                }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Отправить",
                    tint = if (canSend) {
                        VibeChatGreen
                    } else {
                        VibeChatMuted
                    },
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}
