package cn.gdou.edu.timetable

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.gdou.edu.timetable.ui.GdouAppRoot
import cn.gdou.edu.timetable.ui.MainViewModel
import cn.gdou.edu.timetable.ui.theme.GdouTheme
import cn.gdou.edu.timetable.worker.UpdateNotificationHelper

class MainActivity : ComponentActivity() {
    private var openUpdateSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleUpdateIntent(intent)
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            )
        )

        val container = (application as GdouApplication).container

        setContent {
            val settings by container.settingsStore.settings.collectAsStateWithLifecycle()
            GdouTheme(
                seedColorArgb = settings.themeSeedColorArgb,
                darkThemeMode = settings.themeDarkMode
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val vm: MainViewModel = viewModel(factory = MainViewModel.factory(container))
                    GdouAppRoot(
                        viewModel = vm,
                        openUpdateSignal = openUpdateSignal
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUpdateIntent(intent)
    }

    private fun handleUpdateIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(UpdateNotificationHelper.EXTRA_OPEN_UPDATE_DIALOG, false) == true) {
            intent.removeExtra(UpdateNotificationHelper.EXTRA_OPEN_UPDATE_DIALOG)
            openUpdateSignal += 1
        }
    }
}
