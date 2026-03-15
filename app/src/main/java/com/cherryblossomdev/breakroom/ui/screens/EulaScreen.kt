package com.cherryblossomdev.breakroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.AuthRepository
import com.cherryblossomdev.breakroom.data.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class EulaUiState(
    val isLoading: Boolean = true,
    val accepted: Boolean = false,
    val notificationId: Int? = null,
    val isAccepting: Boolean = false,
    val error: String? = null
)

class EulaViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(EulaUiState())
    val uiState: StateFlow<EulaUiState> = _uiState

    init {
        checkStatus()
    }

    fun checkStatus() {
        viewModelScope.launch {
            _uiState.value = EulaUiState(isLoading = true)
            when (val result = authRepository.getEulaStatus()) {
                is AuthResult.Success -> {
                    _uiState.value = EulaUiState(
                        isLoading = false,
                        accepted = result.data.accepted,
                        notificationId = result.data.notificationId
                    )
                }
                is AuthResult.Error -> {
                    // If status can't be fetched, don't block the user
                    _uiState.value = EulaUiState(isLoading = false, accepted = true)
                }
            }
        }
    }

    fun accept() {
        val notifId = _uiState.value.notificationId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAccepting = true, error = null)
            when (authRepository.acceptEula(notifId)) {
                is AuthResult.Success -> {
                    _uiState.value = _uiState.value.copy(isAccepting = false, accepted = true)
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isAccepting = false,
                        error = "Failed to save acceptance. Please try again."
                    )
                }
            }
        }
    }
}

@Composable
fun EulaScreen(
    viewModel: EulaViewModel,
    onAccepted: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Auto-proceed as soon as accepted state is confirmed
    LaunchedEffect(uiState.accepted, uiState.isLoading) {
        if (!uiState.isLoading && uiState.accepted) {
            onAccepted()
        }
    }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Show EULA content for users who haven't accepted yet
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Text(
                text = "End User License Agreement",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Prosaurus — prosaurus.com",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )

            Text(
                text = "Effective Date: March 14, 2026",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 20.dp)
            )

            EulaSectionWithLink(
                number = "1",
                title = "Acceptance and Eligibility",
                beforeLink = "You must be at least 18 years of age to use the Service. By using the Service, you represent and warrant that you meet this requirement. We reserve the right to terminate any account found to belong to a minor.\n\nYour use of the Service is also subject to our ",
                linkText = "Privacy Policy",
                afterLink = ", which is incorporated into this Agreement by reference.",
                onLinkClick = onNavigateToPrivacyPolicy
            )

            EulaSection(
                number = "2",
                title = "License Grant",
                body = "Subject to your compliance with this Agreement, Cherry Blossom Development LLC grants you a limited, non-exclusive, non-transferable, revocable license to access and use the Service for your personal, non-commercial purposes. This license does not include the right to sublicense, sell, resell, transfer, assign, or otherwise exploit the Service or any of its content."
            )

            EulaSection(
                number = "3",
                title = "Prohibited Content — Zero Tolerance Policy",
                body = "We maintain a zero-tolerance policy for objectionable content. The following types of content are strictly prohibited and will result in immediate account termination without warning or refund:\n\n• Sexually explicit or pornographic content of any kind.\n\n• Content that exploits, endangers, or sexualizes minors in any way. Such content will be reported to the NCMEC and relevant law enforcement.\n\n• Hate speech or discriminatory content targeting individuals or groups based on race, ethnicity, religion, gender, sexual orientation, disability, national origin, or any other protected characteristic.\n\n• Content that promotes, glorifies, or incites violence, self-harm, terrorism, or illegal activity.\n\n• Harassment, threats, or intimidation directed at any person or group.\n\n• Spam, misinformation, or deliberately false content intended to deceive or mislead other users.\n\n• Content that violates any applicable law, including copyright, defamation, privacy, or consumer protection laws.\n\n• Malware, phishing links, or any content designed to compromise the security of other users or systems."
            )

            EulaSection(
                number = "4",
                title = "Prohibited Conduct — Zero Tolerance for Abusive Users",
                body = "We maintain a zero-tolerance policy for abusive behavior. The following conduct is strictly prohibited and will result in immediate and permanent account termination:\n\n• Harassment or bullying of other users, including repeated unwanted contact, public humiliation, or coordinated attacks.\n\n• Threatening or intimidating any user, employee, or person associated with the Service.\n\n• Impersonating another person, company, or entity in a misleading or harmful way.\n\n• Unauthorized access or hacking — attempting to access accounts, systems, or data that are not your own.\n\n• Circumventing or undermining security measures, including sharing login credentials or attempting to bypass account verification.\n\n• Deliberately disrupting the Service, including denial-of-service attacks, flooding, or other technically abusive behavior.\n\n• Creating multiple accounts to evade a ban or circumvent any restriction placed on your access.\n\n• Collecting or scraping user data without authorization.\n\nUsers who engage in any of the above conduct will be permanently banned. We cooperate fully with law enforcement when conduct may constitute a criminal offense."
            )

            EulaSection(
                number = "5",
                title = "Content Ownership and Responsibility",
                body = "You retain ownership of content you create and post on the Service. By posting content, you grant Cherry Blossom Development LLC a worldwide, royalty-free, non-exclusive license to host, store, transmit, display, and distribute that content solely for the purpose of operating and improving the Service.\n\nYou are solely responsible for all content you post. We do not endorse any user-submitted content and expressly disclaim all liability arising from it."
            )

            EulaSection(
                number = "6",
                title = "Reporting Violations",
                body = "If you encounter content or behavior that violates this Agreement, please report it immediately to:\n\nabuse@cherryblossomdevelopment.com\n\nReports submitted in good faith will be treated confidentially. We do not tolerate retaliation against users who report violations."
            )

            EulaSection(
                number = "7",
                title = "Enforcement and Account Termination",
                body = "Cherry Blossom Development LLC reserves the right to suspend or permanently terminate any account, at any time and without prior notice, for any violation of this Agreement or for any conduct we determine to be harmful.\n\nUpon termination: your license to use the Service is immediately revoked, access to your account and all associated content will be disabled, active subscription fees are non-refundable in cases of policy violation, and we may retain records of your account and activity as required by law or for abuse prevention."
            )

            EulaSection(
                number = "8",
                title = "Disclaimer of Warranties",
                body = "The Service is provided \"as is\" and \"as available,\" without warranties of any kind, express or implied. We do not warrant that the Service will be uninterrupted, error-free, or free of harmful components."
            )

            EulaSection(
                number = "9",
                title = "Limitation of Liability",
                body = "To the maximum extent permitted by applicable law, Cherry Blossom Development LLC and its officers, directors, employees, and agents shall not be liable for any indirect, incidental, special, consequential, or punitive damages arising out of or related to your use of the Service."
            )

            EulaSection(
                number = "10",
                title = "Governing Law",
                body = "This Agreement is governed by the laws of the State of Washington, United States. Any disputes shall be resolved exclusively in the state or federal courts located in Spokane County, Washington."
            )

            EulaSection(
                number = "11",
                title = "Changes to This Agreement",
                body = "We may update this EULA at any time. Your continued use of the Service after any changes constitutes your acceptance of the updated Agreement."
            )

            EulaSection(
                number = "12",
                title = "Contact Us",
                body = "If you have questions or concerns about this Agreement, please contact us:\n\nCherry Blossom Development LLC\nSpokane, Washington, United States\nlegal@cherryblossomdevelopment.com"
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Accept footer — pinned at the bottom
        Surface(
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (uiState.error != null) {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Button(
                    onClick = { viewModel.accept() },
                    enabled = !uiState.isAccepting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isAccepting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Accept These Terms")
                    }
                }
            }
        }
    }
}

@Composable
private fun EulaSectionWithLink(
    number: String,
    title: String,
    beforeLink: String,
    linkText: String,
    afterLink: String,
    onLinkClick: () -> Unit
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textStyle = MaterialTheme.typography.bodyMedium

    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(color = bodyColor)) { append(beforeLink) }
        pushStringAnnotation(tag = "LINK", annotation = "privacy")
        withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)) { append(linkText) }
        pop()
        withStyle(SpanStyle(color = bodyColor)) { append(afterLink) }
    }

    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Text(
            text = "$number. $title",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Divider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        ClickableText(
            text = annotated,
            style = textStyle,
            onClick = { offset ->
                annotated.getStringAnnotations(tag = "LINK", start = offset, end = offset)
                    .firstOrNull()?.let { onLinkClick() }
            }
        )
    }
}

@Composable
private fun EulaSection(number: String, title: String, body: String) {
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Text(
            text = "$number. $title",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Divider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
        )
    }
}
