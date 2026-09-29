package com.ai.wangcai.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 一个增强版的 OutlinedTextField，当获得焦点时自动选中所有内容，
 * 方便用户直接输入新内容而无需手动删除旧内容。用于文字输入时直接调用系统软键盘。
 */
@Composable
fun SelectAllOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    singleLine: Boolean = false
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = value))
    }
    var lastIsFocused by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(text = value)
        }
    }

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { 
            textFieldValue = it
            if (it.text != value) {
                onValueChange(it.text)
            }
        },
        label = label,
        modifier = modifier.onFocusChanged { focusState ->
            if (focusState.isFocused) {
                if (!lastIsFocused) {
                    textFieldValue = textFieldValue.copy(
                        selection = TextRange(0, textFieldValue.text.length)
                    )
                }
            }
            lastIsFocused = focusState.isFocused
        },
        keyboardOptions = keyboardOptions,
        isError = isError,
        supportingText = supportingText,
        placeholder = placeholder,
        enabled = enabled,
        singleLine = singleLine
    )
}

/**
 * 处理内置数字键盘的输入逻辑
 * @param currentValue 当前已输入的字符串
 * @param key 按键值（"0".."9", ".", "⌫", "CLEAR"）
 * @param isFirstInput 是否为焦点获得后的首次输入（若为 true 则替换原内容）
 * @param maxDecimals 允许的最大小数位数，默认 2 位
 */
fun handleNumericInput(
    currentValue: String,
    key: String,
    isFirstInput: Boolean,
    maxDecimals: Int = 2
): String {
    if (isFirstInput) {
        return when (key) {
            "⌫" -> if (currentValue.isNotEmpty()) currentValue.dropLast(1) else ""
            "CLEAR" -> ""
            "." -> "0."
            else -> {
                if (key in "0".."9") key else currentValue
            }
        }
    }

    return when (key) {
        "⌫" -> if (currentValue.isNotEmpty()) currentValue.dropLast(1) else ""
        "CLEAR" -> ""
        "." -> {
            if (currentValue.isEmpty()) {
                "0."
            } else if (!currentValue.contains(".")) {
                "$currentValue."
            } else {
                currentValue
            }
        }
        else -> {
            if (key in "0".."9") {
                if (currentValue == "0") {
                    key
                } else {
                    val dotIndex = currentValue.indexOf('.')
                    if (dotIndex != -1 && currentValue.length - dotIndex - 1 >= maxDecimals) {
                        currentValue
                    } else if (currentValue.length >= 8) {
                        currentValue
                    } else {
                        currentValue + key
                    }
                }
            } else {
                currentValue
            }
        }
    }
}

/**
 * 专用于数字输入的输入框：
 * readOnly = true 确保点击时不会调起系统软键盘，而是由内置键盘接管。
 * 当处于活跃输入状态时，边框高亮为 DeepGreen。
 */
@Composable
fun NumericInputField(
    value: String,
    onValueChange: (String) -> Unit = {},
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    isActive: Boolean = true,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    Box(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {
                keyboardController?.hide()
                onClick()
            }
        )
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            isError = isError,
            supportingText = supportingText,
            singleLine = true,
            readOnly = true, // 彻底禁止系统软键盘
            enabled = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DeepGreen,
                unfocusedBorderColor = if (isActive) DeepGreen else MaterialTheme.colorScheme.outline,
                focusedLabelColor = DeepGreen,
                unfocusedLabelColor = if (isActive) DeepGreen else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        keyboardController?.hide()
                        onClick()
                    }
                }
        )
    }
}

/**
 * 内置数字键盘：
 * 采用 4 行 x 3 列标准数字键盘布局 (1 2 3 / 4 5 6 / 7 8 9 / . 0 ⌫)
 * 竖屏模式：位于屏幕底部，宽度最大 400.dp
 * 横屏模式：与弹窗左右布局，位于右侧，宽度约 250~290.dp
 */
@Composable
fun CustomNumericKeyboard(
    onKeyPress: (String) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false
) {
    val view = LocalView.current

    fun playFeedback() {
        try {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } catch (_: Exception) {}
    }

    val grid = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫")
    )

    Surface(
        modifier = modifier
            .widthIn(min = if (isLandscape) 240.dp else 260.dp, max = if (isLandscape) 290.dp else 400.dp)
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {} // 拦截点击冒泡，避免触发蒙层关闭
            ),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF3F5F4),
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, Color(0xFFDCE2DF))
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = if (isLandscape) 12.dp else 16.dp, vertical = if (isLandscape) 8.dp else 10.dp)
                .fillMaxWidth()
        ) {
            // 顶栏提示与收起按键
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isLandscape) 24.dp else 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Dialpad,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = DeepGreen
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "数字键盘",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray
                    )
                }

                IconButton(
                    onClick = {
                        playFeedback()
                        onCollapse()
                    },
                    modifier = Modifier.size(if (isLandscape) 24.dp else 28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardHide,
                        contentDescription = "收起键盘",
                        modifier = Modifier.size(18.dp),
                        tint = DeepGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isLandscape) 6.dp else 8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 8.dp)) {
                grid.forEach { rowKeys ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 8.dp)
                    ) {
                        rowKeys.forEach { key ->
                            NumericKeyButton(
                                key = key,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(if (isLandscape) 38.dp else 44.dp),
                                onClick = {
                                    playFeedback()
                                    onKeyPress(key)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumericKeyButton(
    key: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isBackspace = key == "⌫"
    val isDot = key == "."

    Surface(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = DeepGreen.copy(alpha = 0.3f)),
            onClick = onClick
        ),
        shape = RoundedCornerShape(8.dp),
        color = if (isBackspace) Color(0xFFE8ECE9) else Color.White,
        shadowElevation = 1.5.dp,
        border = BorderStroke(0.5.dp, Color(0xFFD5DDD8))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isBackspace) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "退格",
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF3E4F46)
                )
            } else if (isDot) {
                Text(
                    text = ".",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF222E27),
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = key,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF222E27),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
