package com.yy.writingwithai.feature.settings.alias

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.core.note.entity.EntityType
import com.yy.writingwithai.core.ui.dropdown.AppSelectionDropdown

/** entity-extraction-association · 别名管理 screen(tasks §4.3)。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AliasManagementScreen(onBack: () -> Unit, viewModel: AliasManagementViewModel = hiltViewModel()) {
    val aliases by viewModel.aliases.collectAsStateWithLifecycle()
    var entityType by remember { mutableStateOf(EntityType.PERSON) }
    var aliasKey by remember { mutableStateOf("") }
    var canonicalKey by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.entity_alias_management_title)) },
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
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 顶部说明 + 示例 Card:帮用户理解"实体别名"是什么(反馈 #3)。
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.entity_alias_explanation_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.entity_alias_explanation_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.entity_alias_example_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.entity_alias_example_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppSelectionDropdown(
                        options = EntityType.entries,
                        selected = entityType,
                        onSelected = { entityType = it },
                        label = { Text(stringResource(R.string.entity_alias_type_label)) },
                        optionLabel = { it.name },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = aliasKey,
                        onValueChange = { aliasKey = it },
                        label = { Text(stringResource(R.string.entity_alias_input_alias)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = canonicalKey,
                        onValueChange = { canonicalKey = it },
                        label = { Text(stringResource(R.string.entity_alias_input_canonical)) },
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
                        Text(stringResource(R.string.entity_alias_merge_button))
                    }
                }
            }

            items(aliases, key = { it.entityType.name + it.aliasKey }) { alias ->
                ListItem(
                    headlineContent = { Text(alias.aliasKey) },
                    supportingContent = { Text("${alias.entityType.name} → ${alias.canonicalEntityKey}") },
                    trailingContent = {
                        TextButton(onClick = { viewModel.unmerge(alias.entityType, alias.aliasKey) }) {
                            Text(stringResource(R.string.entity_alias_delete_button))
                        }
                    }
                )
            }
        }
    }
}
