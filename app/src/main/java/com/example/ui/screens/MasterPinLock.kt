package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun MasterPinLockScreen(
    onCorrectPin: () -> Unit,
    onPanicPin: () -> Unit,
    correctPin: String?,
    panicPin: String?
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(enteredPin) {
        if (enteredPin.length == 4) {
            when (enteredPin) {
                correctPin -> onCorrectPin()
                panicPin -> onPanicPin()
                else -> {
                    isError = true
                    kotlinx.coroutines.delay(500)
                    enteredPin = ""
                    isError = false
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = if (isError) Color.Red else SecureCyan,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "SYSTEM ACCESS REQUIRED",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
            Text(
                text = "Enter Master Security PIN",
                color = MutedText,
                fontSize = 12.sp
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // PIN Indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { index ->
                    val isActive = index < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (isError) Color.Red.copy(alpha = 0.5f)
                                else if (isActive) SecureCyan else BorderSubtle
                            )
                            .border(
                                1.dp,
                                if (isError) Color.Red else if (isActive) SecureCyan else MutedText.copy(alpha = 0.3f),
                                CircleShape
                            )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(64.dp))
            
            // Numeric Keypad
            val numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "C")
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (i in 0 until 4) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (j in 0 until 3) {
                            val index = i * 3 + j
                            val value = numbers[index]
                            if (value.isNotEmpty()) {
                                PinButton(
                                    text = value,
                                    onClick = {
                                        if (value == "C") {
                                            if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                        } else {
                                            if (enteredPin.length < 4) enteredPin += value
                                        }
                                    }
                                )
                            } else {
                                Spacer(modifier = Modifier.size(72.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PinButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(TitaniumDark)
            .border(1.dp, BorderSubtle, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (text == "C") {
            Icon(Icons.Default.Backspace, contentDescription = null, tint = TextPrimary)
        } else {
            Text(
                text = text,
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
