package com.kimaita.monies.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kimaita.monies.R

@Composable
fun OtherBalances(
    availableFuliza: Double?,
    outstandingFuliza: Double?,
    mshwariBalance: Double?,
    mshwariLoan: Double? = null,
    lockSavings: Double?,
    hide: Boolean = false,
) {
    fun bal(v: Double) = if (hide) HIDDEN else money(v)
    var expanded by remember { mutableStateOf(false) }
    val fulizaMax = (availableFuliza ?: 0.0) + (outstandingFuliza ?: 0.0)
    val hasFuliza = fulizaMax > 0.0
    val owedFuliza = (outstandingFuliza ?: 0.0) > 0.0

    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Text(
            "OTHER BALANCES",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Crossfade(targetState = expanded, label = "balances") { open ->
            if (!open) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasFuliza) Pill {
                        Svc("Fuliza")
                        if (owedFuliza) {
                            Owe(bal(outstandingFuliza!!)); Muted("/${bal(fulizaMax)}")
                        } else {
                            Strong(bal(availableFuliza ?: 0.0)); Muted("available")
                        }
                    }
                    mshwariBalance?.let { Pill { Svc("M-Shwari"); Strong(bal(it)) } }
                    mshwariLoan?.let { Pill { Svc("M-Shwari loan"); Owe(bal(it)) } }
                    lockSavings?.let { Pill { Svc("Lock"); Strong(bal(it)) } }
                    Pill(onClick = { expanded = true }) {
                        Text(
                            "More ›",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                OtherBalancesPanel(
                    availableFuliza,
                    outstandingFuliza,
                    fulizaMax,
                    owedFuliza,
                    mshwariBalance,
                    mshwariLoan,
                    lockSavings,
                    hide,
                    onCollapse = { expanded = false })
            }
        }
    }
}

@Composable
private fun OtherBalancesPanel(
    availableFuliza: Double?, outstandingFuliza: Double?, fulizaMax: Double, owedFuliza: Boolean,
    mshwariBalance: Double?, mshwariLoan: Double?, lockSavings: Double?, hide: Boolean,
    onCollapse: () -> Unit,
) {
    fun bal(v: Double) = if (hide) HIDDEN else money(v)
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cs.surfaceContainerLow)
            .padding(horizontal = 16.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onCollapse)
                .padding(vertical = 12.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically
        ) {
            Text(
                "Balances",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
           Icon(painterResource(R.drawable.collapse), "Collapse")
        }
        if (fulizaMax > 0.0) PanelRow(divider = false) {
            RowLine(
                "Fuliza",
                if (owedFuliza) "${bal(outstandingFuliza ?: 0.0)} used" else "${
                    bal(availableFuliza ?: 0.0)
                } available",
                owed = owedFuliza
            )
            Spacer(Modifier.height(9.dp))
            val used = ((outstandingFuliza ?: 0.0) / fulizaMax).toFloat()
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(cs.surfaceVariant)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(used)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(cs.error)
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "${bal(outstandingFuliza ?: 0.0)} used · ${bal(availableFuliza ?: 0.0)} available · ${
                    bal(
                        fulizaMax
                    )
                } limit",
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant
            )
        }
        mshwariBalance?.let { PanelRow { RowLine("M-Shwari balance", bal(it)) } }
        mshwariLoan?.let { PanelRow { RowLine("M-Shwari loan", bal(it), owed = true) } }
        lockSavings?.let { PanelRow { RowLine("Lock savings", bal(it)) } }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PanelRow(divider: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Column(Modifier.padding(vertical = 13.dp), content = content)
}

@Composable
private fun RowLine(name: String, value: String, owed: Boolean = false) =
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
            value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
            color = if (owed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }

@Composable
internal fun Pill(onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(50))
            .background(cs.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
internal fun Svc(t: String) = Text(
    t,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
internal fun Strong(t: String) =
    Text(t, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

@Composable
private fun Owe(t: String) = Text(
    t,
    style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.error
)

@Composable
private fun Muted(t: String) = Text(
    t,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)
