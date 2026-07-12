package com.mhm.speaktowrite.ui.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mhm.speaktowrite.models.CustomWord
import com.mhm.speaktowrite.theme.AuroraError
import com.mhm.speaktowrite.ui.components.AuroraDivider
import com.mhm.speaktowrite.ui.components.GlassCard
import com.mhm.speaktowrite.ui.components.IconBadge
import com.mhm.speaktowrite.ui.components.SectionEyebrow

@Composable
fun CustomWordsSection(
    customWords: List<CustomWord>,
    onAddWord: () -> Unit,
    onDeleteWord: (CustomWord) -> Unit,
) {
    SectionEyebrow("Custom Words", step = 3, modifier = Modifier.padding(start = 24.dp, top = 8.dp))
    
    GlassCard(modifier = Modifier.padding(horizontal = 24.dp)) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAddWord() }
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                IconBadge(Icons.Default.Add, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Add Custom Word",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Map spoken phrases to specific text.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (customWords.isNotEmpty()) {
                AuroraDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }

            customWords.forEachIndexed { index, word ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    IconBadge(Icons.Default.Translate, MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "\"${word.spokenPhrase}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "→ ${word.replacementText}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    IconButton(onClick = { onDeleteWord(word) }) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = AuroraError
                        )
                    }
                }
                
                if (index < customWords.size - 1) {
                    AuroraDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
                }
            }
        }
    }
}
