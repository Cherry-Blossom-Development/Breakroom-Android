package com.cherryblossomdev.breakroom.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopNavigationBar(
    onMenuClick: () -> Unit,
    title: String = "Breakroom",
    isHomeScreen: Boolean = false,
    onAddBlock: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    isRefreshing: Boolean = false,
    isProfileScreen: Boolean = false,
    onProfileEdit: (() -> Unit)? = null,
    onProfileRefresh: (() -> Unit)? = null,
    profileIsEditMode: Boolean = false,
    onTopBarRefresh: (() -> Unit)? = null,
    onAdd: (() -> Unit)? = null,
    windowInsets: WindowInsets = WindowInsets(0),
    modifier: Modifier = Modifier
) {
    TopAppBar(
        navigationIcon = {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.testTag("nav-menu-button")
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu"
                )
            }
        },
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )
        },
        actions = {
            if (isHomeScreen) {
                onAddBlock?.let { addBlock ->
                    IconButton(onClick = addBlock) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Block"
                        )
                    }
                }
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    onRefresh?.let { refresh ->
                        IconButton(onClick = refresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh"
                            )
                        }
                    }
                }
            }
            onAdd?.let { add ->
                IconButton(onClick = add) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add"
                    )
                }
            }
            onTopBarRefresh?.let { refresh ->
                IconButton(onClick = refresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh"
                    )
                }
            }
            if (isProfileScreen) {
                if (!profileIsEditMode) {
                    onProfileEdit?.let { edit ->
                        OutlinedButton(
                            onClick = edit,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit")
                        }
                    }
                }
                onProfileRefresh?.let { refresh ->
                    IconButton(onClick = refresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        windowInsets = windowInsets,
        modifier = modifier
    )
}
