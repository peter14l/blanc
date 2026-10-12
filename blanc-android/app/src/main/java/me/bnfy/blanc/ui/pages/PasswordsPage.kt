package me.bnfy.blanc.ui.pages

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import me.bnfy.blanc.R

/**
 * Saved Passwords page.
 *
 * Blanc for Android deliberately does not store passwords on-device. Autofill is
 * delegated to Android's system Autofill framework through the Blanc Autofill
 * service (see `BlancAutofillService`), so saved credentials are managed in the
 * system settings rather than in-app. The page honestly reflects that state and
 * offers a direct jump into the system autofill settings where the user manages
 * their saved passwords. Reachable via `/passwords` and the overflow menu.
 */
@Composable
fun PasswordsPage(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val canOpenAutofillSettings = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            Toolbar(
                title = "Saved passwords",
                onBack = onBack
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                EmptyState(
                    icon = R.drawable.ic_lock,
                    title = "No saved passwords",
                    subtitle = "Blanc for Android doesn't store passwords on-device. " +
                        "Autofill runs through Android's built-in password manager via the " +
                        "Blanc Autofill service, so your saved passwords are managed in system settings.",
                    actionText = if (canOpenAutofillSettings) "Open system autofill settings" else null,
                    onAction = if (canOpenAutofillSettings) {
                        {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE))
                            }
                        }
                    } else null
                )
            }
        }
    }
}