package com.kimaita.monies.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.kimaita.monies.R

/**
 * App bar for the bottom-nav tabs: the tab's title, the app-wide account picker, the tab's own
 * [actions], then Settings last.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabTopBar(
    title: String,
    onSettings: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        actions = {
            AccountPicker()
            actions()
            IconButton(onClick = onSettings) {
                Icon(painterResource(R.drawable.settings), contentDescription = "Settings")
            }
        },
    )
}
