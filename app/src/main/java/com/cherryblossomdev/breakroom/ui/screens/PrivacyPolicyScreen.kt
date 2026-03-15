package com.cherryblossomdev.breakroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(paddingValues)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Privacy Policy",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Prosaurus — Mobile Application",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )

        Text(
            text = "Effective Date: February 20, 2026",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 20.dp)
        )

        Text(
            text = "This Privacy Policy describes how Cherry Blossom Development LLC (\"we,\" \"us,\" or \"our\"), based in Spokane, Washington, collects, uses, and protects your information when you use the Prosaurus mobile application (the \"App\"). By using the App, you agree to the practices described in this policy.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        PrivacySection(
            number = "1",
            title = "Age Requirement",
            body = "The Prosaurus App is intended for use by adults only. You must be at least 18 years of age to create an account or use this App. We do not knowingly collect personal information from anyone under the age of 18. If we become aware that a user under 18 has provided us with personal information, we will promptly delete that account and its associated data."
        )

        PrivacySection(
            number = "2",
            title = "Information We Collect",
            body = "We collect information you provide directly, as well as certain information automatically.\n\nInformation You Provide:\n• Account Information: Username, email address, and password when you register.\n• Profile Information: Display name, profile photo, bio, and any other details you choose to add.\n• User Content: Posts, blog entries, artwork, comments, messages, and other content you create or share.\n• Payment Information: Billing details processed through our payment provider. We do not store full payment card numbers on our servers.\n• Communications: Any information you send us via email or support requests.\n\nInformation Collected Automatically:\n• Device Information: Device type, operating system version, and unique device identifiers.\n• Usage Data: Features accessed, content viewed, and interactions within the App.\n• Log Data: IP address, access times, and error reports.\n• Authentication Tokens: Session tokens stored securely to keep you logged in."
        )

        PrivacySection(
            number = "3",
            title = "How We Use Your Information",
            body = "We use the information we collect to:\n• Provide, operate, and improve the App and its features.\n• Authenticate your identity and maintain the security of your account.\n• Process subscription payments and manage your billing.\n• Send you service-related notifications.\n• Respond to your support requests and communications.\n• Monitor and enforce our Terms of Service, including age restrictions.\n• Detect and prevent fraud, abuse, or other harmful activity.\n• Analyze usage trends to improve the user experience.\n\nWe do not sell your personal information to third parties. We do not use your data for behavioral advertising or share it with advertisers."
        )

        PrivacySection(
            number = "4",
            title = "How We Share Your Information",
            body = "We may share your information only in the following limited circumstances:\n\n• With Other Users: Content you post publicly is visible to others as you intend. Your username and profile are visible to other registered users within the App.\n• Service Providers: We work with trusted third-party vendors who assist us in operating the App. These providers are contractually obligated to protect your information.\n• Legal Requirements: We may disclose your information if required to do so by law or court order.\n• Business Transfers: In the event of a merger or acquisition, your information may be transferred as part of that transaction."
        )

        PrivacySection(
            number = "5",
            title = "Data Retention",
            body = "We retain your personal information for as long as your account is active or as needed to provide you with services. If you delete your account, we will delete or anonymize your personal data within a reasonable period, except where we are required to retain it for legal or legitimate business purposes."
        )

        PrivacySection(
            number = "6",
            title = "Data Security",
            body = "We take reasonable technical and organizational measures to protect your information from unauthorized access, loss, or misuse. These measures include encrypted connections (HTTPS/TLS), secure password hashing, and access controls on our servers.\n\nYou are responsible for keeping your password confidential. Do not share your login credentials with anyone."
        )

        PrivacySection(
            number = "7",
            title = "Your Rights and Choices",
            body = "You have the following rights regarding your personal information:\n• Access: You may request a copy of the personal information we hold about you.\n• Correction: You may update or correct inaccurate information through your account settings.\n• Deletion: You may request that we delete your account and associated personal data.\n• Portability: You may request an export of your data in a commonly used format.\n• Opt-Out of Notifications: You can manage notification preferences within the App settings.\n\nTo exercise any of these rights, please contact us at the address listed in Section 10."
        )

        PrivacySection(
            number = "8",
            title = "Third-Party Links and Services",
            body = "The App may contain links to external websites or services not operated by us. We are not responsible for the privacy practices of those third parties. We encourage you to review the privacy policies of any external services you access."
        )

        PrivacySection(
            number = "9",
            title = "Changes to This Policy",
            body = "We may update this Privacy Policy from time to time. When we make material changes, we will update the effective date at the top of this page and, where appropriate, notify you through the App or via email. Your continued use of the App after any changes constitutes your acceptance of the updated policy."
        )

        PrivacySection(
            number = "10",
            title = "Contact Us",
            body = "If you have questions, concerns, or requests regarding this Privacy Policy or your personal data, please contact us:\n\nCherry Blossom Development LLC\nSpokane, Washington\nUnited States\nprivacy@cherryblossomdevelopment.com"
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
    } // end Scaffold content
}

@Composable
private fun PrivacySection(number: String, title: String, body: String) {
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
