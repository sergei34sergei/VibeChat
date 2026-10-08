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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Navy = Color(0xFF070A18)
private val Panel = Color(0xFF11162B)
private val Panel2 = Color(0xFF171D38)
private val Purple = Color(0xFF7C4DFF)
private val Cyan = Color(0xFF19D3FF)
private val Pink = Color(0xFFFF3DBE)
private val TextMain = Color(0xFFF6F5FF)
private val TextMuted = Color(0xFF969CB8)

private data class Chat(val name:String,val last:String,val time:String,val online:Boolean=false,val unread:Int=0)

class MainActivity: ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        setContent { VibeChatApp() }
    }
}

@Composable
fun VibeChatApp(){
    var tab by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<Chat?>(null) }
    MaterialTheme(colorScheme = darkColorScheme(background=Navy, surface=Panel, primary=Cyan)) {
        if(selected != null) ChatScreen(selected!!){ selected=null }
        else Scaffold(containerColor=Navy, bottomBar={ BottomNav(tab){tab=it} }) { p ->
            Column(Modifier.fillMaxSize().padding(p).padding(horizontal=18.dp)) {
                when(tab){
                    0 -> Home{selected=it}
                    1 -> Contacts()
                    else -> Profile()
                }
            }
        }
    }
}

@Composable
private fun Home(onOpen:(Chat)->Unit){
    Row(Modifier.fillMaxWidth().padding(top=20.dp,bottom=14.dp),verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f)){
            Text("Vibe Chat",fontSize=30.sp,fontWeight=FontWeight.ExtraBold,color=TextMain)
            Text("Общайся. Делись. Будь на связи.",fontSize=12.sp,color=TextMuted)
        }
        IconButton(onClick={}){Icon(Icons.Default.Search,null,tint=TextMain)}
        Box(Modifier.size(42.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Purple,Pink))),contentAlignment=Alignment.Center){
            Icon(Icons.Default.Add,null,tint=Color.White,modifier=Modifier.clickable{})
        }
    }
    OutlinedTextField(value="",onValueChange={},modifier=Modifier.fillMaxWidth(),enabled=false,
        placeholder={Text("Поиск чатов и пользователей",color=TextMuted)},leadingIcon={Icon(Icons.Default.Search,null,tint=TextMuted)},
        shape=RoundedCornerShape(18.dp),colors=OutlinedTextFieldDefaults.colors(disabledBorderColor=Color.Transparent,disabledContainerColor=Panel2,disabledTextColor=TextMain,disabledPlaceholderColor=TextMuted,disabledLeadingIconColor=TextMuted))
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        listOf("Все","Личные","Группы","Каналы").forEachIndexed{ i,t ->
            Surface(shape=RoundedCornerShape(18.dp),color=if(i==0) Color.Transparent else Panel2,modifier=Modifier.clickable{}){
                Box(Modifier.background(if(i==0) Brush.horizontalGradient(listOf(Cyan,Purple)) else Brush.linearGradient(listOf(Panel2,Panel2))).padding(horizontal=16.dp,vertical=8.dp)){Text(t,fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=TextMain)}
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    val chats=listOf(
        Chat("Андрей","Привет! Как дела? 🔥","09:41",true,2),
        Chat("Лучшие друзья 💙","Женя: Когда встречаемся?","09:32",true,5),
        Chat("Семья","Мама: Хорошо, спасибо!","08:45",true,1),
        Chat("Кристина","📷 Фото","08:20"),
        Chat("Игры и технологии","Иван: Кто играет сегодня?","07:56",true,3),
        Chat("Дима","🎙 Голосовое сообщение","07:32"),
        Chat("Виктория","🎨 Стикер","Вчера")
    )
    LazyColumn(contentPadding=PaddingValues(vertical=8.dp)){items(chats){c->ChatRow(c,onOpen)}}
}

@Composable private fun ChatRow(c:Chat,onOpen:(Chat)->Unit){
    Row(Modifier.fillMaxWidth().clickable{onOpen(c)}.padding(vertical=9.dp),verticalAlignment=Alignment.CenterVertically){
        Avatar(c.name,c.online)
        Column(Modifier.weight(1f).padding(start=12.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){Text(c.name,fontWeight=FontWeight.Bold,fontSize=16.sp,color=TextMain);Spacer(Modifier.weight(1f));Text(c.time,fontSize=11.sp,color=TextMuted)}
            Text(c.last,maxLines=1,fontSize=13.sp,color=TextMuted)
        }
        if(c.unread>0){Spacer(Modifier.width(7.dp));Box(Modifier.size(22.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(Cyan,Purple))),contentAlignment=Alignment.Center){Text(c.unread.toString(),fontSize=10.sp,fontWeight=FontWeight.Bold,color=Color.White)}}
    }
}

@Composable private fun BottomNav(tab:Int,onTab:(Int)->Unit){
    NavigationBar(containerColor=Color(0xFF0B0F22),tonalElevation=0.dp){
        val items=listOf("Чаты" to Icons.Default.Chat,"Контакты" to Icons.Default.People,"Профиль" to Icons.Default.Person)
        items.forEachIndexed{i,(label,icon)->NavigationBarItem(selected=tab==i,onClick={onTab(i)},icon={Icon(icon,null)},label={Text(label,fontSize=10.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Cyan,selectedTextColor=Cyan,unselectedIconColor=TextMuted,unselectedTextColor=TextMuted,indicatorColor=Color(0x3320D9FF)))}
    }
}

@Composable private fun Contacts(){
    Column(Modifier.fillMaxSize().padding(top=20.dp)){Text("Контакты",fontSize=28.sp,fontWeight=FontWeight.ExtraBold,color=TextMain);Text("Люди, с которыми ты на связи",color=TextMuted,fontSize=13.sp);Spacer(Modifier.height(22.dp));
        Button(onClick={},shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().height(54.dp),colors=ButtonDefaults.buttonColors(containerColor=Purple)){Icon(Icons.Default.PersonAdd,null);Spacer(Modifier.width(8.dp));Text("Добавить контакт",fontWeight=FontWeight.Bold)}
        Spacer(Modifier.height(20.dp)); listOf("Катя","Саша","Макс","Алина").forEach{ Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Avatar(it,true);Text(it,Modifier.padding(start=12.dp),fontWeight=FontWeight.SemiBold,color=TextMain)} }
    }
}

@Composable private fun Profile(){
    Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally){Spacer(Modifier.height(22.dp));
        Box(Modifier.size(112.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Pink,Purple,Cyan))),contentAlignment=Alignment.Center){Box(Modifier.size(98.dp).clip(CircleShape).background(Navy),contentAlignment=Alignment.Center){Text("С",fontSize=46.sp,fontWeight=FontWeight.ExtraBold,color=TextMain)}}
        Spacer(Modifier.height(12.dp));Text("Сергей",fontSize=25.sp,fontWeight=FontWeight.ExtraBold,color=TextMain);Text("● в сети",color=Cyan,fontSize=13.sp);Text("Жизнь — это игра, а я в неё играю 🎮",color=TextMuted,fontSize=12.sp,modifier=Modifier.padding(top=6.dp));Spacer(Modifier.height(22.dp));
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){Stat("524","Друзья");Stat("12","Группы");Stat("8.4K","Подписчики")}
        Spacer(Modifier.height(22.dp));listOf("✎  Изменить профиль","🔔  Уведомления","🔒  Приватность","⚙  Настройки","?  Помощь").forEach{Surface(Modifier.fillMaxWidth().padding(vertical=3.dp),shape=RoundedCornerShape(16.dp),color=Panel2){Text(it,Modifier.padding(16.dp),color=TextMain,fontSize=14.sp)}}
    }
}
@Composable private fun Stat(n:String,l:String){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(n,fontWeight=FontWeight.ExtraBold,fontSize=17.sp,color=TextMain);Text(l,fontSize=11.sp,color=TextMuted)}}

@Composable private fun ChatScreen(chat:Chat,onBack:()->Unit){
    var msg by remember{mutableStateOf("")};var messages by remember{mutableStateOf(listOf("Привет! Как дела? 🔥","Всё отлично! 😎","Сегодня вечером есть планы?"))}
    Scaffold(containerColor=Navy,topBar={TopAppBar(title={Row(verticalAlignment=Alignment.CenterVertically){Avatar(chat.name,chat.online);Column(Modifier.padding(start=10.dp)){Text(chat.name,fontWeight=FontWeight.Bold,color=TextMain);Text(if(chat.online)"в сети" else "был(а) недавно",fontSize=11.sp,color=Cyan)}}},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null,tint=TextMain)}},actions={IconButton(onClick={}){Icon(Icons.Default.Call,null,tint=TextMain)};IconButton(onClick={}){Icon(Icons.Default.MoreVert,null,tint=TextMain)}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Color(0x99070A18)))},bottomBar={Row(Modifier.fillMaxWidth().background(Color(0xFF0B0F22)).padding(8.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick={}){Icon(Icons.Default.AttachFile,null,tint=Cyan)};OutlinedTextField(msg,{msg=it},Modifier.weight(1f),placeholder={Text("Сообщение…",color=TextMuted)},shape=RoundedCornerShape(22.dp),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Cyan,unfocusedBorderColor=Color(0x334C5578),focusedContainerColor=Panel2,unfocusedContainerColor=Panel2,focusedTextColor=TextMain,unfocusedTextColor=TextMain));IconButton(onClick={if(msg.isNotBlank()){messages=messages+msg;msg=""}}){Box(Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Cyan,Purple))),contentAlignment=Alignment.Center){Icon(Icons.Default.Send,null,tint=Color.White)}}}}){p->
        LazyColumn(Modifier.fillMaxSize().padding(p).padding(horizontal=14.dp),contentPadding=PaddingValues(vertical=18.dp)){items(messages){m->Row(Modifier.fillMaxWidth(),horizontalArrangement=if(m==messages.last())Arrangement.End else Arrangement.Start){Surface(color=if(m==messages.last())Color(0xFF234D9A) else Panel2,shape=RoundedCornerShape(18.dp,18.dp,18.dp,5.dp),modifier=Modifier.padding(vertical=5.dp).widthIn(max=300.dp)){Text(m,Modifier.padding(horizontal=15.dp,vertical=11.dp),color=TextMain)}}}}
    }
}

@Composable private fun Avatar(name:String,online:Boolean){
    Box(Modifier.size(54.dp)){Box(Modifier.size(54.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Purple,Cyan))),contentAlignment=Alignment.Center){Box(Modifier.size(50.dp).clip(CircleShape).background(Color(0xFF202744)),contentAlignment=Alignment.Center){Text(name.take(1),fontSize=21.sp,fontWeight=FontWeight.Bold,color=TextMain)}};if(online)Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFF35E39B)).align(Alignment.BottomEnd))}
}
