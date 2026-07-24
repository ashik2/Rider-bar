package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.example.MainActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.RiderBarApplication
import com.example.database.AppShortcut
import com.example.database.RiderBarRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RiderBarAccessibilityService : AccessibilityService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: ComposeView
    private lateinit var windowParams: WindowManager.LayoutParams
    private lateinit var repository: RiderBarRepository

    // Settings state
    private val shortcutsState = mutableStateOf<List<AppShortcut>>(emptyList())
    private val isMinimizedState = mutableStateOf(false)
    private val opacityState = mutableStateOf(90) // 0 to 100
    private val buttonSizeState = mutableStateOf(36) // 32, 36, 40
    private val bubbleSideState = mutableStateOf("right") // left, right
    private val layoutOrderState = mutableStateOf("s1,s2,s3,menu,home,back,s4,s5,s6")

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                try {
                    context.startActivity(launchIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        repository = (application as RiderBarApplication).repository

        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        ContextCompat.registerReceiver(
            this,
            unlockReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        // Listen to Room database updates
        lifecycleScope.launch {
            repository.allShortcuts.collectLatest { list ->
                shortcutsState.value = list
            }
        }
        lifecycleScope.launch {
            repository.getSettingFlow("is_minimized", "false").collectLatest {
                isMinimizedState.value = it.toBoolean()
                updateWindowLayout()
            }
        }
        lifecycleScope.launch {
            repository.getSettingFlow("opacity", "90").collectLatest {
                opacityState.value = it.toIntOrNull() ?: 90
            }
        }
        lifecycleScope.launch {
            repository.getSettingFlow("button_size", "36").collectLatest {
                buttonSizeState.value = it.toIntOrNull() ?: 36
                updateWindowLayout()
            }
        }
        lifecycleScope.launch {
            repository.getSettingFlow("bubble_side", "right").collectLatest {
                bubbleSideState.value = it
                updateWindowLayout()
            }
        }
        lifecycleScope.launch {
            repository.getSettingFlow("layout_order", "s1,s2,s3,menu,home,back,s4,s5,s6").collectLatest {
                layoutOrderState.value = it
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        showFloatingBar()
    }

    private fun showFloatingBar() {
        floatingView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@RiderBarAccessibilityService)
            setViewTreeSavedStateRegistryOwner(this@RiderBarAccessibilityService)
            setContent {
                RiderBarOverlay()
            }
        }

        windowParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.BOTTOM
        }

        try {
            windowManager.addView(floatingView, windowParams)
            updateWindowLayout()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateWindowLayout() {
        if (!::windowManager.isInitialized || !::floatingView.isInitialized || !::windowParams.isInitialized) return
        val density = resources.displayMetrics.density
        val isMin = isMinimizedState.value
        val btnSize = buttonSizeState.value
        val side = bubbleSideState.value

        if (isMin) {
            windowParams.width = (40 * density).toInt()
            windowParams.height = (40 * density).toInt()
            windowParams.gravity = Gravity.BOTTOM or Gravity.START
            windowParams.x = (8 * density).toInt()
            windowParams.y = 0
        } else {
            windowParams.width = WindowManager.LayoutParams.MATCH_PARENT
            windowParams.height = (48 * density).toInt()
            windowParams.gravity = Gravity.BOTTOM
            windowParams.x = 0
            windowParams.y = 0
        }

        try {
            windowManager.updateViewLayout(floatingView, windowParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseLayoutOrder(orderStr: String): List<String> {
        val defaultList = listOf("s1", "s2", "s3", "menu", "home", "back", "s4", "s5", "s6")
        if (orderStr.isBlank()) return defaultList
        val parts = orderStr.split(",")
        val validParts = parts.filter { it in defaultList }.distinct()
        if (validParts.size == defaultList.size) return validParts
        val missing = defaultList.filter { it !in validParts }
        return validParts + missing
    }

    @Composable
    fun RiderBarOverlay() {
        val shortcuts by shortcutsState
        val isMin by isMinimizedState
        val opacity by opacityState
        val btnSize by buttonSizeState
        val side by bubbleSideState

        val barAlpha = opacity / 100f

        if (isMin) {
            // Minimized Floating Bubble
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = barAlpha))
                    .clickable {
                        toggleMinimize(false)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Expand Bar",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            // Full Persistent Horizontal Navigation Bar with responsive weighted segments
            val layoutList = parseLayoutOrder(layoutOrderState.value)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Color.Black.copy(alpha = barAlpha))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                layoutList.forEach { itemKey ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        when (itemKey) {
                            "s1" -> AppShortcutButton(slotId = 1, shortcuts = shortcuts, btnSize = btnSize)
                            "s2" -> AppShortcutButton(slotId = 2, shortcuts = shortcuts, btnSize = btnSize)
                            "s3" -> AppShortcutButton(slotId = 3, shortcuts = shortcuts, btnSize = btnSize)
                            "s4" -> AppShortcutButton(slotId = 4, shortcuts = shortcuts, btnSize = btnSize)
                            "s5" -> AppShortcutButton(slotId = 5, shortcuts = shortcuts, btnSize = btnSize)
                            "s6" -> AppShortcutButton(slotId = 6, shortcuts = shortcuts, btnSize = btnSize)
                            "menu" -> NavButton(
                                icon = Icons.Default.Menu,
                                contentDescription = "Recents",
                                btnSize = btnSize
                            ) {
                                performGlobalAction(GLOBAL_ACTION_RECENTS)
                            }
                            "home" -> NavButton(
                                icon = Icons.Default.Home,
                                contentDescription = "Home",
                                btnSize = btnSize
                            ) {
                                performGlobalAction(GLOBAL_ACTION_HOME)
                            }
                            "back" -> NavButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                btnSize = btnSize,
                                onLongClick = {
                                    toggleMinimize(true)
                                }
                            ) {
                                performGlobalAction(GLOBAL_ACTION_BACK)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun AppShortcutButton(slotId: Int, shortcuts: List<AppShortcut>, btnSize: Int) {
        val shortcut = shortcuts.find { it.slotId == slotId }
        val packageName = shortcut?.packageName

        Box(
            modifier = Modifier
                .size(btnSize.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (packageName != null) Color.White.copy(alpha = 0.15f)
                    else Color.White.copy(alpha = 0.05f)
                )
                .clickable {
                    if (packageName != null) {
                        launchApp(packageName)
                    } else {
                        // Open Settings to configure slots
                        val configIntent = Intent(this, Class.forName("com.example.MainActivity")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(configIntent)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (packageName != null) {
                AndroidView(
                    factory = { context ->
                        ImageView(context).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setPadding(6, 6, 6, 6)
                        }
                    },
                    update = { imageView ->
                        try {
                            val icon = imageView.context.packageManager.getApplicationIcon(packageName)
                            imageView.setImageDrawable(icon)
                        } catch (e: Exception) {
                            imageView.setImageResource(android.R.drawable.sym_def_app_icon)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = "Empty Shortcut Slot",
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size((btnSize * 0.45).dp)
                )
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun NavButton(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        contentDescription: String,
        btnSize: Int,
        onLongClick: (() -> Unit)? = null,
        onClick: () -> Unit
    ) {
        Box(
            modifier = Modifier
                .size(btnSize.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.25f))
                .combinedClickable(
                    onLongClick = onLongClick,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size((btnSize * 0.55).dp)
            )
        }
    }

    private fun launchApp(packageName: String?) {
        if (packageName.isNullOrEmpty()) return
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun toggleMinimize(minimize: Boolean) {
        lifecycleScope.launch {
            repository.saveSetting("is_minimized", minimize.toString())
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(unlockReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (::floatingView.isInitialized) {
            try {
                windowManager.removeView(floatingView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
