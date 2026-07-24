package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.AppShortcut
import com.example.database.RiderBarRepository
import com.example.service.RiderBarAccessibilityService
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.RiderAmber
import com.example.ui.theme.RiderBlack
import com.example.ui.theme.RiderDarkGray
import com.example.ui.theme.RiderGreen
import com.example.ui.theme.RiderOrange
import com.example.ui.theme.RiderRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(val packageName: String, val appName: String)

private fun parseLayoutOrder(orderStr: String): List<String> {
    val defaultList = listOf("s1", "s2", "s3", "menu", "home", "back", "s4", "s5", "s6")
    if (orderStr.isBlank()) return defaultList
    val parts = orderStr.split(",")
    val validParts = parts.filter { it in defaultList }.distinct()
    if (validParts.size == defaultList.size) return validParts
    val missing = defaultList.filter { it !in validParts }
    return validParts + missing
}

private fun serializeLayoutOrder(order: List<String>): String {
    return order.joinToString(",")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = false) {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    RiderDashboard(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

private fun isAccessibilityServiceActive(context: Context): Boolean {
    val serviceClassName = RiderBarAccessibilityService::class.java.name
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.contains(serviceClassName)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiderDashboard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = (context.applicationContext as RiderBarApplication).repository
    val scope = rememberCoroutineScope()

    // Service status
    var isServiceEnabled by remember { mutableStateOf(false) }

    // Query status on lifecycle ON_RESUME
    DisposableEffect(context) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isServiceEnabled = isAccessibilityServiceActive(context)
            }
        }
        val lifecycle = (context as ComponentActivity).lifecycle
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    // Settings streams
    val shortcuts by repository.allShortcuts.collectAsStateWithLifecycle(initialValue = emptyList())
    val opacityVal by repository.getSettingFlow("opacity", "90").collectAsStateWithLifecycle(initialValue = "90")
    val btnSizeVal by repository.getSettingFlow("button_size", "36").collectAsStateWithLifecycle(initialValue = "36")
    val bubbleSideVal by repository.getSettingFlow("bubble_side", "right").collectAsStateWithLifecycle(initialValue = "right")

    val opacity = opacityVal.toIntOrNull() ?: 90
    val btnSize = btnSizeVal.toIntOrNull() ?: 36
    val bubbleSide = bubbleSideVal

    val layoutOrderVal by repository.getSettingFlow("layout_order", "s1,s2,s3,menu,home,back,s4,s5,s6").collectAsStateWithLifecycle(initialValue = "s1,s2,s3,menu,home,back,s4,s5,s6")

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var reorderedList by remember { mutableStateOf(parseLayoutOrder(layoutOrderVal)) }
    var rowWidthPx by remember { mutableStateOf(0) }
    val itemWidthPx = if (rowWidthPx > 0) rowWidthPx / 9f else 1f

    LaunchedEffect(layoutOrderVal) {
        if (draggedIndex == null) {
            reorderedList = parseLayoutOrder(layoutOrderVal)
        }
    }

    // App Selector Dialog state
    var activeSlotForSelection by remember { mutableStateOf<Int?>(null) }
    var showAppSelector by remember { mutableStateOf(false) }

    // Query installed apps list
    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
                installedApps = resolveInfos.map { info ->
                    AppInfo(
                        packageName = info.activityInfo.packageName,
                        appName = info.loadLabel(pm).toString()
                    )
                }.sortedBy { it.appName }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingApps = false
            }
        }
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("status_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isServiceEnabled) RiderGreen.copy(alpha = 0.15f)
                        else RiderRed.copy(alpha = 0.15f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isServiceEnabled) RiderGreen.copy(alpha = 0.5f)
                        else RiderRed.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (isServiceEnabled) RiderGreen else RiderRed)
                                )
                                Text(
                                    text = if (isServiceEnabled) "Rider Bar is ACTIVE" else "Service INACTIVE",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = if (isServiceEnabled) RiderGreen else RiderRed
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isServiceEnabled) "Your customized bottom bar is overlaying your system keys!"
                                else "Please activate Rider Bar in Accessibility settings so it can show your shortcuts and simulate buttons.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                            )
                        }

                        Switch(
                            checked = isServiceEnabled,
                            onCheckedChange = {
                                try {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = RiderGreen,
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("service_toggle")
                        )
                    }
                }
            }

            // Interactive Mockup Preview
            item {
                Column {
                    Text(
                        text = "Interactive Bar Layout & Rearrangement",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "Long press and drag any icon in the preview below to change its order! Tap a slot to map/unmap a launcher shortcut.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Mock bar container
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "DRAG & DROP PREVIEW BAR",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RiderAmber,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                if (draggedIndex != null) {
                                    Text(
                                        text = "Rearranging...",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RiderOrange,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                            }

                            // Horizontal Bar layout mockup with support for live Drag & Drop reordering
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E293B))
                                    .padding(horizontal = 4.dp)
                                    .onGloballyPositioned { coordinates ->
                                        rowWidthPx = coordinates.size.width
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                reorderedList.forEachIndexed { index, itemKey ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .zIndex(if (draggedIndex == index) 1f else 0f)
                                            .graphicsLayer {
                                                if (draggedIndex == index) {
                                                    translationX = dragOffset
                                                    scaleX = 1.25f
                                                    scaleY = 1.25f
                                                    shadowElevation = 8.dp.toPx()
                                                }
                                            }
                                            .pointerInput(index) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { offset ->
                                                        draggedIndex = index
                                                        dragOffset = 0f
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragOffset += dragAmount.x
                                                        val currentDragged = draggedIndex
                                                        if (currentDragged != null) {
                                                            val threshold = itemWidthPx * 0.6f
                                                            if (dragOffset > threshold && currentDragged < 8) {
                                                                val newList = reorderedList.toMutableList()
                                                                val temp = newList[currentDragged]
                                                                newList[currentDragged] = newList[currentDragged + 1]
                                                                newList[currentDragged + 1] = temp
                                                                reorderedList = newList
                                                                draggedIndex = currentDragged + 1
                                                                dragOffset -= itemWidthPx
                                                            } else if (dragOffset < -threshold && currentDragged > 0) {
                                                                val newList = reorderedList.toMutableList()
                                                                val temp = newList[currentDragged]
                                                                newList[currentDragged] = newList[currentDragged - 1]
                                                                newList[currentDragged - 1] = temp
                                                                reorderedList = newList
                                                                draggedIndex = currentDragged - 1
                                                                dragOffset += itemWidthPx
                                                            }
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        draggedIndex = null
                                                        dragOffset = 0f
                                                        scope.launch {
                                                            repository.saveSetting("layout_order", serializeLayoutOrder(reorderedList))
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        draggedIndex = null
                                                        dragOffset = 0f
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        when (itemKey) {
                                            "s1" -> MockShortcutSlot(
                                                slotId = 1,
                                                shortcuts = shortcuts,
                                                btnSize = btnSize,
                                                onClick = {
                                                    activeSlotForSelection = 1
                                                    showAppSelector = true
                                                }
                                            )
                                            "s2" -> MockShortcutSlot(
                                                slotId = 2,
                                                shortcuts = shortcuts,
                                                btnSize = btnSize,
                                                onClick = {
                                                    activeSlotForSelection = 2
                                                    showAppSelector = true
                                                }
                                            )
                                            "s3" -> MockShortcutSlot(
                                                slotId = 3,
                                                shortcuts = shortcuts,
                                                btnSize = btnSize,
                                                onClick = {
                                                    activeSlotForSelection = 3
                                                    showAppSelector = true
                                                }
                                            )
                                            "s4" -> MockShortcutSlot(
                                                slotId = 4,
                                                shortcuts = shortcuts,
                                                btnSize = btnSize,
                                                onClick = {
                                                    activeSlotForSelection = 4
                                                    showAppSelector = true
                                                }
                                            )
                                            "s5" -> MockShortcutSlot(
                                                slotId = 5,
                                                shortcuts = shortcuts,
                                                btnSize = btnSize,
                                                onClick = {
                                                    activeSlotForSelection = 5
                                                    showAppSelector = true
                                                }
                                            )
                                            "s6" -> MockShortcutSlot(
                                                slotId = 6,
                                                shortcuts = shortcuts,
                                                btnSize = btnSize,
                                                onClick = {
                                                    activeSlotForSelection = 6
                                                    showAppSelector = true
                                                }
                                            )
                                            "menu" -> MockNavIcon(Icons.Default.Menu, btnSize = btnSize)
                                            "home" -> MockNavIcon(Icons.Default.Home, btnSize = btnSize)
                                            "back" -> MockNavIcon(Icons.AutoMirrored.Filled.ArrowBack, btnSize = btnSize)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Customization Options
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Aesthetic & Layout Settings",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium
                        )

                        // Opacity slider
                          Column {
                              Row(
                                  modifier = Modifier.fillMaxWidth(),
                                  horizontalArrangement = Arrangement.SpaceBetween,
                                  verticalAlignment = Alignment.CenterVertically
                              ) {
                                  Text(
                                      text = "Bar Background Opacity",
                                      color = MaterialTheme.colorScheme.onSurface,
                                      fontSize = 14.sp,
                                      fontWeight = FontWeight.Bold
                                  )
                                  Text(
                                      text = "$opacity%",
                                      color = RiderOrange,
                                      fontSize = 14.sp,
                                      fontWeight = FontWeight.Bold
                                  )
                              }
                              Text(
                                  text = "Make the background transparent so you can still monitor maps underneath.",
                                  fontSize = 11.sp,
                                  color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                  modifier = Modifier.padding(vertical = 4.dp)
                              )
                              Slider(
                                  value = opacity.toFloat(),
                                  onValueChange = { newVal ->
                                      scope.launch {
                                          repository.saveSetting("opacity", newVal.toInt().toString())
                                      }
                                  },
                                  valueRange = 10f..100f,
                                  colors = SliderDefaults.colors(
                                      activeTrackColor = RiderOrange,
                                      thumbColor = RiderOrange
                                  )
                              )
                          }

                          // Button Size chips
                          Column {
                              Text(
                                  text = "Navigation Button Size",
                                  color = MaterialTheme.colorScheme.onSurface,
                                  fontSize = 14.sp,
                                  fontWeight = FontWeight.Bold,
                                  modifier = Modifier.padding(bottom = 4.dp)
                              )
                              Text(
                                  text = "Set larger buttons for active outdoor riding or wearing gloves.",
                                  fontSize = 11.sp,
                                  color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                  modifier = Modifier.padding(bottom = 8.dp)
                              )
                              Row(
                                  modifier = Modifier.fillMaxWidth(),
                                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                  val sizes = listOf(32 to "Ultra-Compact (32dp)", 36 to "Compact (36dp)", 40 to "Standard (40dp)")
                                  sizes.forEach { (size, label) ->
                                      val isSelected = btnSize == size
                                      Box(
                                          modifier = Modifier
                                              .weight(1f)
                                              .clip(RoundedCornerShape(8.dp))
                                              .background(if (isSelected) RiderOrange else MaterialTheme.colorScheme.surfaceVariant)
                                              .clickable {
                                                  scope.launch {
                                                      repository.saveSetting("button_size", size.toString())
                                                  }
                                              }
                                              .padding(vertical = 10.dp),
                                          contentAlignment = Alignment.Center
                                      ) {
                                          Text(
                                              text = label,
                                              color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                              fontSize = 11.sp,
                                              fontWeight = FontWeight.Bold,
                                              textAlign = TextAlign.Center
                                          )
                                      }
                                  }
                              }
                          }
                      }
                  }
              }



            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // App Selector Modal Dialog
    if (showAppSelector && activeSlotForSelection != null) {
        AppSelectorDialog(
            slotId = activeSlotForSelection!!,
            installedApps = installedApps,
            isLoading = isLoadingApps,
            onDismiss = {
                showAppSelector = false
                activeSlotForSelection = null
            },
            onSelectApp = { appInfo ->
                scope.launch {
                    repository.saveShortcut(activeSlotForSelection!!, appInfo?.packageName, appInfo?.appName)
                    showAppSelector = false
                    activeSlotForSelection = null
                }
            }
        )
    }
}

@Composable
fun MockShortcutSlot(slotId: Int, shortcuts: List<AppShortcut>, btnSize: Int, onClick: () -> Unit) {
    val shortcut = shortcuts.find { it.slotId == slotId }
    val packageName = shortcut?.packageName

    Box(
        modifier = Modifier
            .size(btnSize.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (packageName != null) RiderOrange.copy(alpha = 0.2f)
                else Color.White.copy(alpha = 0.05f)
            )
            .clickable { onClick() },
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
                imageVector = Icons.Default.Add,
                contentDescription = "Add Shortcut",
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size((btnSize * 0.45).dp)
            )
        }
    }
}

@Composable
fun MockNavIcon(imageVector: androidx.compose.ui.graphics.vector.ImageVector, btnSize: Int) {
    Box(
        modifier = Modifier
            .size(btnSize.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size((btnSize * 0.55).dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectorDialog(
    slotId: Int,
    installedApps: List<AppInfo>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelectApp: (AppInfo?) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = if (searchQuery.isEmpty()) {
        installedApps
    } else {
        installedApps.filter { it.appName.contains(searchQuery, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .testTag("app_selector_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Shortcut Slot $slotId Launcher",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(10.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = RiderOrange)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = RiderOrange,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    singleLine = true
                )

                if (isLoading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = RiderOrange)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredApps) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onSelectApp(app) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AndroidView(
                                    factory = { context ->
                                        ImageView(context).apply {
                                            scaleType = ImageView.ScaleType.FIT_CENTER
                                        }
                                    },
                                    update = { imageView ->
                                        try {
                                            val icon = imageView.context.packageManager.getApplicationIcon(app.packageName)
                                            imageView.setImageDrawable(icon)
                                        } catch (e: Exception) {
                                            imageView.setImageResource(android.R.drawable.sym_def_app_icon)
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = app.appName,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (filteredApps.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No matching apps found.",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = { onSelectApp(null) },
                        colors = ButtonDefaults.textButtonColors(contentColor = RiderRed)
                    ) {
                        Text("Clear Shortcut", fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
