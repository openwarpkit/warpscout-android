package io.github.openwarpkit.warpscout.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.openwarpkit.warpscout.BuildConfig
import io.github.openwarpkit.warpscout.R
import io.github.openwarpkit.warpscout.ui.AppViewModel

private data class CreditLink(val label: String, val url: String, val description: Int)

private data class ProjectLink(
    val label: Int,
    val url: String,
    val drawable: Int
)

private val credits = listOf(
    CreditLink("openwarpkit/warpscout-android", "https://github.com/openwarpkit/warpscout-android", R.string.credit_android),
    CreditLink("vernette/warpscout", "https://github.com/vernette/warpscout", R.string.credit_warpscout),
    CreditLink("Cloudflare", "https://one.one.one.one/", R.string.credit_cloudflare),
    CreditLink("puzige/CloudflareWarpSpeedTest", "https://github.com/puzige/CloudflareWarpSpeedTest", R.string.credit_speed_test),
    CreditLink("ampetelin/warp-endpoint-checker", "https://github.com/ampetelin/warp-endpoint-checker", R.string.credit_ipv4),
    CreditLink("TheyCallMeSecond/WARP-Endpoint-IP", "https://github.com/TheyCallMeSecond/WARP-Endpoint-IP", R.string.credit_ipv6),
    CreditLink("SagePtr/mini_quic_generator", "https://github.com/SagePtr/mini_quic_generator", R.string.credit_quic),
    CreditLink("Diniboy1123/usque", "https://github.com/Diniboy1123/usque", R.string.credit_usque),
    CreditLink("nellimonix/base-relay", "https://github.com/nellimonix/base-relay", R.string.credit_relay),
    CreditLink("amnezia-vpn/amneziawg-go", "https://github.com/amnezia-vpn/amneziawg-go", R.string.credit_amnezia),
    CreditLink("charmbracelet/bubbletea", "https://github.com/charmbracelet/bubbletea", R.string.credit_bubbletea)
)

private val projectLinks = listOf(
    ProjectLink(R.string.link_github, "https://github.com/openwarpkit/warpscout-android", R.drawable.ic_github),
    ProjectLink(R.string.link_telegram, "https://t.me/findllimonix", R.drawable.ic_telegram),
    ProjectLink(R.string.link_channel, "https://t.me/+Yr77WgKrgu01Y2Ni", R.drawable.ic_channel),
    ProjectLink(R.string.link_chat, "https://t.me/+uP82UlLX6Ls3ZDdi", R.drawable.ic_chat),
    ProjectLink(R.string.link_donate, "https://pay.cloudtips.ru/p/205564c3", R.drawable.ic_cloudtips)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.about_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.independent_notice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            VersionRow(stringResource(R.string.android_version), BuildConfig.VERSION_NAME)
            VersionRow(stringResource(R.string.core_version), viewModel.coreBridge.coreVersion())
            VersionRow(
                stringResource(R.string.upstream_version),
                "${BuildConfig.UPSTREAM_TAG} (${BuildConfig.UPSTREAM_COMMIT})"
            )
            HorizontalDivider()
            Text(stringResource(R.string.credits), style = MaterialTheme.typography.titleLarge)
            credits.forEach { credit -> CreditRow(context, credit) }
            HorizontalDivider()
            LinkGrid(context)
        }
    }
}

@Composable
private fun CreditRow(context: Context, credit: CreditLink) {
    val description = stringResource(credit.description)
    val primary = MaterialTheme.colorScheme.primary
    val text = buildAnnotatedString {
        withStyle(
            SpanStyle(
                color = primary,
                textDecoration = TextDecoration.Underline
            )
        ) {
            append(credit.label)
        }
        append(" - ")
        append(description)
    }
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { openUrl(context, credit.url) }
            .padding(vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun LinkGrid(context: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        projectLinks.chunked(2).forEach { rowLinks ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowLinks.forEach { link ->
                    LinkButton(
                        link = link,
                        modifier = Modifier.weight(1f),
                        onClick = { openUrl(context, link.url) }
                    )
                }
                if (rowLinks.size == 1) {
                    Surface(modifier = Modifier.weight(1f), color = Color.Transparent) { }
                }
            }
        }
    }
}

@Composable
private fun LinkButton(link: ProjectLink, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(painterResource(link.drawable), contentDescription = null)
            Text(stringResource(link.label), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun VersionRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
