package com.cherryblossomdev.breakroom.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.CollectionsRepository
import com.cherryblossomdev.breakroom.data.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ==================== ViewModel ====================

data class CollectionsPaymentUiState(
    val isLoading: Boolean = false,
    val connectStatus: String = "not_connected", // "not_connected" | "pending" | "active"
    val planSubscribed: Boolean = false,
    val planFeePercent: Int = 5,
    val planPlatform: String? = null,
    val isStarting: Boolean = false,
    val isOpeningPortal: Boolean = false,
    val error: String? = null
)

class CollectionsPaymentViewModel(
    private val repo: CollectionsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionsPaymentUiState())
    val uiState: StateFlow<CollectionsPaymentUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val planResult = repo.getBillingPlan()
            val statusResult = repo.getConnectStatus()

            val plan = (planResult as? BreakroomResult.Success)?.data
            val status = (statusResult as? BreakroomResult.Success)?.data

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                planSubscribed = plan?.subscribed ?: false,
                planFeePercent = plan?.fee_percent ?: 5,
                planPlatform = plan?.platform,
                connectStatus = status?.status ?: "not_connected",
                error = if (plan == null && status == null) "Failed to load payment info" else null
            )
        }
    }

    fun startConnect(onOpenUrl: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isStarting = true, error = null)
            when (val result = repo.startConnect()) {
                is BreakroomResult.Success -> {
                    val data = result.data
                    if (data.status == "active") {
                        _uiState.value = _uiState.value.copy(
                            isStarting = false,
                            connectStatus = "active"
                        )
                    } else if (data.url != null) {
                        _uiState.value = _uiState.value.copy(isStarting = false)
                        onOpenUrl(data.url)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isStarting = false,
                            error = "Unexpected response from server"
                        )
                    }
                }
                is BreakroomResult.Error -> _uiState.value = _uiState.value.copy(
                    isStarting = false, error = result.message
                )
                else -> _uiState.value = _uiState.value.copy(isStarting = false)
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
fun CollectionsPaymentScreen(
    viewModel: CollectionsPaymentViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh status when returning from the browser
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment Setup") },
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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return@Scaffold
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Plan card ──
        PlanCard(
            uiState = uiState,
            onManageSubscription = when (uiState.planPlatform) {
                "stripe" -> { { viewModel.startPortal(::openUrl) } }
                "google" -> { { openUrl("https://play.google.com/store/account/subscriptions") } }
                else -> null
            }
        )

        uiState.error?.let { err ->
            Text(err, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
        }

        // ── Payout Account section ──
        Text("Payout Account", fontWeight = FontWeight.Bold, fontSize = 16.sp)

        when (uiState.connectStatus) {
            "active" -> ActiveConnectCard { openUrl("https://dashboard.stripe.com/express") }
            "pending" -> PendingConnectCard(
                isStarting = uiState.isStarting,
                onContinue = { viewModel.startConnect(::openUrl) }
            )
            else -> NotConnectedCard(
                isStarting = uiState.isStarting,
                onConnect = { viewModel.startConnect(::openUrl) }
            )
        }

        // ── How it works ──
        HowItWorksSection()
    }
    } // Scaffold
}

// ── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun PlanCard(uiState: CollectionsPaymentUiState, onManageSubscription: (() -> Unit)?) {
    val isProPurple = MaterialTheme.colorScheme.tertiary
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
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (isPro) "Prosaurus Pro" else "Free Plan",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isPro) isProPurple else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (isPro) "0% application fee on all artwork sales"
                       else "5% application fee applied to each sale",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            val note = when {
                isPro && uiState.planPlatform == "google" -> "Subscribed via Android — manage in Google Play"
                isPro && uiState.planPlatform == "apple" -> "Subscribed via iOS — manage in the App Store"
                isPro && uiState.planPlatform == "promo" -> "Complimentary Pro account"
                isPro -> "Pro plan active"
                else -> "Upgrade to Pro to keep 100% of your sale price (minus Stripe's processing fee)"
            }
            Text(note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onManageSubscription != null) {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onManageSubscription,
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
        }
    }
}

@Composable
private fun NotConnectedCard(isStarting: Boolean, onConnect: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.CreditCard, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("No payout account connected", fontWeight = FontWeight.Bold)
                Text(
                    "Connect a Stripe account to receive payouts from your sales.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Button(
                onClick = onConnect,
                enabled = !isStarting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isStarting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Redirecting…")
                } else {
                    Text("Connect with Stripe")
                }
            }
        }
    }
}

@Composable
private fun PendingConnectCard(isStarting: Boolean, onContinue: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Warning, contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Setup incomplete", fontWeight = FontWeight.Bold)
                Text(
                    "Your Stripe account was created but onboarding isn't finished yet.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Button(
                onClick = onContinue,
                enabled = !isStarting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isStarting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Redirecting…")
                } else {
                    Text("Continue Stripe Setup")
                }
            }
        }
    }
}

@Composable
private fun ActiveConnectCard(onOpenDashboard: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Stripe account connected", fontWeight = FontWeight.Bold)
                Text(
                    "Your account is ready to accept payments. Payouts go directly to your bank.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            OutlinedButton(onClick = onOpenDashboard, modifier = Modifier.fillMaxWidth()) {
                Text("Open Stripe Dashboard ↗")
            }
        }
    }
}

@Composable
private fun HowItWorksSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Divider()
        Text("How it works", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        listOf(
            "1" to Pair("Connect your Stripe payout account",
                "Create or link a Stripe account. Stripe collects your bank info for payouts."),
            "2" to Pair("Set prices on your products",
                "Add pricing to items in your collections. Each piece can have its own price and shipping cost."),
            "3" to Pair("Customers buy from your store",
                "Stripe processes payments securely. Pro members keep 100% of their sale price (minus Stripe's ~2.9% + $0.30 fee). Free members also have a 5% platform fee deducted.")
        ).forEach { (num, content) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(num, color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(content.first, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(content.second, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
