package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.media.MediaPlayer
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.widget.VideoView
import android.widget.MediaController
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.SecureCyan
import com.example.ui.theme.SecureGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.TitaniumDark
import com.example.data.util.MediaClassifier
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ArchiveEntryInfo(val name: String, val isDirectory: Boolean, val size: Long)

@Composable
fun ZeroKnowledgeDocumentViewer(
    file: File,
    mediaType: MediaClassifier.MediaType,
    mediaName: String
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeFile by remember(file) { mutableStateOf(file) }
    var activeMediaType by remember(mediaType, activeFile) { mutableStateOf(mediaType) }
    var activeMediaName by remember(mediaName, activeFile) { mutableStateOf(mediaName) }

    // Sandbox Explorer States
    var currentVirtualPath by remember { mutableStateOf("") }
    var explorerSearchQuery by remember { mutableStateOf("") }
    var explorerFilesList by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isScanningExplorer by remember { mutableStateOf(false) }

    LaunchedEffect(context, currentVirtualPath, activeFile) {
        isScanningExplorer = true
        withContext(Dispatchers.IO) {
            val list = listSandboxFilesForPath(context, currentVirtualPath)
            explorerFilesList = list
            isScanningExplorer = false
        }
    }

    val filteredExplorerFiles = remember(explorerFilesList, explorerSearchQuery) {
        if (explorerSearchQuery.trim().isEmpty()) {
            explorerFilesList
        } else {
            explorerFilesList.filter { it.name.contains(explorerSearchQuery, ignoreCase = true) }
        }
    }

    // Determine if it is a SQLite Database based on header magic bytes
    var isSqlite by remember(activeFile) {
        val initialIsSqlite = activeFile.name.lowercase().let { it.endsWith(".db") || it.endsWith(".sqlite") }
        mutableStateOf(initialIsSqlite)
    }

    // View mode navigation tabs
    val tabs = remember(activeMediaType, isSqlite) {
        val list = mutableListOf<String>()
        when {
            isSqlite -> {
                list.add("database")
                list.add("hex")
                list.add("strings")
            }
            activeMediaType == MediaClassifier.MediaType.IMAGE -> {
                list.add("image")
                list.add("hex")
                list.add("strings")
            }
            activeMediaType == MediaClassifier.MediaType.AUDIO -> {
                list.add("audio")
                list.add("hex")
                list.add("strings")
            }
            activeMediaType == MediaClassifier.MediaType.VIDEO -> {
                list.add("video")
                list.add("hex")
                list.add("strings")
            }
            activeMediaType == MediaClassifier.MediaType.PDF -> {
                list.add("pdf")
                list.add("hex")
                list.add("strings")
            }
            activeMediaType == MediaClassifier.MediaType.ARCHIVE ||
            activeMediaType == MediaClassifier.MediaType.EPUB ||
            activeMediaType == MediaClassifier.MediaType.MS_OFFICE -> {
                list.add("archive")
                list.add("text")
                list.add("hex")
                list.add("strings")
            }
            else -> {
                list.add("text")
                list.add("hex")
                list.add("strings")
            }
        }
        list.add("explorer")
        list
    }

    var viewMode by remember { mutableStateOf("") }
    
    // Auto-initialize default view mode based on tabs list
    LaunchedEffect(tabs) {
        if (tabs.isNotEmpty() && !tabs.contains(viewMode)) {
            viewMode = tabs[0]
        }
    }

    // Shared States
    var content by remember { mutableStateOf<String?>(null) }
    var archiveEntries by remember { mutableStateOf<List<ArchiveEntryInfo>>(emptyList()) }
    var currentZipPath by remember { mutableStateOf("") }
    var viewingZipEntryName by remember { mutableStateOf<String?>(null) }
    
    // Recursive Child Archive Viewer state
    var zipChildFile by remember { mutableStateOf<File?>(null) }
    var zipChildMediaType by remember { mutableStateOf<MediaClassifier.MediaType?>(null) }
    var zipChildName by remember { mutableStateOf<String?>(null) }

    // Hex Viewer States
    var hexPage by remember { mutableStateOf(0) }
    val hexPageSize = 512
    var hexTotalPages by remember { mutableStateOf(1) }
    var hexBytesSelected by remember { mutableStateOf<ByteArray>(ByteArray(0)) }
    
    // Printable Strings State
    var printableStrings by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
    var isScanningStrings by remember { mutableStateOf(false) }

    // Text search States
    var searchText by remember { mutableStateOf("") }
    var searchMatches by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentMatchIndex by remember { mutableStateOf(0) }

    // File info
    var fileSize by remember { mutableStateOf(0L) }
    var sha256Hash by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Function to load specific Hex page
    fun loadHexPage(targetFile: File, page: Int) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val start = page.toLong() * hexPageSize
                val length = minOf(hexPageSize.toLong(), targetFile.length() - start).toInt()
                if (length > 0) {
                    targetFile.inputStream().use { stream ->
                        stream.skip(start)
                        val bytes = ByteArray(length)
                        stream.read(bytes)
                        hexBytesSelected = bytes
                    }
                } else {
                    hexBytesSelected = ByteArray(0)
                }
                hexPage = page
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Extract text from child file inside ZIP
    fun openZipEntry(entryName: String) {
        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                ZipFile(activeFile).use { zip ->
                    val entry = zip.getEntry(entryName)
                    if (entry != null && !entry.isDirectory) {
                        val cacheDir = File(context.cacheDir, "zip_temp")
                        cacheDir.mkdirs()
                        val suffix = File(entryName).extension.let { if (it.isEmpty()) "bin" else it }
                        val prefix = File(entryName).nameWithoutExtension.let { if (it.length < 3) "zip_entry" else it }
                        val tempFile = File.createTempFile(prefix, ".$suffix", cacheDir)
                        
                        zip.getInputStream(entry).use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        
                        val detectedType = MediaClassifier.classifyFile(tempFile)
                        withContext(Dispatchers.Main) {
                            zipChildFile = tempFile
                            zipChildMediaType = detectedType
                            zipChildName = entryName
                        }
                    }
                }
            } catch (e: Exception) {
                error = "خطأ أثناء قراءة ملف الأرشيف الفرعي: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // Initial loading and hashing
    LaunchedEffect(activeFile) {
        isLoading = true
        content = null
        archiveEntries = emptyList()
        currentZipPath = ""
        viewingZipEntryName = null
        printableStrings = emptyList()
        
        withContext(Dispatchers.IO) {
            try {
                if (!activeFile.exists()) {
                    error = "الملف غير موجود محلياً"
                    return@withContext
                }

                fileSize = activeFile.length()
                
                // Recalculate true activeMediaType and isSqlite on Dispatchers.IO
                val detectedType = com.example.data.util.MediaClassifier.classifyFile(activeFile)
                activeMediaType = detectedType
                
                isSqlite = try {
                    if (activeFile.exists() && activeFile.length() >= 16) {
                        val bytes = ByteArray(16)
                        activeFile.inputStream().use { it.read(bytes) }
                        val header = String(bytes, 0, 15, Charsets.US_ASCII)
                        header == "SQLite format 3"
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    false
                }

                // Calculate SHA-256 for secure identification using a streaming buffer
                try {
                    val digest256 = MessageDigest.getInstance("SHA-256")
                    activeFile.inputStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead = input.read(buffer)
                        while (bytesRead != -1) {
                            digest256.update(buffer, 0, bytesRead)
                            bytesRead = input.read(buffer)
                        }
                    }
                    sha256Hash = digest256.digest().joinToString("") { "%02x".format(it) }
                } catch (e: Exception) {
                    sha256Hash = "N/A"
                }

                // Initial scan for office/epub/archive structure
                if (activeMediaType == MediaClassifier.MediaType.ARCHIVE ||
                    activeMediaType == MediaClassifier.MediaType.EPUB ||
                    activeMediaType == MediaClassifier.MediaType.MS_OFFICE) {
                    try {
                        val entries = mutableListOf<ArchiveEntryInfo>()
                        ZipFile(activeFile).use { zip ->
                            val enumeration = zip.entries()
                            while (enumeration.hasMoreElements()) {
                                val entry = enumeration.nextElement()
                                entries.add(ArchiveEntryInfo(entry.name, entry.isDirectory, entry.size))
                            }
                        }
                        archiveEntries = entries
                    } catch (e: Exception) {
                        // Not a zip file (e.g. legacy binary doc, xls)
                        archiveEntries = emptyList()
                    }
                }

                // Initial Text Extraction
                when {
                    activeMediaType == MediaClassifier.MediaType.TEXT ||
                    activeMediaType == MediaClassifier.MediaType.CODE ||
                    activeMediaType == MediaClassifier.MediaType.DATA -> {
                        val maxLength = 2 * 1024 * 1024
                        content = if (activeFile.length() > maxLength) {
                            activeFile.inputStream().reader().use { reader ->
                                val chars = CharArray(maxLength)
                                val read = reader.read(chars)
                                String(chars, 0, read) + "\n\n... [تم اقتطاع جزء من الملف نظراً لحجمه الكبير]"
                            }
                        } else {
                            activeFile.readText()
                        }
                    }
                    activeMediaName.lowercase().endsWith(".docx") -> {
                        content = extractDocxText(activeFile)
                    }
                    activeMediaName.lowercase().endsWith(".xlsx") -> {
                        content = extractXlsxText(activeFile)
                    }
                }

                // Real parsing bypass: Extract printable strings or decode as UTF-8 for any file format
                if (content == null) {
                    try {
                        val maxFallbackBytes = 1024 * 1024 // 1MB limit for safety
                        val bytes = if (activeFile.length() > maxFallbackBytes) {
                            activeFile.inputStream().use { stream ->
                                val buf = ByteArray(maxFallbackBytes)
                                val read = stream.read(buf)
                                if (read < maxFallbackBytes) buf.copyOf(read) else buf
                            }
                        } else {
                            activeFile.readBytes()
                        }
                        
                        if (bytes.all { it.toInt() in 0..127 || it.toInt() in -128..-1 || it.toChar() == '\n' || it.toChar() == '\r' || it.toChar() == '\t' }) {
                            content = String(bytes, Charsets.UTF_8).take(100000)
                        } else {
                            val printableList = scanPrintableStrings(activeFile)
                            content = if (printableList.isNotEmpty()) {
                                "--- تم تحليل بنية الملف ثنائياً واستخراج النصوص منه بنجاح ---\n\n" +
                                        printableList.joinToString("\n") { it.second }
                            } else {
                                "[ملف ثنائي خالص لا يحتوي على نصوص مباشرة. يرجى استخدام تبويب الستة عشر (Hex) للاستعراض بدقة]"
                            }
                        }
                    } catch (e: Exception) {
                        content = "[حدث خطأ أثناء تحميل محتويات الملف: ${e.message}]"
                    }
                }

                // Setup hex viewer
                hexTotalPages = maxOf(1, ((fileSize + hexPageSize - 1) / hexPageSize).toInt())
                loadHexPage(activeFile, 0)

                error = null
            } catch (e: Exception) {
                error = "خطأ في قراءة وتحليل بنية الملف: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // Lazy scan of strings when requested
    LaunchedEffect(viewMode) {
        if (viewMode == "strings" && printableStrings.isEmpty()) {
            isScanningStrings = true
            withContext(Dispatchers.IO) {
                printableStrings = scanPrintableStrings(activeFile)
                isScanningStrings = false
            }
        }
    }

    // Local Search updates
    LaunchedEffect(searchText, content) {
        val searchSource = content ?: ""
        if (searchText.length >= 2 && searchSource.isNotEmpty()) {
            val matches = mutableListOf<Int>()
            var idx = searchSource.indexOf(searchText, ignoreCase = true)
            while (idx != -1) {
                matches.add(idx)
                idx = searchSource.indexOf(searchText, idx + searchText.length, ignoreCase = true)
            }
            searchMatches = matches
            currentMatchIndex = 0
        } else {
            searchMatches = emptyList()
            currentMatchIndex = 0
        }
    }

    // Annotated search text representation
    val annotatedText = remember(content, searchText, searchMatches, currentMatchIndex) {
        val raw = content ?: ""
        androidx.compose.ui.text.buildAnnotatedString {
            append(raw)
            if (searchText.length >= 2 && searchMatches.isNotEmpty()) {
                searchMatches.forEachIndexed { idx, start ->
                    val end = start + searchText.length
                    val isCurrent = idx == currentMatchIndex
                    addStyle(
                        style = androidx.compose.ui.text.SpanStyle(
                            background = if (isCurrent) SecureGreen.copy(alpha = 0.5f) else Color.Yellow.copy(alpha = 0.3f),
                            color = if (isCurrent) Color.Black else Color.White,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        ),
                        start = start,
                        end = end
                    )
                }
            }
        }
    }

    // Layout
    Column(modifier = Modifier.fillMaxSize()) {
        // High-tech view mode tab row selector
        if (tabs.size > 1) {
            TabRow(
                selectedTabIndex = tabs.indexOf(viewMode).let { if (it == -1) 0 else it },
                containerColor = Color.White,
                contentColor = SecureGreen,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEach { tab ->
                    val label = when (tab) {
                        "text" -> "المستند النصي"
                        "archive" -> "ملفات الحزمة (ZIP)"
                        "hex" -> "الستة عشر (Hex)"
                        "strings" -> "مستخرج النصوص"
                        "database" -> "قاعدة البيانات"
                        "image" -> "معاينة الصورة"
                        "audio" -> "مشغل الصوت"
                        "video" -> "مشغل الفيديو"
                        "pdf" -> "قارئ PDF"
                        "explorer" -> "مستكشف الملفات 📁"
                        else -> tab
                    }
                    Tab(
                        selected = (viewMode == tab),
                        onClick = { viewMode = tab },
                        text = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF070B0E))
                .padding(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = SecureCyan)
            } else if (error != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(error!!, color = Color.Red, textAlign = TextAlign.Center, fontSize = 13.sp)
                }
            } else {
                when (viewMode) {
                    "explorer" -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                "مستكشف ملفات ساندبوكس البيئة المعزولة (Real Files Sandbox Browser)",
                                color = SecureGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            // Search field for file names
                            OutlinedTextField(
                                value = explorerSearchQuery,
                                onValueChange = { explorerSearchQuery = it },
                                placeholder = { Text("ابحث عن ملف بالاسم داخل الساندبوكس...", color = Color.Gray, fontSize = 11.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SecureGreen,
                                    unfocusedBorderColor = BorderSubtle,
                                    cursorColor = SecureGreen,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp)) }
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Path Breadcrumb Navigation Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BorderSubtle, RoundedCornerShape(6.dp))
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                                    .padding(vertical = 6.dp, horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Storage, contentDescription = null, tint = SecureCyan, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "مسار الساندبوكس الحالي: " + if (currentVirtualPath.isEmpty()) "/" else "/$currentVirtualPath",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Files List Column
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(TitaniumLightColor, RoundedCornerShape(10.dp))
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                                    .padding(8.dp)
                            ) {
                                if (filteredExplorerFiles.isEmpty() && currentVirtualPath.isEmpty()) {
                                    Text("الساندبوكس فارغ حالياً.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center))
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        // "Go back" link if not in root
                                        if (currentVirtualPath.isNotEmpty()) {
                                            item {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            currentVirtualPath = if (currentVirtualPath.contains("/")) {
                                                                currentVirtualPath.substringBeforeLast("/")
                                                            } else {
                                                                ""
                                                            }
                                                        }
                                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = SecureGreen, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(".. (الرجوع للمجلد الأعلى)", color = SecureGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                                HorizontalDivider(color = BorderSubtle)
                                            }
                                        }
                                        
                                        items(filteredExplorerFiles) { item ->
                                            val ext = item.name.lowercase()
                                            val icon = when {
                                                item.isDirectory -> Icons.Default.Folder
                                                ext.endsWith(".jpg") || ext.endsWith(".png") || ext.endsWith(".jpeg") -> Icons.Default.Image
                                                ext.endsWith(".mp4") || ext.endsWith(".mkv") -> Icons.Default.VideoLibrary
                                                ext.endsWith(".mp3") || ext.endsWith(".wav") || ext.contains("صوت_مشفر") -> Icons.Default.Mic
                                                ext.endsWith(".pdf") -> Icons.Default.PictureAsPdf
                                                ext.endsWith(".db") || ext.endsWith(".sqlite") -> Icons.Default.Storage
                                                else -> Icons.Default.InsertDriveFile
                                            }
                                            
                                            val tint = when {
                                                item.isDirectory -> SecureGreen
                                                item.absolutePath == activeFile.absolutePath -> SecureCyan // Highlight current viewed file
                                                else -> Color.White.copy(alpha = 0.7f)
                                            }
                                            
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        if (item.isDirectory) {
                                                            currentVirtualPath = if (currentVirtualPath.isEmpty()) item.name else "$currentVirtualPath/${item.name}"
                                                        } else {
                                                            // Load this file in viewer immediately!
                                                            activeFile = java.io.File(item.absolutePath)
                                                            // Auto-reclassified in LaunchedEffect on background thread
                                                            activeMediaName = item.name
                                                            android.widget.Toast.makeText(context, "تم تحميل ملف الساندبوكس: ${item.name}", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                    .background(
                                                        if (item.absolutePath == activeFile.absolutePath) BorderSubtle else Color.Transparent,
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .padding(vertical = 10.dp, horizontal = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = item.name,
                                                            color = if (item.absolutePath == activeFile.absolutePath) SecureCyan else TextPrimary,
                                                            fontSize = 12.sp,
                                                            fontWeight = if (item.absolutePath == activeFile.absolutePath) FontWeight.Bold else FontWeight.Normal,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        if (item.absolutePath == activeFile.absolutePath) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text("(مفتوح حالياً)", color = SecureCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                    if (!item.isDirectory) {
                                                        Text(
                                                            text = "الحجم: ${formatBytesSize(item.size)} • مسار آمن",
                                                            color = Color.Gray,
                                                            fontSize = 9.sp
                                                        )
                                                    }
                                                }
                                                
                                                if (!item.isDirectory) {
                                                    Icon(
                                                        imageVector = Icons.Default.Visibility,
                                                        contentDescription = "View",
                                                        tint = SecureGreen.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.ChevronRight,
                                                        contentDescription = "Open directory",
                                                        tint = Color.Gray,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            HorizontalDivider(color = BorderSubtle)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "image" -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            FileMetadataHeader(activeMediaName, fileSize, sha256Hash, activeFile)
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(TitaniumLightColor, RoundedCornerShape(12.dp))
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                coil.compose.AsyncImage(
                                    model = activeFile,
                                    contentDescription = "Decrypted View",
                                    modifier = Modifier.fillMaxSize().padding(8.dp),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            }
                        }
                    }

                    "audio" -> {
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                            FileMetadataHeader(activeMediaName, fileSize, sha256Hash, activeFile)
                            Spacer(modifier = Modifier.height(20.dp))
                            NativeAudioPlayer(activeFile)
                        }
                    }

                    "video" -> {
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                            FileMetadataHeader(activeMediaName, fileSize, sha256Hash, activeFile)
                            Spacer(modifier = Modifier.height(20.dp))
                            NativeVideoPlayer(activeFile)
                        }
                    }

                    "pdf" -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            FileMetadataHeader(activeMediaName, fileSize, sha256Hash, activeFile)
                            Spacer(modifier = Modifier.height(10.dp))
                            NativePdfViewer(activeFile)
                        }
                    }

                    "database" -> {
                        SQLiteDatabaseExplorer(activeFile)
                    }

                    "text" -> {
                        val displayText = content ?: "[لا توجد بيانات نصية قابلة للقراءة في هذا الملف]"
                        Column(modifier = Modifier.fillMaxSize()) {
                            FileMetadataHeader(activeMediaName, fileSize, sha256Hash, activeFile)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Interactive text search input
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = searchText,
                                    onValueChange = { searchText = it },
                                    placeholder = { Text("ابحث في نصوص المستند...", color = Color.Gray, fontSize = 11.sp) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = SecureGreen,
                                        unfocusedBorderColor = BorderSubtle,
                                        cursorColor = SecureGreen,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                                )
                                if (searchMatches.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "${currentMatchIndex + 1}/${searchMatches.size}",
                                        color = SecureCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = {
                                            if (searchMatches.isNotEmpty()) {
                                                currentMatchIndex = (currentMatchIndex - 1 + searchMatches.size) % searchMatches.size
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Prev", tint = SecureGreen, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            if (searchMatches.isNotEmpty()) {
                                                currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Next", tint = SecureGreen, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            
                            val scrollState = rememberScrollState()
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(TitaniumLightColor, RoundedCornerShape(8.dp))
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                // Draw Line Numbers
                                val linesCount = remember(displayText) { displayText.split('\n').size }
                                Column(
                                    modifier = Modifier
                                        .verticalScroll(scrollState)
                                        .padding(end = 8.dp)
                                        .width(30.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    for (i in 1..linesCount) {
                                        Text(
                                            text = "$i",
                                            color = Color.DarkGray,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .verticalScroll(scrollState)
                                ) {
                                    Text(
                                        text = annotatedText,
                                        color = Color.LightGray,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    "archive" -> {
                        if (archiveEntries.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("هذا الملف ليس حزمة أرشيف صالحة ولا يحتوي على ملفات فرعية قابلة للمعاينة كـ ZIP.", color = Color.Gray, textAlign = TextAlign.Center, fontSize = 12.sp)
                            }
                        } else {
                            // Directory viewer
                            val displayEntries = remember(archiveEntries, currentZipPath) {
                                val pathLen = currentZipPath.length
                                val directChildren = archiveEntries.filter {
                                    it.name.startsWith(currentZipPath) && it.name != currentZipPath
                                }.map { entry ->
                                    val relative = entry.name.substring(pathLen)
                                    val slashIdx = relative.indexOf('/')
                                    if (slashIdx == -1 || slashIdx == relative.length - 1) {
                                        entry
                                    } else {
                                        ArchiveEntryInfo(
                                            name = currentZipPath + relative.substring(0, slashIdx + 1),
                                            isDirectory = true,
                                            size = 0
                                        )
                                    }
                                }.distinctBy { it.name }.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
                                
                                if (currentZipPath.isNotEmpty()) {
                                    val parentPath = currentZipPath.dropLast(1).substringBeforeLast('/', "")
                                    val parentPrefix = if (parentPath.isEmpty()) "" else "$parentPath/"
                                    listOf(ArchiveEntryInfo(parentPrefix, true, -1L)) + directChildren
                                } else {
                                    directChildren
                                }
                            }

                            Column(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    "مسار الأرشيف الحالي: ${if (currentZipPath.isEmpty()) "/" else "/$currentZipPath"}", 
                                    color = SecureGreen, 
                                    fontWeight = FontWeight.Bold, 
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(displayEntries) { entry ->
                                        val isParentLink = entry.size == -1L
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (isParentLink) {
                                                        currentZipPath = entry.name
                                                    } else if (entry.isDirectory) {
                                                        currentZipPath = entry.name
                                                    } else {
                                                        openZipEntry(entry.name)
                                                    }
                                                }
                                                .padding(vertical = 8.dp, horizontal = 4.dp), 
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile, 
                                                contentDescription = null, 
                                                tint = if (entry.isDirectory) SecureGreen else SecureCyan, 
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            val displayName = if (isParentLink) ".." else entry.name.removePrefix(currentZipPath)
                                            Column {
                                                Text(displayName, color = TextPrimary, fontSize = 13.sp)
                                                if (!entry.isDirectory && entry.size >= 0) {
                                                    Text("${formatBytesSize(entry.size)}", color = Color.Gray, fontSize = 9.sp)
                                                }
                                            }
                                        }
                                        HorizontalDivider(color = BorderSubtle)
                                    }
                                }
                            }
                        }
                    }

                    "hex" -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            FileMetadataHeader(activeMediaName, fileSize, sha256Hash, activeFile)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Hex Grid View
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(TitaniumLightColor, RoundedCornerShape(8.dp))
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    item {
                                        Row(modifier = Modifier.fillMaxWidth().background(BorderSubtle).padding(vertical = 4.dp)) {
                                            Text("العنوان (Offset)", color = SecureGreen, fontFamily = FontFamily.Monospace, fontSize = 9.sp, modifier = Modifier.width(75.dp))
                                            Text("القيم السداسية (Hex Values)", color = SecureCyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp, modifier = Modifier.weight(1f))
                                            Text("ASCII", color = Color.Yellow, fontFamily = FontFamily.Monospace, fontSize = 9.sp, modifier = Modifier.width(90.dp))
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                    
                                    val rowCount = (hexBytesSelected.size + 15) / 16
                                    items(rowCount) { rowIndex ->
                                        val rowOffset = hexPage * hexPageSize + rowIndex * 16
                                        val rowBytes = hexBytesSelected.sliceArray(
                                            (rowIndex * 16) until minOf(hexBytesSelected.size, (rowIndex + 1) * 16)
                                        )
                                        
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                            // Offset
                                            Text(
                                                text = "0x%08X".format(rowOffset),
                                                color = SecureGreen.copy(alpha = 0.7f),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                modifier = Modifier.width(75.dp)
                                            )
                                            
                                            // Hex values
                                            val hexStringBuilder = StringBuilder()
                                            for (i in 0 until 16) {
                                                if (i < rowBytes.size) {
                                                    hexStringBuilder.append("%02X ".format(rowBytes[i]))
                                                } else {
                                                    hexStringBuilder.append("   ")
                                                }
                                            }
                                            Text(
                                                text = hexStringBuilder.toString(),
                                                color = Color.LightGray,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            
                                            // ASCII
                                            val asciiStringBuilder = StringBuilder()
                                            for (i in 0 until rowBytes.size) {
                                                val b = rowBytes[i]
                                                if (b in 32..126) {
                                                    asciiStringBuilder.append(b.toChar())
                                                } else {
                                                    asciiStringBuilder.append(".")
                                                }
                                            }
                                            Text(
                                                text = asciiStringBuilder.toString(),
                                                color = Color.Yellow.copy(alpha = 0.8f),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                modifier = Modifier.width(90.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Hex pagination controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        if (hexPage > 0) {
                                            loadHexPage(activeFile, hexPage - 1)
                                        }
                                    },
                                    enabled = hexPage > 0,
                                    colors = ButtonDefaults.buttonColors(containerColor = TitaniumLightColor)
                                ) {
                                    Text("الصفحة السابقة", fontSize = 10.sp)
                                }
                                
                                Text(
                                    "صفحة ${hexPage + 1} من $hexTotalPages (موقع: ${hexPage * hexPageSize} - ${minOf(fileSize, (hexPage + 1).toLong() * hexPageSize)} بايت)",
                                    color = MutedTextColor,
                                    fontSize = 10.sp
                                )
                                
                                Button(
                                    onClick = {
                                        if (hexPage < hexTotalPages - 1) {
                                            loadHexPage(activeFile, hexPage + 1)
                                        }
                                    },
                                    enabled = hexPage < hexTotalPages - 1,
                                    colors = ButtonDefaults.buttonColors(containerColor = TitaniumLightColor)
                                ) {
                                    Text("الصفحة التالية", fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    "strings" -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            FileMetadataHeader(activeMediaName, fileSize, sha256Hash, activeFile)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "تحليل السلاسل النصية المقروءة (Unix-style Strings Scanner):",
                                color = SecureGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(TitaniumLightColor, RoundedCornerShape(8.dp))
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                if (isScanningStrings) {
                                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = SecureCyan)
                                } else if (printableStrings.isEmpty()) {
                                    Text(
                                        "لم يتم العثور على أي سلاسل مقروءة بوضوح في هذا الملف.",
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(printableStrings) { (offset, str) ->
                                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                Text(
                                                    text = "0x%08X:".format(offset),
                                                    color = SecureCyan.copy(alpha = 0.7f),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.width(90.dp)
                                                )
                                                Text(
                                                    text = str,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            HorizontalDivider(color = BorderSubtle)
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

    // Full Screen Overlay to play/browse recursive children extracted from ZIP
    if (zipChildFile != null && zipChildMediaType != null && zipChildName != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {
                try {
                    zipChildFile?.delete()
                } catch (e: Exception) {}
                zipChildFile = null
                zipChildMediaType = null
                zipChildName = null
            },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BorderSubtle)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = SecureCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = zipChildName!!,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                try {
                                    zipChildFile?.delete()
                                } catch (e: Exception) {}
                                zipChildFile = null
                                zipChildMediaType = null
                                zipChildName = null
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                        }
                    }
                    
                    // Display ZeroKnowledgeViewer recursively for zip extraction
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        ZeroKnowledgeDocumentViewer(
                            file = zipChildFile!!,
                            mediaType = zipChildMediaType!!,
                            mediaName = zipChildName!!
                        )
                    }
                }
            }
        }
    }
}

fun saveFileToDownloads(context: android.content.Context, srcFile: File, displayName: String): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, when (displayName.substringAfterLast('.', "").lowercase()) {
                    "apk" -> "application/vnd.android.package-archive"
                    "pdf" -> "application/pdf"
                    "zip" -> "application/zip"
                    "rar" -> "application/x-rar-compressed"
                    "7z" -> "application/x-7z-compressed"
                    "txt" -> "text/plain"
                    "png" -> "image/png"
                    "jpg", "jpeg" -> "image/jpeg"
                    "gif" -> "image/gif"
                    "mp3" -> "audio/mpeg"
                    "wav" -> "audio/wav"
                    "mp4" -> "video/mp4"
                    else -> "application/octet-stream"
                })
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Cipheris")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(srcFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                true
            } else {
                false
            }
        } else {
            val targetDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Cipheris")
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val targetFile = File(targetDir, displayName)
            FileInputStream(srcFile).use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

@Composable
fun FileMetadataHeader(name: String, size: Long, hash: String, file: File? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = TitaniumLightColor),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("المستند: $name", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                Text("الحجم: ${formatBytesSize(size)}", color = SecureCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "بصمة SHA-256: $hash", 
                color = MutedTextColor, 
                fontSize = 8.sp, 
                fontFamily = FontFamily.Monospace,
                lineHeight = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (file != null && file.exists()) {
                Spacer(modifier = Modifier.height(8.dp))
                val ext = name.lowercase()
                val isApk = ext.endsWith(".apk")
                
                Button(
                    onClick = {
                        try {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "com.aistudio.cipheris.pqnxwr.fileprovider",
                                file
                            )
                            val mimeType = if (isApk) {
                                "application/vnd.android.package-archive"
                            } else {
                                when (ext.substringAfterLast('.', "")) {
                                    "pdf" -> "application/pdf"
                                    "zip" -> "application/zip"
                                    "rar" -> "application/x-rar-compressed"
                                    "7z" -> "application/x-7z-compressed"
                                    "txt" -> "text/plain"
                                    "png" -> "image/png"
                                    "jpg", "jpeg" -> "image/jpeg"
                                    "gif" -> "image/gif"
                                    "mp3" -> "audio/mpeg"
                                    "wav" -> "audio/wav"
                                    "mp4" -> "video/mp4"
                                    else -> "*/*"
                                }
                            }

                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, mimeType)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "لا توجد تطبيقات قادرة على فتح هذا الملف: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SecureGreen),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    val icon = if (isApk) Icons.Default.Launch else Icons.Default.OpenInNew
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isApk) "تثبيت التطبيق (APK) 🚀" else "فتح بواسطة تطبيق خارجي 🔓",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        val success = saveFileToDownloads(context, file, name)
                        if (success) {
                            android.widget.Toast.makeText(context, "📥 تم حفظ وتحميل الملف بنجاح في مجلد Downloads/Cipheris", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            android.widget.Toast.makeText(context, "❌ فشل تحميل وحفظ الملف في الجهاز", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SecureCyan),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "تحميل وحفظ الملف بالجهاز الفعلي 📥",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

fun formatBytesSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1] + ""
    return "%.2f %sB".format(bytes / Math.pow(1024.0, exp.toDouble()), pre)
}

// Custom fast ASCII / Plain text streams scanner for PDFs
fun extractPdfTextStream(pdfFile: File): String {
    return try {
        val bytes = pdfFile.readBytes()
        val sb = StringBuilder()
        var index = 0
        
        sb.append("--- مستند PDF مشفر ومحلل محلياً بنجاح ---\n\n")
        
        while (index < bytes.size - 2) {
            // PDF text is held between parentheses inside content streams '(...)'
            if (bytes[index] == '('.toByte()) {
                val start = index + 1
                var end = start
                while (end < bytes.size && bytes[end] != ')'.toByte()) {
                    if (bytes[end] == '\n'.toByte() || bytes[end] == '\r'.toByte()) {
                        break
                    }
                    end++
                }
                if (end < bytes.size && bytes[end] == ')'.toByte()) {
                    val rawStr = String(bytes, start, end - start, Charsets.UTF_8)
                    val cleanStr = rawStr.filter { 
                        it.isLetterOrDigit() || it.isWhitespace() || ".,-:_=()[]{}<>!?/\\#@+*%".contains(it) 
                    }
                    if (cleanStr.trim().isNotEmpty() && cleanStr.length > 2) {
                        sb.append(cleanStr).append(" ")
                    }
                    index = end
                }
            }
            index++
        }
        val text = sb.toString().trim()
        if (text.length > 50) {
            text + "\n\n--- نهاية النصوص المستخرجة بنجاح ---"
        } else {
            "--- معلومات مستند PDF ---\nحجم الملف: ${formatBytesSize(pdfFile.length())}\n\n[لم يتم العثور على مسارات نصوص واضحة في ملف PDF هذا. يرجى التبديل لتبويب الستة عشر (Hex) في الأعلى لتصفح بنية مستند PDF الثنائية وقراءته بالكامل]"
        }
    } catch (e: Exception) {
        "فشل استخراج نصوص PDF: ${e.message}"
    }
}

// Extract word DOCX content natively
fun extractDocxText(docxFile: File): String {
    return try {
        ZipFile(docxFile).use { zip ->
            val entry = zip.getEntry("word/document.xml")
            if (entry != null) {
                val rawXml = zip.getInputStream(entry).reader().readText()
                val sb = StringBuilder()
                var pos = 0
                while (true) {
                    val startTag = rawXml.indexOf("<w:t>", pos)
                    if (startTag == -1) {
                        val startTagAttr = rawXml.indexOf("<w:t ", pos)
                        if (startTagAttr == -1) break
                        val endTagAttrClose = rawXml.indexOf(">", startTagAttr)
                        if (endTagAttrClose == -1) break
                        val endTag = rawXml.indexOf("</w:t>", endTagAttrClose)
                        if (endTag == -1) break
                        sb.append(rawXml.substring(endTagAttrClose + 1, endTag))
                        pos = endTag + 6
                    } else {
                        val endTag = rawXml.indexOf("</w:t>", startTag)
                        if (endTag == -1) break
                        sb.append(rawXml.substring(startTag + 5, endTag))
                        pos = endTag + 6
                    }
                    val pClose = rawXml.indexOf("</w:p>", pos - 6)
                    if (pClose != -1 && pClose < (rawXml.indexOf("<w:p", pos).let { if (it == -1) Int.MAX_VALUE else it })) {
                        sb.append("\n")
                    }
                }
                val text = sb.toString().trim()
                if (text.isNotEmpty()) text else "[مستند Word فارغ]"
            } else {
                "[لم يتم العثور على نصوص مستند Word صالحة]"
            }
        }
    } catch (e: Exception) {
        "فشل تحليل مستند Word: ${e.message}"
    }
}

// Extract Excel XLSX content natively
fun extractXlsxText(xlsxFile: File): String {
    return try {
        ZipFile(xlsxFile).use { zip ->
            val entry = zip.getEntry("xl/sharedStrings.xml")
            if (entry != null) {
                val rawXml = zip.getInputStream(entry).reader().readText()
                val sb = StringBuilder()
                var pos = 0
                sb.append("--- نصوص وخلايا جدول البيانات المستخرجة ---\n\n")
                while (true) {
                    val startTag = rawXml.indexOf("<t>", pos)
                    if (startTag == -1) {
                        val startTagAttr = rawXml.indexOf("<t ", pos)
                        if (startTagAttr == -1) break
                        val endTagAttrClose = rawXml.indexOf(">", startTagAttr)
                        if (endTagAttrClose == -1) break
                        val endTag = rawXml.indexOf("</t>", endTagAttrClose)
                        if (endTag == -1) break
                        sb.append(rawXml.substring(endTagAttrClose + 1, endTag)).append("\n")
                        pos = endTag + 4
                    } else {
                        val endTag = rawXml.indexOf("</t>", startTag)
                        if (endTag == -1) break
                        sb.append(rawXml.substring(startTag + 3, endTag)).append("\n")
                        pos = endTag + 4
                    }
                }
                val text = sb.toString().trim()
                if (text.isNotEmpty()) text else "[ملف Excel فارغ]"
            } else {
                "[لم يتم العثور على خلايا نصوص مشتركة في جدول البيانات]"
            }
        }
    } catch (e: Exception) {
        "فشل تحليل جدول بيانات Excel: ${e.message}"
    }
}

// Printable Strings Scanner
fun scanPrintableStrings(file: File): List<Pair<Long, String>> {
    val results = mutableListOf<Pair<Long, String>>()
    try {
        val maxScanBytes = 1024 * 1024 // 1MB scan limit to prevent ANR/OOM
        val bytes = if (file.length() > maxScanBytes) {
            file.inputStream().use { stream ->
                val buf = ByteArray(maxScanBytes)
                val read = stream.read(buf)
                if (read < maxScanBytes) buf.copyOf(read) else buf
            }
        } else {
            file.readBytes()
        }
        var i = 0
        val buffer = StringBuilder()
        var startOffset = 0L
        while (i < bytes.size) {
            val b = bytes[i]
            // Standard printable ASCII (32..126) + Tabs + Arabic UTF-8 range support
            val uByte = b.toInt() and 0xFF
            val isPrintable = (uByte in 32..126) || (uByte == 9) || (uByte == 10) || (uByte == 13) || (uByte in 0xD8..0xDF) || (uByte in 0x80..0xBF)
            
            if (isPrintable) {
                if (buffer.isEmpty()) {
                    startOffset = i.toLong()
                }
                buffer.append(b.toChar())
            } else {
                if (buffer.length >= 4) {
                    val str = buffer.toString().trim()
                    val isReadable = str.any { it.isLetterOrDigit() }
                    if (isReadable && str.length >= 4) {
                        // Clean non-printable characters before adding
                        val clean = str.filter { it.code in 32..126 || it.code in 0x0600..0x06FF || it.isWhitespace() }
                        if (clean.length >= 4) {
                            results.add(Pair(startOffset, clean))
                        }
                    }
                }
                buffer.clear()
            }
            i++
            if (results.size > 500) break
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return results
}

// Native PDF single page renderer
@Composable
fun NativePdfViewer(file: File) {
    var pageCount by remember { mutableStateOf(0) }
    var currentPage by remember { mutableStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file, currentPage) {
        withContext(Dispatchers.IO) {
            var renderer: PdfRenderer? = null
            var parcelFd: ParcelFileDescriptor? = null
            try {
                parcelFd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(parcelFd)
                pageCount = renderer.pageCount
                
                if (currentPage >= pageCount) {
                    currentPage = 0
                }
                
                if (pageCount > 0) {
                    val page = renderer.openPage(currentPage)
                    // Scale bitmap up for high crisp quality
                    val width = page.width * 2
                    val height = page.height * 2
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    withContext(Dispatchers.Main) {
                        pageBitmap = bitmap
                    }
                }
            } catch (e: Exception) {
                errorMsg = e.message
            } finally {
                try {
                    renderer?.close()
                } catch (ex: Exception) {}
                try {
                    parcelFd?.close()
                } catch (ex: Exception) {}
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (errorMsg != null) {
            Text("خطأ في قراءة ملف PDF: $errorMsg", color = Color.Red, fontSize = 12.sp)
        } else if (pageBitmap != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (currentPage > 0) currentPage-- },
                    enabled = currentPage > 0
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "السابق", tint = if (currentPage > 0) SecureGreen else Color.Gray)
                }
                
                Text(
                    text = "الصفحة ${currentPage + 1} من $pageCount",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(
                    onClick = { if (currentPage < pageCount - 1) currentPage++ },
                    enabled = currentPage < pageCount - 1
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "التالي", tint = if (currentPage < pageCount - 1) SecureGreen else Color.Gray)
                }
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    bitmap = pageBitmap!!.asImageBitmap(),
                    contentDescription = "PDF Page",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SecureGreen)
            }
        }
    }
}

// Native Audio Player
@Composable
fun NativeAudioPlayer(file: File) {
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }
    
    DisposableEffect(file) {
        val player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            duration = this.duration
        }
        mediaPlayer = player
        
        onDispose {
            try {
                player.stop()
            } catch (e: Exception) {}
            player.release()
        }
    }
    
    // Position polling
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                mediaPlayer?.let {
                    currentPosition = it.currentPosition
                }
                kotlinx.coroutines.delay(250)
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TitaniumLightColor, RoundedCornerShape(16.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("مشغل الصوت الآمن داخل البيئة المعزولة", color = SecureGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        // Animated bars
        Row(
            modifier = Modifier
                .height(40.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val barCount = 15
            for (i in 0 until barCount) {
                var targetHeight by remember { mutableStateOf(10.dp) }
                LaunchedEffect(isPlaying) {
                    if (isPlaying) {
                        while (isPlaying) {
                            targetHeight = (10..40).random().dp
                            kotlinx.coroutines.delay((100..200).random().toLong())
                        }
                    } else {
                        targetHeight = 10.dp
                    }
                }
                
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(targetHeight)
                        .background(SecureGreen, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Slider(
            value = currentPosition.toFloat(),
            onValueChange = { newValue ->
                mediaPlayer?.seekTo(newValue.toInt())
                currentPosition = newValue.toInt()
            },
            valueRange = 0f..maxOf(1f, duration.toFloat()),
            colors = SliderDefaults.colors(
                thumbColor = SecureCyan,
                activeTrackColor = SecureCyan,
                inactiveTrackColor = Color.DarkGray
            ),
            modifier = Modifier.fillMaxWidth()
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(currentPosition), color = Color.Gray, fontSize = 10.sp)
            Text(formatTime(duration), color = Color.Gray, fontSize = 10.sp)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(
                onClick = {
                    mediaPlayer?.let {
                        val newPos = maxOf(0, it.currentPosition - 5000)
                        it.seekTo(newPos)
                        currentPosition = newPos
                    }
                }
            ) {
                Icon(Icons.Default.FastRewind, contentDescription = "رجوع 5 ثواني", tint = TextPrimary)
            }
            
            IconButton(
                onClick = {
                    mediaPlayer?.let {
                        if (isPlaying) {
                            it.pause()
                            isPlaying = false
                        } else {
                            it.start()
                            isPlaying = true
                        }
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(SecureGreen, CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                    tint = Color.White
                )
            }
            
            IconButton(
                onClick = {
                    mediaPlayer?.let {
                        val newPos = minOf(duration, it.currentPosition + 5000)
                        it.seekTo(newPos)
                        currentPosition = newPos
                    }
                }
            ) {
                Icon(Icons.Default.FastForward, contentDescription = "تقدم 5 ثواني", tint = TextPrimary)
            }
        }
    }
}

// Native Video Player using standard VideoView
@Composable
fun NativeVideoPlayer(file: File) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(TitaniumLightColor, RoundedCornerShape(16.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    setVideoURI(Uri.fromFile(file))
                    val controller = MediaController(context)
                    controller.setAnchorView(this)
                    setMediaController(controller)
                    start()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// SQLite Database Explorer UI
@Composable
fun SQLiteDatabaseExplorer(file: File) {
    var tables by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedTable by remember { mutableStateOf<String?>(null) }
    var columns by remember { mutableStateOf<List<String>>(emptyList()) }
    var rows by remember { mutableStateOf<List<List<String>>>(emptyList()) }
    var dbError by remember { mutableStateOf<String?>(null) }
    val queryLimit = 50
    var queryOffset by remember { mutableStateOf(0) }
    var totalRowsInTable by remember { mutableStateOf(0) }
    var showTableDropdown by remember { mutableStateOf(false) }

    // Load tables schema
    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    val list = mutableListOf<String>()
                    val cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_%' AND name NOT LIKE 'sqlite_%'", null)
                    if (cursor.moveToFirst()) {
                        do {
                            list.add(cursor.getString(0))
                        } while (cursor.moveToNext())
                    }
                    cursor.close()
                    tables = list
                    if (list.isNotEmpty()) {
                        selectedTable = list[0]
                    }
                }
            } catch (e: Exception) {
                dbError = e.message
            }
        }
    }

    // Load content paginated
    LaunchedEffect(file, selectedTable, queryOffset) {
        selectedTable?.let { tableName ->
            withContext(Dispatchers.IO) {
                try {
                    SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                        val colList = mutableListOf<String>()
                        val pragmaCursor = db.rawQuery("PRAGMA table_info(\"$tableName\")", null)
                        if (pragmaCursor.moveToFirst()) {
                            do {
                                colList.add(pragmaCursor.getString(1))
                            } while (pragmaCursor.moveToNext())
                        }
                        pragmaCursor.close()
                        columns = colList

                        val countCursor = db.rawQuery("SELECT COUNT(*) FROM \"$tableName\"", null)
                        if (countCursor.moveToFirst()) {
                            totalRowsInTable = countCursor.getInt(0)
                        }
                        countCursor.close()

                        val rowList = mutableListOf<List<String>>()
                        val dataCursor = db.rawQuery("SELECT * FROM \"$tableName\" LIMIT $queryLimit OFFSET $queryOffset", null)
                        if (dataCursor.moveToFirst()) {
                            do {
                                val singleRow = mutableListOf<String>()
                                for (i in 0 until dataCursor.columnCount) {
                                    val valStr = try {
                                        when (dataCursor.getType(i)) {
                                            android.database.Cursor.FIELD_TYPE_NULL -> "NULL"
                                            android.database.Cursor.FIELD_TYPE_INTEGER -> dataCursor.getLong(i).toString()
                                            android.database.Cursor.FIELD_TYPE_FLOAT -> dataCursor.getDouble(i).toString()
                                            android.database.Cursor.FIELD_TYPE_STRING -> dataCursor.getString(i)
                                            android.database.Cursor.FIELD_TYPE_BLOB -> "[BLOB - ${dataCursor.getBlob(i).size} B]"
                                            else -> "N/A"
                                        }
                                    } catch (ex: Exception) {
                                        "Error"
                                    }
                                    singleRow.add(valStr)
                                }
                                rowList.add(singleRow)
                            } while (dataCursor.moveToNext())
                        }
                        dataCursor.close()
                        rows = rowList
                    }
                } catch (e: Exception) {
                    dbError = e.message
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (dbError != null) {
            Text("خطأ في قراءة قاعدة البيانات: $dbError", color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
        } else if (tables.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا توجد جداول في قاعدة البيانات هذه.", color = Color.Gray, fontSize = 12.sp)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("تصفح جدول قاعدة البيانات:", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                
                Box {
                    Button(
                        onClick = { showTableDropdown = true },
                        colors = ButtonDefaults.buttonColors(containerColor = TitaniumLightColor)
                    ) {
                        Text(selectedTable ?: "اختر جدول", color = SecureGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = SecureGreen, modifier = Modifier.size(16.dp))
                    }
                    
                    DropdownMenu(
                        expanded = showTableDropdown,
                        onDismissRequest = { showTableDropdown = false },
                        modifier = Modifier.background(Color(0xFF0D1117))
                    ) {
                        tables.forEach { tableName ->
                            DropdownMenuItem(
                                text = { Text(tableName, color = TextPrimary, fontSize = 11.sp) },
                                onClick = {
                                    selectedTable = tableName
                                    queryOffset = 0
                                    showTableDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(TitaniumLightColor, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
            ) {
                if (rows.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("الجدول الحالي فارغ.", color = Color.Gray, fontSize = 11.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BorderSubtle)
                                    .padding(vertical = 6.dp)
                            ) {
                                items(columns) { colName ->
                                    Box(
                                        modifier = Modifier
                                            .width(140.dp)
                                            .padding(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = colName,
                                            color = SecureCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = BorderSubtle)
                        }

                        items(rows) { singleRow ->
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                items(singleRow) { cellVal ->
                                    Box(
                                        modifier = Modifier
                                            .width(140.dp)
                                            .padding(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = cellVal,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            maxLines = 2,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = BorderSubtle)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { if (queryOffset >= queryLimit) queryOffset -= queryLimit },
                    enabled = queryOffset >= queryLimit,
                    colors = ButtonDefaults.buttonColors(containerColor = TitaniumLightColor)
                ) {
                    Text("السابق", fontSize = 10.sp)
                }

                Text(
                    text = "سجلات ${queryOffset + 1} - ${minOf(totalRowsInTable, queryOffset + queryLimit)} من أصل $totalRowsInTable",
                    color = Color.Gray,
                    fontSize = 10.sp
                )

                Button(
                    onClick = { if (queryOffset + queryLimit < totalRowsInTable) queryOffset += queryLimit },
                    enabled = queryOffset + queryLimit < totalRowsInTable,
                    colors = ButtonDefaults.buttonColors(containerColor = TitaniumLightColor)
                ) {
                    Text("التالي", fontSize = 10.sp)
                }
            }
        }
    }
}

val TitaniumLightColor = Color(0xFFEEF2F6)
val MutedTextColor = Color(0xFF64748B)
val ObsidianBlack = Color(0xFFF6F8FA)

fun formatTime(ms: Int): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return "%d:%02d".format(mins, secs)
}

data class FileItem(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val absolutePath: String
)

fun listSandboxFilesForPath(context: android.content.Context, currentVirtualPath: String): List<FileItem> {
    val results = mutableListOf<FileItem>()
    val filesDir = context.filesDir
    val cacheDir = context.cacheDir

    if (currentVirtualPath.isEmpty()) {
        if (filesDir != null && filesDir.exists()) {
            results.add(FileItem(name = "files", isDirectory = true, size = 0L, absolutePath = filesDir.absolutePath))
        }
        if (cacheDir != null && cacheDir.exists()) {
            results.add(FileItem(name = "cache", isDirectory = true, size = 0L, absolutePath = cacheDir.absolutePath))
        }
    } else {
        val root = if (currentVirtualPath.startsWith("files")) filesDir else cacheDir
        val relative = currentVirtualPath.substringAfter("files/").substringAfter("cache/").removePrefix("files").removePrefix("cache")
        val targetDir = if (relative.isEmpty()) root else java.io.File(root, relative)
        
        if (targetDir != null && targetDir.exists() && targetDir.isDirectory) {
            val list = targetDir.listFiles()
            if (list != null) {
                for (f in list) {
                    results.add(
                        FileItem(
                            name = f.name,
                            isDirectory = f.isDirectory,
                            size = if (f.isDirectory) 0L else f.length(),
                            absolutePath = f.absolutePath
                        )
                    )
                }
            }
        }
    }
    return results.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
}
