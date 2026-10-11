
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

    val pageBackground = Brush.verticalGradient(
        listOf(
            Color(0xFF080D1C),
            Color(0xFF17102E),
            Color(0xFF0B2035)
        )
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(pageBackground)
            .statusBarsPadding()
    ) {
        if (privateChatUserId == null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFB56CFF), Color(0xFF53E4F7))
                            ),
                            RoundedCornerShape(17.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("V", color = Color(0xFF101024),
                        fontSize = 29.sp, fontWeight = FontWeight.Black)
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        "VIBE CHAT",
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Общайся без границ",
                        color = Color(0xFF9CA8C7),
                        fontSize = 12.sp
                    )
                }

                Box(
                    Modifier
                        .background(Color(0xFF211D3A), RoundedCornerShape(15.dp))
                        .clickable { tab = "Профиль" }
                        .padding(12.dp)
                ) {
                    Text("👤", fontSize = 21.sp)
                }

                Spacer(Modifier.width(8.dp))

                TextButton(onClick = onLogout) {
                    Text("Выйти", color = Color(0xFF91E8FF), fontSize = 12.sp)
                }
            }

            if (tab == "Чат") {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF6437B9),
                                        Color(0xFF315DAD),
                                        Color(0xFF167F96)
                                    )
                                ),
                                RoundedCornerShape(26.dp)
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Text(
                                "ТВОЙ МИР ОБЩЕНИЯ",
                                color = Color(0xFFC8F7FF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Привет, ${savedName.ifBlank { "друг" }}! ✨",
                                color = Color.White,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Напиши близким или найди новых друзей.",
                                color = Color.White.copy(alpha = 0.88f),
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { tab = "Пользователи" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(
                                        "＋ Новый чат",
                                        color = Color(0xFF33215D),
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                OutlinedButton(
                                    onClick = { tab = "Общий чат" },
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp, Color.White.copy(alpha = 0.7f)
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("🌐 Общий", color = Color.White)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(22.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Сообщения",
                            color = Color.White,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "ЛИЧНЫЕ ЧАТЫ",
                            color = Color(0xFF8D91B6),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Все твои переписки в одном месте",
                        color = Color(0xFF9CA8C7),
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))
                }
            } else {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        when (tab) {
                            "Пользователи" -> "Найди друзей 👥"
                            "Алекс AI" -> "Твой AI-помощник ✨"
                            "Профиль" -> "Твой профиль 👤"
                            "Общий чат" -> "Общение со всеми 🌐"
                            else -> "Vibe Chat"
                        },
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Твоя атмосфера. Твои люди.",
                        color = Color(0xFF9CA8C7),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                }
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111326))
                    .padding(horizontal = 7.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(
                    Triple("Чат", "💬", "Чаты"),
                    Triple("Пользователи", "👥", "Люди"),
                    Triple("Алекс AI", "✨", "Алекс AI"),
                    Triple("Профиль", "👤", "Профиль")
                ).forEach { item ->
                    val selected = tab == item.first

                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { tab = item.first }
                            .background(
                                if (selected) Color(0xFF332451)
                                else Color.Transparent,
                                RoundedCornerShape(17.dp)
                            )
                            .padding(vertical = 9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(item.second, fontSize = 21.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            item.third,
                            color = if (selected) Color(0xFF75E8FF)
                            else Color(0xFF9CA8C7),
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold
                            else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
