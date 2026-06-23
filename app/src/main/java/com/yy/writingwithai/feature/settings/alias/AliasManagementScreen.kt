package com.yy.writingwithai.feature.settings.alias

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.core.note.entity.EntityType

/** entity-extraction-association · 别名管理 screen(tasks §4.3)。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AliasManagementScreen(onBack: () -> Unit, viewModel: AliasManagementViewModel = hiltViewModel()) {
    val aliases by viewModel.aliases.collectAsStateWithLifecycle()
    var entityType by remember { mutableStateOf(EntityType.PERSON) }
    var aliasKey by remember { mutableStateOf("") }
    var canonicalKey by remember { mutableStateOf("") }
    var typeMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("实体别名管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { typeMenuOpen = true }) {
                            Text(entityType.name)
                        }
                        DropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                            EntityType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.name) },
                                    onClick = {
                                        entityType = type
                                        typeMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = aliasKey,
                        onValueChange = { aliasKey = it },
                        label = { Text("别名(alias)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = canonicalKey,
                        onValueChange = { canonicalKey = it },
                        label = { Text("标准实体(canonical)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            viewModel.merge(entityType, aliasKey, canonicalKey)
                            aliasKey = ""
                            canonicalKey = ""
                        },
                        enabled = aliasKey.isNotBlank() && canonicalKey.isNotBlank()
                    ) {
                        Text("合并别名")
                    }
                }
            }

            items(aliases, key = { it.entityType.name + it.aliasKey }) { alias ->
                ListItem(
                    headlineContent = { Text(alias.aliasKey) },
                    supportingContent = { Text("${alias.entityType.name} → ${alias.canonicalEntityKey}") },
                    trailingContent = {
                        TextButton(onClick = { viewModel.unmerge(alias.entityType, alias.aliasKey) }) {
                            Text("删除")
                        }
                    }
                )
            }
        }
    }
}
