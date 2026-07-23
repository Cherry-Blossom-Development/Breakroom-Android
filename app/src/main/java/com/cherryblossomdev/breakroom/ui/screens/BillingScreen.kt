package com.cherryblossomdev.breakroom.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.CollectionsRepository
import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ==================== ViewModel ====================

data class BillingUiState(
    val isLoading: Boolean = false,
    val planSubscribed: Boolean = false,
    val planFeePercent: Int = 5,
    val planPlatform: String? = null,
    val isOpeningPortal: Boolean = false,
    val error: String? = null
)

class BillingViewModel(private val repo: CollectionsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(BillingUiState())
    val uiState: StateFlow<BillingUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = repo.getBillingPlan()) {
                is BreakroomResult.Success -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    planSubscribed = result.data.subscribed,
                    planFeePercent = result.data.fee_percent,
                    planPlatform = result.data.platform
                )
                is BreakroomResult.Error -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = result.message
                )
                else -> _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun startPortal(onOpenUrl: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOpeningPortal = true, error = null)
            when (val result = repo.getBillingPortalUrl()) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(isOpeningPortal = false)
                    onOpenUrl(result.data)
                }
                is BreakroomResult.Error -> _uiState.value = _uiState.value.copy(
                    isOpeningPortal = false, error = result.message
                )
                else -> _uiState.value = _uiState.value.copy(isOpeningPortal = false)
            }
        }
    }
}

// ==================== Screen ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(
    viewModel: BillingViewModel,
    subscriptionViewModel: SubscriptionViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    LaunchedEffect(subscriptionViewModel.purchaseSuccess) {
        if (subscriptionViewModel.purchaseSuccess) {
            subscriptionViewModel.clearPurchaseSuccess()
            viewModel.load()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Billing & Plans") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                windowInsets = WindowInsets(0)
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Prosaurus offers two tiers. Most features are completely free — Pro is only needed " +
                "if you want to sell artwork without a platform fee, or need extra session storage.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            CurrentPlanBanner(uiState)

            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            FreeTierCard(isCurrent = !uiState.planSubscribed)

            ProTierCard(
                uiState = uiState,
                onUpgrade = { activity?.let { subscriptionViewModel.startPurchase(it) } },
                onManage = when (uiState.planPlatform) {
                    "stripe" -> { { viewModel.startPortal(::openUrl) } }
                    "google" -> { { openUrl("https://play.google.com/store/account/subscriptions") } }
                    else -> null
                },
                isPurchasing = subscriptionViewModel.isPurchasing,
                purchaseError = subscriptionViewModel.errorMessage
            )

            FeeBreakdownCard()

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CurrentPlanBanner(uiState: BillingUiState) {
    val isPro = uiState.planSubscribed
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPro)
                MaterialTheme.colorScheme.tertiaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clearAndSetSemantics {
                    contentDescription = "Your current plan: ${if (isPro) "Pro" else "Free"}. " +
                        if (isPro) "0% platform fee on sales" else "5% platform fee on sales"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "YOUR CURRENT PLAN",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (isPro) "Pro" else "Free",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPro) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                if (isPro) "0% platform fee\non sales"
                else "5% platform fee\non sales",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun FreeTierCard(isCurrent: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            "FREE",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isCurrent) {
                        Text(
                            "Current",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text("\$0 / month", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Divider()
            listOf(
                true  to "Chat & messaging",
                true  to "Breakroom dashboard",
                true  to "Blog",
                true  to "Collections & storefront",
                true  to "Gallery",
                true  to "Friends & social",
                true  to "Company profiles",
                true  to "Bands & instruments",
                true  to "Projects & Lyrics",
                true  to "Sessions (standard storage)",
                true  to "Help desk access",
                false to "5% Prosaurus fee on art sales"
            ).forEach { (positive, label) ->
                FeatureRow(positive = positive, label = label)
            }
        }
    }
}

@Composable
private fun ProTierCard(
    uiState: BillingUiState,
    onUpgrade: () -> Unit,
    onManage: (() -> Unit)?,
    isPurchasing: Boolean,
    purchaseError: String?
) {
    val isCurrent = uiState.planSubscribed
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            "PRO",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    if (isCurrent) {
                        Text(
                            "Current",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (!isCurrent) {
                    Text("\$3.99 / month", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            Divider()
            FeatureRow(positive = true, label = "Everything in Free")
            Divider()
            FeatureRow(positive = true, label = "No Prosaurus platform fee on art sales", isPro = true)
            FeatureRow(positive = true, label = "Extra storage on Sessions", isPro = true)

            purchaseError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            when {
                isCurrent && onManage != null -> {
                    Spacer(Modifier.height(2.dp))
                    OutlinedButton(
                        onClick = onManage,
                        enabled = !uiState.isOpeningPortal,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isOpeningPortal) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Opening…")
                        } else {
                            Text("Manage Subscription")
                        }
                    }
                }
                !isCurrent -> {
                    Spacer(Modifier.height(2.dp))
                    Button(
                        onClick = onUpgrade,
                        enabled = !isPurchasing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isPurchasing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Processing…")
                        } else {
                            Text("Upgrade to Pro — \$3.99/mo")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(positive: Boolean, label: String, isPro: Boolean = false) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = if (positive) "✓" else "✗",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                !positive -> MaterialTheme.colorScheme.error
                isPro -> MaterialTheme.colorScheme.primary
                else -> Color(0xFF4CAF50)
            }
        )
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isPro) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun FeeBreakdownCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("How art sale fees work", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "When a buyer purchases artwork through your storefront, payment is processed by Stripe. " +
                "Stripe always charges their standard processing fee — this is not a Prosaurus fee and cannot be waived.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FeeExampleCard(
                    label = "Free — \$10 sale",
                    rows = listOf(
                        Triple("Stripe", "−\$0.59", false),
                        Triple("Platform fee", "−\$0.50", false)
                    ),
                    total = "\$8.91",
                    modifier = Modifier.weight(1f),
                    isPro = false
                )
                FeeExampleCard(
                    label = "Pro — \$10 sale",
                    rows = listOf(
                        Triple("Stripe", "−\$0.59", false),
                        Triple("Platform fee", "waived", true)
                    ),
                    total = "\$9.41",
                    modifier = Modifier.weight(1f),
                    isPro = true
                )
            }
            Text(
                "At roughly 8 sales per month averaging \$10, Pro pays for itself. " +
                "At higher volume or higher prices, the savings are significantly larger.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun FeeExampleCard(
    label: String,
    rows: List<Triple<String, String, Boolean>>, // label, amount, waived
    total: String,
    modifier: Modifier = Modifier,
    isPro: Boolean
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isPro)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Divider()
            rows.forEach { (rowLabel, amount, waived) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(rowLabel, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        amount, fontSize = 11.sp,
                        color = if (waived) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = if (waived) FontStyle.Italic else FontStyle.Normal
                    )
                }
            }
            Divider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("You receive", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(total, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
