package com.ai.wangcai.ui

import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * 一个增强版的 OutlinedTextField，当获得焦点时自动选中所有内容，
 * 方便用户直接输入新内容而无需手动删除旧内容。
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
    // 初始状态下，如果已经有内容，默认准备好全选
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = value))
    }
    
    // 追踪焦点状态，确保从“未选中”到“选中”时执行全选
    var lastIsFocused by remember { mutableStateOf(false) }

    // 当外部 value 改变时（例如初始化加载），同步更新内部 state
    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(text = value)
        }
    }

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { 
            textFieldValue = it
            // 只有当文本内容真正改变时才回调
            if (it.text != value) {
                onValueChange(it.text)
            }
        },
        label = label,
        modifier = modifier.onFocusChanged { focusState ->
            if (focusState.isFocused) {
                // 如果是新获得焦点，执行全选
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
