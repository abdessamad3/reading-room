package com.readingroom.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** The steps of each flow that asks for a PIN. */
private enum class PinFlow { TURN_OFF_LOCK, CREATE_THEN_LOCK, CHANGE_VERIFY, CHANGE_CREATE }

@Composable
fun AppRoot(viewModel: AppViewModel, lock: LockController) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val settings = state.settings
    val snackbar = remember { SnackbarHostState() }
    var pinFlow by rememberSaveable { mutableStateOf<PinFlow?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.showMessage(null)
        }
    }

    // The app list is only for when the lock is off.
    LaunchedEffect(settings.lockEnabled, screen) {
        if (settings.lockEnabled && screen is Screen.Apps) viewModel.go(Screen.Library)
    }

    // With the lock on, Back at the library must not leave the app.
    BackHandler(enabled = pinFlow == null && screen == Screen.Library && settings.lockEnabled) { }
    BackHandler(enabled = pinFlow == null && screen != Screen.Library) { viewModel.go(Screen.Library) }
    BackHandler(enabled = pinFlow != null) { pinFlow = null }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = screen) {
                Screen.Library -> LibraryScreen(
                    viewModel = viewModel,
                    onLockOn = {
                        if (settings.hasPin) viewModel.setLockEnabled(true) else pinFlow = PinFlow.CREATE_THEN_LOCK
                    },
                )
                is Screen.Reader -> ReaderScreen(viewModel, s.bookId)
                Screen.Settings -> SettingsScreen(
                    viewModel = viewModel,
                    lock = lock,
                    onBack = { viewModel.go(Screen.Library) },
                    onTurnLockOn = {
                        if (settings.hasPin) viewModel.setLockEnabled(true) else pinFlow = PinFlow.CREATE_THEN_LOCK
                    },
                    onTurnLockOff = { pinFlow = PinFlow.TURN_OFF_LOCK },
                    onChangePin = { pinFlow = if (settings.hasPin) PinFlow.CHANGE_VERIFY else PinFlow.CREATE_THEN_LOCK },
                )
                Screen.Apps -> AppsScreen(onBack = { viewModel.go(Screen.Library) })
            }

            pinFlow?.let { flow ->
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PinFlowScreen(flow, viewModel, onClose = { pinFlow = null }, onNext = { pinFlow = it })
                }
            }
        }
    }
}

@Composable
private fun PinFlowScreen(flow: PinFlow, vm: AppViewModel, onClose: () -> Unit, onNext: (PinFlow) -> Unit) {
    when (flow) {
        PinFlow.TURN_OFF_LOCK -> PinScreen(
            title = "Enter your PIN",
            subtitle = "The lock turns off and your other apps come back.",
            mode = PinMode.VERIFY,
            onCancel = onClose,
        ) { pin ->
            if (vm.checkPin(pin)) {
                vm.setLockEnabled(false)
                vm.showMessage("Focus lock is off.")
                onClose()
                null
            } else "Wrong PIN. Try again."
        }

        PinFlow.CREATE_THEN_LOCK -> PinScreen(
            title = "Choose a 4-digit PIN",
            subtitle = "You will need it to turn the lock off. Pick one you will remember.",
            mode = PinMode.CREATE,
            onCancel = onClose,
        ) { pin ->
            vm.setPin(pin)
            vm.setLockEnabled(true)
            vm.showMessage("PIN saved. Focus lock is on.")
            onClose()
            null
        }

        PinFlow.CHANGE_VERIFY -> PinScreen(
            title = "Enter your current PIN",
            subtitle = "Then you can choose a new one.",
            mode = PinMode.VERIFY,
            onCancel = onClose,
        ) { pin ->
            if (vm.checkPin(pin)) {
                onNext(PinFlow.CHANGE_CREATE)
                null
            } else "Wrong PIN. Try again."
        }

        PinFlow.CHANGE_CREATE -> PinScreen(
            title = "Choose a new PIN",
            subtitle = "4 digits.",
            mode = PinMode.CREATE,
            onCancel = onClose,
        ) { pin ->
            vm.setPin(pin)
            vm.showMessage("PIN changed.")
            onClose()
            null
        }
    }
}
