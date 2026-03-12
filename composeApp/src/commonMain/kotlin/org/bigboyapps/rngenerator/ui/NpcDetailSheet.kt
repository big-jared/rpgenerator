package org.bigboyapps.rngenerator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── NPC Detail Sheet ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NpcDetailSheet(
    details: NpcDetailsUi,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.parchmentLight,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AppColors.parchmentEdge)
            )
        }
    ) {
        NpcDetailContent(details)
    }
}

@Composable
fun NpcDetailContent(details: NpcDetailsUi) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // ── Header: Portrait + Name + Archetype ──────────────
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NpcPortraitImage(name = details.name, size = 80.dp)

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = details.name,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = AppColors.inkDark,
                        fontWeight = FontWeight.Bold
                    )
                )

                Text(
                    text = details.archetype,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = AppColors.bronze,
                        fontStyle = FontStyle.Italic
                    )
                )
            }
        }

        // ── Relationship Bar ─────────────────────────────────
        item {
            RelationshipBar(
                status = details.relationshipStatus,
                affinity = details.affinity
            )
        }

        // ── Description / Lore ───────────────────────────────
        if (details.lore.isNotBlank()) {
            item {
                SectionCard(title = "Lore") {
                    Text(
                        text = details.lore,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = AppColors.inkDark,
                            lineHeight = 22.sp
                        )
                    )
                }
            }
        } else if (details.description.isNotBlank()) {
            item {
                SectionCard(title = "About") {
                    Text(
                        text = details.description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = AppColors.inkDark,
                            lineHeight = 22.sp
                        )
                    )
                }
            }
        }

        // ── Personality ──────────────────────────────────────
        if (details.traits.isNotEmpty()) {
            item {
                SectionCard(title = "Personality") {
                    // Trait chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        details.traits.forEach { trait ->
                            TraitChip(trait)
                        }
                    }

                    if (details.speechPattern.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Speaks: ${details.speechPattern}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = AppColors.inkFaded,
                                fontStyle = FontStyle.Italic
                            )
                        )
                    }

                    if (details.motivations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Motivations",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = AppColors.bronze,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        details.motivations.forEach { motivation ->
                            Row(
                                modifier = Modifier.padding(top = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StarIcon(size = 12.dp, color = AppColors.bronze)
                                Text(
                                    text = motivation,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = AppColors.inkMedium
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Shop ─────────────────────────────────────────────
        if (details.hasShop && details.shopItems.isNotEmpty()) {
            item {
                SectionCard(title = details.shopName ?: "Shop") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        details.shopItems.forEach { item ->
                            ShopItemRow(item)
                        }
                    }
                }
            }
        }

        // ── Recent Conversations ─────────────────────────────
        if (details.recentConversations.isNotEmpty()) {
            item {
                SectionCard(title = "Recent Conversations") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        details.recentConversations.forEach { conv ->
                            ConversationEntry(conv)
                        }
                    }
                }
            }
        }
    }
}

// ── Relationship Bar ────────────────────────────────────────────

@Composable
private fun RelationshipBar(status: String, affinity: Int) {
    val displayStatus = status.replace("_", " ").lowercase()
        .replaceFirstChar { it.uppercase() }

    val barColor = when {
        affinity >= 40 -> AppColors.xpGreen
        affinity >= 10 -> AppColors.bronzeLight
        affinity >= -10 -> AppColors.parchmentEdge
        affinity >= -40 -> AppColors.hpRed.copy(alpha = 0.6f)
        else -> AppColors.hpRed
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.parchmentDark.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayStatus,
                style = MaterialTheme.typography.labelLarge.copy(
                    color = AppColors.inkDark,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "$affinity",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = AppColors.inkFaded
                )
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        // Affinity bar: -100 to 100 mapped to 0..1
        val progress = ((affinity + 100) / 200f).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AppColors.parchmentEdge.copy(alpha = 0.3f))
                .border(1.dp, AppColors.parchmentEdge.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(
                        Brush.horizontalGradient(
                            listOf(barColor.copy(alpha = 0.7f), barColor)
                        ),
                        RoundedCornerShape(3.dp)
                    )
            )
        }
    }
}

// ── Section Card ────────────────────────────────────────────────

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = AppColors.parchment,
                    cornerRadius = CornerRadius(8f),
                    size = size
                )
                drawRoundRect(
                    color = AppColors.parchmentEdge.copy(alpha = 0.4f),
                    cornerRadius = CornerRadius(8f),
                    style = Stroke(width = 1f)
                )
            }
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            ScrollIcon(size = 14.dp, color = AppColors.bronze)
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    color = AppColors.bronze,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        OrnamentalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

// ── Trait Chip ───────────────────────────────────────────────────

@Composable
private fun TraitChip(trait: String) {
    Box(
        modifier = Modifier
            .background(
                AppColors.parchmentDark.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp)
            )
            .border(1.dp, AppColors.parchmentEdge.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = trait,
            style = MaterialTheme.typography.bodySmall.copy(
                color = AppColors.inkMedium,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

// ── Shop Item Row ───────────────────────────────────────────────

@Composable
private fun ShopItemRow(item: ShopItemUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.parchmentDark.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = AppColors.inkDark,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall.copy(color = AppColors.inkFaded),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            CoinIcon(size = 14.dp, color = AppColors.gold)
            Text(
                text = "${item.price}",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = AppColors.gold,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        if (item.stock >= 0) {
            Text(
                text = "x${item.stock}",
                style = MaterialTheme.typography.bodySmall.copy(color = AppColors.inkFaded)
            )
        }
    }
}

// ── Conversation Entry ──────────────────────────────────────────

@Composable
private fun ConversationEntry(conv: ConversationUi) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Player
        Text(
            text = "You: \"${conv.playerInput}\"",
            style = MaterialTheme.typography.bodySmall.copy(
                color = AppColors.leather,
                fontStyle = FontStyle.Italic
            )
        )
        // NPC response
        Text(
            text = "\"${conv.npcResponse}\"",
            style = MaterialTheme.typography.bodySmall.copy(
                color = AppColors.inkDark
            ),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Preview Wrapper ─────────────────────────────────────────────

internal fun npcDetailPreviewContent(details: NpcDetailsUi): @Composable () -> Unit = {
    NpcDetailContent(details)
}
