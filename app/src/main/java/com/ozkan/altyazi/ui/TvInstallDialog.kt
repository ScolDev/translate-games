package com.ozkan.altyazi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ozkan.altyazi.R
import com.ozkan.altyazi.couchsubs.tv.lg.LgInstallError
import com.ozkan.altyazi.couchsubs.tv.samsung.InstallStep
import com.ozkan.altyazi.remote.RemoteKeys
import com.ozkan.altyazi.tv.LgTv
import com.ozkan.altyazi.tv.TvInstallState
import com.ozkan.altyazi.tv.TvLink
import com.ozkan.altyazi.ui.theme.AppColors

/**
 * Installs the CouchSubs TV app on an LG TV through Developer Mode: pick the TV (found on the network
 * or typed), type the passphrase the Developer Mode app shows, install. The key is kept, so updates
 * and later launches need no passphrase.
 */
@Composable
fun TvInstallDialog(tv: TvLink, savedHost: String?, onInstalled: (String) -> Unit, onDismiss: () -> Unit) {
    val state by tv.install.collectAsState()
    var tvs by remember { mutableStateOf<List<LgTv>?>(null) }
    var host by remember { mutableStateOf(savedHost.orEmpty()) }
    var passphrase by remember { mutableStateOf("") }
    val working = state is TvInstallState.Working
    val hasKey = tv.hasKey(host)

    LaunchedEffect(Unit) {
        tv.resetInstall()
        val found = tv.findTvs()
        tvs = found
        if (host.isBlank()) (found.firstOrNull { it.devMode } ?: found.firstOrNull())?.let { host = it.host }
    }

    Dialog(onDismissRequest = { if (!working) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = AppColors.SurfaceHigh, modifier = Modifier.width(640.dp).imePadding()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Tv, null, tint = AppColors.Primary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.tv_install_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, enabled = !working) { Icon(Icons.Rounded.Close, stringResource(R.string.action_close)) }
                }
                Text(stringResource(R.string.tv_install_body), style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
                Spacer(Modifier.height(16.dp))

                Surface(shape = RoundedCornerShape(18.dp), color = AppColors.Surface) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.tv_install_steps_title), style = MaterialTheme.typography.titleMedium)
                        listOf(R.string.tv_install_step1, R.string.tv_install_step2, R.string.tv_install_step3, R.string.tv_install_step4)
                            .forEachIndexed { i, step -> StepLine(i + 1, stringResource(step)) }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text(stringResource(R.string.tv_install_tvs), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                when (val list = tvs) {
                    null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.tv_install_searching), color = AppColors.TextSecondary)
                    }
                    else -> if (list.isEmpty()) {
                        Text(stringResource(R.string.tv_install_none), style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
                    } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        list.forEach { found ->
                            Surface(
                                onClick = { host = found.host },
                                enabled = !working,
                                shape = RoundedCornerShape(14.dp),
                                color = AppColors.Surface,
                                border = BorderStroke(1.dp, if (host == found.host) AppColors.Primary else AppColors.Outline),
                            ) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(found.name, style = MaterialTheme.typography.titleMedium)
                                        Text(found.host, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                    }
                                    Text(
                                        stringResource(if (found.devMode) R.string.tv_install_devmode_on else R.string.tv_install_devmode_off),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (found.devMode) AppColors.Success else AppColors.Accent,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.filter { c -> c.isDigit() || c == '.' }.take(15) },
                    label = { Text(stringResource(R.string.tv_install_address)) },
                    isError = host.isNotEmpty() && !RemoteKeys.isIpv4(host),
                    singleLine = true,
                    enabled = !working,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it.trim().take(12) },
                    label = { Text(stringResource(if (hasKey) R.string.tv_install_passphrase_saved else R.string.tv_install_passphrase)) },
                    singleLine = true,
                    enabled = !working,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.tv_install_session_note), style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)

                Spacer(Modifier.height(18.dp))
                InstallStatus(state)

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = onDismiss, enabled = !working) {
                        Text(stringResource(if (state is TvInstallState.Done) R.string.action_close else R.string.action_cancel))
                    }
                    Button(
                        onClick = { tv.install(host, passphrase, onInstalled) },
                        enabled = !working && RemoteKeys.isIpv4(host) && (passphrase.isNotBlank() || hasKey),
                    ) { Text(stringResource(if (state is TvInstallState.Failed) R.string.action_retry else R.string.tv_install_button)) }
                }
            }
        }
    }
}

@Composable
private fun StepLine(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(AppColors.Primary.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Text(number.toString(), color = AppColors.Primary, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InstallStatus(state: TvInstallState) {
    when (state) {
        TvInstallState.Idle -> Unit
        is TvInstallState.Working -> Column {
            LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)), color = AppColors.Primary)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(when (state.step) {
                    InstallStep.KEY -> R.string.tv_step_key
                    InstallStep.INSTALLING -> R.string.tv_step_installing
                    InstallStep.LAUNCHING -> R.string.tv_step_launching
                    else -> R.string.tv_step_connecting
                }),
                color = AppColors.TextSecondary,
            )
        }
        is TvInstallState.Done -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = AppColors.Success)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(if (state.installed) R.string.tv_install_done else R.string.tv_install_current, state.version))
        }
        is TvInstallState.Failed -> Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = AppColors.Danger)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    stringResource(when (state.error) {
                        LgInstallError.UNREACHABLE -> R.string.tv_err_unreachable
                        LgInstallError.KEY_SERVER_OFF -> R.string.tv_err_key_server
                        LgInstallError.WRONG_PASSPHRASE -> R.string.tv_err_passphrase
                        LgInstallError.KEY_REFUSED -> R.string.tv_err_key_refused
                        LgInstallError.NOT_ENOUGH_SPACE -> R.string.tv_err_space
                        LgInstallError.PACKAGE_MISSING -> R.string.tv_err_package
                        else -> R.string.tv_err_other
                    }),
                    color = AppColors.Danger,
                )
                state.detail?.takeIf { state.error == null || state.error == LgInstallError.INSTALL_FAILED || state.error == LgInstallError.CONNECTION_LOST }
                    ?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary) }
            }
        }
    }
}
