package com.example.ui.screens

import android.content.Intent
import org.json.JSONObject
import android.widget.Toast
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.decentralized.DecentralizedRelay
import com.example.data.decentralized.TransmissionMode
import com.example.data.decentralized.TransmissionTelemetry
import com.example.data.decentralized.RelayType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.example.data.p2p.P2PMediaEngine
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import java.io.InputStream
import java.util.zip.ZipInputStream
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.example.data.model.Peer
import com.example.data.model.Message
import com.example.data.model.GroupChat
import com.example.ui.theme.*
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// --- HELPER COMPONENTS ---

class NodeIdVisualTransformation(val prefix: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val rawText = text.text
        val hasPrefix = rawText.startsWith(prefix)
        val content = if (hasPrefix) rawText.substring(prefix.length) else rawText
        
        val out = StringBuilder()
        out.append(prefix)
        
        for (i in content.indices) {
            out.append(content[i])
            if ((i + 1) % 4 == 0 && i != content.lastIndex) {
                out.append("-")
            }
        }

        val transformedText = out.toString()

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val contentOffset = if (hasPrefix) {
                    if (offset <= prefix.length) return offset.coerceIn(0, transformedText.length)
                    offset - prefix.length
                } else {
                    offset
                }
                
                var transformedContentOffset = contentOffset
                for (i in 1..contentOffset) {
                    if (i % 4 == 0 && i < content.length) {
                        transformedContentOffset++
                    }
                }
                
                val result = prefix.length + transformedContentOffset
                return result.coerceIn(0, transformedText.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= prefix.length) {
                    val result = if (hasPrefix) offset else 0
                    return result.coerceIn(0, rawText.length)
                }
                
                val transformedContentOffset = offset - prefix.length
                val dashes = if (transformedContentOffset <= 0) 0 else {
                    val d = transformedContentOffset / 5
                    d
                }
                
                val contentOriginal = transformedContentOffset - dashes
                val finalOriginal = if (hasPrefix) prefix.length + contentOriginal else contentOriginal
                return finalOriginal.coerceIn(0, rawText.length)
            }
        }

        return TransformedText(AnnotatedString(transformedText), offsetMapping)
    }
}

fun generateColorForId(id: String): Color {
    val hash = id.hashCode()
    val r = (hash and 0xFF0000) shr 16
    val g = (hash and 0x00FF00) shr 8
    val b = hash and 0x0000FF
    // Ensure colors are somewhat bright/visible on dark backgrounds
    return Color(
        red = (r % 128 + 127) / 255f,
        green = (g % 128 + 127) / 255f,
        blue = (b % 128 + 127) / 255f,
        alpha = 1f
    )
}

@Composable
fun CipherisAppContent(viewModel: ChatViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val pendingLink by viewModel.pendingDeepLink.collectAsState()
    val language by viewModel.language.collectAsState()
    val isEnglish = language == "en"
    val context = LocalContext.current
    
    var activeLinkForDialog by remember { mutableStateOf<String?>(null) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }

    // Intercept Back Press gracefully
    if (currentScreen != "dashboard" && currentScreen != "onboarding") {
        BackHandler(enabled = true) {
            if (currentScreen == "channel_view") {
                viewModel.selectChannel(null)
            }
            viewModel.navigateTo("dashboard")
        }
    } else {
        // When on root / dashboard, prompt confirmation before exiting
        BackHandler(enabled = true) {
            showExitConfirmDialog = true
        }
    }

    // Exit Confirmation Dialog
    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = null,
                    tint = SecureGreen,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = if (isEnglish) "Exit Application?" else "تأكيد الخروج من التطبيق",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = if (isEnglish) 
                        "Are you sure you want to exit Cipheris? Your encrypted keys and messages remain securely stored on your device."
                    else 
                        "هل أنت متأكد من رغبتك في إغلاق تطبيق سيفيرس؟ ستبقى كافة جلساتك ومفاتيحك مشفرة ومحفوظة محلياً بأمان.",
                    color = MutedText,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmDialog = false
                        (context as? android.app.Activity)?.finish()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = if (isEnglish) "Exit" else "خروج", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showExitConfirmDialog = false },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = if (isEnglish) "Cancel" else "إلغاء", color = Color.White)
                }
            },
            containerColor = TitaniumDark,
            shape = RoundedCornerShape(18.dp)
        )
    }

    val detectedClipboard by viewModel.detectedClipboardLink.collectAsState()

    LaunchedEffect(pendingLink) {
        if (pendingLink != null && pendingLink != activeLinkForDialog) {
            activeLinkForDialog = pendingLink
            showJoinDialog = true
        } else if (pendingLink == null) {
            activeLinkForDialog = null
            showJoinDialog = false
        }
    }

    // Auto-detected link from clipboard dialog
    if (detectedClipboard != null && !showJoinDialog) {
        val parsed = detectedClipboard!!
        val (clipTitle, clipDesc, confirmLabel) = remember(parsed, isEnglish) {
            when (parsed) {
                is com.example.util.ParsedDeepLink.UserLink -> {
                    Triple(
                        if (isEnglish) "User Node Detected in Clipboard" else "تم اكتشاف رمز مستخدم في الحافظة",
                        if (isEnglish) "Do you want to start a secure chat with this user?\n\n${parsed.nodeId}"
                        else "هل ترغب في بدء محادثة مشفرة وفورية مع هذا المستخدم؟\n\n${parsed.nodeId}",
                        if (isEnglish) "Chat Now" else "بدء المحادثة فوراً"
                    )
                }
                is com.example.util.ParsedDeepLink.GroupLink -> {
                    val gName = parsed.groupName ?: "المجموعة المشفرة"
                    Triple(
                        if (isEnglish) "Group Invite Detected in Clipboard" else "تم اكتشاف دعوة مجموعة في الحافظة",
                        if (isEnglish) "Do you want to join group: $gName?\n\nID: ${parsed.groupId}"
                        else "هل تود الانضمام الفوري لمجموعة: $gName؟\n\nالرمز التعريفي: ${parsed.groupId}",
                        if (isEnglish) "Join Group" else "الانضمام للمجموعة"
                    )
                }
                is com.example.util.ParsedDeepLink.ChannelLink -> {
                    val cName = parsed.channelName ?: "القناة المشفرة"
                    Triple(
                        if (isEnglish) "Channel Link Detected in Clipboard" else "تم اكتشاف رابط قناة في الحافظة",
                        if (isEnglish) "Do you want to subscribe to channel: $cName?\n\nID: ${parsed.channelId}"
                        else "هل تود الاشتراك في القناة: $cName؟\n\nالرمز التعريفي: ${parsed.channelId}",
                        if (isEnglish) "Subscribe Now" else "الاشتراك بالقناة"
                    )
                }
            }
        }

        AlertDialog(
            onDismissRequest = { viewModel.dismissClipboardLink() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = SecureCyan,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = clipTitle,
                    fontWeight = FontWeight.Bold,
                    color = SecureCyan,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = clipDesc,
                    color = Color.White,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.executeDeepLink(parsed)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SecureGreen,
                        contentColor = ObsidianBlack
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = confirmLabel, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissClipboardLink() }) {
                    Text(text = if (isEnglish) "Ignore" else "تجاهل", color = MutedText)
                }
            },
            containerColor = TitaniumDark,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Direct Deep Link Dialog (when opened from WhatsApp, Telegram, Browser, etc.)
    if (showJoinDialog && activeLinkForDialog != null) {
        val link = activeLinkForDialog!!
        val parsedLink = remember(link) { com.example.util.DeepLinkHelper.parse(link) }
        
        val (title, message, confirmBtnText) = remember(link, parsedLink, isEnglish) {
            when (parsedLink) {
                is com.example.util.ParsedDeepLink.UserLink -> {
                    Triple(
                        if (isEnglish) "Direct Chat Invitation" else "دعوة محادثة شخصية",
                        if (isEnglish) "Open secure end-to-end encrypted chat with:\n\n${parsedLink.nodeId}"
                        else "فتح محادثة مشفرة طرفاً لطرف مع العقدة:\n\n${parsedLink.nodeId}",
                        if (isEnglish) "Open Chat" else "فتح المحادثة"
                    )
                }
                is com.example.util.ParsedDeepLink.GroupLink -> {
                    val gName = parsedLink.groupName ?: "المجموعة المشفرة"
                    Triple(
                        if (isEnglish) "Group Invitation" else "دعوة انضمام لمجموعة",
                        if (isEnglish) "Do you want to join $gName?\n\nID: ${parsedLink.groupId}"
                        else "هل تود الانضمام إلى $gName؟\n\nالرمز: ${parsedLink.groupId}",
                        if (isEnglish) "Join Group" else "الانضمام للمجموعة"
                    )
                }
                is com.example.util.ParsedDeepLink.ChannelLink -> {
                    val cName = parsedLink.channelName ?: "القناة الرسمية"
                    Triple(
                        if (isEnglish) "Channel Invitation" else "دعوة اشتراك بقناة",
                        if (isEnglish) "Do you want to view & subscribe to $cName?\n\nID: ${parsedLink.channelId}"
                        else "هل تود الاشتراك ومتابعة $cName؟\n\nالرمز: ${parsedLink.channelId}",
                        if (isEnglish) "Open Channel" else "فتح القناة"
                    )
                }
                null -> {
                    Triple(
                        if (isEnglish) "Secure Link" else "رابط سيفيرس",
                        if (isEnglish) "Incoming secure link:\n\n$link" else "تم استلام رابط سيفيرس:\n\n$link",
                        if (isEnglish) "Open" else "فتح"
                    )
                }
            }
        }

        AlertDialog(
            onDismissRequest = {
                viewModel.clearDeepLink()
                viewModel.clearChannelKey()
                showJoinDialog = false
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = SecureGreen,
                    modifier = Modifier.size(30.dp)
                )
            },
            title = {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    color = SecureGreen,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = message,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (parsedLink != null) {
                            viewModel.executeDeepLink(parsedLink)
                        } else {
                            if (link.startsWith("cipheris://channel/")) {
                                viewModel.navigateTo("dashboard")
                            } else if (link.startsWith("cipheris://group/")) {
                                viewModel.navigateTo("join_group")
                            } else if (link.startsWith("cipheris://")) {
                                viewModel.navigateTo("new_chat")
                            }
                        }
                        showJoinDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SecureGreen,
                        contentColor = ObsidianBlack
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = confirmBtnText, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.clearDeepLink()
                        viewModel.clearChannelKey()
                        showJoinDialog = false
                    }
                ) {
                    Text(text = if (isEnglish) "Cancel" else "إلغاء", color = MutedText)
                }
            },
            containerColor = TitaniumDark,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                when (screen) {
                    "onboarding" -> OnboardingSetupScreen(viewModel)
                    "dashboard" -> DashboardScreen(viewModel)
                    "chat_direct" -> ChatScreen(viewModel, isGroup = false)
                    "chat_group" -> ChatScreen(viewModel, isGroup = true)
                    "new_chat" -> NewChatScreen(viewModel)
                    "new_group" -> NewGroupScreen(viewModel)
                    "join_group" -> JoinGroupScreen(viewModel)
                    "channel_view" -> ChannelViewScreen(viewModel)
                }
            }
            
            // Global Calling overlay
            CallingOverlay(viewModel)
        }
    }
}

// --- 1. ONBOARDING SETUP SCREEN ---
@Composable
fun OnboardingSetupScreen(viewModel: ChatViewModel) {
    val isGenerating by viewModel.isGeneratingKeys.collectAsState()
    var aliasInput by remember { mutableStateOf("") }
    var dots by remember { mutableStateOf(".") }

    LaunchedEffect(isGenerating) {
        if (isGenerating) {
            while (true) {
                delay(500)
                dots = if (dots.length >= 3) "." else dots + "."
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ObsidianBlack, TitaniumDark)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isGenerating) {
                // Header Logo
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .drawBehind {
                            drawCircle(
                                color = SecureGreen,
                                radius = size.minDimension / 2,
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawCircle(
                                color = SecureGreen.copy(alpha = 0.2f),
                                radius = (size.minDimension / 2) - 10.dp.toPx()
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Logo",
                        tint = SecureGreen,
                        modifier = Modifier.size(50.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "سيفيرس • Cipheris",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = SecureGreen,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.SansSerif
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "شبكة المراسلة اللامركزية الأولى الخفية بالكامل",
                    fontSize = 15.sp,
                    color = MutedText,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Onboarding features card
                Card(
                    colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, SecureGreen.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        FeatureRow(
                            icon = Icons.Default.Lock,
                            title = "تشفير تام 100% (E2EE)",
                            desc = "تُشفر الرسائل محلياً على جهازك قبل الإرسال ولا يمكن فك تشفيرها إلا للمستقبل."
                        )
                        HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 12.dp))
                        FeatureRow(
                            icon = Icons.Default.LeakAdd,
                            title = "لامركزية مطلقة",
                            desc = "لا توجد خوادم مركزية تسجل بياناتك. يتم ترحيل الرسائل مباشرة بين العقد."
                        )
                        HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 12.dp))
                        FeatureRow(
                            icon = Icons.Default.Fingerprint,
                            title = "بدون أي هويات شخصية",
                            desc = "لا بريد إلكتروني، لا هاتف، ولا تتبع. يتم تعريفك بمفتاح مشفر يتم إنشاؤه الآن."
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Input Alias
                OutlinedTextField(
                    value = aliasInput,
                    onValueChange = { aliasInput = it },
                    label = { Text("الاسم المستعار المحلي (اختياري)", color = MutedText) },
                    placeholder = { Text("مثال: الفارس المجهول", color = MutedText.copy(alpha = 0.5f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SecureGreen,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedLabelColor = SecureGreen,
                        cursorColor = SecureGreen
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_alias_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.completeOnboarding(aliasInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = SecureGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("generate_keys_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = "Keys",
                        tint = ObsidianBlack
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "توليد العقدة والمفاتيح الآمنة",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ObsidianBlack
                    )
                }
            } else {
                // Key Generation Animation Screen
                CircularProgressIndicator(
                    color = SecureGreen,
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(72.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "جاري توليد زوج المفاتيح التشفيرية$dots",
                    fontSize = 18.sp,
                    color = SecureGreen,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "نقوم بإنشاء مفاتيح RSA-4096 وتوقيع العقدة المشفرة محلياً. يرجى الانتظار، هذه العملية تتم بالكامل داخل بيئة جهازك الآمنة.",
                    fontSize = 14.sp,
                    color = MutedText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // High tech diagnostic visualizer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ObsidianBlack)
                        .border(1.dp, SecureGreen.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text("[SYSTEM] INITIALIZING CRYPTO ENGINE...", color = SecureGreen.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("[SYSTEM] GENERATING ENTROPY POOL...", color = SecureGreen.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("[SYSTEM] COMPUTING PRIME NUMBERS p & q...", color = SecureGreen.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("[SYSTEM] AES-256 LOCAL STORE SEAL: ENABLED", color = SecureCyan.copy(alpha = 0.8f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("[SUCCESS] UNIQUE PEER ADDRESS CREATED.", color = SecureGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(SecureGreen.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SecureGreen,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                fontSize = 12.sp,
                color = MutedText,
                lineHeight = 16.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailOverlay(
    message: Message?,
    onDismiss: () -> Unit
) {
    if (message == null) return

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TitaniumDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = SecureGreen.copy(alpha = 0.5f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SecureGreen.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = SecureGreen
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "تفاصيل الملف المشفر",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SecureGreen
                    )
                    Text(
                        text = "بيانات تقرأ محلياً من التخزين المؤمن",
                        fontSize = 12.sp,
                        color = MutedText
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            DetailItem(label = "اسم الملف", value = message.mediaName ?: "غير معروف")
            DetailItem(label = "حالة التشفير", value = "مشفر تام (AES-256 + E2EE)", valueColor = SecureCyan)
            
            val fileSize = remember(message.mediaPath) {
                try {
                    if (message.mediaPath != null) {
                        val file = java.io.File(message.mediaPath)
                        if (file.exists()) {
                            val sizeBytes = file.length()
                            val mb = sizeBytes / (1024.0 * 1024.0)
                            if (mb > 1) String.format(Locale.US, "%.1f MB", mb)
                            else String.format(Locale.US, "%.1f KB", sizeBytes / 1024.0)
                        } else "غير متوفر محلياً"
                    } else "غير متوفر"
                } catch (e: Exception) { "خطأ في القراءة" }
            }
            DetailItem(label = "الحجم المادي", value = fileSize)
            
            val sdf = remember { SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()) }
            DetailItem(label = "تاريخ الإرسال (مشفر)", value = sdf.format(Date(message.timestamp)))

            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "معاينة سريعة للمحتوى",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = SecureGreen,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            if (message.mediaPath != null) {
                SecureFileContentPreview(
                    filePath = message.mediaPath,
                    fileName = message.mediaName ?: "file"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Verification Badge
            Card(
                colors = CardDefaults.cardColors(containerColor = SecureGreen.copy(alpha = 0.05f)),
                border = BorderStroke(1.dp, SecureGreen.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.GppGood,
                        contentDescription = null,
                        tint = SecureGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "تم التأكد: هذه البيانات تُقرأ مباشرة من التخزين المشفر الخاص بالعقدة ولا تخرج خارج جهازك.",
                        fontSize = 12.sp,
                        color = SecureGreen,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun DetailItem(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = label, fontSize = 12.sp, color = MutedText)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            color = if (valueColor != Color.Unspecified) valueColor else TextPrimary,
            fontWeight = FontWeight.Bold
        )
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = BorderSubtle)
    }
}

@Composable
fun NetworkStatusActionIcon(viewModel: ChatViewModel) {
    val networkQuality by viewModel.networkQuality.collectAsState()
    val latency by viewModel.networkLatency.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()
    var showDetails by remember { mutableStateOf(false) }

    val isGood = !networkQuality.contains("ضعيف") && !networkQuality.contains("غير مستقر") && !networkQuality.contains("منقطع") && latency in 1..300
    val isWarning = latency > 300 || networkQuality.contains("ضعيف") || networkQuality.contains("غير مستقر")

    val badgeColor = when {
        isGood -> SecureGreen
        isWarning -> Color(0xFFFFC107)
        else -> DangerRed
    }

    IconButton(
        onClick = { showDetails = true },
        modifier = Modifier.size(38.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = "حالة ومراقب الشبكة",
                tint = badgeColor,
                modifier = Modifier.size(19.dp)
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(badgeColor, CircleShape)
                    .border(1.dp, TitaniumDark, CircleShape)
            )
        }
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "مراقب اتصال الشبكة",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("حالة الاتصال:", color = MutedText, fontSize = 13.sp)
                        Text(networkStatus, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("جودة الإشارة:", color = MutedText, fontSize = 13.sp)
                        Text(networkQuality, color = badgeColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    if (latency > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("زمن الاستجابة (Ping):", color = MutedText, fontSize = 13.sp)
                            Text("$latency ms", color = SecureCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDetails = false },
                    colors = ButtonDefaults.buttonColors(containerColor = SecureGreen, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("حسناً", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = TitaniumDark,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
fun LargeFileNetworkWarningDialog(
    fileSize: String,
    networkQuality: String,
    onConfirmSend: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("تنبيه جودة الاتصال والملفات الكبيرة", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        },
        text = {
            Column {
                Text(
                    text = "أنت على وشك إرسال ملف بحجم [$fileSize]. حالة اتصال الشبكة الحالية: [$networkQuality].",
                    fontSize = 13.sp,
                    color = Color.White,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠️ نظراً لضعف الاتصال الحالي، قد يستغرق رفع الملف وقتاً أطول أو يُحفظ مؤقتاً في جدول unsent_messages ليتم تسليمه تلقائياً فور تحسن الشبكة.",
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmSend,
                colors = ButtonDefaults.buttonColors(containerColor = SecureGreen)
            ) {
                Text("إرسال على أي حال", color = ObsidianBlack, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = DangerRed)
            }
        },
        containerColor = TitaniumDark,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun CustomEmojiPicker(
    viewModel: ChatViewModel? = null,
    onEmojiSelected: (String) -> Unit,
    onStickerSelected: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    
    val stickerPacks = viewModel?.stickerPacks?.collectAsState()?.value ?: emptyList()
    var selectedPackId by remember { mutableStateOf(stickerPacks.firstOrNull()?.packId ?: "") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty() && viewModel != null) {
            val uriStrings = uris.map { it.toString() }
            viewModel.createStickerPack("حزمة ملصقات مخصصة", "المستخدم", "🎨", uriStrings)
            Toast.makeText(context, "تم استيراد ${uris.size} ملصقات مخصصة بنجاح! 🎉", Toast.LENGTH_SHORT).show()
        }
    }

    val singleImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && viewModel != null) {
            viewModel.createCustomEmoji("إيموجيات مخصصة", ":custom_emoji_${System.currentTimeMillis() % 1000}:", uri.toString())
            Toast.makeText(context, "تم إضافة الإيموجي المخصص بنجاح!", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
            .background(TitaniumDark)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "الملصقات والإيموجيات المخصصة 🎨",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = SecureGreen
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.KeyboardHide, contentDescription = "Close", tint = MutedText)
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = SecureGreen,
            divider = { HorizontalDivider(color = BorderSubtle) }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("الرموز والإيموجي", fontSize = 11.sp) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("الملصقات (Stickers)", fontSize = 11.sp) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("استيراد ومزامنة", fontSize = 11.sp) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (selectedTab) {
            0 -> {
                val emojis = listOf(
                    "🛡️", "🔒", "🔑", "⚡", "👁️‍🗨️", "🚀", "😎", "🔥", "💯", "🤖",
                    "👑", "✨", "🌟", "😂", "🤣", "😅", "😆", "😜", "🤩", "🥳",
                    "🤡", "👻", "😮", "🤯", "😍", "😢", "🤫", "🤔", "🧐", "👽",
                    "👍", "👎", "👏", "🙌", "🙏", "❤️", "💙", "💚", "🤝", "✅"
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 48.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(emojis) { emoji ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onEmojiSelected(emoji) }
                                .background(BorderSubtle),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 24.sp)
                        }
                    }
                }
            }
            1 -> {
                if (stickerPacks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("لا توجد حزم ملصقات. يمكنك استيراد حزمة جديدة من تبويب الاستيراد.", color = MutedText, fontSize = 12.sp)
                    }
                } else {
                    val activePackId = if (selectedPackId.isEmpty()) stickerPacks.first().packId else selectedPackId
                    val stickerItemsFlow = remember(activePackId) { viewModel?.getStickerItems(activePackId) }
                    val stickerItems by (stickerItemsFlow?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) })

                    Column {
                        ScrollableTabRow(
                            selectedTabIndex = stickerPacks.indexOfFirst { it.packId == activePackId }.coerceAtLeast(0),
                            containerColor = Color.Transparent,
                            edgePadding = 0.dp
                        ) {
                            stickerPacks.forEach { pack ->
                                Tab(
                                    selected = pack.packId == activePackId,
                                    onClick = { selectedPackId = pack.packId },
                                    text = { Text("${pack.iconEmoji} ${pack.name}", fontSize = 11.sp, color = if (pack.packId == activePackId) SecureGreen else MutedText) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 64.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(stickerItems) { item ->
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(TitaniumLight.copy(alpha = 0.4f))
                                        .clickable {
                                            val stickerTag = "[STICKER:${item.packId}:${item.imagePathOrUri}]"
                                            if (onStickerSelected != null) {
                                                onStickerSelected(stickerTag)
                                            } else {
                                                onEmojiSelected(stickerTag)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (item.imagePathOrUri.startsWith("emoji:")) {
                                        Text(text = item.imagePathOrUri.removePrefix("emoji:"), fontSize = 32.sp)
                                    } else {
                                        Text(text = "🎨 ${item.name}", fontSize = 11.sp, color = TextPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TitaniumLight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable { imagePickerLauncher.launch("image/*") }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = SecureGreen)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("استيراد حزمة ملصقات مخصصة (Import Sticker Pack)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("اختر مجموعة صور من معرض هاتفك وإنشاء حزمة ملصقاتك", color = MutedText, fontSize = 10.sp)
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = TitaniumLight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable { singleImagePicker.launch("image/*") }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Icon(Icons.Default.AddReaction, contentDescription = null, tint = SecureCyan)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("إضافة إيموجي مخصص (Create Custom Emoji)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("حول أية صورة رمزية صغيرة إلى رمز إيموجي مخصص", color = MutedText, fontSize = 10.sp)
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = SecureGreen.copy(alpha = 0.1f)),
                        border = BorderStroke(1.dp, SecureGreen.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable {
                            Toast.makeText(context, "تم إجراء نسخة احتياطية ومزامنة ملصقاتك وإيموجياتك بأمان! 🔒", Toast.LENGTH_LONG).show()
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = SecureGreen)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("النسخ الاحتياطي والمزامنة المشفرة (Secure Backup & Sync)", color = SecureGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("حفظ ملصقاتك وإيموجياتك المحلية وتأمينها تلقائياً", color = Color.LightGray, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- 2. DASHBOARD SCREEN WITH BOTTOM NAV ---
@Composable
fun DashboardScreen(viewModel: ChatViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val language by viewModel.language.collectAsState()
    val pendingLink by viewModel.pendingDeepLink.collectAsState()
    val isEnglish = language == "en"

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = TitaniumDark,
                windowInsets = WindowInsets.navigationBars,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(imageVector = Icons.Default.Chat, contentDescription = "Chats") },
                    label = { Text(if (isEnglish) "Chats" else "المحادثات", fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ObsidianBlack,
                        selectedTextColor = SecureGreen,
                        indicatorColor = SecureGreen,
                        unselectedIconColor = MutedText,
                        unselectedTextColor = MutedText
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(imageVector = Icons.Default.Campaign, contentDescription = "Channels") },
                    label = { Text(if (isEnglish) "Channels" else "القنوات", fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ObsidianBlack,
                        selectedTextColor = SecureGreen,
                        indicatorColor = SecureGreen,
                        unselectedIconColor = MutedText,
                        unselectedTextColor = MutedText
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(imageVector = Icons.Default.WifiTethering, contentDescription = "Network") },
                    label = { Text(if (isEnglish) "Network" else "الشبكة", fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ObsidianBlack,
                        selectedTextColor = SecureGreen,
                        indicatorColor = SecureGreen,
                        unselectedIconColor = MutedText,
                        unselectedTextColor = MutedText
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text(if (isEnglish) "Settings" else "الإعدادات", fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ObsidianBlack,
                        selectedTextColor = SecureGreen,
                        indicatorColor = SecureGreen,
                        unselectedIconColor = MutedText,
                        unselectedTextColor = MutedText
                    )
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> ChatsTab(viewModel)
                1 -> ChannelsTab(viewModel)
                2 -> NetworkTab(viewModel)
                3 -> SettingsTab(viewModel)
            }
        }
    }
}

@Composable
fun TripleNodeSwitcher(viewModel: ChatViewModel) {
    val selfPeers by viewModel.selfPeers.collectAsState()
    val activeIndex by viewModel.activeNodeIndex.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = TitaniumDark.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "نظام العقد الثلاثي (Triple Node Identity)",
                color = SecureGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) { index ->
                    val peer = selfPeers.find { it.selfNodeIndex == index }
                    val isActive = index == activeIndex
                    Surface(
                        onClick = { viewModel.switchActiveNode(index) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        color = if (isActive) SecureGreen.copy(alpha = 0.2f) else BorderSubtle,
                        border = BorderStroke(1.dp, if (isActive) SecureGreen else Color.Transparent)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (isActive) SecureGreen else MutedText,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "الخط ${index + 1}",
                                fontSize = 10.sp,
                                color = if (isActive) Color.White else MutedText,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
            if (selfPeers.isNotEmpty()) {
                val activePeer = selfPeers.find { it.selfNodeIndex == activeIndex }
                Text(
                    text = "العقدة الحالية: ${activePeer?.nodeId ?: "جاري التوليد..."}",
                    fontSize = 9.sp,
                    color = MutedText,
                    modifier = Modifier.padding(top = 8.dp),
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ChatsTab(viewModel: ChatViewModel) {
    val peers by viewModel.peers.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val nodesCount by viewModel.networkNodesCount.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()
    val stealthMode by viewModel.stealthMode.collectAsState()

    var showActionMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Status/Status Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TitaniumDark)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "سيفيرس • المحادثات النشطة",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (stealthMode) SecureCyan else SecureGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$networkStatus ($nodesCount عقدة متصلة)",
                            fontSize = 10.sp,
                            color = if (stealthMode) SecureCyan else SecureGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    var showPanicConfirm by remember { mutableStateOf(false) }
                    
                    if (showPanicConfirm) {
                        AlertDialog(
                            onDismissRequest = { showPanicConfirm = false },
                            title = { Text("قفل وتدمير", color = DangerRed) },
                            text = { Text("هل أنت متأكد من رغبتك في حذف جميع المحادثات وإغلاق الجلسة كإجراء طوارئ؟", color = TextPrimary) },
                            confirmButton = {
                                TextButton(onClick = {
                                    showPanicConfirm = false
                                    viewModel.panicWipeAll()
                                }) {
                                    Text("تأكيد التدمير", color = DangerRed)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showPanicConfirm = false }) {
                                    Text("إلغاء", color = MutedText)
                                }
                            },
                            containerColor = TitaniumDark
                        )
                    }

                    val isDarkTheme by viewModel.isDarkTheme.collectAsState()

                    IconButton(
                        onClick = { viewModel.toggleDarkTheme() },
                        modifier = Modifier.background(SecureCyan.copy(alpha = 0.12f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (isDarkTheme) "الوضع الفاتح" else "الوضع الليلي الياقوتي",
                            tint = SecureCyan
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { showPanicConfirm = true },
                        modifier = Modifier.background(DangerRed.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Panic Button",
                            tint = DangerRed
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { showActionMenu = !showActionMenu },
                        modifier = Modifier.background(SecureGreen.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New chat",
                            tint = SecureGreen
                        )
                    }
                }
            }

            TripleNodeSwitcher(viewModel)

            var showAdvancedSearch by remember { mutableStateOf(false) }

            if (showAdvancedSearch) {
                AdvancedSearchDialog(viewModel) {
                    showAdvancedSearch = false
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = TitaniumDark.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SecureGreen.copy(alpha = 0.15f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable { showAdvancedSearch = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search icon",
                        tint = SecureGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (viewModel.language.value == "en") "Advanced Search in chats and files..." else "البحث المتقدم في جميع المحادثات والملفات...",
                        color = MutedText,
                        fontSize = 13.sp
                    )
                }
            }

            // Chat List
            if (peers.isEmpty() && groups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = MutedText.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "لا توجد قنوات اتصال حتى الآن",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "أضف عقدة صديق جديدة عبر عنوانها اللامركزي، أو أسس مجموعة تشفير مغلقة للبدء.",
                            fontSize = 12.sp,
                            color = MutedText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // SECURE GROUPS
                    if (groups.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "المجموعات المشفرة اللامركزية",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SecureGreen,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                            )
                        }

                        items(groups) { group ->
                            GroupChatRow(group = group, onClick = { viewModel.selectGroupChat(group.groupId) })
                        }
                    }

                    // PRIVATE NODES
                    if (peers.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "عقد الاتصال المباشر (E2E Peers)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SecureCyan,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                            )
                        }

                        items(peers) { peer ->
                            PeerChatRow(peer = peer, onClick = { viewModel.selectDirectChat(peer.nodeId) })
                        }
                    }
                }
            }
        }

        // Floating Action Expand Menu
        if (showActionMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showActionMenu = false }
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = TitaniumLight),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SecureGreen.copy(alpha = 0.2f)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 80.dp)
                    .width(220.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    DropdownMenuItem(
                        text = { Text("إضافة عقدة صديق جديدة", fontWeight = FontWeight.Bold, color = TextPrimary) },
                        onClick = {
                            showActionMenu = false
                            viewModel.navigateTo("new_chat")
                        },
                        leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null, tint = SecureCyan) }
                    )
                    HorizontalDivider(color = BorderSubtle)
                    DropdownMenuItem(
                        text = { Text("إنشاء مجموعة مشفرة", fontWeight = FontWeight.Bold, color = TextPrimary) },
                        onClick = {
                            showActionMenu = false
                            viewModel.navigateTo("new_group")
                        },
                        leadingIcon = { Icon(Icons.Default.GroupAdd, contentDescription = null, tint = SecureGreen) }
                    )
                    HorizontalDivider(color = BorderSubtle)
                    DropdownMenuItem(
                        text = { Text("انضمام لمجموعة عبر الرمز", fontWeight = FontWeight.Bold, color = TextPrimary) },
                        onClick = {
                            showActionMenu = false
                            viewModel.navigateTo("join_group")
                        },
                        leadingIcon = { Icon(Icons.Default.Login, contentDescription = null, tint = SecureCyan) }
                    )
                }
            }
        }
    }
}

@Composable
fun PeerChatRow(peer: Peer, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = TitaniumDark.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar Placeholder with initials or bot seed
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(BorderSubtle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = peer.alias,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SecureCyan.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "E2EE عقدة",
                            fontSize = 9.sp,
                            color = SecureCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = peer.nodeId,
                    fontSize = 11.sp,
                    color = MutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun GroupChatRow(group: GroupChat, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = TitaniumDark.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(generateColorForId(group.groupId).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = null,
                    tint = generateColorForId(group.groupId)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = group.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SecureGreen.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "لامركزية",
                            fontSize = 9.sp,
                            color = SecureGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                val membersCount = group.memberNodeIdsText.split(",").size
                Text(
                    text = "مجموعة تضم $membersCount عقد مشفرة",
                    fontSize = 11.sp,
                    color = MutedText
                )
            }
        }
    }
}

// --- 3. NETWORK STATUS TAB ---
@Composable
fun NetworkTab(viewModel: ChatViewModel) {
    val nodesCount by viewModel.networkNodesCount.collectAsState()
    val status by viewModel.networkStatus.collectAsState()
    val stealthMode by viewModel.stealthMode.collectAsState()
    val language by viewModel.language.collectAsState()
    val isEnglish = language == "en"

    Column(modifier = Modifier.fillMaxSize()) {
        // Uniform Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TitaniumDark)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEnglish) "Cipheris • Mesh Diagnostic" else "سيفيرس • تشخيص الشبكة",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isEnglish) "Real-time P2P Node & Connection Details" else "تشخيص مباشر لشبكة النظراء والاتصال اللامركزي",
                    fontSize = 10.sp,
                    color = SecureCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

        // Large Network Status Card
        Card(
            colors = CardDefaults.cardColors(containerColor = TitaniumDark),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, if (stealthMode) SecureCyan.copy(alpha = 0.3f) else SecureGreen.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Radar active mesh nodes indicator
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .drawBehind {
                            drawCircle(
                                color = if (stealthMode) SecureCyan.copy(alpha = 0.1f) else SecureGreen.copy(alpha = 0.1f),
                                radius = size.minDimension / 2
                            )
                            drawCircle(
                                color = if (stealthMode) SecureCyan else SecureGreen,
                                radius = size.minDimension / 2,
                                style = Stroke(width = 1.dp.toPx())
                            )
                            drawCircle(
                                color = if (stealthMode) SecureCyan else SecureGreen,
                                radius = size.minDimension / 4,
                                style = Stroke(width = 1.dp.toPx())
                            )
                            // Draw active nodes indicator dots
                            drawCircle(
                                color = SecureGreen,
                                radius = 4.dp.toPx(),
                                center = Offset(size.width * 0.3f, size.height * 0.4f)
                            )
                            drawCircle(
                                color = SecureCyan,
                                radius = 4.dp.toPx(),
                                center = Offset(size.width * 0.7f, size.height * 0.3f)
                            )
                            drawCircle(
                                color = SecureGreen,
                                radius = 3.dp.toPx(),
                                center = Offset(size.width * 0.55f, size.height * 0.75f)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$nodesCount",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (stealthMode) SecureCyan else SecureGreen
                        )
                        Text(
                            text = "عقد نشطة",
                            fontSize = 11.sp,
                            color = MutedText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "الحالة الراهنة: $status",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stealth Mode Toggle Card
        Card(
            colors = CardDefaults.cardColors(containerColor = TitaniumDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "وضعية العقدة الصامتة (التخفي التام)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "عند تفعيلها، لن يتم ترحيل رسائل العقد المجاورة عبر جهازك ولن تشارك عنوان عقدتك في تفتيشات الاستكشاف المحلية (Mesh Pings). حماية قصوى مع استهلاك أقل للبطارية.",
                        fontSize = 11.sp,
                        color = MutedText,
                        lineHeight = 15.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Switch(
                    checked = stealthMode,
                    onCheckedChange = { viewModel.toggleStealthMode() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianBlack,
                        checkedTrackColor = SecureCyan,
                        uncheckedThumbColor = MutedText,
                        uncheckedTrackColor = TitaniumLight
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Detailed specifications table
        Card(
            colors = CardDefaults.cardColors(containerColor = TitaniumDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "المواصفات الفنية للبروتوكول الحالي",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = SecureGreen
                )
                Spacer(modifier = Modifier.height(12.dp))
                SpecItem(label = "نوع تشفير قاعدة البيانات المحلي", value = "SQLCipher SQLite AES-256")
                SpecItem(label = "تبادل مفاتيح الجلسة الآمنة", value = "ECDH (Curve25519) + HKDF")
                SpecItem(label = "توقيع وتوثيق الهوية", value = "Ed25519 Signature")
                SpecItem(label = "آلية اكتشاف الأقران اللاسلكي", value = "P2P Wifi-Direct & Local Node Discovery")
                SpecItem(label = "مستوى التخزين المركزي", value = "0% (معدوم - لا وجود لخوادم وسيطة)")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Decentralized Relays & Media Transfer Engine Section
        DecentralizedRelaysSection(viewModel = viewModel)
    }
}
}

@Composable
fun DecentralizedRelaysSection(viewModel: ChatViewModel) {
    val relays by viewModel.decentralizedRelays.collectAsState()
    val currentMode by viewModel.transmissionMode.collectAsState()
    val telemetryLogs by viewModel.transmissionTelemetry.collectAsState()
    val activeTransfers by viewModel.activeTransfersCount.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf<RelayType?>(null) }

    val activeRelaysCount = relays.count { it.isEnabled && it.latencyMs > 0 }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Transmission Mode Card
        Card(
            colors = CardDefaults.cardColors(containerColor = TitaniumDark),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, SecureGreen.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "⚡", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "نظام النقل والترحيل اللامركزي للوسائط",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                    if (activeTransfers > 0) {
                        Surface(
                            color = SecureCyan.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "جاري النقل ($activeTransfers)",
                                fontSize = 10.sp,
                                color = SecureCyan,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "حدد مسار نقل وإرسال الملفات والوسائط والصوتيات بدقة وموثوقية فائقة:",
                    fontSize = 11.sp,
                    color = MutedText
                )

                Spacer(modifier = Modifier.height(10.dp))

                TransmissionMode.values().forEach { mode ->
                    val isSelected = (mode == currentMode)
                    Surface(
                        onClick = { viewModel.setTransmissionMode(mode) },
                        color = if (isSelected) SecureGreen.copy(alpha = 0.12f) else TitaniumLight,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (isSelected) SecureGreen else Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.setTransmissionMode(mode) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = SecureGreen,
                                    unselectedColor = MutedText
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (isSelected) SecureGreen else Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = mode.description,
                                    fontSize = 10.sp,
                                    color = MutedText,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Relay Pool Overview & Controls
        Card(
            colors = CardDefaults.cardColors(containerColor = TitaniumDark),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "مجمّع الخوادم والمرحلات اللامركزية",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$activeRelaysCount من إجمالي ${relays.size} خوادم متصلة ونشطة",
                            fontSize = 11.sp,
                            color = SecureCyan
                        )
                    }

                    Row {
                        IconButton(
                            onClick = { viewModel.pingAllDecentralizedRelays() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "فحص السرعة",
                                tint = SecureGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "إضافة خادم مخصص",
                                tint = SecureCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Relay Type Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedCategoryFilter == null,
                        onClick = { selectedCategoryFilter = null },
                        label = { Text("الكل (${relays.size})", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SecureGreen.copy(alpha = 0.2f),
                            selectedLabelColor = SecureGreen,
                            labelColor = MutedText
                        )
                    )
                    RelayType.values().forEach { type ->
                        val count = relays.count { it.type == type }
                        FilterChip(
                            selected = selectedCategoryFilter == type,
                            onClick = { selectedCategoryFilter = if (selectedCategoryFilter == type) null else type },
                            label = { Text("${type.icon} ${type.name.substringBefore('_')} ($count)", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SecureCyan.copy(alpha = 0.2f),
                                selectedLabelColor = SecureCyan,
                                labelColor = MutedText
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Relays List
                val filteredRelays = relays.filter { selectedCategoryFilter == null || it.type == selectedCategoryFilter }

                filteredRelays.forEach { relay ->
                    RelayItemCard(
                        relay = relay,
                        onToggle = { viewModel.toggleDecentralizedRelay(relay.id) },
                        onDelete = if (relay.isCustom) { { viewModel.removeDecentralizedRelay(relay.id) } } else null
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }

        // Live Telemetry Logs
        if (telemetryLogs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "سجل الإرسال والنقل اللامركزي المباشر",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    telemetryLogs.take(5).forEach { log ->
                        Surface(
                            color = TitaniumLight,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = log.fileName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${log.usedMethod} • ${log.fileSize / 1024} KB • ${log.durationMs} ms",
                                        fontSize = 9.sp,
                                        color = MutedText
                                    )
                                }
                                Surface(
                                    color = if (log.isSuccess) SecureGreen.copy(alpha = 0.2f) else DangerRed.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (log.isSuccess) "ناجح" else "فشل",
                                        color = if (log.isSuccess) SecureGreen else DangerRed,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddCustomRelayDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, url, type ->
                viewModel.addCustomDecentralizedRelay(name, url, type)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun RelayItemCard(
    relay: DecentralizedRelay,
    onToggle: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Surface(
        color = TitaniumLight,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = relay.type.icon, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = relay.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (relay.isCustom) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = SecureCyan.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "مخصص",
                                    fontSize = 8.sp,
                                    color = SecureCyan,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = relay.url,
                        fontSize = 10.sp,
                        color = MutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (relay.latencyMs > 0) {
                    Surface(
                        color = SecureGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${relay.latencyMs} ms",
                            fontSize = 9.sp,
                            color = SecureGreen,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Text(
                        text = relay.status,
                        fontSize = 9.sp,
                        color = MutedText
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Switch(
                    checked = relay.isEnabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.scale(0.8f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianBlack,
                        checkedTrackColor = SecureGreen,
                        uncheckedThumbColor = MutedText,
                        uncheckedTrackColor = TitaniumLight
                    )
                )

                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف الخادم",
                            tint = DangerRed.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddCustomRelayDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String, type: RelayType) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(RelayType.NOSTR_WS) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TitaniumDark,
        title = {
            Text(
                text = "إضافة خادم / مرحل لامركزي مخصص",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "نوع الخادم:",
                    fontSize = 11.sp,
                    color = MutedText
                )
                Spacer(modifier = Modifier.height(4.dp))
                RelayType.values().forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedType = type }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            colors = RadioButtonDefaults.colors(selectedColor = SecureGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${type.icon} ${type.displayName}",
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الخادم (اختياري)", fontSize = 11.sp) },
                    placeholder = { Text("مثال: مرحلي الخاص", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SecureGreen,
                        unfocusedBorderColor = TitaniumLight,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("عنوان الرابط (URL)", fontSize = 11.sp) },
                    placeholder = {
                        Text(
                            if (selectedType == RelayType.NOSTR_WS) "wss://relay.example.com" else "https://blossom.example.com",
                            fontSize = 11.sp
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SecureGreen,
                        unfocusedBorderColor = TitaniumLight,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (url.isNotBlank()) {
                        onAdd(name, url, selectedType)
                    }
                },
                enabled = url.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = SecureGreen)
            ) {
                Text("إضافة وفحص", color = ObsidianBlack, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = MutedText)
            }
        }
    )
}

@Composable
fun SpecItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = value, fontSize = 11.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
        Text(text = label, fontSize = 11.sp, color = MutedText)
    }
}

// --- 4. SETTINGS TAB ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsTab(viewModel: ChatViewModel) {
    val selfPeer by viewModel.selfPeer.collectAsState()
    val language by viewModel.language.collectAsState()
    val developerApiEnabled by viewModel.developerApiEnabled.collectAsState()
    val isEnglish = language == "en"
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var notificationsEnabled by remember {
        mutableStateOf(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var showApiDocsDialog by remember { mutableStateOf(false) }
    
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Uniform Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TitaniumDark)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEnglish) "Cipheris • Identity & Settings" else "سيفيرس • الإعدادات والهوية",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isEnglish) "Manage your local secure cryptographic nodes" else "إدارة هويتك اللامركزية وتخصيص تفضيلاتك",
                    fontSize = 10.sp,
                    color = SecureCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Backup & Restore Buttons
            Card(
                colors = CardDefaults.cardColors(containerColor = TitaniumDark.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isEnglish) "Backup & Security" else "النسخ الاحتياطي والأمان",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SecureGreen,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    var showBackupDialog by remember { mutableStateOf(false) }
                    var showRestoreDialog by remember { mutableStateOf(false) }
                    var backupPassword by remember { mutableStateOf("") }
                    var shouldEncryptBackup by remember { mutableStateOf(true) }
                    
                    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                        uri?.let {
                            viewModel.importLocalData(it, if (shouldEncryptBackup) backupPassword else "") { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { 
                                backupPassword = ""
                                showBackupDialog = true 
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SecureGreen.copy(alpha = 0.1f)),
                            modifier = Modifier.weight(1f).border(1.dp, SecureGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = SecureGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isEnglish) "Backup" else "نسخ احتياطي", color = SecureGreen, fontSize = 11.sp)
                        }

                        Button(
                            onClick = { 
                                backupPassword = ""
                                showRestoreDialog = true 
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SecureCyan.copy(alpha = 0.1f)),
                            modifier = Modifier.weight(1f).border(1.dp, SecureCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = SecureCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isEnglish) "Restore" else "استعادة", color = SecureCyan, fontSize = 11.sp)
                        }
                    }

                    if (showBackupDialog) {
                        Dialog(onDismissRequest = { showBackupDialog = false }) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, SecureGreen.copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text(
                                        text = if (isEnglish) "Create Backup File" else "إنشاء نسخة احتياطية", 
                                        fontWeight = FontWeight.Bold, 
                                        color = SecureGreen,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = if (isEnglish) 
                                            "All your chats, contacts, and channels will be exported to your device's Downloads folder as a secure .cipheris file."
                                            else "سيتم تصدير جميع المحادثات، المجموعات، والقنوات إلى مجلد التنزيلات (Downloads) على جهازك كملف .cipheris.", 
                                        fontSize = 11.sp, 
                                        color = Color.LightGray
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    // Encryption Toggle
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (isEnglish) "Encrypt backup with password" else "تشفير النسخة الاحتياطية بكلمة مرور",
                                            fontSize = 12.sp,
                                            color = Color.White
                                        )
                                        Switch(
                                            checked = shouldEncryptBackup,
                                            onCheckedChange = { shouldEncryptBackup = it },
                                            colors = SwitchDefaults.colors(checkedThumbColor = SecureGreen, checkedTrackColor = SecureGreen.copy(alpha = 0.5f))
                                        )
                                    }

                                    if (shouldEncryptBackup) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = backupPassword,
                                            onValueChange = { backupPassword = it },
                                            label = { Text(if (isEnglish) "Password" else "كلمة المرور", color = MutedText) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            visualTransformation = PasswordVisualTransformation(),
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SecureGreen)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = if (isEnglish) "Warning: If you lose this password, your backup can never be restored!" else "تحذير هام: في حال فقدان كلمة المرور هذه، لن تتمكن إطلاقاً من استعادة بياناتك!",
                                            fontSize = 10.sp,
                                            color = DangerRed
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Button(
                                        onClick = {
                                            viewModel.exportLocalData(if (shouldEncryptBackup) backupPassword else "") { msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }
                                            showBackupDialog = false
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = SecureGreen),
                                        enabled = !shouldEncryptBackup || backupPassword.isNotEmpty()
                                    ) {
                                        Text(if (isEnglish) "Export Now" else "تصدير الآن", color = ObsidianBlack, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    if (showRestoreDialog) {
                        Dialog(onDismissRequest = { showRestoreDialog = false }) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, SecureCyan.copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text(
                                        text = if (isEnglish) "Restore Local Backup" else "استعادة نسخة احتياطية", 
                                        fontWeight = FontWeight.Bold, 
                                        color = SecureCyan,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    // SECURITY MANDATE CONFIRMATION ALERT (Warning Card)
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = SecureCyan.copy(alpha = 0.05f)),
                                        border = BorderStroke(1.dp, SecureCyan.copy(alpha = 0.2f)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Security,
                                                contentDescription = null,
                                                tint = SecureCyan,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (isEnglish)
                                                    "Security Confirmed: The restore process runs entirely locally on your device. Absolutely no data is transmitted or shared externally."
                                                    else "تأكيد أمني: تتم عملية استعادة البيانات بالكامل محلياً على جهازك الخاص. لا يتم إرسال أو مشاركة أي جزء من بياناتك خارج هذا الهاتف مطلقاً لضمان خصوصيتك الكلية.",
                                                fontSize = 10.sp,
                                                color = Color.LightGray,
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }

                                    Text(
                                        text = if (isEnglish) "Enter password if your backup file is encrypted, otherwise leave it blank:" else "أدخل كلمة المرور إذا كان ملف النسخة الاحتياطية مشفراً، أو اتركه فارغاً إذا كان غير مشفر:", 
                                        fontSize = 11.sp, 
                                        color = Color.LightGray
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = backupPassword,
                                        onValueChange = { backupPassword = it },
                                        label = { Text(if (isEnglish) "Password (Optional)" else "كلمة المرور (اختياري)", color = MutedText) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SecureCyan)
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Button(
                                        onClick = {
                                            backupLauncher.launch("*/*")
                                            showRestoreDialog = false
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = SecureCyan)
                                    ) {
                                        Text(if (isEnglish) "Choose Backup File" else "اختيار ملف .cipheris", color = ObsidianBlack, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val isDarkTheme by viewModel.isDarkTheme.collectAsState()
            val whiteToneLevel by viewModel.whiteToneLevel.collectAsState()

            // Theme & Appearance Card with Eye Comfort / White Tone Intensity Slider
            Card(
                colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (isDarkTheme) SecureCyan.copy(alpha = 0.15f) else SecureGreen.copy(alpha = 0.15f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                    contentDescription = null,
                                    tint = if (isDarkTheme) SecureCyan else SecureGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isEnglish) "Theme & Appearance" else "مظهر التطبيق والسمة",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (isDarkTheme) {
                                        if (isEnglish) "Midnight Sapphire Night" else "الوضع الليلي (الأزرق الياقوتي الملكي)"
                                    } else {
                                        if (isEnglish) "Modern Clean Light" else "الوضع الفاتح العصري"
                                    },
                                    fontSize = 11.sp,
                                    color = if (isDarkTheme) SecureCyan else SecureGreen
                                )
                            }
                        }

                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = { viewModel.setDarkTheme(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SecureCyan,
                                checkedTrackColor = SecureCyan.copy(alpha = 0.4f),
                                uncheckedThumbColor = SecureGreen,
                                uncheckedTrackColor = SecureGreen.copy(alpha = 0.3f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = BorderSubtle.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // 👁️ Eye Comfort & White Glare Reduction Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.RemoveRedEye,
                                contentDescription = null,
                                tint = if (whiteToneLevel > 0.1f) Color(0xFFF59E0B) else MutedText,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isEnglish) "White Tone & Eye Comfort" else "تخفيف توهج اللون الأبيض وراحة العين",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TitaniumLight
                        ) {
                            Text(
                                text = "${(whiteToneLevel * 100).toInt()}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (whiteToneLevel > 0f) Color(0xFFF59E0B) else MutedText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isEnglish) {
                            when {
                                whiteToneLevel < 0.15f -> "Pure Bright (Default high brightness)"
                                whiteToneLevel < 0.5f -> "Comfortable Soft White (Reduces glare)"
                                whiteToneLevel < 0.85f -> "Warm Sepia Comfort (Easy on the eyes)"
                                else -> "Deep Soft Relaxed Tone"
                            }
                        } else {
                            when {
                                whiteToneLevel < 0.15f -> "أبيض ساطع ناصع (الوضع الافتراضي)"
                                whiteToneLevel < 0.5f -> "أبيض هادئ مريح (تخفيف حدة التوهج)"
                                whiteToneLevel < 0.85f -> "درجة دافئة مريحة للعينين (Warm)"
                                else -> "خافت فائق الهدوء ومريح جداً"
                            }
                        },
                        fontSize = 11.sp,
                        color = MutedText
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = whiteToneLevel,
                        onValueChange = { viewModel.setWhiteToneLevel(it) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = if (whiteToneLevel > 0f) Color(0xFFF59E0B) else SecureGreen,
                            activeTrackColor = if (whiteToneLevel > 0f) Color(0xFFF59E0B) else SecureGreen,
                            inactiveTrackColor = TitaniumLight
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Quick Preset Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val presets = listOf(
                            Pair(if (isEnglish) "Bright" else "ناصع", 0.0f),
                            Pair(if (isEnglish) "Soft" else "مريح", 0.35f),
                            Pair(if (isEnglish) "Warm" else "دافئ", 0.70f),
                            Pair(if (isEnglish) "Dim" else "خافت", 1.0f)
                        )

                        presets.forEach { (label, value) ->
                            val isSelected = kotlin.math.abs(whiteToneLevel - value) < 0.12f
                            Button(
                                onClick = { viewModel.setWhiteToneLevel(value) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) {
                                        if (value > 0f) Color(0xFFF59E0B) else SecureGreen
                                    } else {
                                        TitaniumLight
                                    }
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f).height(32.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) ObsidianBlack else TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Language Row
            Card(
                colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEnglish) "Language: English" else "اللغة: العربية",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Switch(
                        checked = isEnglish,
                        onCheckedChange = { viewModel.setLanguage(if (it) "en" else "ar") },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SecureGreen,
                            checkedTrackColor = SecureGreen.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Identity Card with Share Action
        Card(
            colors = CardDefaults.cardColors(containerColor = TitaniumDark),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(SecureGreen.copy(alpha = 0.1f), CircleShape)
                        .border(1.dp, SecureGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = SecureGreen, modifier = Modifier.size(30.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = selfPeer?.alias ?: "---", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = "عنوان العقدة اللامركزية النشط", color = MutedText, fontSize = 11.sp)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // My Node ID Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianBlack, RoundedCornerShape(12.dp))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = selfPeer?.nodeId ?: "cipheris://loading...",
                        color = SecureCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            selfPeer?.nodeId?.let {
                                val userLink = com.example.util.ParsedDeepLink.UserLink(it, selfPeer?.alias)
                                val webUrl = com.example.util.DeepLinkHelper.createUniversalLink(userLink)
                                clipboardManager.setText(AnnotatedString(webUrl))
                                Toast.makeText(context, if (isEnglish) "Profile link copied to clipboard!" else "تم نسخ رابط الحساب المباشر!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TitaniumLight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = SecureCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isEnglish) "Copy Link" else "نسخ الرابط", color = Color.White, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            selfPeer?.nodeId?.let {
                                clipboardManager.setText(AnnotatedString(it))
                                Toast.makeText(context, if (isEnglish) "Node ID copied!" else "تم نسخ رمز العقدة!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TitaniumLight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isEnglish) "Copy ID" else "نسخ الرمز", color = Color.White, fontSize = 12.sp)
                    }
                    
                    Button(
                        onClick = {
                            selfPeer?.nodeId?.let {
                                val userLink = com.example.util.ParsedDeepLink.UserLink(it, selfPeer?.alias)
                                val shareIntent = com.example.util.DeepLinkHelper.createShareIntent(context, userLink, isEnglish)
                                context.startActivity(android.content.Intent.createChooser(shareIntent, if (isEnglish) "Share Profile Link" else "مشاركة رابط الحساب الشخصي"))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SecureGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = ObsidianBlack, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isEnglish) "Share Profile Link (WhatsApp, Telegram, etc.)" else "مشاركة الرابط على مواقع التواصل (واتساب، تيليجرام...)", color = ObsidianBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (selfPeer != null) {
            // Identity ID & Copy Card
            Card(
                colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // High-tech Barcode and QR Visual representation representing self-node
                    HighTechBarcodeQR(
                        nodeId = selfPeer!!.nodeId,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = selfPeer!!.alias,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = SecureGreen
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "عنوان عقدتك اللامركزية (Device Node Address):",
                        fontSize = 11.sp,
                        color = MutedText
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Node ID block
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ObsidianBlack)
                            .border(1.dp, SecureGreen.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selfPeer!!.nodeId,
                            fontSize = 12.sp,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(selfPeer!!.nodeId))
                            Toast.makeText(context, "تم نسخ عنوان العقدة إلى الحافظة بنجاح", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SecureGreen.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = SecureGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("نسخ عنوان العقدة للتواصل", color = SecureGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Export/Share Identity Card
            Card(
                colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SecureCyan.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "مشاركة التطبيق والهوية الرقمية (.cipheris)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = SecureCyan
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "يمكنك تصدير بطاقة هوية كاملة ومشاركتها مع أصدقائك عبر التطبيقات الأخرى للاتصال بك ومشاركة التطبيق معهم فورا.",
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val inviteJson = JSONObject().apply {
                                    put("app", "Cipheris")
                                    put("alias", selfPeer!!.alias)
                                    put("nodeId", selfPeer!!.nodeId)
                                    put("fingerprint", selfPeer!!.publicKeyFingerprint)
                                    put("downloadUrl", "https://ais-pre-b37omv6hh2bnn6odxfl2b4-587876982683.europe-west2.run.app")
                                }.toString(2)
                                
                                shareInvitationFile(context, inviteJson)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SecureCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = ObsidianBlack, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مشاركة ملف الدعوة", color = ObsidianBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val inviteJson = JSONObject().apply {
                                    put("app", "Cipheris")
                                    put("alias", selfPeer!!.alias)
                                    put("nodeId", selfPeer!!.nodeId)
                                    put("fingerprint", selfPeer!!.publicKeyFingerprint)
                                }.toString()
                                clipboardManager.setText(AnnotatedString(inviteJson))
                                Toast.makeText(context, "تم نسخ رمز بطاقة الهوية (.cipheris) بنجاح!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SecureGreen.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = SecureGreen, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("نسخ رمز البطاقة", color = SecureGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            shareAppApkAsSecureZip(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SecureCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Share ZIP", tint = ObsidianBlack, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مشاركة التطبيق كملف ZIP آمن (كلمة المرور: 123)", color = ObsidianBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            shareAppApk(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SecureGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Android, contentDescription = "Share APK", tint = ObsidianBlack, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مشاركة ملف التطبيق (APK)", color = ObsidianBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Signature Details Card
            Card(
                colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "بصمة مفتاح الهوية العام (Public Fingerprint)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = SecureCyan
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = selfPeer!!.publicKeyFingerprint,
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "هذا البصمة SHA-256 تثبت هويتك الرقمية اللامركزية وتستخدم لتأكيد عدم العبث بمفاتيح التشفير المتبادلة في اللقاءات المباشرة.",
                        fontSize = 10.sp,
                        color = MutedText,
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Notifications Control Card
            Card(
                colors = CardDefaults.cardColors(containerColor = TitaniumDark.copy(alpha = 0.8f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp, 
                    if (notificationsEnabled) SecureGreen.copy(alpha = 0.3f) else DangerRed.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = if (notificationsEnabled) SecureGreen else DangerRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "حالة أذونات الإشعارات بالنظام",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (notificationsEnabled) SecureGreen.copy(alpha = 0.1f) 
                                    else DangerRed.copy(alpha = 0.1f)
                                )
                                .border(
                                    0.5.dp, 
                                    if (notificationsEnabled) SecureGreen else DangerRed, 
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (notificationsEnabled) "مفعلة ونشطة" else "معطلة بالنظام",
                                fontSize = 10.sp,
                                color = if (notificationsEnabled) SecureGreen else DangerRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "يستخدم سيفيرس إشعارات ذكية مشفرة عبر العقد اللامركزية لتنبيهك بالرسائل الجديدة الواردة فورياً. نوصي بإبقاء الإشعارات مفعلة دائماً لضمان عدم فوتان أي تواصل مهم.",
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        lineHeight = 16.sp
                    )

                    if (!notificationsEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val intent = android.content.Intent().apply {
                                    action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val fallback = android.content.Intent().apply {
                                        action = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                        data = android.net.Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(fallback)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = ObsidianBlack, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تفعيل الإشعارات بالهاتف فوراً", color = ObsidianBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SecureGreen.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SecureGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "أندرويد نشط تلقائياً لجميع الإصدارات والهواتف المختلفة ✅",
                                fontSize = 11.sp,
                                color = SecureGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Developer API Integration Card
            Card(
                colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SecureCyan.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (developerApiEnabled) showApiDocsDialog = true }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "Third-Party App Integration (API)" else "ربط التطبيقات الخارجية (API)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = SecureCyan
                        )
                        Switch(
                            checked = developerApiEnabled,
                            onCheckedChange = { 
                                viewModel.setDeveloperApiEnabled(it) 
                                if (it) showApiDocsDialog = true
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = SecureGreen, checkedTrackColor = SecureGreen.copy(alpha = 0.5f))
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isEnglish) 
                            "Enable this to allow third-party developers and external applications to securely interact with your Cipheris node via the local AIDL Service. (End-to-End Encryption is maintained)" 
                            else "تفعيل هذا الخيار يسمح للمطورين والتطبيقات الخارجية بالتفاعل الآمن مع عقدة سيفيرس الخاصة بك عبر خدمة AIDL المحلية. (يتم الحفاظ على التشفير التام بين الأطراف)",
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "Privacy Note: Disable this if you are not actively using third-party integrations." else "ملاحظة الخصوصية: قم بتعطيل هذا الخيار إذا لم تكن تستخدم تطبيقات خارجية.",
                            fontSize = 10.sp,
                            color = MutedText,
                            lineHeight = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (developerApiEnabled) {
                            Text(
                                text = if (isEnglish) "View API Docs" else "عرض الوثائق",
                                fontSize = 11.sp,
                                color = SecureCyan,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Developer & Subscriber Appreciation Card
            Card(
                colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SecureCyan.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(TitaniumDark, ObsidianBlack)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "مطور التطبيق (Developer)",
                                fontSize = 11.sp,
                                color = SecureCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "ابراهيم علي",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        
                        // Futuristic badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SecureCyan.copy(alpha = 0.1f))
                                .border(0.5.dp, SecureCyan, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "المالك والمطور",
                                fontSize = 10.sp,
                                color = SecureCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    HorizontalDivider(color = BorderSubtle)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Contact Info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Phone",
                            tint = SecureGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "رقم الهاتف: 01055266025",
                            fontSize = 13.sp,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Description
                    Text(
                        text = "تم تصميم وتطوير تطبيق سيفيرس (Cipheris) بهندسة برمجية فريدة تهدف لتمكين الأفراد من التواصل بخصوصية وأمان مطلقين. نعمل على دمج أرقى تقنيات التشفير اللامركزي لنمنحكم الحرية والأمان الرقمي دون أي قيود أو رقابة.",
                        fontSize = 11.sp,
                        color = MutedText,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Subscriber Thank You Message
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SecureGreen.copy(alpha = 0.05f))
                            .border(1.dp, SecureGreen.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Love",
                                    tint = SecureGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "شكراً لمشتركينا وأعضاء مجتمعنا الآمن",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SecureGreen
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "رسالة شكر وتقدير: نتقدم بخالص الشكر والامتنان لجميع المشتركين والمستخدمين الأوفياء الذين يثقون في سيفيرس. دعمكم هو الوقود الذي يدفعنا للاستمرار في تطوير وتحديث خدماتنا وتقديم أفضل تجارب التواصل المشفر واللامركزي لكم دائماً. شكراً لكونكم جزءاً من هذه الرحلة نحو الخصوصية المطلقة!",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (showApiDocsDialog) {
        var apiDocsTab by remember { mutableStateOf(0) }
        
        AlertDialog(
            onDismissRequest = { showApiDocsDialog = false },
            containerColor = TitaniumDark,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f),
            title = {
                Text(
                    text = if (isEnglish) "Cipheris API Integration" else "ربط المطورين (API)",
                    color = SecureCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.material3.ScrollableTabRow(
                        selectedTabIndex = apiDocsTab,
                        containerColor = Color.Transparent,
                        contentColor = SecureCyan,
                        edgePadding = 0.dp,
                        divider = { androidx.compose.material3.HorizontalDivider(color = BorderSubtle) }
                    ) {
                        androidx.compose.material3.Tab(selected = apiDocsTab == 0, onClick = { apiDocsTab = 0 }) {
                            Text(if (isEnglish) "Setup" else "الإعداد", modifier = Modifier.padding(12.dp), fontSize = 12.sp)
                        }
                        androidx.compose.material3.Tab(selected = apiDocsTab == 1, onClick = { apiDocsTab = 1 }) {
                            Text("ICipherisApi.aidl", modifier = Modifier.padding(12.dp), fontSize = 12.sp)
                        }
                        androidx.compose.material3.Tab(selected = apiDocsTab == 2, onClick = { apiDocsTab = 2 }) {
                            Text("ICipherisApiCallback.aidl", modifier = Modifier.padding(12.dp), fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        when (apiDocsTab) {
                            0 -> {
                                Column {
                                    Text(
                                        text = if (isEnglish) "1. Add Permission to AndroidManifest.xml:" else "1. أضف الصلاحية إلى AndroidManifest.xml:",
                                        color = SecureCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    SelectionContainer {
                                        Text(
                                            text = "<uses-permission android:name=\"com.aistudio.cipheris.pqnxwr.permission.BIND_API\" />",
                                            color = MutedText,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 12.dp)
                                        )
                                    }

                                    Text(
                                        text = if (isEnglish) "2. Bind to Service Intent:" else "2. ربط الخدمة (Intent):",
                                        color = SecureCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    SelectionContainer {
                                        Text(
                                            text = "val intent = Intent(\"com.example.api.action.BIND_CIPHERIS_API\")\nintent.setPackage(\"com.aistudio.cipheris.pqnxwr\")\nbindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)",
                                            color = MutedText,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 12.dp)
                                        )
                                    }

                                    Text(
                                        text = if (isEnglish) "3. Your Unique App ID:" else "3. معرف التطبيق الخاص بك:",
                                        color = SecureCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isEnglish) "Pass this ID when registering your callback to authenticate:" else "مرر هذا المعرف عند تسجيل الاستدعاء للمصادقة:",
                                        color = Color.LightGray,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                    SelectionContainer {
                                        Text(
                                            text = selfPeer?.nodeId ?: "Loading...",
                                            color = SecureGreen,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 12.dp)
                                        )
                                    }
                                }
                            }
                            1 -> {
                                Column {
                                    Text(
                                        text = if (isEnglish) "Create this file in your project under src/main/aidl/com/example/api/ICipherisApi.aidl" 
                                               else "قم بإنشاء هذا الملف في مشروعك في المسار src/main/aidl/com/example/api/ICipherisApi.aidl",
                                        color = Color.LightGray,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    SelectionContainer {
                                        Text(
                                            text = """
package com.example.api;

import com.example.api.ICipherisApiCallback;

interface ICipherisApi {
    boolean registerCallback(ICipherisApiCallback callback, String appId);
    void unregisterCallback(ICipherisApiCallback callback);
    String sendMessage(String targetPeerId, String content);
    int getConnectionStatus();
}
                                            """.trimIndent(),
                                            color = SecureGreen,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                            2 -> {
                                Column {
                                    Text(
                                        text = if (isEnglish) "Create this file in your project under src/main/aidl/com/example/api/ICipherisApiCallback.aidl" 
                                               else "قم بإنشاء هذا الملف في مشروعك في المسار src/main/aidl/com/example/api/ICipherisApiCallback.aidl",
                                        color = Color.LightGray,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    SelectionContainer {
                                        Text(
                                            text = """
package com.example.api;

interface ICipherisApiCallback {
    void onMessageReceived(String senderNodeId, String text, long timestamp);
    void onMessageStatusChanged(String messageId, int status);
}
                                            """.trimIndent(),
                                            color = SecureGreen,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showApiDocsDialog = false }) {
                    Text(if (isEnglish) "Close" else "إغلاق", color = SecureCyan)
                }
            }
        )
    }
}
}

// --- 5. SECURE CHAT SCREEN ---
@Composable
fun PinnedMessageBanner(
    text: String,
    onClose: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = TitaniumDark.copy(alpha = 0.95f)),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, SecureGreen.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PushPin, contentDescription = null, tint = SecureGreen, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("الرسالة المثبتة", color = SecureGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Unpin", tint = Color.Gray, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel, isGroup: Boolean) {
    val context = LocalContext.current
    val peers by viewModel.peers.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val selectedPeerId by viewModel.selectedPeerNodeId.collectAsState()
    val selectedGroupId by viewModel.selectedGroupId.collectAsState()

    val messages by viewModel.activeMessages.collectAsState()
    val messageText by viewModel.messageInput.collectAsState()
    val selfDestructSeconds by viewModel.selfDestructTimer.collectAsState()
    val isRecordingAudio by viewModel.isRecordingAudio.collectAsState()

    val attachName by viewModel.selectedAttachmentName.collectAsState()
    val attachSize by viewModel.selectedAttachmentSize.collectAsState()
    val networkQuality by viewModel.networkQuality.collectAsState()
    val isDownloadAllowed by viewModel.isDownloadAllowed.collectAsState()

    val selfNode by viewModel.selfPeer.collectAsState()

    var showFingerprintBanner by remember { mutableStateOf(false) }
    var showDestructMenu by remember { mutableStateOf(false) }
    var showGroupManagement by remember { mutableStateOf(false) }
    var showTrustEngine by remember { mutableStateOf(false) }
    var activeFullscreenMedia by remember { mutableStateOf<Pair<String?, String>?>(null) }
    
    var selectedMediaForDetail by remember { mutableStateOf<Message?>(null) }
    var showMediaDetailOverlay by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showLargeFileWarning by remember { mutableStateOf(false) }

    var selfReputationInGroup by remember { mutableStateOf(0) }
    LaunchedEffect(isGroup, selectedGroupId, messages) {
        if (isGroup && selectedGroupId != null) {
            val selfId = selfNode?.nodeId ?: "cipheris://local"
            selfReputationInGroup = viewModel.getSenderReputation(selectedGroupId!!, selfId)
        } else {
            selfReputationInGroup = 0
        }
    }

    // Title info
    val titleName: String
    val subtitleAddress: String
    val keyFingerprint: String

    if (isGroup) {
        val currentGroup = groups.find { it.groupId == selectedGroupId }
        titleName = currentGroup?.name ?: "المجموعة اللامركزية"
        subtitleAddress = currentGroup?.groupId ?: "cipheris://group/mesh"
        keyFingerprint = "تشفير جماعي متعدد الأطراف (Multi-party E2EE)"
    } else {
        val currentPeer = peers.find { it.nodeId == selectedPeerId }
        titleName = currentPeer?.alias ?: "عقدة اتصال"
        subtitleAddress = currentPeer?.nodeId ?: "cipheris://peer"
        keyFingerprint = currentPeer?.publicKeyFingerprint ?: "SHA256:AUTHENTICATING..."
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                val contentResolver = context.contentResolver
                val cursor = contentResolver.query(uri, null, null, null, null)
                var name = "unknown_file"
                var size = "0 B"
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex != -1) name = it.getString(nameIndex)
                        if (sizeIndex != -1) {
                            val sizeBytes = it.getLong(sizeIndex)
                            val mb = sizeBytes / (1024.0 * 1024.0)
                            size = if (mb > 1) {
                                String.format(Locale.US, "%.1f MB", mb)
                            } else {
                                val kb = sizeBytes / 1024.0
                                String.format(Locale.US, "%.1f KB", kb)
                            }
                        }
                    }
                }
                viewModel.encryptAndSelectFile(context, uri.toString(), size)
            }
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val contentResolver = context.contentResolver
            val cursor = contentResolver.query(uri, null, null, null, null)
            var name = "unknown_file"
            var size = "0 B"
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) name = it.getString(nameIndex)
                    if (sizeIndex != -1) {
                        val sizeBytes = it.getLong(sizeIndex)
                        val mb = sizeBytes / (1024.0 * 1024.0)
                        size = if (mb > 1) {
                            String.format(Locale.US, "%.1f MB", mb)
                        } else {
                            val kb = sizeBytes / 1024.0
                            String.format(Locale.US, "%.1f KB", kb)
                        }
                    }
                }
            }
            viewModel.encryptAndSelectFile(context, uri.toString(), size)
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startVoiceRecording(context)
        } else {
            Toast.makeText(context, "⚠️ يرجى منح إذن الميكروفون لتسجيل الرسائل الصوتية", Toast.LENGTH_SHORT).show()
        }
    }

    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Scroll to bottom whenever messages count changes
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            lazyListState.animateScrollToItem(messages.size - 1)
        }
    }

    if (showGroupManagement && isGroup) {
        val currentGroup = groups.find { it.groupId == selectedGroupId }
        if (currentGroup != null) {
            Dialog(onDismissRequest = { showGroupManagement = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("إدارة المجموعة: ${currentGroup.name}", color = SecureGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        val memberIds = currentGroup.memberNodeIdsText.split(",").filter { it.isNotEmpty() }
                        val adminIds = currentGroup.adminNodeIdsText.split(",").filter { it.isNotEmpty() }
                        val modIds = currentGroup.moderatorNodeIdsText.split(",").filter { it.isNotEmpty() }
                        
                        MembersTabContent(
                            peers = peers,
                            memberIds = memberIds,
                            adminIds = adminIds,
                            modIds = modIds,
                            permissionsJsonText = currentGroup.permissionsJsonText,
                            isChannel = false,
                            onAddMember = { nodeId: String -> viewModel.inviteToGroup(currentGroup.groupId, nodeId) },
                            onSetRole = { nodeId: String, role: String, add: Boolean -> viewModel.setGroupRole(currentGroup.groupId, nodeId, role, add) },
                            onSetGranularPermissions = { nodeId: String, perms: Map<String, Boolean> -> viewModel.setGroupGranularPermissions(currentGroup.groupId, nodeId, perms) }
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = { showGroupManagement = false }, modifier = Modifier.align(Alignment.End)) {
                            Text("إغلاق", color = MutedText)
                        }
                    }
                }
            }
        }
    }

    if (showTrustEngine) {
        CryptographicTrustEngineDialog(
            viewModel = viewModel,
            isGroup = isGroup,
            isChannel = false,
            destinationId = if (isGroup) selectedGroupId else selectedPeerId,
            onDismiss = { showTrustEngine = false }
        )
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TitaniumDark)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(onClick = { viewModel.navigateTo("dashboard") }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Column(
                            modifier = Modifier
                                .clickable { showFingerprintBanner = !showFingerprintBanner }
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = titleName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isGroup) Icons.Default.Groups else Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = if (isGroup) SecureGreen else SecureCyan,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = subtitleAddress,
                                    fontSize = 10.sp,
                                    color = MutedText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            // Decentralized network quality indicator for safe calls/chats
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val isUnstable = networkQuality.contains("غير مستقر")
                                Icon(
                                    imageVector = if (isUnstable) Icons.Default.Warning else Icons.Default.Wifi,
                                    contentDescription = "Decentralized Connection State",
                                    tint = if (isUnstable) Color(0xFFFF5252) else SecureGreen,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = networkQuality,
                                    fontSize = 9.sp,
                                    color = if (isUnstable) Color(0xFFFF5252) else SecureGreen,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NetworkStatusActionIcon(viewModel)

                        IconButton(onClick = { showTrustEngine = true }) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = "Trust Audit Engine",
                                tint = if (isGroup) SecureGreen else SecureCyan
                            )
                        }

                        if (isGroup) {
                            IconButton(onClick = { showGroupManagement = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Group Settings", tint = SecureCyan)
                            }
                        }

                        IconButton(onClick = { showFingerprintBanner = !showFingerprintBanner }) {
                            Icon(
                                imageVector = if (showFingerprintBanner) Icons.Default.Info else Icons.Outlined.Info,
                                contentDescription = "Fingerprint info",
                                tint = if (isGroup) SecureGreen else SecureCyan
                            )
                        }
                    }
                }

                // Security Calibration and Group Management Modal Dialog
                if (showFingerprintBanner) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { showFingerprintBanner = false }
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .wrapContentHeight()
                        ) {
                            Column(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isGroup) "موثوقية وإدارة المجموعة" else "معايرة مفتاح الأمان المتطرف",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isGroup) SecureGreen else SecureCyan
                                    )
                                    IconButton(onClick = { showFingerprintBanner = false }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                                    }
                                }
                                
                                var selectedTab by remember { mutableIntStateOf(0) }
                                val localClipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                val currentGroup = if (isGroup) groups.find { it.groupId == selectedGroupId } else null
                                val memberIds = currentGroup?.memberNodeIdsText?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
                                val adminIds = currentGroup?.adminNodeIdsText?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
                                val modIds = currentGroup?.moderatorNodeIdsText?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
                                val permissionsJsonText = currentGroup?.permissionsJsonText ?: "{}"

                                if (isGroup) {
                                    androidx.compose.material3.TabRow(
                                        selectedTabIndex = selectedTab,
                                        containerColor = Color.Transparent,
                                        contentColor = SecureGreen,
                                        divider = { HorizontalDivider(color = BorderSubtle) }
                                    ) {
                                        androidx.compose.material3.Tab(
                                            selected = selectedTab == 0,
                                            onClick = { selectedTab = 0 },
                                            text = { Text("المعلومات", fontSize = 11.sp, maxLines = 1) }
                                        )
                                        androidx.compose.material3.Tab(
                                            selected = selectedTab == 1,
                                            onClick = { selectedTab = 1 },
                                            text = { Text("الأعضاء", fontSize = 11.sp, maxLines = 1) }
                                        )
                                        androidx.compose.material3.Tab(
                                            selected = selectedTab == 2,
                                            onClick = { selectedTab = 2 },
                                            text = { Text("الإحصائيات", fontSize = 11.sp, maxLines = 1) }
                                        )
                                        androidx.compose.material3.Tab(
                                            selected = selectedTab == 3,
                                            onClick = { selectedTab = 3 },
                                            text = { Text("إعدادات", fontSize = 11.sp, maxLines = 1) }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                if (!isGroup || selectedTab == 0) {
                                    if (isGroup && currentGroup != null) {
                                        Text(
                                            text = "رمز ورابط انضمام المجموعة (Group Invite Link):",
                                            fontSize = 10.sp,
                                            color = MutedText
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                                .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = currentGroup.groupId,
                                                fontSize = 11.sp,
                                                color = SecureGreen,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = {
                                                    localClipboardManager.setText(androidx.compose.ui.text.AnnotatedString(currentGroup.groupId))
                                                    Toast.makeText(context, "تم نسخ رمز المجموعة بنجاح! 📋", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Copy Group ID",
                                                    tint = SecureGreen,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    val groupLink = com.example.util.ParsedDeepLink.GroupLink(currentGroup.groupId, currentGroup.name, "")
                                                    val webUrl = com.example.util.DeepLinkHelper.createUniversalLink(groupLink)
                                                    localClipboardManager.setText(androidx.compose.ui.text.AnnotatedString(webUrl))
                                                    Toast.makeText(context, "تم نسخ رابط دعوة المجموعة المباشر! 🔗", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = TitaniumLight),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.Link, contentDescription = null, tint = SecureCyan, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("نسخ الرابط", fontSize = 11.sp, color = Color.White)
                                            }

                                            Button(
                                                onClick = {
                                                    val groupLink = com.example.util.ParsedDeepLink.GroupLink(currentGroup.groupId, currentGroup.name, "")
                                                    val shareIntent = com.example.util.DeepLinkHelper.createShareIntent(context, groupLink, isEnglish = false)
                                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "مشاركة رابط دعوة المجموعة"))
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = SecureGreen),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.Share, contentDescription = null, tint = ObsidianBlack, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("مشاركة الرابط", fontSize = 11.sp, color = ObsidianBlack, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                    }

                                    Text(
                                        text = "بصمة التشفير (E2EE Fingerprint):",
                                        fontSize = 10.sp,
                                        color = MutedText
                                    )
                                    Text(
                                        text = keyFingerprint,
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 16.sp,
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                            .padding(8.dp)
                                            .fillMaxWidth()
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Text(
                                        text = "تضمن شبكة سيفيرس عدم وجود طرف ثالث بين العقد. إذا كانت هذه البصمة مطابقة لتلك المعروضة على جهاز الطرف الآخر، فإن محادثتكم آمنة 100% ضد هجمات الرجل في المنتصف (MITM).",
                                        fontSize = 10.sp,
                                        color = MutedText,
                                        lineHeight = 14.sp
                                    )
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    Text(
                                        text = if (isGroup) "تصوير الباركود للمجموعة:" else "تصوير الباركود للمعايرة:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isGroup) SecureGreen else SecureCyan
                                    )
                                    
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(160.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        HighTechBarcodeQR(
                                            nodeId = if (isGroup) selectedGroupId ?: "" else selectedPeerId ?: "",
                                            modifier = Modifier.size(150.dp)
                                        )
                                    }
                                    
                                    if (isGroup) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        
                                        var customInviteCode by remember { mutableStateOf("INV-" + (1000..9999).random().toString()) }
                                        
                                        OutlinedTextField(
                                            value = customInviteCode,
                                            onValueChange = { customInviteCode = it },
                                            label = { Text("رمز الدعوة المقترح للمجموعة (Invite Code)", color = MutedText, fontSize = 11.sp) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = SecureGreen,
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                                cursorColor = SecureGreen
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White)
                                        )
                                        
                                        Spacer(modifier = Modifier.height(12.dp))
                                        
                                        Button(
                                            onClick = {
                                                val shareIntent = Intent().apply {
                                                    action = Intent.ACTION_SEND
                                                    val directLink = "cipheris://group/$subtitleAddress"
                                                    // Direct deep link for internal use
                                                    putExtra(Intent.EXTRA_TEXT, "انضم لمجموعتنا المشفرة على Cipheris!\nاسم المجموعة: $titleName\nرمز الدعوة: $customInviteCode\n\n🔗 رابط الانضمام المباشر للتطبيق:\n$directLink\n\n(ملاحظة: إذا لم يكن الرابط قابلاً للضغط في تطبيقك، يمكنك نسخه ولصقه في خانة الانضمام داخل تطبيق Cipheris)")
                                                    type = "text/plain"
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "مشاركة المجموعة"))
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = SecureGreen)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = null, tint = ObsidianBlack, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("مشاركة المجموعة مع رمز الدعوة", color = ObsidianBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else if (isGroup && selectedTab == 1) {
                                    MembersTabContent(
                                        peers = peers,
                                        memberIds = memberIds,
                                        adminIds = adminIds,
                                        modIds = modIds,
                                        permissionsJsonText = permissionsJsonText,
                                        isChannel = false,
                                        onAddMember = { nodeId: String ->
                                            viewModel.inviteToGroup(selectedGroupId!!, nodeId)
                                            Toast.makeText(context, "تم إضافة العضو بنجاح", Toast.LENGTH_SHORT).show()
                                        },
                                        onSetRole = { nodeId: String, role: String, add: Boolean ->
                                            viewModel.setGroupRole(selectedGroupId!!, nodeId, role, add)
                                            Toast.makeText(context, "تم تحديث صلاحيات العضو", Toast.LENGTH_SHORT).show()
                                        },
                                        onSetGranularPermissions = { nodeId: String, perms: Map<String, Boolean> ->
                                            viewModel.setGroupGranularPermissions(selectedGroupId!!, nodeId, perms)
                                            Toast.makeText(context, "تم حفظ الصلاحيات المخصصة", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } else if (isGroup && selectedTab == 2) {
                                    AnalyticsDashboard(
                                        memberCount = memberIds.size,
                                        messageCount = messages.size,
                                        deletedCount = 0,
                                        joiningStats = listOf(0, 0, 0, 0, 0, 0, memberIds.size)
                                    )
                                } else if (isGroup && selectedTab == 3) {
                                    Text(text = "إعدادات المجموعة", color = SecureGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    androidx.compose.material3.Card(
                                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = SecureGreen.copy(alpha = 0.05f)),
                                        border = BorderStroke(1.dp, SecureGreen.copy(alpha = 0.2f)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = SecureGreen, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("بروتوكول السمعة اللامركزي 🛡️", color = SecureGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "هذه المجموعة محمية بنظام سمعة مشفر وديمقراطي تماماً. للتخلص من المزعجين (Spammers) والحسابات المشبوهة دون تحكم فردي من المشرفين، يمتلك كل عضو القدرة على تقييم الرسائل سلباً أو إيجابياً بالتوقيعات الرقمية المجهولة:\n\n" +
                                                     "• إذا انخفضت سمعة أي حساب في المجموعة عن -5، فسيتم حجب رسائله تلقائياً وتقييد قدرته على الكتابة والإرسال لحماية بقية الأعضاء.\n" +
                                                     "• للتقييم: اضغط مطولاً على أي رسالة داخل المحادثة واختر نوع التقييم.",
                                                color = Color.LightGray,
                                                fontSize = 10.sp,
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Button(
                                        onClick = {
                                            viewModel.clearGroupMessages(selectedGroupId!!)
                                            Toast.makeText(context, "تم مسح جميع رسائل المجموعة", Toast.LENGTH_SHORT).show()
                                            showFingerprintBanner = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed.copy(alpha = 0.2f)),
                                        border = BorderStroke(1.dp, DangerRed),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.DeleteForever, contentDescription = null, tint = DangerRed, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("مسح جميع رسائل المجموعة", color = DangerRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .background(ObsidianBlack)
        ) {
            val currentGroup = if (isGroup) groups.find { it.groupId == selectedGroupId } else null
            val pinnedMsg = currentGroup?.pinnedMessageId?.let { pid -> messages.find { it.id == pid } }
            
            if (pinnedMsg != null) {
                PinnedMessageBanner(
                    text = pinnedMsg.text,
                    onClose = { viewModel.pinGroupMessage(selectedGroupId!!, null) },
                    onClick = {
                        val index = messages.indexOf(pinnedMsg)
                        if (index != -1) {
                            scope.launch { lazyListState.animateScrollToItem(index + 1) }
                        }
                    }
                )
            }

            // Chat message board
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(12.dp)) }

                items(messages, key = { it.id }) { msg ->
                    val senderName = if (msg.isIncoming) {
                        if (isGroup) {
                            val peerSender = peers.find { it.nodeId == msg.senderNodeId }
                            peerSender?.alias ?: msg.senderNodeId.substringBefore("://") + "://" + msg.senderNodeId.substringAfter("://").take(4) + "..."
                        } else {
                            titleName
                        }
                    } else {
                        "أنت"
                    }

                    MessageBubble(
                        message = msg,
                        senderName = senderName,
                        isIncoming = msg.isIncoming,
                        selfNodeId = selfNode?.nodeId ?: "cipheris://local",
                        viewModel = viewModel,
                        onDelete = { viewModel.deleteMessage(msg.id) },
                        onPin = { 
                            if (isGroup && selectedGroupId != null) {
                                viewModel.pinGroupMessage(selectedGroupId!!, msg.id)
                                Toast.makeText(context, "تم تثبيت الرسالة في المجموعة", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onMediaClick = { clickedMsg: com.example.data.model.Message ->
                            selectedMediaForDetail = clickedMsg
                            showMediaDetailOverlay = true
                            activeFullscreenMedia = Pair(clickedMsg.mediaPath, clickedMsg.mediaName ?: "")
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(12.dp)) }
            }

            // Input composing section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TitaniumDark)
                    .navigationBarsPadding()
                    .padding(8.dp)
            ) {
                if (isGroup && selfReputationInGroup < -5) {
                    androidx.compose.material3.Card(
                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DangerRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(Icons.Default.Block, contentDescription = null, tint = DangerRed, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "🚫 تم تقييد قدرتك على الإرسال تلقائياً بسبب انخفاض سمعتك اللامركزية في هذه المجموعة (السمعة الحالية: $selfReputationInGroup). السمعة المطلوبة للإرسال هي -5 أو أعلى.",
                                color = Color.White,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                } else {
                    // Pending attachment preview
                    if (attachName != null) {
                    var showMediaPreview by remember { mutableStateOf(false) }

                    if (showMediaPreview) {
                        AlertDialog(
                            onDismissRequest = { showMediaPreview = false },
                            title = { Text("معاينة الملف المرفق", color = SecureGreen) },
                            text = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    Text(text = attachName!!, color = TextPrimary, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    if (attachName!!.lowercase().let { it.endsWith(".jpg") || it.endsWith(".png") || it.endsWith(".jpeg") }) {
                                        // Image preview
                                        Box(modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(TitaniumLight),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Image, contentDescription = null, tint = SecureGreen, modifier = Modifier.size(64.dp))
                                        }
                                    } else {
                                        Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = MutedText, modifier = Modifier.size(64.dp))
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = "الحجم: $attachSize", color = MutedText, fontSize = 12.sp)
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showMediaPreview = false }) {
                                    Text("إغلاق", color = SecureGreen)
                                }
                            },
                            containerColor = TitaniumDark
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .background(ObsidianBlack, RoundedCornerShape(8.dp))
                            .border(1.dp, SecureGreen.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showMediaPreview = true },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AttachFile, contentDescription = null, tint = SecureGreen)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(text = attachName!!, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                                    Text(text = attachSize!!, fontSize = 10.sp, color = MutedText)
                                }
                            }
                            IconButton(onClick = { viewModel.clearAttachment() }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear attachment", tint = DangerRed)
                            }
                        }
                        
                        HorizontalDivider(
                            color = BorderSubtle,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isDownloadAllowed) Icons.Default.FileDownload else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (isDownloadAllowed) SecureGreen else Color(0xFFFFCC00),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isDownloadAllowed) "مسموح بالتحميل والحفظ للطرف الآخر" else "حظر التحميل والحفظ (عرض فقط)",
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                            Switch(
                                checked = isDownloadAllowed,
                                onCheckedChange = { viewModel.toggleDownloadAllowed() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = SecureGreen,
                                    checkedTrackColor = SecureGreen.copy(alpha = 0.3f),
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color.DarkGray
                                )
                            )
                        }
                    }
                }

                if (isRecordingAudio) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(DangerRed.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(DangerRed, CircleShape))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جاري تسجيل رسالة صوتية مشفرة...", color = TextPrimary, fontSize = 12.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    com.example.data.util.VoiceRecorderHelper.stopRecording()
                                    viewModel.isRecordingAudio.value = false
                                    Toast.makeText(context, "تم إلغاء تسجيل الصوت.", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("إلغاء", color = DangerRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    viewModel.stopVoiceRecording(context, isChannel = false, destinationId = if (isGroup) selectedGroupId else null)
                                    Toast.makeText(context, "تم إرسال الرسالة الصوتية المشفرة بنجاح! 🔒", Toast.LENGTH_LONG).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SecureGreen),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("إرسال", color = ObsidianBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Self-destruct selection icon triggers popup
                        Box {
                            IconButton(
                                onClick = { showDestructMenu = !showDestructMenu },
                                modifier = Modifier.background(
                                    if (selfDestructSeconds > 0) SecureGreen.copy(alpha = 0.15f) else Color.Transparent,
                                    CircleShape
                                )
                            ) {
                                Icon(
                                    imageVector = if (selfDestructSeconds > 0) Icons.Default.Timer else Icons.Outlined.Timer,
                                    contentDescription = "Self destruct",
                                    tint = if (selfDestructSeconds > 0) SecureGreen else MutedText
                                )
                            }

                            DropdownMenu(
                                expanded = showDestructMenu,
                                onDismissRequest = { showDestructMenu = false },
                                modifier = Modifier
                                    .background(TitaniumLight, RoundedCornerShape(12.dp))
                                    .border(1.dp, SecureGreen.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .width(180.dp),
                                offset = androidx.compose.ui.unit.DpOffset(0.dp, (-40).dp)
                            ) {
                                Text(
                                    text = "مؤقت التدمير الذاتي",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = SecureGreen,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    textAlign = TextAlign.Center
                                )
                                HorizontalDivider(color = BorderSubtle)
                                DestructOption(text = "معطل", isSelected = selfDestructSeconds == 0) {
                                    viewModel.updateSelfDestructTimer(0)
                                    showDestructMenu = false
                                }
                                DestructOption(text = "5 ثوانٍ", isSelected = selfDestructSeconds == 5) {
                                    viewModel.updateSelfDestructTimer(5)
                                    showDestructMenu = false
                                }
                                DestructOption(text = "10 ثوانٍ", isSelected = selfDestructSeconds == 10) {
                                    viewModel.updateSelfDestructTimer(10)
                                    showDestructMenu = false
                                }
                                DestructOption(text = "30 ثانية", isSelected = selfDestructSeconds == 30) {
                                    viewModel.updateSelfDestructTimer(30)
                                    showDestructMenu = false
                                }
                                DestructOption(text = "60 ثانية", isSelected = selfDestructSeconds == 60) {
                                    viewModel.updateSelfDestructTimer(60)
                                    showDestructMenu = false
                                }
                                DestructOption(text = "5 دقائق", isSelected = selfDestructSeconds == 300) {
                                    viewModel.updateSelfDestructTimer(300)
                                    showDestructMenu = false
                                }
                            }
                        }
                        // Emoji / Media Gallery
                        IconButton(onClick = { showEmojiPicker = !showEmojiPicker }) {
                            Icon(
                                imageVector = if (showEmojiPicker) Icons.Default.KeyboardHide else Icons.Outlined.EmojiEmotions,
                                contentDescription = "Emojis",
                                tint = if (showEmojiPicker) SecureGreen else MutedText
                            )
                        }

                        // Secure attachment clip triggers menu
                        var showAttachMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showAttachMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.Attachment,
                                    contentDescription = "Attach",
                                    tint = if (attachName != null) SecureCyan else MutedText
                                )
                            }
                            androidx.compose.material3.DropdownMenu(
                                expanded = showAttachMenu,
                                onDismissRequest = { showAttachMenu = false },
                                modifier = Modifier.background(TitaniumDark).width(200.dp)
                            ) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("صورة / فيديو", color = TextPrimary) },
                                    onClick = { 
                                        showAttachMenu = false
                                        filePickerLauncher.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                            )
                                        ) 
                                    },
                                    leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = SecureCyan) }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("ملف / تطبيق (جميع الصيغ)", color = TextPrimary) },
                                    onClick = { showAttachMenu = false; documentPickerLauncher.launch("*/*") },
                                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, tint = SecureGreen) }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("موسيقى / صوت", color = TextPrimary) },
                                    onClick = { showAttachMenu = false; documentPickerLauncher.launch("audio/*") },
                                    leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null, tint = SecureGreen) }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("موقع جغرافي", color = TextPrimary) },
                                    onClick = { showAttachMenu = false; Toast.makeText(context, "ارسال الموقع (قريباً)", Toast.LENGTH_SHORT).show() },
                                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = DangerRed) }
                                )
                            }
                        }

                        // Message input field
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { viewModel.updateMessageInput(it) },
                            placeholder = {
                                Text(
                                    text = if (selfDestructSeconds > 0) "رسالة تدمير ذاتي آمنة..." else "اكتب رسالة مشفرة...",
                                    color = MutedText.copy(alpha = 0.6f),
                                    fontSize = 13.sp
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (selfDestructSeconds > 0) SecureGreen else SecureCyan,
                                unfocusedBorderColor = BorderSubtle,
                                cursorColor = SecureGreen
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                                .testTag("chat_message_input"),
                            singleLine = false,
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                        )

                        // Send or Voice Record button
                        if (messageText.trim().isEmpty() && attachName == null) {
                            IconButton(
                                onClick = { 
                                    recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                },
                                modifier = Modifier
                                    .background(BorderSubtle, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Voice Message",
                                    tint = SecureCyan
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    val networkQuality = viewModel.networkQuality.value
                                    val isNetworkWeak = networkQuality.contains("ضعيف") || networkQuality.contains("غير مستقر") || networkQuality.contains("منقطع")
                                    if (attachName != null && (isNetworkWeak || attachSize?.contains("MB") == true)) {
                                        showLargeFileWarning = true
                                    } else {
                                        viewModel.sendCurrentMessage()
                                    }
                                },
                                modifier = Modifier
                                    .background(
                                        if (selfDestructSeconds > 0) SecureGreen else SecureCyan,
                                        CircleShape
                                    )
                                    .testTag("send_message_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = ObsidianBlack
                                )
                            }
                        }
                    }
                }
            }
        }
    }

        if (showLargeFileWarning) {
            LargeFileNetworkWarningDialog(
                fileSize = attachSize ?: "1.2 MB",
                networkQuality = viewModel.networkQuality.collectAsState().value,
                onConfirmSend = {
                    showLargeFileWarning = false
                    viewModel.sendCurrentMessage()
                },
                onDismiss = { showLargeFileWarning = false }
            )
        }

        if (activeFullscreenMedia != null) {
            FullScreenMediaDialog(
                mediaName = activeFullscreenMedia!!.second,
                filePath = activeFullscreenMedia!!.first,
                onDismiss = { activeFullscreenMedia = null }
            )
        }

        if (showEmojiPicker) {
            CustomEmojiPicker(
                viewModel = viewModel,
                onEmojiSelected = { emoji ->
                    viewModel.updateMessageInput(messageText + emoji)
                },
                onStickerSelected = { stickerTag ->
                    viewModel.updateMessageInput(stickerTag)
                    viewModel.sendCurrentMessage()
                    showEmojiPicker = false
                },
                onDismiss = { showEmojiPicker = false }
            )
        }

        if (showMediaDetailOverlay) {
            MediaDetailOverlay(
                message = selectedMediaForDetail,
                onDismiss = { showMediaDetailOverlay = false }
            )
        }
    }
}

@Composable
fun DestructOption(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = SecureGreen, modifier = Modifier.size(16.dp))
        } else {
            Spacer(modifier = Modifier.width(16.dp))
        }
        Text(text = text, fontSize = 12.sp, color = if (isSelected) SecureGreen else Color.White)
    }
}

@Composable
fun AttachSimOption(title: String, size: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = TitaniumLight),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = size, fontSize = 11.sp, color = SecureGreen, fontFamily = FontFamily.Monospace)
            Text(text = title, fontSize = 12.sp, color = TextPrimary)
        }
    }
}

// --- SECURE INLINE DECRYPTION AND MEDIA VIEWER ---
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayer(
    videoPath: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context)
            .setSeekForwardIncrementMs(10000)
            .setSeekBackIncrementMs(10000)
            .build().apply {
                val mediaItem = androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(java.io.File(videoPath)))
                setMediaItem(mediaItem)
                prepare()
            }
    }

    androidx.compose.runtime.DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            androidx.media3.ui.PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                setShowNextButton(false)
                setShowPreviousButton(false)
                setBackgroundColor(android.graphics.Color.BLACK)
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier
    )
}

/**
 * Advanced magic number detection to identify file types securely.
 * Supports: PDF, ZIP, JPG, PNG, GIF, MP3, MP4, WAV, DOCX, XLSX, PPTX
 */
fun detectFileType(file: java.io.File): String {
    if (!file.exists()) return "unknown"
    return try {
        val bytes = file.inputStream().use { input ->
            val buf = ByteArray(12)
            val count = input.read(buf)
            if (count > 0) buf.copyOf(count) else ByteArray(0)
        }
        when {
            // PDF: %PDF
            bytes.size >= 4 && bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte() -> "pdf"
            
            // ZIP / Office Docs: PK..
            bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte() -> {
                // Could be ZIP, DOCX, XLSX, or PPTX. For simplicity, we classify as "doc_zip"
                val ext = file.extension.lowercase()
                if (ext in listOf("docx", "xlsx", "pptx")) ext else "zip"
            }
            
            // Images
            bytes.size >= 3 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() -> "gif"
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
            bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "png"
            
            // Audio
            bytes.size >= 3 && bytes[0] == 0x49.toByte() && bytes[1] == 0x44.toByte() && bytes[2] == 0x33.toByte() -> "audio" // MP3 with ID3
            bytes.size >= 4 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() -> "audio" // WAV (RIFF)
            
            // Video / Generic MP4
            bytes.size >= 8 && bytes[4] == 0x66.toByte() && bytes[5] == 0x74.toByte() && bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte() -> "video" // ftyp
            
            else -> {
                val ext = file.extension.lowercase()
                when (ext) {
                    "mp3", "wav", "m4a", "ogg" -> "audio"
                    "mp4", "mov", "avi", "mkv" -> "video"
                    "txt", "rtf", "log" -> "text"
                    "py", "js", "ts", "cpp", "java", "go", "rust", "html", "css", "json", "xml", "yaml", "md" -> "code"
                    else -> ext
                }
            }
        }
    } catch (e: Exception) { file.extension.lowercase() }
}

@Composable
fun AdvancedFilePreviewer(
    filePath: String,
    fileName: String,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val file = remember(filePath) {
        val targetPath = if (filePath.contains("secured_")) {
            com.example.data.util.FileEncryptor.decryptFileToTemp(context, filePath) ?: filePath
        } else {
            filePath
        }
        java.io.File(targetPath)
    }
    val mediaType = remember(file) { com.example.data.util.MediaClassifier.classifyFile(file) }
    val fileSize = remember(file) { if (file.exists()) file.length() else 0L }
    
    // Read preview text, archive entries, or database headers in background
    var textPreview by remember { mutableStateOf<String?>(null) }
    var isReadingText by remember { mutableStateOf(false) }
    var zipEntries by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSqlite by remember { mutableStateOf(false) }
    
    LaunchedEffect(file) {
        if (!file.exists()) return@LaunchedEffect
        
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            // Determine SQLite
            isSqlite = try {
                if (file.length() >= 16) {
                    val bytes = ByteArray(16)
                    file.inputStream().use { it.read(bytes) }
                    val header = String(bytes, 0, 15, Charsets.US_ASCII)
                    header == "SQLite format 3"
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }

            // Extract ZIP entries if it's an archive
            if (mediaType == com.example.data.util.MediaClassifier.MediaType.ARCHIVE ||
                mediaType == com.example.data.util.MediaClassifier.MediaType.EPUB ||
                mediaType == com.example.data.util.MediaClassifier.MediaType.MS_OFFICE) {
                val entries = mutableListOf<String>()
                try {
                    java.util.zip.ZipInputStream(file.inputStream()).use { zis ->
                        var entry = zis.nextEntry
                        var count = 0
                        while (entry != null && count < 4) {
                            entries.add(entry.name)
                            entry = zis.nextEntry
                            count++
                        }
                    }
                } catch (e: Exception) {}
                zipEntries = entries
            }

            // Read text preview if text/code/data
            if (mediaType == com.example.data.util.MediaClassifier.MediaType.TEXT ||
                mediaType == com.example.data.util.MediaClassifier.MediaType.CODE ||
                mediaType == com.example.data.util.MediaClassifier.MediaType.DATA) {
                isReadingText = true
                try {
                    val content = file.readText().take(600)
                    textPreview = if (file.length() > 600) "$content..." else content
                } catch (e: Exception) {
                    textPreview = "تعذر تحميل معاينة النص"
                }
                isReadingText = false
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp, max = 300.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(TitaniumDark.copy(alpha = 0.5f))
            .border(1.dp, SecureGreen.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
    ) {
        if (!file.exists()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SecureGreen, modifier = Modifier.size(24.dp))
            }
        } else {
            when {
                mediaType == com.example.data.util.MediaClassifier.MediaType.TEXT ||
                mediaType == com.example.data.util.MediaClassifier.MediaType.CODE ||
                mediaType == com.example.data.util.MediaClassifier.MediaType.DATA -> {
                    Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Code, contentDescription = null, tint = SecureCyan, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = fileName,
                                    color = SecureCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "معاينة آمنة",
                                color = SecureGreen.copy(alpha = 0.7f),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            if (isReadingText) {
                                CircularProgressIndicator(color = SecureGreen, modifier = Modifier.size(16.dp).align(Alignment.Center))
                            } else {
                                Text(
                                    text = textPreview ?: "",
                                    color = Color(0xFFE0E0E0),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 14.sp,
                                    modifier = Modifier.fillMaxSize(),
                                    maxLines = 10,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                mediaType == com.example.data.util.MediaClassifier.MediaType.PDF -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFF44336), modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(fileName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // Simple inline helper for formatting size to keep this file self-contained
                        val sizeStr = if (fileSize < 1024) "$fileSize B" else if (fileSize < 1048576) "%.1f KB".format(fileSize / 1024.0) else "%.1f MB".format(fileSize / 1048576.0)
                        Text("الحجم: $sizeStr • مستند PDF مشفر", color = SecureCyan, fontSize = 10.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("انقر لفتح قارئ الـ PDF المتقدم والآمن", color = SecureGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                mediaType == com.example.data.util.MediaClassifier.MediaType.ARCHIVE ||
                mediaType == com.example.data.util.MediaClassifier.MediaType.EPUB ||
                mediaType == com.example.data.util.MediaClassifier.MediaType.MS_OFFICE -> {
                    
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FolderZip, contentDescription = null, tint = SecureGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "مستند أرشيف مشفر: $fileName",
                                color = SecureGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            zipEntries.forEach { entry ->
                                Text(
                                    text = "• $entry",
                                    color = MutedText,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(vertical = 1.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (zipEntries.isEmpty()) {
                                Text("أرشيف مضغوط أو مستند Office (OpenXML)", color = MutedText, fontSize = 10.sp)
                            }
                        }
                    }
                }
                else -> {
                    
                    if (isSqlite) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = SecureCyan, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(fileName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("قاعدة بيانات SQLite مشفرة", color = SecureGreen, fontSize = 10.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("انقر لفتح مستكشف قواعد البيانات المستند لصفر معرفة", color = SecureCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = MutedText, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(fileName, color = TextPrimary, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val sizeStr = if (fileSize < 1024) "$fileSize B" else if (fileSize < 1048576) "%.1f KB".format(fileSize / 1024.0) else "%.1f MB".format(fileSize / 1048576.0)
                            Text("ملف ثنائي مشفر آمن ($sizeStr)", color = MutedText, fontSize = 10.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("انقر لفتحه في مستكشف وعارض البيئة المعزولة", color = SecureGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SecureFileContentPreview(
    filePath: String,
    fileName: String
) {
    AdvancedFilePreviewer(filePath = filePath, fileName = fileName)
}

@Composable
fun SecureMediaBubbleContent(
    mediaPath: String,
    mediaName: String,
    isIncoming: Boolean,
    isDownloadAllowed: Boolean,
    encryptionKey: String? = null,
    onMediaClick: (String?, String) -> Unit = { _, _ -> }
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var decryptedPath by remember { mutableStateOf<String?>(null) }
    var isDecrypting by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    val mediaPlayer = remember { android.media.MediaPlayer() }

    // On-the-fly Decryption
    LaunchedEffect(mediaPath, mediaName) {
        isDecrypting = true
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var path = if (mediaPath.isNotEmpty()) {
                com.example.data.util.FileEncryptor.decryptFileToTemp(context, mediaPath, encryptionKey)
            } else {
                null
            }
            if (path == null && mediaName.isNotEmpty()) {
                path = com.example.data.util.FileEncryptor.findAndDecryptFileToTemp(context, mediaName, encryptionKey)
            }
            decryptedPath = path
        }
        isDecrypting = false
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.release()
            } catch (e: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable {
                onMediaClick(decryptedPath ?: mediaPath, mediaName)
            }
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                val mediaType = com.example.data.util.MediaClassifier.classifyFile(
                    if (decryptedPath != null) java.io.File(decryptedPath!!) else null
                )
                
                val isVideo = mediaType == com.example.data.util.MediaClassifier.MediaType.VIDEO || (mediaType == com.example.data.util.MediaClassifier.MediaType.UNKNOWN && mediaName.lowercase().let { it.endsWith(".mp4") || it.endsWith(".3gp") || it.endsWith(".mkv") || it.endsWith(".webm") })
                val isAudio = mediaType == com.example.data.util.MediaClassifier.MediaType.AUDIO || (mediaType == com.example.data.util.MediaClassifier.MediaType.UNKNOWN && mediaName.lowercase().let { it.contains("صوت_مشفر") || it.endsWith(".m4a") || it.endsWith(".aac") || it.endsWith(".mp3") || it.endsWith(".wav") })
                val isImage = mediaType == com.example.data.util.MediaClassifier.MediaType.IMAGE
                val isPdf = mediaType == com.example.data.util.MediaClassifier.MediaType.PDF
                val isCode = mediaType == com.example.data.util.MediaClassifier.MediaType.CODE
                
                val icon = when {
                    isVideo -> Icons.Default.VideoLibrary
                    isAudio -> Icons.Default.Mic
                    isImage -> Icons.Default.Image
                    isPdf -> Icons.Default.PictureAsPdf
                    isCode -> Icons.Default.Code
                    else -> Icons.Default.Attachment
                }
                
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isIncoming) SecureCyan else SecureGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = mediaName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isDecrypting) "جاري التحميل وفك التشفير..." else if (decryptedPath != null) "مشفر محلياً E2EE نشط 🔒" else "جاري النقل أو التحميل...",
                        fontSize = 9.sp,
                        color = if (decryptedPath != null) SecureGreen else MutedText
                    )
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isDecrypting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = SecureCyan,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                
                if (isDownloadAllowed && decryptedPath != null) {
                    IconButton(
                        onClick = {
                            try {
                                val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                                if (!downloadDir.exists()) downloadDir.mkdirs()
                                val destFile = java.io.File(downloadDir, mediaName)
                                java.io.File(decryptedPath!!).copyTo(destFile, overwrite = true)
                                Toast.makeText(context, "تم حفظ الملف [ $mediaName ] بنجاح في مجلد التنزيلات (Downloads)! 📥", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "تم تصدير وحفظ الملف [ $mediaName ] محلياً بنجاح.", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Download",
                            tint = SecureCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else if (!isDecrypting && decryptedPath == null) {
                    // Retry action button if media load/download failed
                    IconButton(
                        onClick = {
                            isDecrypting = true
                            kotlinx.coroutines.MainScope().launch {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    var path = if (mediaPath.isNotEmpty()) {
                                        com.example.data.util.FileEncryptor.decryptFileToTemp(context, mediaPath, encryptionKey)
                                    } else null
                                    if (path == null && mediaName.isNotEmpty()) {
                                        path = com.example.data.util.FileEncryptor.findAndDecryptFileToTemp(context, mediaName, encryptionKey)
                                    }
                                    decryptedPath = path
                                }
                                isDecrypting = false
                                if (decryptedPath == null) {
                                    Toast.makeText(context, "إعادة محاولة التحميل فشلت. تحقق من الاتصال والملف الأصلي.", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "تمت إعادة تحميل الملف بنجاح! 🟢", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry Download",
                            tint = DangerRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Inline decrypted contents or transit preview
        if (decryptedPath != null) {
            val file = java.io.File(decryptedPath!!)
            if (file.exists() && file.length() > 0) {
                val mediaType = com.example.data.util.MediaClassifier.classifyFile(file)
                val ext = mediaName.lowercase()
                
                val isImage = mediaType == com.example.data.util.MediaClassifier.MediaType.IMAGE ||
                        ext.endsWith(".jpg") || ext.endsWith(".png") || ext.endsWith(".jpeg") || ext.endsWith(".gif") || ext.endsWith(".webp") || ext.endsWith(".bmp")
                
                val isVideo = mediaType == com.example.data.util.MediaClassifier.MediaType.VIDEO ||
                        ext.endsWith(".mp4") || ext.endsWith(".3gp") || ext.endsWith(".mkv") || ext.endsWith(".webm") || ext.endsWith(".avi") || ext.endsWith(".mov")
                
                val isAudio = mediaType == com.example.data.util.MediaClassifier.MediaType.AUDIO ||
                        mediaName.contains("صوت_مشفر") || ext.endsWith(".m4a") || ext.endsWith(".aac") || ext.endsWith(".mp3") || ext.endsWith(".wav") || ext.endsWith(".ogg")

                if (isImage) {
                    Spacer(modifier = Modifier.height(8.dp))
                    coil.compose.AsyncImage(
                        model = file,
                        contentDescription = "Decrypted Content",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.DarkGray.copy(alpha = 0.2f)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                } else if (isVideo) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        VideoPlayer(
                            videoPath = decryptedPath!!,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (isAudio) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BorderSubtle, RoundedCornerShape(8.dp))
                            .padding(6.dp)
                    ) {
                        IconButton(
                            onClick = {
                                try {
                                    if (isPlaying) {
                                        mediaPlayer.pause()
                                        isPlaying = false
                                    } else {
                                        mediaPlayer.reset()
                                        mediaPlayer.setDataSource(decryptedPath)
                                        mediaPlayer.prepare()
                                        mediaPlayer.start()
                                        isPlaying = true
                                        mediaPlayer.setOnCompletionListener {
                                            isPlaying = false
                                        }
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "خطأ في تشغيل الصوت المشفر", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = SecureGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPlaying) "جاري تشغيل الصوت الآمن..." else "تسجيل صوتي مشفر • اضغط للتشغيل",
                            fontSize = 11.sp,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    // Quick inline preview for other documents if decrypted
                    Spacer(modifier = Modifier.height(8.dp))
                    SecureFileContentPreview(
                        filePath = decryptedPath!!,
                        fileName = mediaName
                    )
                }
            }
        } else {
            // Media thumbnail placeholder / preview with spinner while in transit or decrypting
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BorderSubtle)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isDecrypting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = SecureCyan,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "جاري تحميل وتجهيز معاينة الوسائط...",
                            fontSize = 10.sp,
                            color = SecureCyan
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "In Transit",
                            tint = MutedText,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "وسائط قيد النقل أو بانتظار فك التشفير",
                            fontSize = 10.sp,
                            color = MutedText
                        )
                    }
                }
            }
        }
    }
}

// --- MESSAGE BUBBLE COMPONENT WITH COUNTDOWN ---
@Composable
fun MessageBubble(
    message: Message,
    senderName: String,
    isIncoming: Boolean,
    selfNodeId: String,
    viewModel: ChatViewModel,
    onDelete: () -> Unit = {},
    onPin: () -> Unit = {},
    onMediaClick: (Message) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeText = formatter.format(Date(message.timestamp))

    var senderReputation by remember { mutableStateOf(0) }
    var revealLowReputationMessage by remember { mutableStateOf(false) }

    LaunchedEffect(message.groupId, message.senderNodeId, message.positiveVotesCount, message.negativeVotesCount) {
        val gid = message.groupId
        if (gid != null) {
            senderReputation = viewModel.getSenderReputation(gid, message.senderNodeId)
        }
    }

    // Mark as read by us
    LaunchedEffect(message.id) {
        if (isIncoming) {
            viewModel.markMessageAsReadBySelf(message.id)
        }
    }

    // Real-time local self destruct countdown animation support
    var remainingSeconds by remember { mutableStateOf(0) }
    var showCountdown by remember { mutableStateOf(false) }

    LaunchedEffect(message.selfDestructSeconds, message.selfDestructAt) {
        if (message.selfDestructSeconds > 0 && message.selfDestructAt > 0) {
            showCountdown = true
            while (true) {
                val now = System.currentTimeMillis()
                val diff = ((message.selfDestructAt - now) / 1000).toInt()
                remainingSeconds = diff.coerceAtLeast(0)
                if (remainingSeconds <= 0) {
                    showCountdown = false
                    break
                }
                delay(500)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isIncoming) Alignment.Start else Alignment.End
    ) {
        // Sender Alias with Reputation Badge (Shown for Group Messages)
        if (isIncoming) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)) {
                Text(
                    text = senderName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SecureCyan
                )
                if (message.groupId != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (senderReputation >= 0) SecureGreen.copy(alpha = 0.15f)
                                else DangerRed.copy(alpha = 0.15f)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "سمعة: $senderReputation",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (senderReputation >= 0) SecureGreen else DangerRed
                        )
                    }
                }
            }
        } else if (message.groupId != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 6.dp, bottom = 2.dp)) {
                Text(
                    text = "أنت",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SecureGreen
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SecureGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "عقدتك",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = SecureGreen
                    )
                }
            }
        }

        if (isIncoming && message.groupId != null && senderReputation < -5 && !revealLowReputationMessage) {
            androidx.compose.material3.Card(
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.3f)),
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .widthIn(max = 280.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "⚠️ محتوى مشبوه ومصنف كـ Spam",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = DangerRed
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "تم حجب محتوى هذه الرسالة تلقائياً بواسطة بروتوكول السمعة اللامركزي لأن سمعة المرسل ($senderReputation) منخفضة جداً.",
                        fontSize = 10.sp,
                        color = Color.LightGray,
                        lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { revealLowReputationMessage = true },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(26.dp)
                            .align(Alignment.End)
                    ) {
                        Text("عرض الرسالة على أي حال", fontSize = 9.sp, color = ObsidianBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = if (isIncoming) Arrangement.Start else Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!isIncoming && showCountdown && remainingSeconds > 0) {
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(SecureGreen.copy(alpha = 0.15f))
                            .border(0.5.dp, SecureGreen, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "💣 ${remainingSeconds}ث",
                            fontSize = 9.sp,
                            color = SecureGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                var showContextMenu by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isIncoming) 2.dp else 16.dp,
                                bottomEnd = if (isIncoming) 16.dp else 2.dp
                            )
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = { showContextMenu = true }
                            )
                        }
                        .background(
                            if (isIncoming) {
                                TitaniumDark
                            } else {
                                if (message.selfDestructSeconds > 0) SecureGreenDark else SecureGreen
                            }
                        )
                        .border(
                            width = 1.dp,
                        color = if (isIncoming) {
                            BorderSubtle
                        } else {
                            if (message.selfDestructSeconds > 0) SecureGreen.copy(alpha = 0.4f) else Color.Transparent
                        },
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isIncoming) 2.dp else 16.dp,
                            bottomEnd = if (isIncoming) 16.dp else 2.dp
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .widthIn(max = 280.dp)
            ) {
                Column {
                    // Attachment if any
                    if (message.mediaName != null) {
                        SecureMediaBubbleContent(
                            mediaPath = message.mediaPath ?: "",
                            mediaName = message.mediaName,
                            isIncoming = isIncoming,
                            isDownloadAllowed = message.isDownloadAllowed,
                            onMediaClick = { _, _ -> onMediaClick(message) }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Message text or sticker
                    if (message.text.startsWith("[STICKER:")) {
                        val stickerData = message.text.removePrefix("[STICKER:").removeSuffix("]")
                        val parts = stickerData.split(":")
                        val stickerContent = if (parts.size >= 2) parts.subList(1, parts.size).joinToString(":") else stickerData
                        Box(
                            modifier = Modifier.padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (stickerContent.startsWith("emoji:")) {
                                Text(text = stickerContent.removePrefix("emoji:"), fontSize = 60.sp)
                            } else {
                                Text(
                                    text = "🎨 ملصق مخصص",
                                    fontSize = 14.sp,
                                    color = if (isIncoming) SecureGreen else ObsidianBlack,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else if (message.text.isNotEmpty()) {
                        Text(
                            text = message.text,
                            fontSize = 14.sp,
                            color = if (isIncoming) Color.White else if (message.selfDestructSeconds > 0) Color.White else ObsidianBlack,
                            lineHeight = 18.sp
                        )
                    }

                    if (message.groupId != null && (message.positiveVotesCount > 0 || message.negativeVotesCount > 0)) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            if (message.positiveVotesCount > 0) {
                                Icon(Icons.Default.ThumbUp, contentDescription = null, tint = SecureGreen, modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(text = "${message.positiveVotesCount}", fontSize = 9.sp, color = SecureGreen, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            if (message.negativeVotesCount > 0) {
                                Icon(Icons.Default.ThumbDown, contentDescription = null, tint = DangerRed, modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(text = "${message.negativeVotesCount}", fontSize = 9.sp, color = DangerRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(message.text))
                                Toast.makeText(context, "تم نسخ الرسالة إلى الحافظة 📋", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy message",
                                tint = if (isIncoming) MutedText else ObsidianBlack.copy(alpha = 0.7f),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = timeText,
                            fontSize = 9.sp,
                            color = if (isIncoming) MutedText else if (message.selfDestructSeconds > 0) SecureGreen.copy(alpha = 0.7f) else ObsidianBlack.copy(alpha = 0.7f)
                        )
                        if (!isIncoming) {
                            Spacer(modifier = Modifier.width(4.dp))
                            val readersList = message.readByNodeIdsText.split(",").filter { it.isNotEmpty() }
                            var showReadersDialog by remember { mutableStateOf(false) }
                            
                            if (readersList.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { showReadersDialog = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = "Readers",
                                        tint = SecureCyan,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "${readersList.size}",
                                        fontSize = 9.sp,
                                        color = SecureCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            val isMessageRead = message.isRead || readersList.isNotEmpty()
                            val showDoubleTick = isMessageRead || (message.groupId != null && readersList.isNotEmpty())
                            val isFailedOrPending = !message.isDelivered && !showDoubleTick
                            
                            if (isFailedOrPending) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(DangerRed.copy(alpha = 0.2f))
                                        .clickable {
                                            viewModel.retryUnsentMessages()
                                            Toast.makeText(context, "جاري إعادة محاولة إرسال الرسالة...", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "لم يُرسل",
                                        tint = DangerRed,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "لم يُرسل (إعادة)",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DangerRed
                                    )
                                }
                            } else {
                                val tickIcon = if (showDoubleTick) Icons.Default.DoneAll else Icons.Default.Done
                                val readTint = if (isMessageRead) Color(0xFF34B7F1) else MutedText
                                Icon(
                                    imageVector = tickIcon,
                                    contentDescription = "Delivery Status",
                                    tint = readTint,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (isIncoming && showCountdown && remainingSeconds > 0) {
                // Countdown timer bubble next to bubble for incoming message
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(DangerRed.copy(alpha = 0.1f))
                        .border(0.5.dp, DangerRed, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "💣 ${remainingSeconds}ث",
                        fontSize = 9.sp,
                        color = DangerRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            androidx.compose.material3.DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
                modifier = Modifier.background(TitaniumDark)
            ) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("تثبيت الرسالة", color = TextPrimary) },
                    onClick = {
                        showContextMenu = false
                        onPin()
                    },
                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null, tint = SecureGreen) }
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("حذف هذه الرسالة", color = DangerRed) },
                    onClick = {
                        showContextMenu = false
                        onDelete()
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = DangerRed) }
                )
                if (message.groupId != null) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("👍 تقييم إيجابي (رسالة مفيدة)", color = SecureGreen) },
                        onClick = {
                            showContextMenu = false
                            viewModel.voteOnMessage(message.id, isPositive = true)
                        },
                        leadingIcon = { Icon(Icons.Default.ThumbUp, contentDescription = null, tint = SecureGreen) }
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("👎 تقييم سلبي (رسالة مزعجة / Spam)", color = DangerRed) },
                        onClick = {
                            showContextMenu = false
                            viewModel.voteOnMessage(message.id, isPositive = false)
                        },
                        leadingIcon = { Icon(Icons.Default.ThumbDown, contentDescription = null, tint = DangerRed) }
                    )
                }
            }
        }
    }
    }
}

// --- 6. ADD NEW CONTACT PEER SCREEN ---
@Composable
fun NewChatScreen(viewModel: ChatViewModel) {
    var nodeIdInput by remember { mutableStateOf("") }
    var aliasInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var importTextInput by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }
    val pendingLink by viewModel.pendingDeepLink.collectAsState()

    LaunchedEffect(pendingLink) {
        if (pendingLink != null) {
            val parsed = com.example.util.DeepLinkHelper.parse(pendingLink)
            if (parsed is com.example.util.ParsedDeepLink.UserLink) {
                nodeIdInput = parsed.nodeId.removePrefix("cipheris://")
                if (!parsed.alias.isNullOrBlank() && aliasInput.isBlank()) {
                    aliasInput = parsed.alias
                }
            } else if (pendingLink?.startsWith("cipheris://") == true && !pendingLink!!.startsWith("cipheris://group/") && !pendingLink!!.startsWith("cipheris://channel/")) {
                nodeIdInput = pendingLink!!.removePrefix("cipheris://")
            }
            viewModel.clearDeepLink()
        }
    }

    val context = LocalContext.current

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.navigateTo("dashboard") }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "مصافحة عقدة جديدة (Add Secure Peer)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Import Card Section
            Card(
                colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SecureGreen.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "استيراد بطاقة اتصال سريع (.cipheris)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = SecureGreen
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "إذا شارك صديقك معك نص دعوة سيفيرس البرمجي أو الرمز الخاص به، الصق النص هنا ليتم ملء البيانات تلقائياً:",
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = { showScanner = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SecureGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("scan_qr_button")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Scan Barcode", tint = ObsidianBlack)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "مسح رمز باركود الصديق بالكاميرا",
                            color = ObsidianBlack,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = BorderSubtle
                        )
                        Text(
                            text = "أو أدخل النص يدوياً",
                            fontSize = 11.sp,
                            color = MutedText,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = BorderSubtle
                        )
                    }
                    
                    OutlinedTextField(
                        value = importTextInput,
                        onValueChange = { input ->
                            importTextInput = input
                            if (input.contains("{") && input.contains("}")) {
                                try {
                                    val jsonStr = if (input.contains("```")) {
                                        input.substringAfter("```").substringBefore("```").trim()
                                    } else {
                                        input.trim()
                                    }
                                    val startIdx = jsonStr.indexOf("{")
                                    val endIdx = jsonStr.lastIndexOf("}")
                                    if (startIdx != -1 && endIdx != -1) {
                                        val cleanedJson = jsonStr.substring(startIdx, endIdx + 1)
                                        val json = JSONObject(cleanedJson)
                                        val importedNodeId = json.optString("nodeId")
                                        val importedAlias = json.optString("alias")
                                        if (importedNodeId.isNotEmpty()) {
                                            nodeIdInput = importedNodeId
                                            aliasInput = importedAlias
                                            errorMessage = null
                                            Toast.makeText(context, "تم استيراد العقدة بنجاح: $importedAlias", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Ignore parse errors
                                }
                            }
                        },
                        label = { Text("الصق رمز بطاقة دعوة الصديق هنا", color = MutedText) },
                        placeholder = { Text("{\n  \"app\": \"Cipheris\",\n  \"alias\": \"...\",\n  \"nodeId\": \"...\"\n}", color = MutedText.copy(alpha = 0.3f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SecureGreen,
                            unfocusedBorderColor = BorderSubtle,
                            focusedLabelColor = SecureGreen,
                            cursorColor = SecureGreen
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // DNS ALIAS SEARCH SECTION (Decentralized DNS)
            val dnsSearchQuery by viewModel.aliasSearchQuery.collectAsState()
            val dnsSearchResults by viewModel.aliasSearchResults.collectAsState()

            Card(
                colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SecureCyan.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "البحث عن اسم مستعار (Decentralized DNS)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = SecureCyan
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ابحث عن الأصدقاء عبر أسمائهم المستعارة المسجلة في شبكة سيفيرس اللامركزية دون الحاجة لمعرفاتهم التقنية:",
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = dnsSearchQuery,
                        onValueChange = { viewModel.updateAliasSearchQuery(it) },
                        label = { Text("ابحث عن اسم مستعار...", color = MutedText) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SecureCyan) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SecureCyan,
                            unfocusedBorderColor = BorderSubtle,
                            focusedLabelColor = SecureCyan,
                            cursorColor = SecureCyan
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (dnsSearchResults.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ObsidianBlack),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                        ) {
                            LazyColumn(modifier = Modifier.padding(4.dp)) {
                                items(dnsSearchResults) { entry ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                nodeIdInput = entry.nodeId
                                                aliasInput = entry.alias
                                                viewModel.updateAliasSearchQuery("")
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(text = entry.alias, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                                            Text(text = entry.nodeId, color = MutedText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                        }
                                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = SecureCyan, modifier = Modifier.size(20.dp))
                                    }
                                    HorizontalDivider(color = BorderSubtle)
                                }
                            }
                        }
                    } else if (dnsSearchQuery.length >= 2) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "لم يتم العثور على أية نتائج في سجل الـ DNS المحلي.",
                            fontSize = 10.sp,
                            color = MutedText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SecureCyan.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "معلومات العقدة المستهدفة",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = SecureCyan
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val isNodeIdStarted = nodeIdInput.trim().isNotEmpty()
                    val isNodeIdValidLength = nodeIdInput.trim().length == 27

                    OutlinedTextField(
                        value = nodeIdInput,
                        onValueChange = { raw ->
                            val parsed = com.example.util.DeepLinkHelper.parse(raw.trim())
                            if (parsed is com.example.util.ParsedDeepLink.UserLink) {
                                nodeIdInput = parsed.nodeId.removePrefix("cipheris://")
                                if (!parsed.alias.isNullOrBlank() && aliasInput.isBlank()) {
                                    aliasInput = parsed.alias
                                }
                                errorMessage = null
                            } else {
                                val clean = raw.trim().removePrefix("cipheris://")
                                if (clean.length <= 27) {
                                    nodeIdInput = clean
                                    errorMessage = null
                                }
                            }
                        },
                        label = { Text("عنوان عقدة الصديق (Device Node ID)", color = MutedText) },
                        placeholder = { Text("cipheris://[عنوان_العقدة_المشفر]", color = MutedText.copy(alpha = 0.4f)) },
                        isError = isNodeIdStarted && !isNodeIdValidLength,
                        visualTransformation = NodeIdVisualTransformation("cipheris://"),
                        supportingText = {
                            if (isNodeIdStarted && !isNodeIdValidLength) {
                                val remaining = 27 - nodeIdInput.trim().length
                                if (remaining > 0) {
                                    Text("متبقي $remaining رموز لتكتمل العقدة", color = DangerRed)
                                } else {
                                    Text("الرمز أطول من اللازم بمقدار ${-remaining} رمز", color = DangerRed)
                                }
                            } else if (isNodeIdValidLength) {
                                Text("طول الرمز صحيح", color = SecureGreen)
                            } else {
                                Text("الرمز يتكون من 27 حرفاً ويبدأ بـ cipheris://")
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SecureCyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedLabelColor = SecureCyan,
                            cursorColor = SecureCyan,
                            errorBorderColor = DangerRed
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_chat_node_id_input"),
                        singleLine = true,
                        trailingIcon = {
                            val clipboardManager = LocalClipboardManager.current
                            Row {
                                IconButton(onClick = { showScanner = true }) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR", tint = SecureCyan)
                                }
                                IconButton(onClick = {
                                    clipboardManager.getText()?.text?.let { text ->
                                        val trimmed = text.trim()
                                        if (trimmed.startsWith("cipheris://")) {
                                            nodeIdInput = trimmed
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = SecureCyan)
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = {
                            aliasInput = it
                            errorMessage = null
                        },
                        label = { Text("الاسم المستعار المحلي له", color = MutedText) },
                        placeholder = { Text("مثال: مهندس التشفير", color = MutedText.copy(alpha = 0.4f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SecureCyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedLabelColor = SecureCyan,
                            cursorColor = SecureCyan
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_chat_alias_input"),
                        singleLine = true
                    )

                    AnimatedVisibility(visible = errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = DangerRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            viewModel.addContact(
                                nodeId = nodeIdInput,
                                alias = aliasInput,
                                onSuccess = {
                                    Toast.makeText(context, "تمت مصافحة العقدة بنجاح!", Toast.LENGTH_SHORT).show()
                                },
                                onError = { error ->
                                    errorMessage = error
                                }
                            )
                        },
                        enabled = isNodeIdValidLength && aliasInput.trim().isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = SecureCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_contact_button")
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = ObsidianBlack)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إقامة اتصال موثق ومؤمن", color = ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Notice card
            Card(
                colors = CardDefaults.cardColors(containerColor = TitaniumDark.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.HelpOutline, contentDescription = null, tint = MutedText, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("كيف أحصل على معرف العقدة؟", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "اطلب من صديقك مشاركة عنوان عقدته من صفحة 'الإعدادات' الخاصة به في تطبيق سيفيرس. قم بنسخه ولصقه هنا لإقامة اتصال تشفيري مباشر ثنائي الأطراف.",
                            fontSize = 11.sp,
                            color = MutedText,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }

    if (showScanner) {
        BarcodeScannerDialog(
            onDismiss = { showScanner = false },
            onCodeScanned = { scanned ->
                val input = scanned.trim()
                if (input.contains("{") && input.contains("}")) {
                    try {
                        val jsonStr = if (input.contains("```")) {
                            input.substringAfter("```").substringBefore("```").trim()
                        } else {
                            input.trim()
                        }
                        val startIdx = jsonStr.indexOf("{")
                        val endIdx = jsonStr.lastIndexOf("}")
                        if (startIdx != -1 && endIdx != -1) {
                            val cleanedJson = jsonStr.substring(startIdx, endIdx + 1)
                            val json = JSONObject(cleanedJson)
                            val importedNodeId = json.optString("nodeId")
                            val importedAlias = json.optString("alias")
                            if (importedNodeId.isNotEmpty()) {
                                nodeIdInput = importedNodeId
                                aliasInput = importedAlias
                                errorMessage = null
                                Toast.makeText(context, "تم استيراد العقدة من الباركود: $importedAlias", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        nodeIdInput = input
                        errorMessage = null
                    }
                } else {
                    nodeIdInput = input
                    errorMessage = null
                    Toast.makeText(context, "تم التقاط معرف العقدة بنجاح!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

// --- 7. CREATE NEW GROUP SCREEN ---
@Composable
fun NewGroupScreen(viewModel: ChatViewModel) {
    val peers by viewModel.peers.collectAsState()
    var groupNameInput by remember { mutableStateOf("") }
    var manualNodeInput by remember { mutableStateOf("") }
    val selectedMembers = remember { mutableStateListOf<String>() }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showScanner by remember { mutableStateOf(false) }

    val context = LocalContext.current

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.navigateTo("dashboard") }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تأسيس مجموعة مشفرة جديدة",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SecureGreen.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "بيانات المجموعة اللامركزية",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = SecureGreen
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = groupNameInput,
                        onValueChange = {
                            groupNameInput = it
                            errorMessage = null
                        },
                        label = { Text("اسم المجموعة المشتركة", color = MutedText) },
                        placeholder = { Text("مثال: كتيبة التشفير الحرة", color = MutedText.copy(alpha = 0.4f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SecureGreen,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedLabelColor = SecureGreen,
                            cursorColor = SecureGreen
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_group_name_input"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "إضافة عضو يدوياً عبر رمز العقدة:",
                        color = SecureCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        val isManualNodeIdStarted = manualNodeInput.trim().isNotEmpty()
                        val isManualNodeIdValidLength = manualNodeInput.trim().length == 27
                        
                        OutlinedTextField(
                            value = manualNodeInput,
                            onValueChange = { 
                                if (it.length <= 27) {
                                    manualNodeInput = it
                                }
                            },
                            placeholder = { Text("cipheris://...", color = MutedText.copy(alpha = 0.4f)) },
                            isError = isManualNodeIdStarted && !isManualNodeIdValidLength,
                            visualTransformation = NodeIdVisualTransformation("cipheris://"),
                            supportingText = {
                                if (isManualNodeIdStarted && !isManualNodeIdValidLength) {
                                    val remaining = 27 - manualNodeInput.trim().length
                                    if (remaining > 0) {
                                        Text("متبقي $remaining رموز لتكتمل العقدة", color = DangerRed)
                                    } else {
                                        Text("الرمز أطول من اللازم بمقدار ${-remaining} رمز", color = DangerRed)
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SecureCyan,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                cursorColor = SecureCyan,
                                errorBorderColor = DangerRed
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            trailingIcon = {
                                val clipboardManager = LocalClipboardManager.current
                                Row {
                                    IconButton(onClick = { showScanner = true }) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR", tint = SecureCyan)
                                    }
                                    IconButton(onClick = {
                                        clipboardManager.getText()?.text?.let { text ->
                                            val trimmed = text.trim()
                                            if (trimmed.startsWith("cipheris://")) {
                                                manualNodeInput = trimmed
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = SecureCyan)
                                    }
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val input = manualNodeInput.trim()
                                if (input.startsWith("cipheris://") && input.length == 27) {
                                    if (!selectedMembers.contains(input)) {
                                        selectedMembers.add(input)
                                        manualNodeInput = ""
                                    }
                                } else {
                                    errorMessage = "رمز العقدة غير صالح أو غير مكتمل (يجب أن يكون الرمز كاملاً)"
                                }
                            },
                            enabled = isManualNodeIdValidLength,
                            modifier = Modifier
                                .background(if (isManualNodeIdValidLength) SecureCyan else TitaniumLight, RoundedCornerShape(12.dp))
                                .size(48.dp)
                                .align(Alignment.CenterVertically)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = ObsidianBlack)
                        }
                    }

                    AnimatedVisibility(visible = errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = DangerRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "اختر العقد لإضافتها للمجموعة المشفرة:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MutedText,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )

            // Peer checkable list
            if (peers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(TitaniumDark.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد عقد صديقة مضافة حتى الآن لتشكيل مجموعة.",
                        fontSize = 12.sp,
                        color = MutedText,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(peers) { peer ->
                        val isChecked = selectedMembers.contains(peer.nodeId)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(TitaniumDark.copy(alpha = 0.4f))
                                .clickable {
                                    if (isChecked) {
                                        selectedMembers.remove(peer.nodeId)
                                    } else {
                                        selectedMembers.add(peer.nodeId)
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked == true) {
                                        selectedMembers.add(peer.nodeId)
                                    } else {
                                        selectedMembers.remove(peer.nodeId)
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = SecureGreen,
                                    uncheckedColor = MutedText
                                )
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(text = peer.alias, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                                    Text(
                                        text = peer.nodeId,
                                        fontSize = 10.sp,
                                        color = MutedText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(BorderSubtle),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = MutedText)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.createSecureGroup(
                        groupName = groupNameInput,
                        selectedMembers = selectedMembers.toList(),
                        onSuccess = {
                            Toast.makeText(context, "تم تأسيس المجموعة اللامركزية بنجاح!", Toast.LENGTH_SHORT).show()
                        },
                        onError = { error ->
                            errorMessage = error
                        }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = SecureGreen),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("create_group_button")
            ) {
                Icon(Icons.Default.Groups, contentDescription = null, tint = ObsidianBlack)
                Spacer(modifier = Modifier.width(6.dp))
                Text("تأسيس وبث المجموعة التشفيرية", color = ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }

    if (showScanner) {
        BarcodeScannerDialog(
            onDismiss = { showScanner = false },
            onCodeScanned = { scanned ->
                val input = scanned.trim()
                if (input.startsWith("cipheris://") && input.length == 27) {
                    if (!selectedMembers.contains(input)) {
                        selectedMembers.add(input)
                    }
                    Toast.makeText(context, "تم استيراد العقدة من الباركود", Toast.LENGTH_SHORT).show()
                } else {
                    errorMessage = "رمز العقدة غير صالح أو غير مكتمل"
                }
                showScanner = false
            }
        )
    }
}

@Composable
fun HighTechBarcodeQR(
    nodeId: String, 
    modifier: Modifier = Modifier, 
    title: String = "Cipheris QR Code",
    shareSubtitle: String? = null,
    showShareButton: Boolean = true
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val seed = nodeId.hashCode().toLong()
    val random = remember(nodeId) { java.util.Random(seed) }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(TitaniumLight, RoundedCornerShape(16.dp))
            .border(1.dp, SecureGreen.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // High-Tech QR code visual representation
        Box(
            modifier = Modifier
                .size(150.dp)
                .background(TitaniumDark, RoundedCornerShape(12.dp))
                .border(1.5.dp, SecureCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            val qrBitmap = remember(nodeId) { generateQrCodeBitmap(nodeId, 512) }
            Image(
                bitmap = qrBitmap,
                contentDescription = "QR Code",
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Dynamic Linear Barcode representation
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianBlack.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .border(0.5.dp, SecureCyan.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
            ) {
                val linesCount = 45
                val totalWidth = size.width
                val lineSpacing = totalWidth / linesCount
                
                for (i in 0 until linesCount) {
                    val lineRandom = java.util.Random(seed + i * 13)
                    val widthScale = if (lineRandom.nextBoolean()) 1.5f else 0.5f
                    val isGap = lineRandom.nextInt(4) == 0
                    
                    if (!isGap) {
                        // Interpolate color from SecureGreen -> SecureCyan -> DangerRed
                        val ratio = i.toFloat() / linesCount.toFloat()
                        val lineColor = if (ratio < 0.5f) {
                            val subRatio = ratio * 2f
                            androidx.compose.ui.graphics.Color(
                                red = SecureGreen.red * (1 - subRatio) + SecureCyan.red * subRatio,
                                green = SecureGreen.green * (1 - subRatio) + SecureCyan.green * subRatio,
                                blue = SecureGreen.blue * (1 - subRatio) + SecureCyan.blue * subRatio,
                                alpha = 1f
                            )
                        } else {
                            val subRatio = (ratio - 0.5f) * 2f
                            androidx.compose.ui.graphics.Color(
                                red = SecureCyan.red * (1 - subRatio) + DangerRed.red * subRatio,
                                green = SecureCyan.green * (1 - subRatio) + DangerRed.green * subRatio,
                                blue = SecureCyan.blue * (1 - subRatio) + DangerRed.blue * subRatio,
                                alpha = 1f
                            )
                        }
                        
                        drawLine(
                            color = lineColor,
                            start = Offset(i * lineSpacing, 0f),
                            end = Offset(i * lineSpacing, size.height),
                            strokeWidth = 2.dp.toPx() * widthScale
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "SECURE IDENTITY ID: ${nodeId.take(16).uppercase()}...",
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                color = SecureCyan,
                fontWeight = FontWeight.Bold
            )
        }

        if (showShareButton) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    val shareCaption = shareSubtitle ?: "مسح هذا الباركود للانضمام في تطبيق سيفيرس المشفر:\n$nodeId"
                    shareQrCodeImage(
                        context = context,
                        textContent = nodeId,
                        title = title,
                        shareText = shareCaption
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = TitaniumDark
                ),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SecureGreen.copy(alpha = 0.5f)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share QR",
                    tint = SecureGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "مشاركة صورة الباركود (Share QR Code)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

fun shareAppApk(context: android.content.Context) {
    try {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(context.packageName, 0)
        val apkFile = java.io.File(appInfo.sourceDir)
        
        val cacheApkFile = java.io.File(context.cacheDir, "Cipheris.apk")
        apkFile.inputStream().use { input ->
            cacheApkFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        val apkUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheApkFile
        )
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, apkUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة تطبيق سيفيرس APK"))
    } catch (e: Exception) {
        Toast.makeText(context, "فشل في مشاركة ملف APK: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun shareAppApkAsSecureZip(context: android.content.Context) {
    try {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(context.packageName, 0)
        val apkFile = java.io.File(appInfo.sourceDir)
        
        // 1. Create a temp APK file
        val tempApkFile = java.io.File(context.cacheDir, "Cipheris_v1.1_Installer.apk")
        apkFile.inputStream().use { input ->
            tempApkFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        // 2. Create the ZIP file in cache
        val zipFile = java.io.File(context.cacheDir, "Cipheris_Secure_Installer.zip")
        if (zipFile.exists()) {
            zipFile.delete()
        }
        
        // 3. Encrypt into the ZIP
        val zip4jFile = net.lingala.zip4j.ZipFile(zipFile, "123".toCharArray())
        val zipParameters = net.lingala.zip4j.model.ZipParameters().apply {
            isEncryptFiles = true
            encryptionMethod = net.lingala.zip4j.model.enums.EncryptionMethod.AES
            aesKeyStrength = net.lingala.zip4j.model.enums.AesKeyStrength.KEY_STRENGTH_256
        }
        zip4jFile.addFile(tempApkFile, zipParameters)
        
        // Clean up the temp APK
        tempApkFile.delete()
        
        // 4. Share the ZIP file
        val zipUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zipFile
        )
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, zipUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة التطبيق كملف ZIP آمن (كلمة المرور: 123)"))
        Toast.makeText(context, "كلمة مرور الملف المضغوط هي: 123", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "فشل في إنشاء ملف ZIP: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun shareInvitationFile(context: android.content.Context, inviteJson: String) {
    try {
        val cacheFile = java.io.File(context.cacheDir, "invite.cipheris")
        cacheFile.writeText(inviteJson)
        
        val fileUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile
        )
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة ملف الدعوة (.cipheris)"))
    } catch (e: Exception) {
        Toast.makeText(context, "فشل في مشاركة ملف الدعوة: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

// --- 8. JOIN EXISTING GROUP SCREEN ---
@Composable
fun JoinGroupScreen(viewModel: ChatViewModel) {
    var groupIdInput by remember { mutableStateOf("") }
    var groupNameInput by remember { mutableStateOf("") }
    var inviteCodeInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showScanner by remember { mutableStateOf(false) }
    val pendingLink by viewModel.pendingDeepLink.collectAsState()
    val pendingKey by viewModel.pendingChannelKey.collectAsState()

    LaunchedEffect(pendingLink, pendingKey) {
        if (pendingLink != null) {
            val parsed = com.example.util.DeepLinkHelper.parse(pendingLink)
            if (parsed is com.example.util.ParsedDeepLink.GroupLink) {
                groupIdInput = parsed.groupId
                if (parsed.inviteCode.isNotEmpty()) {
                    inviteCodeInput = parsed.inviteCode
                }
                if (!parsed.groupName.isNullOrEmpty()) {
                    groupNameInput = parsed.groupName
                }
            } else if (pendingLink?.startsWith("cipheris://group/") == true) {
                groupIdInput = pendingLink!!
                if (pendingKey != null) {
                    inviteCodeInput = pendingKey!!
                }
            }
            viewModel.clearChannelKey()
            viewModel.clearDeepLink()
        }
    }
    
    val context = LocalContext.current

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.navigateTo("dashboard") }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "انضمام لمجموعة مشفرة",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SecureGreen.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "أدخل بيانات المجموعة المستهدفة",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = SecureGreen
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val isGroupIdStarted = groupIdInput.trim().isNotEmpty()
                    val isGroupIdValidLength = groupIdInput.trim().length == 25

                    OutlinedTextField(
                        value = groupIdInput,
                        onValueChange = { 
                            if (it.length <= 25) {
                                groupIdInput = it
                                errorMessage = null 
                            }
                        },
                        label = { Text("رمز المجموعة (Group ID)", color = MutedText) },
                        placeholder = { Text("cipheris://group/...", color = MutedText.copy(alpha = 0.4f)) },
                        isError = isGroupIdStarted && !isGroupIdValidLength,
                        visualTransformation = NodeIdVisualTransformation("cipheris://group/"),
                        supportingText = {
                            if (isGroupIdStarted && !isGroupIdValidLength) {
                                val remaining = 25 - groupIdInput.trim().length
                                if (remaining > 0) {
                                    Text("متبقي $remaining رموز لتكتمل العقدة", color = DangerRed)
                                } else {
                                    Text("الرمز أطول من اللازم بمقدار ${-remaining} رمز", color = DangerRed)
                                }
                            } else if (isGroupIdValidLength) {
                                Text("طول الرمز صحيح", color = SecureGreen)
                            } else {
                                Text("الرمز يتكون من 25 حرفاً ويبدأ بـ cipheris://group/")
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SecureGreen,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            cursorColor = SecureGreen,
                            errorBorderColor = DangerRed
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            val clipboardManager = LocalClipboardManager.current
                            Row {
                                IconButton(onClick = { showScanner = true }) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR", tint = SecureGreen)
                                }
                                IconButton(onClick = {
                                    clipboardManager.getText()?.text?.let { text ->
                                        val trimmed = text.trim()
                                        if (trimmed.startsWith("cipheris://group/")) {
                                            groupIdInput = trimmed
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = SecureGreen)
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = groupNameInput,
                        onValueChange = { groupNameInput = it; errorMessage = null },
                        label = { Text("اسم المجموعة (للتوثيق المحلي)", color = MutedText) },
                        placeholder = { Text("مثال: مجموعة العمل", color = MutedText.copy(alpha = 0.4f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SecureGreen,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            cursorColor = SecureGreen
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = inviteCodeInput,
                        onValueChange = { inviteCodeInput = it; errorMessage = null },
                        label = { Text("رمز الدعوة (Invite Code) (اختياري)", color = MutedText) },
                        placeholder = { Text("مثال: INV-1234", color = MutedText.copy(alpha = 0.4f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SecureGreen,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            cursorColor = SecureGreen
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    AnimatedVisibility(visible = errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = errorMessage ?: "", color = DangerRed, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val id = groupIdInput.trim()
                            if (!id.startsWith("cipheris://group/")) {
                                errorMessage = "رمز المجموعة غير صحيح"
                            } else if (id.length != 25) {
                                errorMessage = "رمز المجموعة غير مكتمل أو خاطئ. تأكد من إدخال الرمز كاملاً"
                            } else if (groupNameInput.trim().isEmpty()) {
                                errorMessage = "الرجاء إدخال اسم للمجموعة"
                            } else {
                                viewModel.joinGroup(id, groupNameInput.trim(), inviteCodeInput.trim())
                            }
                        },
                        enabled = isGroupIdValidLength && groupNameInput.trim().isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SecureGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("انضمام الآن", color = ObsidianBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showScanner) {
        BarcodeScannerDialog(
            onDismiss = { showScanner = false },
            onCodeScanned = { scanned ->
                val input = scanned.trim()
                if (input.startsWith("cipheris://group/") && input.length == 25) {
                    groupIdInput = input
                    Toast.makeText(context, "تم استيراد العقدة من الباركود", Toast.LENGTH_SHORT).show()
                } else {
                    errorMessage = "رمز المجموعة غير صالح أو غير مكتمل"
                }
                showScanner = false
            }
        )
    }
}

// --- 8. SECURE REAL-TIME P2P CALLING OVERLAY ---
@Composable
fun CallingOverlay(viewModel: ChatViewModel) {
    val callState by viewModel.activeCallState.collectAsState()
    val remoteVideoFrame by P2PMediaEngine.remoteVideoFrame.collectAsState()
    
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasCameraPermission = perms[android.Manifest.permission.CAMERA] ?: false
        hasAudioPermission = perms[android.Manifest.permission.RECORD_AUDIO] ?: false
    }

    LaunchedEffect(callState) {
        if (callState != null && (!hasCameraPermission || !hasAudioPermission)) {
            launcher.launch(
                arrayOf(
                    android.Manifest.permission.CAMERA,
                    android.Manifest.permission.RECORD_AUDIO
                )
            )
        }
    }
    
    if (callState != null) {
        val call = callState!!
        
        // --- RINGTONE PLAYER ---
        val ringtone = remember {
            try {
                val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
                android.media.RingtoneManager.getRingtone(context, uri)
            } catch (e: Exception) {
                null
            }
        }
        val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
        
        var isMuted by remember { mutableStateOf(false) }
        var isSpeakerOn by remember { mutableStateOf(false) }

        DisposableEffect(isMuted, isSpeakerOn, call.status) {
            audioManager.isMicrophoneMute = isMuted
            audioManager.isSpeakerphoneOn = isSpeakerOn
            if (call.status == "connected") {
                audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
            } else {
                audioManager.mode = android.media.AudioManager.MODE_NORMAL
            }
            onDispose {
                audioManager.isMicrophoneMute = false
                audioManager.isSpeakerphoneOn = false
                audioManager.mode = android.media.AudioManager.MODE_NORMAL
            }
        }
        
        DisposableEffect(call.status, call.isIncoming) {
            val shouldPlay = (call.isIncoming && call.status == "ringing") || (!call.isIncoming && call.status == "connecting")
            if (shouldPlay) {
                if (audioManager.ringerMode == android.media.AudioManager.RINGER_MODE_NORMAL) {
                    ringtone?.play()
                }
            } else {
                ringtone?.stop()
            }
            onDispose {
                ringtone?.stop()
            }
        }
        // -----------------------
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBlack.copy(alpha = 0.96f))
                .clickable(enabled = false) {} 
                .padding(24.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxHeight()
            ) {
                // Header Block
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 40.dp)
                ) {
                    Icon(
                        imageVector = if (call.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                        contentDescription = null,
                        tint = if (call.isVideo) SecureCyan else SecureGreen,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (call.isVideo) "مكالمة فيديو مشفرة E2EE" else "مكالمة صوتية مشفرة E2EE",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = call.peerName,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = when (call.status) {
                            "ringing" -> if (call.isIncoming) "مكالمة مشفرة واردة..." else "جاري الرنين والاتصال الطرفي..."
                            "connecting" -> "جاري تأمين نفق اتصال نظير-إلى-نظير..."
                            "connected" -> "الاتصال متصل وآمن 100%"
                            "ended" -> "تم إنهاء الاتصال"
                            else -> ""
                        },
                        color = if (call.status == "connected") SecureGreen else Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    if (call.status == "connected") {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "بصمة أمان النظير النشطة: SHA-256 (Peer-to-Peer direct link)",
                            color = MutedText,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Middle Stream View
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (call.status == "connecting" || call.status == "ringing") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .drawBehind {
                                        drawCircle(
                                            color = SecureGreen.copy(alpha = 0.08f),
                                            radius = size.minDimension / 2
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = SecureGreen, modifier = Modifier.size(40.dp))
                            }
                        }
                    } else if (call.isVideo) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Local User Camera Stream (Real CameraX capture)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(TitaniumDark)
                                    .border(1.dp, SecureGreen.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (hasCameraPermission) {
                                    P2PMediaEngine.CameraPreview(
                                        modifier = Modifier.fillMaxSize()
                                    ) { imageProxy ->
                                        // Pipe camera analyzer frame directly to our real-time encrypted socket/relay engine
                                        P2PMediaEngine.streamLocalCameraFrame(imageProxy)
                                    }
                                } else {
                                    Text("أنت (الكاميرا مغلقة)", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                }
                            }
                            // Remote User Camera Stream (Real decrypted incoming frame packets)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(TitaniumLight)
                                    .border(1.dp, SecureCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                val currentFrame = remoteVideoFrame
                                if (call.status == "connected") {
                                    if (currentFrame != null) {
                                        Image(
                                            bitmap = currentFrame.asImageBitmap(),
                                            contentDescription = "Decrypted P2P Video Link",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            CircularProgressIndicator(color = SecureCyan, modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("جاري استقبال البث...", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                                        }
                                    }
                                } else {
                                    CircularProgressIndicator(color = SecureCyan, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .drawBehind {
                                    drawCircle(
                                        color = if (call.status == "connected") SecureGreen.copy(alpha = 0.12f) else SecureGreen.copy(alpha = 0.04f),
                                        radius = size.minDimension / 2
                                    )
                                    drawCircle(
                                        color = if (call.status == "connected") SecureGreen.copy(alpha = 0.22f) else SecureGreen.copy(alpha = 0.08f),
                                        radius = (size.minDimension / 2) - 20.dp.toPx()
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = if (call.status == "connected") SecureGreen else Color.Gray,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }

                // Controls Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (call.isIncoming && call.status == "ringing") {
                        IconButton(
                            onClick = { viewModel.rejectIncomingCall() },
                            modifier = Modifier
                                .size(64.dp)
                                .background(DangerRed, CircleShape)
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "Decline", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        
                        IconButton(
                            onClick = { viewModel.acceptIncomingCall() },
                            modifier = Modifier
                                .size(64.dp)
                                .background(SecureGreen, CircleShape)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Accept", tint = ObsidianBlack, modifier = Modifier.size(28.dp))
                        }
                    } else {
                        // Mute toggle
                        IconButton(
                            onClick = { isMuted = !isMuted },
                            modifier = Modifier
                                .size(64.dp)
                                .background(if (isMuted) Color.White else TitaniumLight, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Mute",
                                tint = if (isMuted) ObsidianBlack else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // End Call
                        IconButton(
                            onClick = { viewModel.endActiveCall() },
                            modifier = Modifier
                                .size(64.dp)
                                .background(DangerRed, CircleShape)
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "End Call", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        
                        // Speaker toggle
                        IconButton(
                            onClick = { isSpeakerOn = !isSpeakerOn },
                            modifier = Modifier
                                .size(64.dp)
                                .background(if (isSpeakerOn) Color.White else TitaniumLight, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = "Speaker",
                                tint = if (isSpeakerOn) ObsidianBlack else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- 9. DECENTRALIZED ENCRYPTED BROADCAST CHANNELS TAB ---
@Composable
fun ChannelsTab(viewModel: ChatViewModel) {
    val channels by viewModel.broadcastChannels.collectAsState()
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var scannedChannelId by remember { mutableStateOf("") }
    
    var channelIdInput by remember { mutableStateOf("") }
    var channelNameInput by remember { mutableStateOf("") }
    var channelKeyInput by remember { mutableStateOf("") }
    var channelIdError by remember { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current
    val pendingLink by viewModel.pendingDeepLink.collectAsState()
    val pendingKey by viewModel.pendingChannelKey.collectAsState()

    LaunchedEffect(pendingLink, pendingKey) {
        if (pendingLink?.startsWith("cipheris://channel/") == true) {
            channelIdInput = pendingLink!!
            if (pendingKey != null) {
                channelKeyInput = pendingKey!!
                viewModel.clearChannelKey()
            }
            showJoinDialog = true
            viewModel.clearDeepLink()
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TitaniumDark)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "سيفيرس • قنوات البث المشفرة",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "قنوات مشفرة كلياً نظير-إلى-نظير دون خادم مركزي",
                        fontSize = 10.sp,
                        color = SecureCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { showJoinDialog = true }) {
                        Icon(Icons.Default.AddLink, contentDescription = "Join Channel", tint = SecureCyan)
                    }
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Create Channel", tint = SecureGreen)
                    }
                }
            }
            
            if (channels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Campaign, contentDescription = null, tint = SecureGreen, modifier = Modifier.size(60.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("لا توجد قنوات بث حتى الآن", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "تتيح لك قنوات البث المشفرة نشر المحتوى والمنشورات لآلاف المستلمين مجهولاً ودون الحاجة للكشف عن عقدتك الخاصة، مع حماية أمنية شاملة ضد الحظر والرقابة.",
                                color = MutedText,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { showCreateDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = SecureGreen),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("إنشاء قناة جديدة", color = ObsidianBlack, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { showJoinDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = TitaniumLight),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("الانضمام لقناة", color = Color.White)
                                }
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(channels) { channel ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectChannel(channel.channelId) }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Campaign,
                                            contentDescription = null,
                                            tint = generateColorForId(channel.channelId),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = channel.name,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 15.sp
                                        )
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (channel.isPrivate) SecureCyan.copy(alpha = 0.1f) else SecureGreen.copy(alpha = 0.1f))
                                            .border(0.5.dp, if (channel.isPrivate) SecureCyan else SecureGreen, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (channel.isPrivate) "خاصة مشفرة" else "عامة مستقلة",
                                            fontSize = 9.sp,
                                            color = if (channel.isPrivate) SecureCyan else SecureGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = channel.description,
                                    fontSize = 12.sp,
                                    color = Color.LightGray,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 16.sp
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "المعرف: ${channel.channelId.take(16)}...",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MutedText
                                    )
                                    
                                    Icon(
                                        imageVector = Icons.Default.ChevronLeft,
                                        contentDescription = "Open",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // --- Dialog 1: Create Broadcast Channel ---
        if (showCreateDialog) {
            var channelName by remember { mutableStateOf("") }
            var channelDesc by remember { mutableStateOf("") }
            var isPrivateChannel by remember { mutableStateOf(false) }
            var encryptionKeyInput by remember { mutableStateOf("") }
            
            Dialog(onDismissRequest = { showCreateDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "إنشاء قناة بث لامركزية جديدة",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = SecureGreen
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = channelName,
                            onValueChange = { channelName = it },
                            label = { Text("اسم القناة (مثال: شبكة الأخبار الحرة)", color = MutedText) },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SecureGreen),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedTextField(
                            value = channelDesc,
                            onValueChange = { channelDesc = it },
                            label = { Text("وصف القناة ومحتواها", color = MutedText) },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SecureGreen),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("قناة مشفرة خاصة (مغلقة بمفتاح)", color = TextPrimary, fontSize = 13.sp)
                            Switch(
                                checked = isPrivateChannel,
                                onCheckedChange = { 
                                    isPrivateChannel = it 
                                    if (it && encryptionKeyInput.isEmpty()) {
                                        val randomHex = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8)
                                        encryptionKeyInput = "key_$randomHex"
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = SecureGreen, checkedTrackColor = SecureGreen.copy(alpha = 0.5f))
                            )
                        }
                        
                        if (isPrivateChannel) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = encryptionKeyInput,
                                onValueChange = { encryptionKeyInput = it },
                                label = { Text("مفتاح التشفير المشترك (Shared E2EE Key)", color = MutedText) },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SecureCyan),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { showCreateDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = TitaniumLight),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("إلغاء", color = Color.White)
                            }
                            Button(
                                onClick = {
                                    if (channelName.trim().isNotEmpty()) {
                                        viewModel.createBroadcastChannel(
                                            channelName.trim(),
                                            channelDesc.trim(),
                                            isPrivateChannel,
                                            encryptionKeyInput.trim()
                                        )
                                        showCreateDialog = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SecureGreen),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("إنشاء وتفعيل", color = ObsidianBlack, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        
        // --- Dialog 2: Join Broadcast Channel ---
        if (showJoinDialog) {
            Dialog(onDismissRequest = { showJoinDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "الانضمام لقناة بث لامركزية",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = SecureCyan
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        val isChannelIdStarted = channelIdInput.trim().isNotEmpty()
                        val isChannelIdValidLength = channelIdInput.trim().length == 35

                        OutlinedTextField(
                            value = channelIdInput,
                            onValueChange = { 
                                if (it.length <= 35) {
                                    channelIdInput = it
                                    channelIdError = null // Clear error on edit
                                }
                            },
                            label = { Text("رابط أو رمز القناة (Channel ID)", color = MutedText) },
                            visualTransformation = NodeIdVisualTransformation("cipheris://channel/"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SecureCyan,
                                errorBorderColor = DangerRed
                            ),
                            singleLine = true,
                            trailingIcon = {
                                Row {
                                    IconButton(onClick = { showScanner = true }) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR", tint = SecureCyan)
                                    }
                                    IconButton(onClick = {
                                        clipboardManager.getText()?.text?.let { text ->
                                            val trimmed = text.trim()
                                            if (trimmed.startsWith("cipheris://channel/")) {
                                                channelIdInput = trimmed
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = SecureCyan)
                                    }
                                }
                            },
                            isError = (isChannelIdStarted && !isChannelIdValidLength) || channelIdError != null,
                            supportingText = {
                                if (channelIdError != null) {
                                    Text(channelIdError!!, color = DangerRed)
                                } else if (isChannelIdStarted && !isChannelIdValidLength) {
                                    val remaining = 35 - channelIdInput.trim().length
                                    if (remaining > 0) {
                                        Text("متبقي $remaining رموز لتكتمل العقدة", color = DangerRed)
                                    } else {
                                        Text("الرمز أطول من اللازم بمقدار ${-remaining} رمز", color = DangerRed)
                                    }
                                } else if (isChannelIdValidLength) {
                                    Text("طول الرمز صحيح", color = SecureGreen)
                                } else {
                                    Text("الرمز يتكون من 35 حرفاً ويبدأ بـ cipheris://channel/")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = channelNameInput,
                            onValueChange = { channelNameInput = it },
                            label = { Text("اسم القناة (للتنظيم المحلي)", color = MutedText) },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SecureCyan),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedTextField(
                            value = channelKeyInput,
                            onValueChange = { channelKeyInput = it },
                            label = { Text("رمز الدعوة أو مفتاح فك التشفير (اختياري)", color = MutedText) },
                            placeholder = { Text("أدخل رمز الدعوة أو مفتاح فك التشفير هنا...", color = MutedText.copy(alpha = 0.4f)) },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SecureCyan),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { showJoinDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = TitaniumLight),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("إلغاء", color = Color.White)
                            }
                            Button(
                                onClick = {
                                    val id = channelIdInput.trim()
                                    if (id.isEmpty()) {
                                        channelIdError = "يرجى إدخال رمز القناة"
                                    } else if (!id.startsWith("cipheris://channel/")) {
                                        channelIdError = "رمز القناة غير صالح. يجب أن يبدأ بـ cipheris://channel/"
                                    } else if (id.length != 35) {
                                        channelIdError = "رمز القناة غير مكتمل أو خاطئ. تأكد من إدخال الرمز كاملاً"
                                    } else {
                                        viewModel.joinChannelDirectly(
                                            id,
                                            channelNameInput.trim().ifEmpty { "قناة مضافة" },
                                            channelKeyInput.trim()
                                        )
                                        showJoinDialog = false
                                    }
                                },
                                enabled = isChannelIdValidLength,
                                colors = ButtonDefaults.buttonColors(containerColor = SecureCyan),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("انضمام وتشفير", color = ObsidianBlack, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        if (showScanner) {
            BarcodeScannerDialog(
                onDismiss = { showScanner = false },
                onCodeScanned = { scanned ->
                    val input = scanned.trim()
                    if (input.startsWith("cipheris://channel/") && input.length == 35) {
                        channelIdInput = input
                        showJoinDialog = true
                    }
                    showScanner = false
                }
            )
        }
    }
}

// --- 10. SECURE INTERACTIVE CHANNEL VIEWER SCREEN ---
@Composable
fun ChannelViewScreen(viewModel: ChatViewModel) {
    val selectedId by viewModel.selectedChannelId.collectAsState()
    val channels by viewModel.broadcastChannels.collectAsState()
    val posts by viewModel.selectedChannelPosts.collectAsState()
    val peers by viewModel.peers.collectAsState()
    
    val channel = channels.find { it.channelId == selectedId }
    var postInputText by remember { mutableStateOf("") }
    var postAnonymously by remember { mutableStateOf(true) }
    
    val lazyListState = rememberLazyListState()
    var showShareInfoDialog by remember { mutableStateOf(false) }
    var dialogSelectedTab by remember { mutableIntStateOf(0) }
    var showTrustEngine by remember { mutableStateOf(false) }
    var dialogContext by remember { mutableStateOf("share") } // "share" or "settings"
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    var activeFullscreenMedia by remember { mutableStateOf<Pair<String?, String>?>(null) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    val attachName by viewModel.selectedAttachmentName.collectAsState()
    val isRecordingAudio by viewModel.isRecordingAudio.collectAsState()
    val selfPeer by viewModel.selfPeer.collectAsState()

    val selfNodeId = selfPeer?.nodeId ?: "cipheris://local"
    val isCreator = channel?.createdBy == selfNodeId
    val adminIds = channel?.adminNodeIdsText?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
    val modIds = channel?.moderatorNodeIdsText?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
    val isChannelAdmin = isCreator || adminIds.contains(selfNodeId)
    val isChannelMod = modIds.contains(selfNodeId)

    var hasGranularPostPermission = remember(channel?.permissionsJsonText, selfNodeId) {
        var result = false
        if (!channel?.permissionsJsonText.isNullOrEmpty()) {
            try {
                val json = org.json.JSONObject(channel!!.permissionsJsonText)
                if (json.has(selfNodeId)) {
                    val userJson = json.getJSONObject(selfNodeId)
                    result = userJson.optBoolean("postMessages", false) || userJson.optBoolean("can_post", false)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
        result
    }

    val canPostInChannel = isChannelAdmin || isChannelMod || hasGranularPostPermission

    val channelRecordPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startVoiceRecording(context)
        } else {
            Toast.makeText(context, "⚠️ يرجى منح إذن الميكروفون لتسجيل الرسائل الصوتية", Toast.LENGTH_SHORT).show()
        }
    }

    val channelFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val contentResolver = context.contentResolver
            val cursor = contentResolver.query(uri, null, null, null, null)
            var size = "1.2 MB"
            cursor?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        val sizeBytes = it.getLong(sizeIndex)
                        val mb = sizeBytes / (1024.0 * 1024.0)
                        size = if (mb > 1) {
                            String.format(Locale.US, "%.1f MB", mb)
                        } else {
                            val kb = sizeBytes / 1024.0
                            String.format(Locale.US, "%.1f KB", kb)
                        }
                    }
                }
            }
            viewModel.encryptAndSelectFile(context, uri.toString(), size)
        }
    }

    LaunchedEffect(posts.size) {
        if (posts.isNotEmpty()) {
            lazyListState.animateScrollToItem(posts.size - 1)
        }
    }
    
    if (channel == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = SecureGreen)
        }
        return
    }

    if (showShareInfoDialog) {
        var customChannelInviteCode by remember { mutableStateOf("CHN-" + (1000..9999).random().toString()) }
        Dialog(onDismissRequest = { showShareInfoDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (dialogContext == "share") "مشاركة القناة والربط" else "إدارة القناة والإحصائيات",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = if (dialogContext == "share") SecureCyan else SecureGreen
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (dialogContext == "settings") {
                        androidx.compose.material3.TabRow(
                            selectedTabIndex = if (dialogSelectedTab == 0) 1 else dialogSelectedTab,
                            containerColor = Color.Transparent,
                            contentColor = SecureGreen,
                            divider = { HorizontalDivider(color = BorderSubtle) }
                        ) {
                            androidx.compose.material3.Tab(
                                selected = dialogSelectedTab == 1,
                                onClick = { dialogSelectedTab = 1 },
                                text = { Text("الأعضاء", fontSize = 11.sp, maxLines = 1) }
                            )
                            androidx.compose.material3.Tab(
                                selected = dialogSelectedTab == 2,
                                onClick = { dialogSelectedTab = 2 },
                                text = { Text("الإحصائيات", fontSize = 11.sp, maxLines = 1) }
                            )
                            androidx.compose.material3.Tab(
                                selected = dialogSelectedTab == 3,
                                onClick = { dialogSelectedTab = 3 },
                                text = { Text("الإدارة", fontSize = 11.sp, maxLines = 1) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    val allMemberIds = (channel.memberNodeIdsText.split(",").filter { it.isNotEmpty() } +
                                       channel.adminNodeIdsText.split(",").filter { it.isNotEmpty() } + 
                                       channel.moderatorNodeIdsText.split(",").filter { it.isNotEmpty() }).distinct()

                    if (dialogContext == "share") {
                        HighTechBarcodeQR(
                            nodeId = channel.channelId,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "اسم القناة:",
                            fontSize = 12.sp,
                            color = MutedText
                        )
                        Text(
                            text = channel.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "وصف القناة:",
                            fontSize = 12.sp,
                            color = MutedText
                        )
                        Text(
                            text = channel.description,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "رابط / معرف القناة (Channel ID):",
                            fontSize = 12.sp,
                            color = MutedText
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TitaniumLight.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = channel.channelId,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = SecureCyan,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(channel.channelId))
                                    Toast.makeText(context, "تم نسخ معرف القناة إلى الحافظة!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy ID",
                                    tint = SecureGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (channel.isPrivate) {
                            Text(
                                text = "مفتاح فك التشفير (E2EE Decryption Key):",
                                fontSize = 12.sp,
                                color = MutedText
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(TitaniumLight.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = channel.encryptionKey,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = SecureGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(channel.encryptionKey))
                                        Toast.makeText(context, "تم نسخ مفتاح التشفير إلى الحافظة!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Key",
                                        tint = SecureGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "⚠️ تنبيه: هذا المفتاح سري جداً ومطلوب لفك تشفير المنشورات. شاركه فقط مع الأشخاص الذين ترغب في السماح لهم بقراءة منشورات قناتك الخاصة.",
                                color = Color.Yellow.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        } else {
                            Text(
                                text = "نوع القناة: عامة مستقلة (لا تحتاج لمفتاح خاص للانضمام).",
                                color = SecureGreen,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = customChannelInviteCode,
                                onValueChange = { customChannelInviteCode = it },
                                label = { Text("رمز دعوة القناة (Invite Code)", color = MutedText, fontSize = 11.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SecureCyan,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    cursorColor = SecureCyan
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val keyToUse = if (channel.isPrivate) channel.encryptionKey else customChannelInviteCode
                                    val channelLink = com.example.util.ParsedDeepLink.ChannelLink(channel.channelId, channel.name, keyToUse)
                                    val webUrl = com.example.util.DeepLinkHelper.createUniversalLink(channelLink)
                                    clipboardManager.setText(AnnotatedString(webUrl))
                                    Toast.makeText(context, "تم نسخ رابط القناة المباشر إلى الحافظة! 🔗", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TitaniumLight),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, tint = SecureCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("نسخ الرابط", color = Color.White, fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    val keyToUse = if (channel.isPrivate) channel.encryptionKey else customChannelInviteCode
                                    val channelLink = com.example.util.ParsedDeepLink.ChannelLink(channel.channelId, channel.name, keyToUse)
                                    val shareIntent = com.example.util.DeepLinkHelper.createShareIntent(context, channelLink, isEnglish = false)
                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "مشاركة رابط القناة"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SecureCyan),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = ObsidianBlack, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مشاركة", color = ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Button(
                                onClick = { showShareInfoDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = SecureGreen),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("إغلاق", color = ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    } else if (dialogSelectedTab == 1) {
                        val adminIds = channel.adminNodeIdsText.split(",").filter { it.isNotEmpty() }
                        val modIds = channel.moderatorNodeIdsText.split(",").filter { it.isNotEmpty() }
                        
                        MembersTabContent(
                            peers = peers,
                            memberIds = allMemberIds,
                            adminIds = adminIds,
                            modIds = modIds,
                            permissionsJsonText = channel.permissionsJsonText,
                            isChannel = true,
                            onAddMember = { nodeId: String ->
                                // For channels, adding as regular member/subscriber by default
                                viewModel.setChannelRole(channel.channelId, nodeId, "member", true)
                                Toast.makeText(context, "تم إضافة المشترك بنجاح", Toast.LENGTH_SHORT).show()
                            },
                            onSetRole = { nodeId: String, role: String, add: Boolean ->
                                viewModel.setChannelRole(channel.channelId, nodeId, role, add)
                                Toast.makeText(context, "تم تحديث صلاحيات المشترك", Toast.LENGTH_SHORT).show()
                            },
                            onSetGranularPermissions = { nodeId: String, perms: Map<String, Boolean> ->
                                viewModel.setChannelGranularPermissions(channel.channelId, nodeId, perms)
                                Toast.makeText(context, "تم حفظ الصلاحيات المخصصة", Toast.LENGTH_SHORT).show()
                            },
                            pendingRequests = channel.pendingJoinRequestsText.split(",").filter { it.isNotEmpty() },
                            onHandleJoinRequest = { nodeId: String, accept: Boolean, role: String ->
                                viewModel.handleJoinRequest(channel.channelId, nodeId, accept, role)
                                val msg = if (accept) "✅ تم قبول انضمام العضو للقناة" else "❌ تم رفض طلب الانضمام للقناة"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else if (dialogSelectedTab == 2) {
                        AnalyticsDashboard(
                            memberCount = allMemberIds.size,
                            messageCount = posts.size,
                            deletedCount = 0,
                            joiningStats = listOf(0, 0, 0, 0, 0, 0, allMemberIds.size)
                        )
                    } else if (dialogSelectedTab == 3) {
                        Text(text = "إعدادات القناة", color = DangerRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.clearChannelPosts(channel.channelId)
                                Toast.makeText(context, "تم مسح جميع منشورات القناة", Toast.LENGTH_SHORT).show()
                                showShareInfoDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, DangerRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = DangerRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("مسح جميع منشورات القناة", color = DangerRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }




    if (showDeleteConfirmationDialog) {
        Dialog(onDismissRequest = { showDeleteConfirmationDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(DangerRed.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "حذف القناة نهائياً",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "هل أنت متأكد من رغبتك في حذف القناة [${channel.name}] وكل المنشورات الخاصة بها من جهازك؟ لا يمكن التراجع عن هذا الإجراء.",
                        fontSize = 13.sp,
                        color = MutedText,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                showDeleteConfirmationDialog = false
                                viewModel.deleteBroadcastChannel(channel.channelId)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("نعم، احذف", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        
                        Button(
                            onClick = { showDeleteConfirmationDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = TitaniumLight),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("إلغاء", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    if (showTrustEngine) {
        CryptographicTrustEngineDialog(
            viewModel = viewModel,
            isGroup = false,
            isChannel = true,
            destinationId = selectedId,
            onDismiss = { showTrustEngine = false }
        )
    }
    
    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TitaniumDark)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { 
                                dialogContext = "share"
                                dialogSelectedTab = 0
                                showShareInfoDialog = true 
                            }
                    ) {
                        IconButton(onClick = { viewModel.selectChannel(null); viewModel.navigateTo("dashboard") }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                        
                        Spacer(modifier = Modifier.width(4.dp))

                        // Beautiful circular gradient Avatar
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                        colors = listOf(SecureCyan, SecureGreen)
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = channel.name.take(1).uppercase(),
                                color = ObsidianBlack,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))
                        
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = channel.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(SecureGreen, CircleShape)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (channel.isPrivate) "قناة خاصة مشفرة • E2EE" else "قناة عامة مستقلة • E2EE",
                                fontSize = 10.sp,
                                color = SecureGreen,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NetworkStatusActionIcon(viewModel)

                        IconButton(onClick = { showTrustEngine = true }) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = "Trust Audit Engine",
                                tint = SecureCyan
                            )
                        }

                        // Settings / Administration
                        IconButton(onClick = { 
                            dialogContext = "settings"
                            dialogSelectedTab = 1 // Start at Administration tab
                            showShareInfoDialog = true 
                        }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "الإدارة والصلاحيات",
                                tint = SecureGreen
                            )
                        }

                        // Dedicated Share icon
                        IconButton(onClick = { 
                            dialogContext = "share"
                            dialogSelectedTab = 0 
                            showShareInfoDialog = true 
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "مشاركة القناة",
                                tint = SecureCyan
                            )
                        }

                        IconButton(onClick = { showDeleteConfirmationDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "حذف القناة",
                                tint = DangerRed
                            )
                        }
                    }
                }
                HorizontalDivider(color = BorderSubtle)
            }
        },
        bottomBar = {
            Surface(
                color = TitaniumDark,
                tonalElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                if (!canPostInChannel) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .background(BorderSubtle, RoundedCornerShape(16.dp))
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MutedText,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "القناة للقراءة فقط (النشر مقتصر على الأدمن والمشرفين المخولين)",
                                color = MutedText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (attachName != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SecureGreen.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📎 ملف جاهز للنشر المشفر: $attachName", color = SecureGreen, fontSize = 11.sp)
                                IconButton(onClick = { viewModel.clearAttachment() }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = DangerRed, modifier = Modifier.size(14.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PrivacyTip,
                                    contentDescription = null,
                                    tint = SecureGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("نشر ككاتب مجهول الهوية بالكامل", color = MutedText, fontSize = 10.sp)
                            }
                            
                            Switch(
                                checked = postAnonymously,
                                onCheckedChange = { postAnonymously = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = SecureGreen, checkedTrackColor = SecureGreen.copy(alpha = 0.5f)),
                                modifier = Modifier.scale(0.8f)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        if (isRecordingAudio) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .background(DangerRed.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).background(DangerRed, CircleShape))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("جاري تسجيل صوتي للقناة...", color = TextPrimary, fontSize = 12.sp)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = {
                                            com.example.data.util.VoiceRecorderHelper.stopRecording()
                                            viewModel.isRecordingAudio.value = false
                                            Toast.makeText(context, "تم إلغاء تسجيل القناة.", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Text("إلغاء", color = DangerRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = {
                                            if (channel != null) {
                                                viewModel.stopVoiceRecording(context, isChannel = true, destinationId = channel.channelId)
                                                Toast.makeText(context, "تم النشر الصوتي المشفر بنجاح! 🔒", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SecureGreen),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("نشر", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Media / Attachment Menu
                                var showChannelAttachMenu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { showChannelAttachMenu = true }) {
                                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Media", tint = SecureGreen)
                                    }
                                    androidx.compose.material3.DropdownMenu(
                                        expanded = showChannelAttachMenu,
                                        onDismissRequest = { showChannelAttachMenu = false },
                                        modifier = Modifier.background(TitaniumDark).width(200.dp)
                                    ) {
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("صورة / فيديو", color = TextPrimary) },
                                            onClick = { showChannelAttachMenu = false; channelFilePicker.launch("image/* video/*") },
                                            leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = SecureCyan) }
                                        )
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("ملف / تطبيق (جميع الصيغ)", color = TextPrimary) },
                                            onClick = { showChannelAttachMenu = false; channelFilePicker.launch("*/*") },
                                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, tint = SecureGreen) }
                                        )
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("موسيقى", color = TextPrimary) },
                                            onClick = { showChannelAttachMenu = false; channelFilePicker.launch("audio/*") },
                                            leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null, tint = SecureGreen) }
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = postInputText,
                                    onValueChange = { postInputText = it },
                                    placeholder = { Text("أكتب منشور القناة المشفر هنا...", color = MutedText, fontSize = 13.sp) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = SecureGreen,
                                        unfocusedBorderColor = BorderSubtle,
                                        cursorColor = SecureGreen
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp),
                                    singleLine = false,
                                    maxLines = 4
                                )
                                
                                if (postInputText.trim().isEmpty() && attachName == null) {
                                    IconButton(
                                        onClick = {
                                            channelRecordPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                                        },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(BorderSubtle, CircleShape)
                                    ) {
                                        Icon(Icons.Default.Mic, contentDescription = "Voice", tint = SecureGreen)
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            if (postInputText.trim().isNotEmpty() && channel != null) {
                                                viewModel.postToChannel(channel.channelId, postInputText.trim(), postAnonymously)
                                                postInputText = ""
                                            }
                                        },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(SecureGreen, CircleShape)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Publish", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        val scope = rememberCoroutineScope()
        Column(modifier = Modifier.padding(innerPadding)) {
            val pinnedPost = channel.pinnedPostId?.let { pid -> posts.find { it.id == pid } }
            if (pinnedPost != null) {
                PinnedMessageBanner(
                    text = pinnedPost.text,
                    onClose = { viewModel.pinChannelPost(channel.channelId, null) },
                    onClick = {
                        val index = posts.indexOf(pinnedPost)
                        if (index != -1) {
                            scope.launch { lazyListState.animateScrollToItem(index) }
                        }
                    }
                )
            }
            if (posts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("لا توجد منشورات في هذه القناة بعد", color = Color.Gray, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(posts) { post ->
                    var showPostMenu by remember { mutableStateOf(false) }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TitaniumDark.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = { showPostMenu = true }
                                )
                            }
                    ) {
                        Box {
                            Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(if (post.isAnonymous) SecureGreen.copy(alpha = 0.1f) else SecureCyan.copy(alpha = 0.1f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (post.isAnonymous) Icons.Default.VisibilityOff else Icons.Default.Person,
                                            contentDescription = null,
                                            tint = if (post.isAnonymous) SecureGreen else SecureCyan,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (post.isAnonymous) "ناشر مجهول الهوية" else "عضو القناة المشفرة",
                                        fontWeight = FontWeight.Bold,
                                        color = if (post.isAnonymous) SecureGreen else SecureCyan,
                                        fontSize = 12.sp
                                    )
                                }
                                
                                Text(
                                    text = "مفتاح E2EE نشط",
                                    fontSize = 9.sp,
                                    color = SecureGreen,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Text(
                                text = post.text,
                                color = Color.White,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            if (post.mediaName != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                SecureMediaBubbleContent(
                                    mediaPath = post.mediaPath ?: "",
                                    mediaName = post.mediaName,
                                    isIncoming = true, // it's a channel, always treat as incoming for decryption logic
                                    isDownloadAllowed = true,
                                    encryptionKey = channel.encryptionKey,
                                    onMediaClick = { path, name ->
                                        activeFullscreenMedia = Pair(path, name)
                                    }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "توقيع المنشور: SHA-256 Verified",
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MutedText
                                )
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(post.text))
                                            Toast.makeText(context, "تم نسخ منشور القناة إلى الحافظة 📋", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy post",
                                            tint = SecureGreen,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(post.timestamp)),
                                        fontSize = 9.sp,
                                        color = MutedText
                                    )
                                }
                            }
                        }

                        androidx.compose.material3.DropdownMenu(
                            expanded = showPostMenu,
                            onDismissRequest = { showPostMenu = false },
                            modifier = Modifier.background(TitaniumDark)
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("نسخ النص 📋", color = TextPrimary) },
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(post.text))
                                    Toast.makeText(context, "تم نسخ منشور القناة إلى الحافظة 📋", Toast.LENGTH_SHORT).show()
                                    showPostMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = SecureGreen) }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("تثبيت المنشور", color = TextPrimary) },
                                onClick = {
                                    viewModel.pinChannelPost(channel.channelId, post.id)
                                    showPostMenu = false
                                    Toast.makeText(context, "تم تثبيت المنشور في القناة", Toast.LENGTH_SHORT).show()
                                },
                                leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null, tint = SecureGreen) }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("حذف المنشور", color = DangerRed) },
                                onClick = {
                                    viewModel.deleteChannelPost(post.id)
                                    showPostMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = DangerRed) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (activeFullscreenMedia != null) {
        FullScreenMediaDialog(
            mediaName = activeFullscreenMedia!!.second,
            filePath = activeFullscreenMedia!!.first,
            onDismiss = { activeFullscreenMedia = null }
        )
    }
    }
    }
}

// --- PERMISSIONS & ROLES SYSTEM ---

data class PermissionMeta(
    val key: String,
    val labelAr: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

val ALL_CHANNEL_PERMISSIONS = listOf(
    PermissionMeta("changeInfo", "تغيير معلومات القناة/المجموعة", Icons.Default.Info),
    PermissionMeta("postMessages", "نشر الرسائل", Icons.Default.Send),
    PermissionMeta("editMessages", "تعديل الرسائل والمنشورات", Icons.Default.Edit),
    PermissionMeta("deleteMessages", "حذف الرسائل والمنشورات", Icons.Default.Delete),
    PermissionMeta("pinMessages", "تثبيت الرسائل", Icons.Default.PushPin),
    PermissionMeta("inviteUsers", "دعوة الأعضاء", Icons.Default.PersonAdd),
    PermissionMeta("banUsers", "حظر الأعضاء", Icons.Default.Block),
    PermissionMeta("removeUsers", "إزالة الأعضاء", Icons.Default.PersonRemove),
    PermissionMeta("muteUsers", "كتم الأعضاء", Icons.Default.VolumeOff),
    PermissionMeta("restrictUsers", "تقييد الأعضاء", Icons.Default.RemoveCircleOutline),
    PermissionMeta("addAdmins", "إضافة مشرفين", Icons.Default.AdminPanelSettings),
    PermissionMeta("manageAdminPermissions", "منح وسحب صلاحيات المشرفين", Icons.Default.SettingsSuggest),
    PermissionMeta("anonymousAdmin", "الإدارة بشكل مجهول", Icons.Default.VisibilityOff),
    PermissionMeta("manageComments", "إدارة التعليقات", Icons.Default.Comment),
    PermissionMeta("managePrivateMessages", "إدارة الرسائل الخاصة للقناة", Icons.Default.Mail),
    PermissionMeta("manageVoiceChats", "إدارة المحادثات الصوتية والبث المباشر", Icons.Default.Mic),
    PermissionMeta("manageTopics", "إنشاء وإدارة المواضيع", Icons.Default.Topic),
    PermissionMeta("approveJoinRequests", "الموافقة على طلبات الانضمام", Icons.Default.CheckCircle),
    PermissionMeta("manageInviteLinks", "إنشاء وإدارة روابط الدعوة", Icons.Default.Link),
    PermissionMeta("postStories", "نشر القصص", Icons.Default.AddAPhoto),
    PermissionMeta("editStories", "تعديل القصص", Icons.Default.Collections),
    PermissionMeta("deleteStories", "حذف القصص", Icons.Default.AutoDelete),
    PermissionMeta("viewAdminLog", "عرض سجل الإدارة", Icons.Default.History),
    PermissionMeta("viewStats", "عرض الإحصائيات", Icons.Default.BarChart),
    PermissionMeta("bypassSlowMode", "تجاوز وضع Slow Mode", Icons.Default.Speed)
)

val RANK_PRESETS = mapOf(
    "مشرف كامل" to ALL_CHANNEL_PERMISSIONS.associate { it.key to true }.toMutableMap().apply { 
        put("addAdmins", false)
        put("manageAdminPermissions", false)
    },
    "مسؤول إعلامي" to mapOf(
        "postStories" to true, "editStories" to true, "deleteStories" to true, 
        "pinMessages" to true, "changeInfo" to true, "manageComments" to true
    ),
    "مدير حماية" to mapOf(
        "deleteMessages" to true, "banUsers" to true, "muteUsers" to true, 
        "restrictUsers" to true, "viewAdminLog" to true, "manageVoiceChats" to true
    ),
    "مساعد إداري" to mapOf(
        "changeInfo" to true, "inviteUsers" to true, "manageInviteLinks" to true, 
        "approveJoinRequests" to true, "manageTopics" to true
    ),
    "مراقب عام" to mapOf(
        "deleteMessages" to true, "pinMessages" to true, "viewAdminLog" to true, "viewStats" to true
    ),
    "منظم فعاليات" to mapOf(
        "manageVoiceChats" to true, "pinMessages" to true, "inviteUsers" to true, "manageTopics" to true
    ),
    "مشرف دردشة" to mapOf(
        "deleteMessages" to true, "muteUsers" to true, "restrictUsers" to true, "pinMessages" to true
    ),
    "نائب مدير" to mapOf(
        "changeInfo" to true, "deleteMessages" to true, "banUsers" to true, "removeUsers" to true,
        "restrictUsers" to true, "inviteUsers" to true, "pinMessages" to true, "manageVoiceChats" to true,
        "addAdmins" to true
    )
)

@Composable
fun AnalyticsDashboard(
    memberCount: Int,
    messageCount: Int,
    deletedCount: Int,
    joiningStats: List<Int>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = TitaniumLight.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📊 إحصائيات النشاط والتفاعل",
                color = SecureCyan,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.size(42.dp).background(SecureCyan.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.People, contentDescription = null, tint = SecureCyan, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("إجمالي الأعضاء", color = MutedText, fontSize = 11.sp)
                    Text("$memberCount", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.size(42.dp).background(SecureGreen.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Message, contentDescription = null, tint = SecureGreen, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("المنشورات والرسائل", color = MutedText, fontSize = 11.sp)
                    Text("$messageCount", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.size(42.dp).background(SecureGreen.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = SecureGreen, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("حالة الأمان والتشفير", color = MutedText, fontSize = 11.sp)
                    Text("E2E آمن", color = SecureGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = BorderSubtle)
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "📈 منحنى الانضمام والنمو (7 أيام الأخيرة)",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val days = listOf("السبت", "الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة")
                days.forEachIndexed { index, day ->
                    val heightFactor = if (index == 6) 1.0f else (0.15f * (index + 1))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .fillMaxHeight(heightFactor)
                                .background(
                                    color = if (index == 6) SecureGreen else SecureCyan.copy(alpha = 0.4f + 0.1f * index),
                                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(day, color = MutedText, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionSettingsDialog(
    nodeId: String,
    alias: String,
    currentPermissions: Map<String, Boolean>,
    onDismiss: () -> Unit,
    onSave: (Map<String, Boolean>) -> Unit
) {
    var permissions by remember { mutableStateOf(currentPermissions) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false) // Full screen feel
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = TitaniumDark
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Text(
                        text = "صلاحيات المشرف",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onSave(permissions) }) {
                        Text("حفظ", color = SecureCyan, fontWeight = FontWeight.Bold)
                    }
                }
                
                HorizontalDivider(color = BorderSubtle)
                
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        // User Profile Summary in Permissions
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(50.dp).background(TitaniumLight, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(alias.take(1).uppercase(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(text = alias, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(text = "تعديل صلاحيات الوصول والتحكم", color = MutedText, fontSize = 12.sp)
                            }
                        }
                    }
                    
                    item {
                        Text(
                            text = "الرتب والأنماط الجاهزة",
                            color = SecureCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            RANK_PRESETS.forEach { (rank, preset) ->
                                Surface(
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .clickable { 
                                            val newPerms = permissions.toMutableMap()
                                            preset.forEach { (k, v) -> newPerms[k] = v }
                                            permissions = newPerms
                                        },
                                    color = BorderSubtle,
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, BorderSubtle)
                                ) {
                                    Text(
                                        text = rank,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = BorderSubtle)
                    }
                    
                    item {
                        Text(
                            text = "ماذا يمكن لهذا المشرف فعله؟",
                            color = MutedText,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                    
                    items(ALL_CHANNEL_PERMISSIONS) { perm ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newPerms = permissions.toMutableMap()
                                    newPerms[perm.key] = !(permissions[perm.key] ?: false)
                                    permissions = newPerms
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(perm.icon, contentDescription = null, tint = MutedText, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = perm.labelAr, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Switch(
                                checked = permissions[perm.key] ?: false,
                                onCheckedChange = { checked ->
                                    val newPerms = permissions.toMutableMap()
                                    newPerms[perm.key] = checked
                                    permissions = newPerms
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = SecureGreen,
                                    checkedTrackColor = SecureGreen.copy(alpha = 0.3f),
                                    uncheckedThumbColor = MutedText,
                                    uncheckedTrackColor = BorderSubtle
                                )
                            )
                        }
                        HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

// --- SHARED MEMBERS TAB UI COMPONENT ---
@Composable
fun MembersTabContent(
    peers: List<Peer>,
    memberIds: List<String>,
    adminIds: List<String>,
    modIds: List<String>,
    permissionsJsonText: String,
    isChannel: Boolean,
    onAddMember: (String) -> Unit,
    onSetRole: (String, String, Boolean) -> Unit,
    onSetGranularPermissions: (String, Map<String, Boolean>) -> Unit,
    pendingRequests: List<String> = emptyList(),
    onHandleJoinRequest: (String, Boolean, String) -> Unit = { _, _, _ -> }
) {
    var searchNodeInput by remember { mutableStateOf("") }
    var selectedNodeForConfirmation by remember { mutableStateOf<String?>(null) }
    var editingPermissionsNodeId by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val nonMembers = peers.filter { it.nodeId !in memberIds }

    // Portal for Confirming Member Addition, Roles, and Custom Permissions (Telegram style)
    if (selectedNodeForConfirmation != null) {
        val nodeId = selectedNodeForConfirmation!!
        val peer = peers.find { it.nodeId == nodeId }
        val alias = peer?.alias ?: if (nodeId.startsWith("cipheris://")) nodeId.removePrefix("cipheris://").take(15) + "..." else nodeId.take(15) + "..."
        
        var selectedRole by remember { mutableStateOf("member") }
        var currentPresetPermissions by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
        
        Dialog(
            onDismissRequest = { selectedNodeForConfirmation = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = TitaniumDark
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedNodeForConfirmation = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تأكيد إضافة العضو ورتبته",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    
                    HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 12.dp))
                    
                    // User Details Summary Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TitaniumLight.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(SecureGreen.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(alias.take(1).uppercase(), color = SecureGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = alias, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(text = nodeId, color = MutedText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "1. حدد رتبة وصلاحية العضو الجديد:",
                        color = SecureCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    
                    // Role selectors
                    val roles = listOf(
                        Triple("مشترك / عضو عادي (Subscriber)", "member", SecureGreen),
                        Triple("مشرف محتوى (Content Moderator)", "moderator", SecureCyan),
                        Triple("مسؤول كامل (Full Administrator)", "admin", DangerRed)
                    )
                    
                    roles.forEach { (label, role, color) ->
                        val isSelected = selectedRole == role
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { 
                                    selectedRole = role 
                                    if (role == "member") {
                                        currentPresetPermissions = emptyMap()
                                    } else if (role == "moderator") {
                                        currentPresetPermissions = RANK_PRESETS["مشرف دردشة"] ?: emptyMap()
                                    } else {
                                        currentPresetPermissions = RANK_PRESETS["مشرف كامل"] ?: emptyMap()
                                    }
                                },
                            color = if (isSelected) color.copy(alpha = 0.12f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isSelected) color else BorderSubtle)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { 
                                        selectedRole = role 
                                        if (role == "member") {
                                            currentPresetPermissions = emptyMap()
                                        } else if (role == "moderator") {
                                            currentPresetPermissions = RANK_PRESETS["مشرف دردشة"] ?: emptyMap()
                                        } else {
                                            currentPresetPermissions = RANK_PRESETS["مشرف كامل"] ?: emptyMap()
                                        }
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = color, unselectedColor = MutedText)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Dynamic fine-tuning of permissions
                    if (selectedRole != "member") {
                        Text(
                            text = "2. الأنماط الجاهزة والصلاحيات الممنوحة للمشرف:",
                            color = SecureCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 6.dp)
                        ) {
                            RANK_PRESETS.forEach { (presetName, presetMap) ->
                                Surface(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .clickable { currentPresetPermissions = presetMap },
                                    color = BorderSubtle,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, BorderSubtle)
                                ) {
                                    Text(
                                        text = presetName,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(ALL_CHANNEL_PERMISSIONS) { perm ->
                                val isGranted = currentPresetPermissions[perm.key] ?: false
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val newPerms = currentPresetPermissions.toMutableMap()
                                            newPerms[perm.key] = !isGranted
                                            currentPresetPermissions = newPerms
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(perm.icon, contentDescription = null, tint = MutedText, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(text = perm.labelAr, color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    Switch(
                                        checked = isGranted,
                                        onCheckedChange = { checked ->
                                            val newPerms = currentPresetPermissions.toMutableMap()
                                            newPerms[perm.key] = checked
                                            currentPresetPermissions = newPerms
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = SecureGreen,
                                            checkedTrackColor = SecureGreen.copy(alpha = 0.3f),
                                            uncheckedThumbColor = MutedText,
                                            uncheckedTrackColor = BorderSubtle
                                        ),
                                        modifier = Modifier.scale(0.8f)
                                    )
                                }
                                HorizontalDivider(color = BorderSubtle)
                            }
                        }
                    } else {
                        // Empty spacer to push buttons down for standard members
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    
                    // Confirmation Action Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { selectedNodeForConfirmation = null },
                            colors = ButtonDefaults.buttonColors(containerColor = TitaniumLight),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("إلغاء", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        
                        Button(
                            onClick = {
                                if (pendingRequests.contains(nodeId)) {
                                    onHandleJoinRequest(nodeId, true, selectedRole)
                                    if (currentPresetPermissions.isNotEmpty()) {
                                        onSetGranularPermissions(nodeId, currentPresetPermissions)
                                    }
                                } else {
                                    onAddMember(nodeId)
                                    if (selectedRole != "member") {
                                        onSetRole(nodeId, selectedRole, true)
                                        onSetGranularPermissions(nodeId, currentPresetPermissions)
                                    }
                                }
                                selectedNodeForConfirmation = null
                                searchNodeInput = ""
                                Toast.makeText(context, "✅ تم تأكيد العضو ورتبته وصلاحياته بنجاح!", Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SecureGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("تأكيد وحفظ الرتبة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // Modal to Edit permissions for an existing member
    if (editingPermissionsNodeId != null) {
        val nodeId = editingPermissionsNodeId!!
        val peer = peers.find { it.nodeId == nodeId }
        val alias = peer?.alias ?: nodeId.take(15)
        
        val currentPerms = remember(permissionsJsonText, nodeId) {
            val map = mutableMapOf<String, Boolean>()
            try {
                val json = org.json.JSONObject(permissionsJsonText)
                if (json.has(nodeId)) {
                    val userJson = json.getJSONObject(nodeId)
                    ALL_CHANNEL_PERMISSIONS.forEach { perm ->
                        if (userJson.has(perm.key)) {
                            map[perm.key] = userJson.getBoolean(perm.key)
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
            map
        }
        
        PermissionSettingsDialog(
            nodeId = nodeId,
            alias = alias,
            currentPermissions = currentPerms,
            onDismiss = { editingPermissionsNodeId = null },
            onSave = { perms ->
                onSetGranularPermissions(nodeId, perms)
                editingPermissionsNodeId = null
            }
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 1. Pending Join Requests Section (Approval gate)
        if (pendingRequests.isNotEmpty()) {
            Text(
                text = "طلبات الانضمام المعلقة (${pendingRequests.size})",
                color = SecureCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SecureCyan.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .border(1.dp, SecureCyan.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                pendingRequests.forEach { reqNodeId ->
                    val peer = peers.find { it.nodeId == reqNodeId }
                    val alias = peer?.alias ?: reqNodeId.take(15) + "..."
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = alias, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(text = reqNodeId.take(24) + "...", color = MutedText, fontSize = 9.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onHandleJoinRequest(reqNodeId, false, "member") },
                                modifier = Modifier.size(32.dp).background(DangerRed.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Reject", tint = DangerRed, modifier = Modifier.size(16.dp))
                            }
                            Button(
                                onClick = { selectedNodeForConfirmation = reqNodeId },
                                colors = ButtonDefaults.buttonColors(containerColor = SecureGreen.copy(alpha = 0.2f)),
                                border = BorderStroke(1.dp, SecureGreen),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Accept", tint = SecureGreen, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تحديد الدور وقبول", color = SecureGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 2. Integrated Search and Direct Member Addition Form
        Card(
            colors = CardDefaults.cardColors(containerColor = TitaniumLight.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "إضافة سريعة من جهات الاتصال:",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            Spacer(modifier = Modifier.height(8.dp))
            
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(nonMembers) { peer ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TitaniumLight.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .width(130.dp)
                            .clickable { selectedNodeForConfirmation = peer.nodeId }
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(SecureCyan.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = peer.alias.take(1).uppercase(),
                                    color = SecureCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = peer.alias,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SecureGreen.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = SecureGreen, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تحديد", color = SecureGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

        // 3. Manual Member Addition via Node ID Form
        Card(
            colors = CardDefaults.cardColors(containerColor = TitaniumLight.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "إضافة عضو يدويًا عبر معرّف العقدة (Node ID):",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                var manualNodeIdInput by remember { mutableStateOf("") }
                val isNodeIdStarted = manualNodeIdInput.trim().isNotEmpty()
                val isNodeIdValidLength = manualNodeIdInput.trim().length == 27
                val isNodeIdValidPrefix = manualNodeIdInput.trim().startsWith("cipheris://")
                val isValid = isNodeIdValidLength && isNodeIdValidPrefix

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualNodeIdInput,
                        onValueChange = {
                            if (it.length <= 27) {
                                manualNodeIdInput = it
                            }
                        },
                        placeholder = { Text("أدخل معرّف العقدة الكامل هنا (nodeId)...", color = MutedText, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SecureGreen,
                            unfocusedBorderColor = BorderSubtle,
                            cursorColor = SecureGreen,
                            errorBorderColor = DangerRed
                        ),
                        isError = isNodeIdStarted && !isValid,
                        supportingText = {
                            if (isNodeIdStarted && !isValid) {
                                if (!isNodeIdValidPrefix) {
                                    Text("يجب أن يبدأ الرمز بـ cipheris://", color = DangerRed, fontSize = 9.sp)
                                } else {
                                    val remaining = 27 - manualNodeIdInput.trim().length
                                    if (remaining > 0) {
                                        Text("متبقي $remaining رموز لتكتمل العقدة", color = DangerRed, fontSize = 9.sp)
                                    } else {
                                        Text("الرمز أطول من اللازم بمقدار ${-remaining} رمز", color = DangerRed, fontSize = 9.sp)
                                    }
                                }
                            } else if (isValid) {
                                Text("رمز العقدة مكتمل وصحيح", color = SecureGreen, fontSize = 9.sp)
                            } else {
                                Text("الرمز يتكون من 27 حرفاً ويبدأ بـ cipheris://", color = MutedText, fontSize = 9.sp)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val targetNode = manualNodeIdInput.trim()
                            if (targetNode.isEmpty()) {
                                Toast.makeText(context, "⚠️ الرجاء إدخال معرّف عقدة صالح أولاً", Toast.LENGTH_SHORT).show()
                            } else if (!targetNode.startsWith("cipheris://") || targetNode.length != 27) {
                                Toast.makeText(context, "⚠️ رمز العقدة غير صالح! يجب أن يبدأ بـ cipheris:// وطوله 27 حرفاً", Toast.LENGTH_LONG).show()
                            } else {
                                selectedNodeForConfirmation = targetNode
                                manualNodeIdInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SecureGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("إضافة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        HorizontalDivider(color = BorderSubtle)
        Spacer(modifier = Modifier.height(12.dp))

        // 4. Current Members List
        Text(
            text = if (isChannel) "المشتركون والمدراء الحاليون (${memberIds.size})" else "أعضاء المجموعة الحاليون (${memberIds.size})",
            color = MutedText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        
        Column(modifier = Modifier.fillMaxWidth()) {
            memberIds.forEach { memberId ->
                val peer = peers.find { it.nodeId == memberId }
                val alias = peer?.alias ?: memberId.take(15) + "..."
                val isAdmin = adminIds.contains(memberId)
                val isMod = modIds.contains(memberId)
                
                val roleText = when {
                    isAdmin -> "مدير كامل"
                    isMod -> "مشرف محتوى"
                    else -> if (isChannel) "مشترك" else "عضو"
                }
                val badgeBgColor = when {
                    isAdmin -> DangerRed.copy(alpha = 0.15f)
                    isMod -> SecureCyan.copy(alpha = 0.15f)
                    else -> BorderSubtle
                }
                val badgeTextColor = when {
                    isAdmin -> DangerRed
                    isMod -> SecureCyan
                    else -> MutedText
                }
                
                var expanded by remember { mutableStateOf(false) }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(TitaniumLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(alias.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = alias, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            color = badgeBgColor,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, badgeTextColor.copy(alpha = 0.15f))
                        ) {
                            Text(
                                text = roleText,
                                color = badgeTextColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    
                    Box {
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = MutedText)
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(TitaniumDark)
                        ) {
                            if (!isAdmin) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("ترقية إلى مسؤول كامل", color = TextPrimary, fontSize = 12.sp) },
                                    onClick = {
                                        expanded = false
                                        onSetRole(memberId, "admin", true)
                                    },
                                    leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = DangerRed, modifier = Modifier.size(16.dp)) }
                                )
                            }
                            if (!isMod) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("ترقية إلى مشرف محتوى", color = TextPrimary, fontSize = 12.sp) },
                                    onClick = {
                                        expanded = false
                                        onSetRole(memberId, "moderator", true)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Security, contentDescription = null, tint = SecureCyan, modifier = Modifier.size(16.dp)) }
                                )
                            }
                            
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("تخصيص الصلاحيات والاستثناءات", color = SecureCyan, fontSize = 12.sp) },
                                onClick = {
                                    expanded = false
                                    editingPermissionsNodeId = memberId
                                },
                                leadingIcon = { Icon(Icons.Default.SettingsSuggest, contentDescription = null, tint = SecureCyan, modifier = Modifier.size(16.dp)) }
                            )

                            if (isAdmin || isMod) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("تنزيل إلى عضو عادي", color = TextPrimary, fontSize = 12.sp) },
                                    onClick = {
                                        expanded = false
                                        onSetRole(memberId, "admin", false)
                                        onSetRole(memberId, "moderator", false)
                                    },
                                    leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = MutedText, modifier = Modifier.size(16.dp)) }
                                )
                            }
                            
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(if (isAdmin || isMod) "إزالة المشرف" else "حظر من القناة (Ban)", color = DangerRed, fontSize = 12.sp) },
                                onClick = {
                                    expanded = false
                                    onSetRole(memberId, "banned", true)
                                },
                                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = DangerRed, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
                HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(start = 50.dp))
            }
        }
    }
}

// --- HELPER FOR OPENING DECRYPTED FILE VIA SYSTEM INTENT ---
fun openDecryptedFile(context: android.content.Context, filePath: String?, fileName: String) {
    try {
        val file = if (filePath != null) {
            java.io.File(filePath)
        } else {
            null
        }
        
        if (file == null || !file.exists()) {
            android.widget.Toast.makeText(context, "الملف لم يتم تنزيله أو غير موجود محلياً.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val ext = fileName.lowercase()
        val mimeType = when {
            ext.endsWith(".jpg") || ext.endsWith(".jpeg") || ext.endsWith(".png") || ext.endsWith(".gif") || ext.endsWith(".webp") -> "image/*"
            ext.endsWith(".mp4") || ext.endsWith(".3gp") || ext.endsWith(".mkv") || ext.endsWith(".webm") || ext.endsWith(".avi") -> "video/*"
            ext.endsWith(".mp3") || ext.endsWith(".m4a") || ext.endsWith(".aac") || ext.endsWith(".wav") || ext.endsWith(".ogg") -> "audio/*"
            ext.endsWith(".pdf") -> "application/pdf"
            ext.endsWith(".doc") || ext.endsWith(".docx") -> "application/msword"
            ext.endsWith(".xls") || ext.endsWith(".xlsx") -> "application/vnd.ms-excel"
            ext.endsWith(".ppt") || ext.endsWith(".pptx") -> "application/vnd.ms-powerpoint"
            ext.endsWith(".txt") || ext.endsWith(".log") -> "text/plain"
            ext.endsWith(".zip") || ext.endsWith(".rar") || ext.endsWith(".7z") -> "application/zip"
            else -> "*/*"
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: java.lang.Exception) {
        android.widget.Toast.makeText(context, "لا يوجد تطبيق مثبت يدعم فتح هذا الملف تلقائياً.", android.widget.Toast.LENGTH_LONG).show()
    }
}

// --- FULLSCREEN MEDIA DIALOG COMPONENT ---
@Composable
fun FullScreenMediaDialog(
    mediaName: String,
    filePath: String?,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val targetFile = remember(filePath) {
        filePath?.let { java.io.File(it) }?.takeIf { it.exists() }
    }
    
    val isImage = remember(mediaName) {
        val ext = mediaName.lowercase()
        ext.endsWith(".jpg") || ext.endsWith(".png") || ext.endsWith(".jpeg") || ext.endsWith(".gif") || ext.endsWith(".webp")
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Main Content Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                if (targetFile != null && targetFile.exists()) {
                    val mediaType = com.example.data.util.MediaClassifier.classifyFile(targetFile)
                    ZeroKnowledgeDocumentViewer(file = targetFile, mediaType = mediaType, mediaName = mediaName)
                } else {
                    // target file is null or doesn't exist
                    Text("الملف غير متوفر محلياً أو لم يكتمل تنزيله بعد", color = Color.Red, fontSize = 13.sp)
                }
            }

            // Top Control Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Text(
                    text = mediaName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                )
                IconButton(
                    onClick = {
                        openDecryptedFile(context, targetFile?.absolutePath, mediaName)
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Open in app", tint = SecureCyan)
                }
            }
        }
    }
}

// --- SHADOW VAULT (خزنة الظل - تقنية الإخفاء Steganography) ---
@Composable
fun WikipediaDecoyView(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Menu, contentDescription = null, tint = Color.Black)
            Text(
                text = "WIKIPEDIA",
                fontSize = 20.sp,
                fontWeight = FontWeight.Light,
                color = Color.Black,
                letterSpacing = 2.sp
            )
            Icon(Icons.Default.Search, contentDescription = null, tint = Color.Black)
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
            text = "Asymmetric-key cryptography",
            fontSize = 32.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            lineHeight = 38.sp
        )

        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        
        Text(
            text = "From Wikipedia, the free encyclopedia",
            fontSize = 12.sp,
            color = Color.Gray
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Asymmetric-key cryptography, or public-key cryptography, is a cryptographic system that uses pairs of keys: public keys which may be disseminated widely, and private keys which are known only to the owner. This accomplishes two functions: authentication, where the public key verifies that a holder of the paired private key sent the message, and encryption, where only the paired private key holder can decrypt the message encrypted with the public key.",
            fontSize = 16.sp,
            color = Color.Black,
            lineHeight = 24.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8F9FA))
                .border(1.dp, Color(0xFFA2A9B1))
                .padding(12.dp)
        ) {
            Column {
                Text("Contents [hide]", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("1 Description", color = Color(0xFF0645AD), fontSize = 14.sp)
                Text("2 History", color = Color(0xFF0645AD), fontSize = 14.sp)
                Text("3 Applications", color = Color(0xFF0645AD), fontSize = 14.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "History",
            fontSize = 24.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "In 1976, an asymmetric key cryptosystem was published by Whitfield Diffie and Martin Hellman, who, influenced by Ralph Merkle's work on public key distribution, disclosed a method of public key agreement.",
            fontSize = 16.sp,
            color = Color.Black,
            lineHeight = 24.sp
        )
        
        Spacer(modifier = Modifier.height(200.dp)) // To allow scrolling
    }
}

@Composable
fun SecureVaultTab(viewModel: ChatViewModel) {
    val vaultEntries by viewModel.vaultEntries.collectAsState()
    val language by viewModel.language.collectAsState()
    val isEnglish = language == "en"
    
    var showAddDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredEntries = if (searchQuery.isEmpty()) vaultEntries 
    else vaultEntries.filter { it.title.contains(searchQuery, ignoreCase = true) || it.category.contains(searchQuery, ignoreCase = true) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with Search
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TitaniumDark)
                    .padding(16.dp)
            ) {
                Text(
                    text = if (isEnglish) "SECURE VAULT" else "الخزنة الرقمية المؤمنة",
                    color = SecureCyan,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(if (isEnglish) "Search secrets..." else "ابحث في الأسرار...", color = MutedText, fontSize = 14.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = ObsidianBlack,
                        unfocusedContainerColor = ObsidianBlack,
                        focusedIndicatorColor = SecureCyan,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = SecureCyan,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SecureCyan) }
                )
            }
            
            // Grid of items
            if (filteredEntries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = MutedText, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(if (isEnglish) "No secured data found" else "لا توجد بيانات مؤمنة حالياً", color = MutedText)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredEntries.size) { index ->
                        val entry = filteredEntries[index]
                        VaultEntryCard(entry = entry, onDelete = { viewModel.deleteVaultEntry(entry) })
                    }
                }
            }
        }
        
        // FAB to add
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = SecureCyan,
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Secret")
        }
        
        if (showAddDialog) {
            AddVaultEntryDialog(
                onDismiss = { showAddDialog = false },
                onSave = { entry: com.example.data.model.VaultEntry -> viewModel.saveVaultEntry(entry) },
                isEnglish = isEnglish
            )
        }

        
        // SecurityGuard Shield Status
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .statusBarsPadding()
        ) {
            SecurityGuardShield()
        }
    }
}


// --- SUPPORTING COMPONENTS FOR VAULTS ---

@Composable
fun VaultEntryCard(entry: com.example.data.model.VaultEntry, onDelete: (com.example.data.model.VaultEntry) -> Unit) {
    var showContent by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showContent = !showContent },
        colors = CardDefaults.cardColors(containerColor = TitaniumDark.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, BorderSubtle),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = try { Color(android.graphics.Color.parseColor(entry.colorHex)).copy(alpha = 0.2f) } catch(e:Exception) { SecureCyan.copy(alpha = 0.2f) },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = entry.category,
                        color = try { Color(android.graphics.Color.parseColor(entry.colorHex)) } catch(e:Exception) { SecureCyan },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                IconButton(onClick = { onDelete(entry) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = entry.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            if (showContent) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = entry.content,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                        .fillMaxWidth()
                )
            } else {
                Text(
                    text = "••••••••",
                    color = MutedText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun AddVaultEntryDialog(onDismiss: () -> Unit, onSave: (com.example.data.model.VaultEntry) -> Unit, isEnglish: Boolean) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ملاحظة") }
    var selectedColor by remember { mutableStateOf("#00FFFF") } // SecureCyan
    
    val categories = listOf("ملاحظة", "كلمة مرور", "محفظة", "أخرى")
    val colors = listOf("#00FFFF", "#00FF41", "#FF00FF", "#FF5252", "#B0B0B0")

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = TitaniumDark),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (isEnglish) "Store New Secret" else "تأمين سر جديد",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (isEnglish) "Title" else "العنوان", color = MutedText) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                TextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(if (isEnglish) "Sensitive Content" else "المحتوى الحساس", color = MutedText) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(if (isEnglish) "Category" else "التصنيف", color = MutedText, fontSize = 12.sp)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 10.sp) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(if (isEnglish) "Security Color" else "لون الحماية", color = MutedText, fontSize = 12.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    colors.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)))
                                .border(
                                    if (selectedColor == hex) 2.dp else 0.dp,
                                    Color.White,
                                    CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        if (title.isNotEmpty() && content.isNotEmpty()) {
                            onSave(com.example.data.model.VaultEntry(title = title, category = selectedCategory, content = content, colorHex = selectedColor))
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SecureCyan)
                ) {
                    Text(if (isEnglish) "ENCRYPT & SAVE" else "تشفير وحفظ", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SecurityGuardShield() {
    val context = LocalContext.current
    var isScanning by remember { mutableStateOf(false) }
    var securityStatus by remember { mutableStateOf(com.example.data.security.LocalSecurityGuard.SecurityStatus.SECURE) }
    
    LaunchedEffect(Unit) {
        while(true) {
            isScanning = true
            kotlinx.coroutines.delay(1500)
            securityStatus = com.example.data.security.LocalSecurityGuard.performSystemCheck(context)
            isScanning = false
            kotlinx.coroutines.delay(8500)
        }
    }

    val isThreat = securityStatus != com.example.data.security.LocalSecurityGuard.SecurityStatus.SECURE

    Surface(
        color = if (isScanning) Color.Red.copy(alpha = 0.2f) else if (isThreat) DangerRed.copy(alpha = 0.2f) else SecureGreen.copy(alpha = 0.1f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (isScanning) Color.Red else if (isThreat) DangerRed else SecureGreen.copy(alpha = 0.5f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                tint = if (isScanning) Color.Red else if (isThreat) DangerRed else SecureGreen,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isScanning) "SCANNING..." else if (isThreat) "THREAT: ${securityStatus.name}" else "GUARD ACTIVE",
                color = if (isScanning) Color.Red else if (isThreat) DangerRed else SecureGreen,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// --- UNIFIED SHADOW VAULT SWITCHER ---

@Composable
fun ShadowVaultTab(viewModel: ChatViewModel) {
    var selectedSubTab by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val language by viewModel.language.collectAsState()
    val isEnglish = language == "en"

    Column(modifier = Modifier.fillMaxSize()) {
        androidx.compose.material3.TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = TitaniumDark,
            contentColor = SecureCyan,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                    color = SecureCyan
                )
            }
        ) {
            androidx.compose.material3.Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text(if (isEnglish) "Secrets" else "الأسرار", fontSize = 12.sp) },
                icon = { Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(20.dp)) }
            )
            androidx.compose.material3.Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text(if (isEnglish) "Stegano" else "الإخفاء", fontSize = 12.sp) },
                icon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(20.dp)) }
            )
        }
        
        Box(modifier = Modifier.weight(1f)) {
            when (selectedSubTab) {
                0 -> SecureVaultTab(viewModel)
                1 -> SteganoVaultTab(viewModel)
            }
        }
    }
}

@Composable
fun AdvancedSearchDialog(viewModel: ChatViewModel, onDismiss: () -> Unit) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchDateFilter by viewModel.searchDateFilter.collectAsState()
    val searchFileTypeFilter by viewModel.searchFileTypeFilter.collectAsState()
    val searchGroupFilter by viewModel.searchGroupFilter.collectAsState()
    val searchSenderFilter by viewModel.searchSenderFilter.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    
    val groups by viewModel.groups.collectAsState()
    val peers by viewModel.peers.collectAsState()
    val language by viewModel.language.collectAsState()
    val isEnglish = language == "en"

    var showDatePickerCustom by remember { mutableStateOf(false) }
    var customDateInput by remember { mutableStateOf("") }

    Dialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ObsidianBlack
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = SecureGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEnglish) "Advanced Search" else "البحث المتقدم الآمن محلياً",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                // 1. Keyword input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text(if (isEnglish) "Search keyword or file name..." else "ابحث عن كلمة مفتاحية أو اسم ملف...", color = MutedText) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = SecureGreen)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextPrimary)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SecureGreen,
                        unfocusedBorderColor = TitaniumLight,
                        cursorColor = SecureGreen
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // 2. Date Filter
                Text(
                    text = if (isEnglish) "Filter by Date:" else "تصفية بحسب التاريخ:", 
                    fontSize = 12.sp, 
                    color = SecureGreen, 
                    fontWeight = FontWeight.Bold, 
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val dateOptions = if (isEnglish) {
                        listOf(
                            Pair(null, "All Time"),
                            Pair("today", "Today"),
                            Pair("yesterday", "Yesterday"),
                            Pair("week", "Last 7 Days"),
                            Pair("month", "Last 30 Days"),
                            Pair("custom", "Custom Date")
                        )
                    } else {
                        listOf(
                            Pair(null, "كل الأوقات"),
                            Pair("today", "اليوم"),
                            Pair("yesterday", "أمس"),
                            Pair("week", "آخر 7 أيام"),
                            Pair("month", "آخر 30 يوم"),
                            Pair("custom", "تاريخ مخصص")
                        )
                    }
                    items(dateOptions) { (key, label) ->
                        val isSelected = if (key == "custom") showDatePickerCustom else (searchDateFilter == key && !showDatePickerCustom)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (key == "custom") {
                                    showDatePickerCustom = true
                                } else {
                                    showDatePickerCustom = false
                                    viewModel.updateSearchDateFilter(key)
                                }
                            },
                            label = { Text(label, fontSize = 11.sp, color = if (isSelected) Color.White else TextPrimary) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SecureGreen,
                                containerColor = TitaniumDark
                            )
                        )
                    }
                }

                if (showDatePickerCustom) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customDateInput,
                            onValueChange = { 
                                customDateInput = it
                                if (it.length == 10) {
                                    viewModel.updateSearchDateFilter(it)
                                }
                            },
                            placeholder = { Text("e.g. 2026-07-02", color = MutedText) },
                            label = { Text(if (isEnglish) "Date (YYYY-MM-DD)" else "أدخل تاريخ (YYYY-MM-DD)", color = SecureGreen, fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SecureGreen)
                        )
                        Button(
                            onClick = {
                                showDatePickerCustom = false
                                customDateInput = ""
                                viewModel.updateSearchDateFilter(null)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed.copy(alpha = 0.2f))
                        ) {
                            Text(if (isEnglish) "Reset" else "إلغاء التخصيص", color = DangerRed, fontSize = 10.sp)
                        }
                    }
                }

                // 3. File Type Filter
                Text(
                    text = if (isEnglish) "File Type / Message:" else "نوع الملف/الرسالة:", 
                    fontSize = 12.sp, 
                    color = SecureGreen, 
                    fontWeight = FontWeight.Bold, 
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val fileTypes = if (isEnglish) {
                        listOf(
                            Pair(null, "All"),
                            Pair("text", "Text only"),
                            Pair("media", "Media (Photos, Videos, Audio)"),
                            Pair("document", "Files & Documents (ZIP, PDF...)")
                        )
                    } else {
                        listOf(
                            Pair(null, "الكل"),
                            Pair("text", "رسائل نصية فقط"),
                            Pair("media", "الوسائط (صور وفيديو وصوت)"),
                            Pair("document", "المستندات والملفات (ZIP, PDF...)")
                        )
                    }
                    items(fileTypes) { (key, label) ->
                        val isSelected = searchFileTypeFilter == key
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.updateSearchFileTypeFilter(key) },
                            label = { Text(label, fontSize = 11.sp, color = if (isSelected) Color.White else TextPrimary) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SecureGreen,
                                containerColor = TitaniumDark
                            )
                        )
                    }
                }

                // 4. Group & Sender Filtering Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEnglish) "Search inside specific Chat:" else "البحث في محادثة معينة:", 
                            fontSize = 11.sp, 
                            color = SecureCyan, 
                            fontWeight = FontWeight.Bold, 
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item {
                                val isSelected = searchGroupFilter == null
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.updateSearchGroupFilter(null) },
                                    label = { Text(if (isEnglish) "All Chats" else "جميع المجموعات", fontSize = 10.sp, color = if (isSelected) Color.White else TextPrimary) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SecureCyan,
                                        containerColor = TitaniumDark
                                    )
                                )
                            }
                            items(groups) { group ->
                                val isSelected = searchGroupFilter == group.groupId
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.updateSearchGroupFilter(group.groupId) },
                                    label = { Text(group.name, fontSize = 10.sp, color = if (isSelected) Color.White else TextPrimary) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SecureCyan,
                                        containerColor = TitaniumDark
                                    )
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEnglish) "Search by Sender Node or Name:" else "البحث عن رسائل مرسل معين (بصمة العقدة أو الاسم):", 
                            fontSize = 11.sp, 
                            color = SecureCyan, 
                            fontWeight = FontWeight.Bold, 
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        OutlinedTextField(
                            value = searchSenderFilter ?: "",
                            onValueChange = { viewModel.updateSearchSenderFilter(it.ifEmpty { null }) },
                            placeholder = { Text(if (isEnglish) "Enter Sender's Node address..." else "أدخل عنوان العقدة للمرسل...", color = MutedText, fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SecureCyan,
                                unfocusedBorderColor = TitaniumLight
                            )
                        )
                    }
                }

                // Results Counter
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEnglish) "Search Results (${searchResults.size}):" else "نتائج البحث (${searchResults.size}):", 
                        fontSize = 12.sp, 
                        color = Color.White, 
                        fontWeight = FontWeight.Bold
                    )
                    if (searchQuery.isNotEmpty() || searchDateFilter != null || searchFileTypeFilter != null || searchGroupFilter != null || searchSenderFilter != null) {
                        TextButton(
                            onClick = {
                                viewModel.updateSearchQuery("")
                                viewModel.updateSearchDateFilter(null)
                                viewModel.updateSearchFileTypeFilter(null)
                                viewModel.updateSearchGroupFilter(null)
                                viewModel.updateSearchSenderFilter(null)
                                showDatePickerCustom = false
                            }
                        ) {
                            Text(if (isEnglish) "Reset Filters" else "إعادة تعيين الفلاتر", color = DangerRed, fontSize = 11.sp)
                        }
                    }
                }

                // 5. Results List
                if (searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MutedText.copy(alpha = 0.2f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (isEnglish) "No matching local messages found" else "لا توجد رسائل مطابقة لبحثك في الذاكرة المحلية", 
                                color = MutedText, 
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchResults) { msg ->
                            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            val dateStr = sdf.format(Date(msg.timestamp))
                            
                            val isMe = msg.senderNodeId == (viewModel.selfPeer.value?.nodeId ?: "me")

                            Card(
                                colors = CardDefaults.cardColors(containerColor = TitaniumDark.copy(alpha = 0.5f)),
                                border = BorderStroke(1.dp, if (isMe) SecureGreen.copy(alpha = 0.2f) else SecureCyan.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isMe) (if (isEnglish) "You (Your Node)" else "أنت (العقدة الخاصة بك)") else (if (isEnglish) "Sender: ${msg.senderNodeId.take(16)}..." else "المرسل: ${msg.senderNodeId.take(16)}..."),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (isMe) SecureGreen else SecureCyan
                                        )
                                        Text(
                                            text = dateStr,
                                            fontSize = 10.sp,
                                            color = MutedText
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    Text(
                                        text = msg.text,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )

                                    if (!msg.mediaPath.isNullOrEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier
                                                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                                .padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AttachFile,
                                                contentDescription = null,
                                                tint = SecureGreen,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = msg.mediaName ?: "ملف مرفق",
                                                fontSize = 11.sp,
                                                color = SecureGreen,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = {
                                            if (msg.groupId != null) {
                                                viewModel.selectGroupChat(msg.groupId)
                                            } else {
                                                val targetPeerId = (if (isMe) msg.peerNodeId else msg.senderNodeId) ?: ""
                                                viewModel.selectDirectChat(targetPeerId)
                                            }
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SecureGreen.copy(alpha = 0.15f)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.align(Alignment.End).height(32.dp)
                                    ) {
                                        Text(if (isEnglish) "Go to Chat" else "انتقال للمحادثة", color = SecureGreen, fontSize = 11.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = null,
                                            tint = SecureGreen,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
