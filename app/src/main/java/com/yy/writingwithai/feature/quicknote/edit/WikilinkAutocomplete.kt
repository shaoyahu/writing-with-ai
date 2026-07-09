package com.yy.writingwithai.feature.quicknote.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.R
import com.yy.writingwithai.core.data.db.NoteDao
import com.yy.writingwithai.core.data.db.entity.NoteEntity
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WikilinkAutocomplete(prefix: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    if (prefix.length < 1) return
    val context = LocalContext.current
    var candidates by remember { mutableStateOf<List<NoteEntity>>(emptyList()) }
    // review r2 修:rememberUpdatedState 保证 clickable lambda 始终持有最新 onSelect,
    // 避免重组时 onSelect 变化但 clickable 未更新导致调用旧回调。
    val currentOnSelect by rememberUpdatedState(onSelect)

    LaunchedEffect(prefix) {
        withContext(Dispatchers.IO) {
            try {
                val entry = EntryPoints.get(
                    context.applicationContext,
                    WikilinkEntryPoint::class.java
                )
                val q = "%${prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
                candidates = entry.noteDao().searchByTitlePrefix(q, limit = 8)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) { }
        }
    }

    Surface(modifier = modifier.fillMaxWidth(), shadowElevation = 2.dp) {
        Column {
            if (candidates.isEmpty()) {
                // fix M32 (full-review):stringResource 走 strings.xml 而非 Composable 里硬编码中文。
                val createNewLabel = stringResource(R.string.wikilink_autocomplete_create_new, prefix)
                Text(
                    text = createNewLabel,
                    modifier = Modifier.fillMaxWidth().clickable { currentOnSelect(prefix) }.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                candidates.forEach { note ->
                    Text(
                        text = note.title,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { currentOnSelect(note.title) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WikilinkEntryPoint {
    fun noteDao(): NoteDao
}
