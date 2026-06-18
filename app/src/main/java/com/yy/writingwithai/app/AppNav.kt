@file:Suppress("FunctionNaming")

package com.yy.writingwithai.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yy.writingwithai.feature.quicknote.detail.QuickNoteDetailScreen
import com.yy.writingwithai.feature.quicknote.edit.QuickNoteEditorScreen
import com.yy.writingwithai.feature.quicknote.list.QuickNoteListScreen
import kotlinx.serialization.Serializable

/**
 * writing-with-ai · 应用 NavHost(M1 接入 quick-note-feature)。
 *
 * 路由结构:
 * - [QuicknoteList] 列表入口(应用默认目的地)
 * - [QuicknoteDetail] 详情(`id` 为 Note.id)
 * - [QuicknoteEdit] 编辑(`id` 缺省或 "NEW" 视为新建)
 *
 * 后续 change(`aiwriting` / `settings` / `onboarding` 等)继续追加目的地。
 */
@Composable
fun AppNav() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = QuicknoteList,
    ) {
        composable<QuicknoteList> {
            QuickNoteListScreen(
                onNoteClick = { id -> navController.navigate(QuicknoteDetail(id)) },
                onCreateClick = { navController.navigate(QuicknoteEdit()) },
            )
        }
        composable<QuicknoteDetail> {
            QuickNoteDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(QuicknoteEdit(id)) },
                onDeleted = { navController.popBackStack() },
            )
        }
        composable<QuicknoteEdit> {
            QuickNoteEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    // 编辑保存:pop 回列表 / 详情
                    navController.popBackStack()
                },
            )
        }
    }
}

@Serializable
data object QuicknoteList

@Serializable
data class QuicknoteDetail(val id: String)

@Serializable
data class QuicknoteEdit(val id: String? = "NEW")
