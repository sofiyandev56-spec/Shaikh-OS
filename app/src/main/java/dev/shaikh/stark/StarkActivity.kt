package dev.shaikh.stark

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dev.shaikh.stark.admin.StarkPolicy
import dev.shaikh.stark.gateway.GatewayRoot
import dev.shaikh.stark.shell.ShellRoot
import dev.shaikh.stark.ui.StarkTheme

/**
 * Single entry point with two roles, decided by where Android launched it:
 * inside the STARK work profile (we are profile owner) it is the STARK shell;
 * on the normal side it is the gateway that creates or enters STARK.
 */
class StarkActivity : ComponentActivity() {

    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val policy = StarkPolicy(this)
        val isShell = policy.isProfileOwner()
        if (isShell) {
            policy.ensureBaseline()
            // Keep STARK's screen out of the Recents thumbnail.
            setRecentsScreenshotEnabled(false)
        }

        setContent {
            StarkTheme {
                if (isShell) {
                    ShellRoot(resumeTick, onLocked = { moveTaskToBack(true) })
                } else {
                    GatewayRoot(resumeTick)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}
