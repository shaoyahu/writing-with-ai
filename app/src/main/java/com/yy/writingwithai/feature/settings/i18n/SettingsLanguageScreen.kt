@file:Suppress("FunctionNaming")

package com.yy.writingwithai.feature.settings.i18n

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalCornerRadius
import com.yy.writingwithai.app.ui.theme.LocalSpacing
import com.yy.writingwithai.core.i18n.LocaleSelection

/**
 * language-switcher · 「我的 → 设置 → 语言」3 选 1 屏。
 *
 * 选项:跟随系统 / 中文 / English。点选 → 写 DataStore + Activity.recreate()。
 * recreate 后 NavController 回到 root(`AppShell`)，整个 UI 走新 locale 资源。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsLanguageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsLanguageViewModel = hiltViewModel()
) {
    val current by viewModel.current.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val spacing = LocalSpacing.current
    val cornerRadius = LocalCornerRadius.current

    // review-2026-07-02 code-quality:非 Activity context(如 Preview / Glance)时
    // 静默忽略点击会令用户困惑;改为 Toast 提示"无法切换"。
    val onSelect: (LocaleSelection) -> Unit = { selection ->
        val act = activity
        if (act != null) {
            viewModel.select(selection, act)
        } else {
            Toast.makeText(context, "Cannot switch language in this context", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_language_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(spacing.md)
        ) {
            Text(
                text = stringResource(R.string.settings_language_section_display),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.size(spacing.sm))
            Surface(
                shape = RoundedCornerShape(cornerRadius.lg),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    LanguageOption(
                        label = stringResource(R.string.settings_language_option_system),
                        selected = current == LocaleSelection.SYSTEM,
                        onClick = { onSelect(LocaleSelection.SYSTEM) }
                    )
                    LanguageOption(
                        label = stringResource(R.string.settings_language_option_zh),
                        selected = current == LocaleSelection.ZH,
                        onClick = { onSelect(LocaleSelection.ZH) }
                    )
                    LanguageOption(
                        label = stringResource(R.string.settings_language_option_en),
                        selected = current == LocaleSelection.EN,
                        onClick = { onSelect(LocaleSelection.EN) }
                    )
                }
            }
            Spacer(Modifier.size(spacing.sm2))
            Text(
                text = stringResource(R.string.settings_language_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.sm2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Translate,
            contentDescription = null,
            modifier = Modifier.size(spacing.md2 - spacing.xs),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(spacing.sm2))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        RadioButton(selected = selected, onClick = onClick)
    }
}
