package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*
import com.example.ui.viewmodel.ChatViewModel
import com.example.data.util.SteganographyHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun SteganoVaultTab(viewModel: ChatViewModel) {
    val language by viewModel.language.collectAsState()
    val isEnglish = language == "en"
    var secretText by remember { mutableStateOf("") }
    var revealedText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val hideLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            isProcessing = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (bitmap != null) {
                        val hiddenBitmap = SteganographyHelper.embedText(bitmap, secretText)
                        if (hiddenBitmap != null) {
                            // Save to cache dir so user can share/view it, or just to downloads
                            val outputDir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "Cipheris")
                            if (!outputDir.exists()) outputDir.mkdirs()
                            
                            val outputFile = File(outputDir, "stegano_${System.currentTimeMillis()}.png")
                            FileOutputStream(outputFile).use { out ->
                                hiddenBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }
                            
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, if (isEnglish) "Image saved with hidden data (PNG)" else "تم حفظ الصورة مع البيانات المخفية بنجاح (PNG)", Toast.LENGTH_LONG).show()
                                secretText = ""
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, if (isEnglish) "Image too small or text too long" else "الصورة صغيرة جداً أو النص طويل جداً", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    val revealLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            isProcessing = true
            revealedText = ""
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (bitmap != null) {
                        val extracted = SteganographyHelper.extractText(bitmap)
                        withContext(Dispatchers.Main) {
                            if (extracted != null) {
                                revealedText = extracted
                            } else {
                                Toast.makeText(context, if (isEnglish) "No hidden data found in this image" else "لا توجد بيانات سرية في هذه الصورة", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(TitaniumDark)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_shadow_vault_hero_1782972704693),
                    contentDescription = "Shadow Vault Hero",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.85f
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = if (isEnglish) "Shadow Vault" else "خزنة الظل",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (isEnglish) "Steganography & Plausible Deniability" else "أدوات الإخفاء الرقمي والتنكر المتقدم",
                        color = SecureGreenDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        item {
            Text(
                text = if (isEnglish) "Embed Secret Data" else "تشفير وإخفاء بيانات داخل صورة",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = if (isEnglish) "Hide text messages inside any image file. The image will look normal to everyone else." else "قم بإخفاء رسائل نصية داخل أي ملف صورة. ستظهر الصورة بشكل طبيعي لأي شخص آخر.",
                        color = MutedText,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = secretText,
                        onValueChange = { secretText = it },
                        placeholder = { Text(if (isEnglish) "Enter secret message..." else "أدخل الرسالة السرية...", color = MutedText) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SecureCyan,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { 
                            if (secretText.isNotEmpty()) {
                                hideLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            } else {
                                Toast.makeText(context, if (isEnglish) "Enter text first" else "أدخل النص أولاً", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SecureCyan),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isEnglish) "Select Image & Embed" else "اختر صورة وقم بالإخفاء", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text(
                text = if (isEnglish) "Extract Hidden Data" else "استخراج البيانات المخفية",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Button(
                        onClick = { 
                            revealLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SecureGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isEnglish) "Scan Image for Secrets" else "افحص صورة عن رسائل مخفية", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    if (revealedText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(if (isEnglish) "Found Message:" else "الرسالة المستخرجة:", color = SecureGreen, fontSize = 12.sp)
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(text = revealedText, color = TextPrimary, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
    
    if (isProcessing) {
        AlertDialog(
            onDismissRequest = { },
            containerColor = TitaniumDark,
            title = { Text(if (isEnglish) "Processing..." else "جاري المعالجة...", color = TextPrimary) },
            text = { CircularProgressIndicator(color = SecureCyan) },
            confirmButton = {}
        )
    }
}
