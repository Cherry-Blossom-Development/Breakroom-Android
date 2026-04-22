package com.cherryblossomdev.breakroom.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PaywallDialog(
    subscriptionViewModel: SubscriptionViewModel,
    onDismiss: () -> Unit,
    onSubscribed: () -> Unit
) {
    val activity = LocalContext.current as? Activity

    // When purchase succeeds, notify parent and dismiss
    LaunchedEffect(subscriptionViewModel.purchaseSuccess) {
        if (subscriptionViewModel.purchaseSuccess) {
            subscriptionViewModel.clearPurchaseSuccess()
            onSubscribed()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!subscriptionViewModel.isPurchasing) onDismiss() },
        title = {
            Text(
                text = "Upgrade to Premium",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Unlock the full Breakroom experience:",
                    style = MaterialTheme.typography.bodyMedium
                )
                PaywallFeatureRow("Unlimited band practice sessions")
                PaywallFeatureRow("Unlimited individual sessions")
                PaywallFeatureRow("Unlimited bands")
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "\$3.99 / month",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                subscriptionViewModel.errorMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { activity?.let { subscriptionViewModel.startPurchase(it) } },
                enabled = !subscriptionViewModel.isPurchasing
            ) {
                if (subscriptionViewModel.isPurchasing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Processing…")
                } else {
                    Text("Subscribe")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !subscriptionViewModel.isPurchasing
            ) {
                Text("Not now")
            }
        }
    )
}

@Composable
private fun PaywallFeatureRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}
