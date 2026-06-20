package com.yy.writingwithai.feature.aiwriting.action

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yy.writingwithai.R
import com.yy.writingwithai.feature.aiwriting.AiActionFabState

/**
 * 详情屏 FAB(根据选区切 Share / AutoAwesome)。
 *
 * - `fabState.selectionEmpty == true` → Share FAB(onClick 触发 M1 既有 share)
 * - `fabState.selectionEmpty == false` → AutoAwesome FAB(onClick 唤起 ActionSheet)
 *
 * 见 ai-actions spec "ActionSheet" + quick-note spec "FAB on detail screen shows
 * Share or AI action affordance"。
 */
@Composable
fun AiActionFab(
    fabState: AiActionFabState,
    onShareClick: () -> Unit,
    onAiClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (fabState.selectionEmpty) {
        FloatingActionButton(
            onClick = onShareClick,
            modifier = modifier
        ) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = stringResource(R.string.quicknote_detail_share)
            )
        }
    } else {
        FloatingActionButton(
            onClick = onAiClick,
            modifier = modifier
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = stringResource(R.string.aiwriting_action_expand)
            )
        }
    }
}
