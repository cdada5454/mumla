package se.lublin.mumla.app

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.AttrRes
import androidx.annotation.ColorRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import se.lublin.mumla.R
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR

class DrawerAdapter(
    private val context: Context,
    private val provider: DrawerDataProvider
) {

    interface DrawerDataProvider {
        fun isConnected(): Boolean
        fun getConnectedServerName(): String?
        fun getDrawerProfileName(): String
        fun getDrawerAvatarTexture(): ByteArray?
        fun getDrawerAvatarUri(): String?
    }

    interface DrawerSelectionListener {
        fun onDrawerRowClicked(id: Int)
        fun onDrawerProfileClicked()
        fun onDonateClicked()
    }

    open class DrawerRow(@JvmField val id: Int, @JvmField val title: String)
    class DrawerHeader(id: Int, title: String) : DrawerRow(id, title)
    class DrawerItem(id: Int, title: String, @JvmField val icon: Int) : DrawerRow(id, title)

    private val rows = listOf(
        DrawerHeader(HEADER_CONNECTED_SERVER, context.getString(R.string.drawer_not_connected)),
        DrawerItem(ITEM_SERVER, context.getString(R.string.drawer_server), R.drawable.radio_24px),
        DrawerItem(ITEM_CALL, context.getString(R.string.drawer_call), R.drawable.call_24px),
        DrawerItem(ITEM_CHAT, context.getString(R.string.chat), R.drawable.conversation_24px),
        DrawerItem(ITEM_QR_CODE, context.getString(R.string.drawer_qr_code), R.drawable.qr_code_2_24px),
        DrawerItem(ITEM_INFO, context.getString(R.string.information), R.drawable.info_24px),
        DrawerItem(ITEM_DISCONNECT, context.getString(R.string.disconnect), R.drawable.cancel_24px),
        DrawerItem(ITEM_SETTINGS, context.getString(R.string.action_settings), R.drawable.settings_24px)
    )
    private var composeView: ComposeView? = null
    private var listener: DrawerSelectionListener? = null
    private var selectedItemId: Int = ITEM_FAVOURITES

    fun bind(view: ComposeView, listener: DrawerSelectionListener) {
        composeView = view
        this.listener = listener
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        render()
    }

    fun notifyDataSetChanged() {
        render()
    }

    fun setSelectedItem(id: Int) {
        selectedItemId = id
        render()
    }

    fun getItemWithId(id: Int): DrawerRow? {
        return rows.firstOrNull { it.id == id }
    }

    private fun render() {
        val currentListener = listener ?: return
        val donateLinkResId = context.resources.getIdentifier("donate_link_foss", "string", context.packageName)
        val showDonate = donateLinkResId != 0
        val colors = resolveDrawerColors(context)
        composeView?.setBackgroundColor(colors.backgroundArgb)
        composeView?.setContent {
            DrawerContent(
                rows = rows.map { row ->
                    if (row.id == HEADER_CONNECTED_SERVER && provider.isConnected()) {
                        DrawerHeader(HEADER_CONNECTED_SERVER, provider.getConnectedServerName() ?: row.title)
                    } else {
                        row
                    }
                },
                connected = provider.isConnected(),
                showDonate = showDonate,
                profileName = provider.getDrawerProfileName(),
                avatarTexture = provider.getDrawerAvatarTexture(),
                avatarUri = provider.getDrawerAvatarUri(),
                selectedItemId = selectedItemId,
                colors = colors,
                onProfileClick = { currentListener.onDrawerProfileClicked() },
                onRowClick = { currentListener.onDrawerRowClicked(it.id) },
                onDonateClick = { currentListener.onDonateClicked() }
            )
        }
    }

    companion object {
        const val HEADER_CONNECTED_SERVER = 0
        const val ITEM_SERVER = 1
        const val ITEM_PINNED_CHANNELS = 2
        const val ITEM_INFO = 3
        const val ITEM_ACCESS_TOKENS = 4
        const val HEADER_SERVERS = 5
        const val ITEM_FAVOURITES = 6
        const val ITEM_SETTINGS = 10
        const val ITEM_CHAT = 11
        const val ITEM_DISCONNECT = 12
        const val ITEM_QR_CODE = 13
        const val ITEM_CALL = 14
    }
}

@Composable
private fun DrawerContent(
    rows: List<DrawerAdapter.DrawerRow>,
    connected: Boolean,
    showDonate: Boolean,
    profileName: String,
    avatarTexture: ByteArray?,
    avatarUri: String?,
    selectedItemId: Int,
    colors: DrawerColors,
    onProfileClick: () -> Unit,
    onRowClick: (DrawerAdapter.DrawerRow) -> Unit,
    onDonateClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(colors.background)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Top
    ) {
        DrawerProfileHeader(
            profileName = profileName,
            avatarTexture = avatarTexture,
            avatarUri = avatarUri,
            colors = colors,
            onClick = onProfileClick
        )
        Spacer(modifier = Modifier.height(35.dp))
        if (showDonate) {
            Box(modifier = Modifier.padding(horizontal = 10.dp)) {
                DrawerActionRow(
                    title = stringResource(R.string.donate_foss),
                    icon = R.drawable.ic_action_favourite_on,
                    enabled = true,
                    selected = false,
                    colors = colors,
                    onClick = onDonateClick
                )
            }
        }
        rows.forEach { row ->
            Box(modifier = Modifier.padding(horizontal = 10.dp)) {
                DrawerMenuRow(
                    row = row,
                    enabled = isRowEnabled(row, connected),
                    selected = isRowSelected(row, selectedItemId),
                    colors = colors,
                    onClick = onRowClick
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@Composable
private fun DrawerProfileHeader(
    profileName: String,
    avatarTexture: ByteArray?,
    avatarUri: String?,
    colors: DrawerColors,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val bitmap = remember(avatarTexture, avatarUri) {
        avatarTexture?.let {
            BitmapFactory.decodeByteArray(it, 0, it.size)
        } ?: avatarUri?.let {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(it))?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
        }
    }
    val headerHeight = 230.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .clickable(onClick = onClick)
            .background(colors.profileBackground),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            val imageBitmap = bitmap.asImageBitmap()
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Black,
                                    0.16f to Color.Black.copy(alpha = 0.70f),
                                    0.34f to Color.Transparent,
                                    1.0f to Color.Transparent
                                )
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .blur(18.dp),
                    contentScale = ContentScale.Crop
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.20f),
                                Color.Black.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.62f to Color.Transparent,
                                    0.82f to Color.Black.copy(alpha = 0.76f),
                                    1.0f to Color.Black
                                )
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .blur(18.dp),
                    contentScale = ContentScale.Crop
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(86.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.22f),
                                Color.Black.copy(alpha = 0.58f)
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(colors.avatarBackground),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = profileName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = TextStyle(
                        color = colors.avatarContent,
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
        BasicText(
            text = profileName,
            style = TextStyle(
                color = colors.avatarContent,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = 18.dp)
        )
    }
}

@Composable
private fun DrawerMenuRow(
    row: DrawerAdapter.DrawerRow,
    enabled: Boolean,
    selected: Boolean,
    colors: DrawerColors,
    onClick: (DrawerAdapter.DrawerRow) -> Unit
) {
    val icon = when (row) {
        is DrawerAdapter.DrawerItem -> row.icon
        is DrawerAdapter.DrawerHeader -> if (row.id == DrawerAdapter.HEADER_CONNECTED_SERVER) {
            R.drawable.database_24px
        } else {
            R.drawable.ic_action_channels
        }
        else -> R.drawable.ic_action_channels
    }
    DrawerActionRow(
        title = row.title,
        icon = icon,
        enabled = enabled,
        isHeader = row is DrawerAdapter.DrawerHeader,
        selected = selected,
        colors = colors,
        onClick = { onClick(row) }
    )
}

@Composable
private fun DrawerActionRow(
    title: String,
    icon: Int,
    enabled: Boolean,
    isHeader: Boolean = false,
    selected: Boolean = false,
    colors: DrawerColors,
    onClick: () -> Unit
) {
    val contentColor = when {
        selected -> colors.selectedContent
        isHeader -> colors.headerContent
        else -> colors.content
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (selected) 48.dp else 52.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                when {
                    selected -> colors.selectedRowBackground
                    else -> Color.Transparent
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = if (selected) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(contentColor),
            modifier = Modifier
                .size(32.dp)
                .padding(5.dp)
        )
        BasicText(
            text = title,
            style = TextStyle(
                color = contentColor,
                fontSize = 15.sp,
                fontWeight = if (selected || isHeader) FontWeight.Bold else FontWeight.Medium
            ),
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        )
    }
}

private data class DrawerColors(
    val backgroundArgb: Int,
    val background: Color,
    val rowBackground: Color,
    val headerBackground: Color,
    val content: Color,
    val headerContent: Color,
    val selectedRowBackground: Color,
    val selectedContent: Color,
    val profileBackground: Color,
    val profileContent: Color,
    val avatarBackground: Color,
    val avatarContent: Color
)

private fun resolveDrawerColors(context: Context): DrawerColors {
    val dynamicContext = DynamicColors.wrapContextIfAvailable(context)
    val backgroundArgb = resolveDynamicColor(
        context,
        dynamicContext,
        MaterialR.attr.colorSurface,
        R.color.drawer_background
    )
    return DrawerColors(
        backgroundArgb = backgroundArgb,
        background = Color(backgroundArgb),
        rowBackground = Color(
            resolveDynamicColor(
                context,
                dynamicContext,
                MaterialR.attr.colorPrimaryContainer,
                R.color.drawer_item_background
            )
        ),
        headerBackground = Color(
            resolveDynamicColor(
                context,
                dynamicContext,
                MaterialR.attr.colorPrimaryContainer,
                R.color.drawer_item_background
            )
        ),
        content = Color(
            resolveDynamicColor(
                context,
                dynamicContext,
                MaterialR.attr.colorOnPrimaryContainer,
                R.color.drawer_item_text
            )
        ),
        headerContent = Color(
            resolveDynamicColor(
                context,
                dynamicContext,
                MaterialR.attr.colorOnPrimaryContainer,
                R.color.drawer_item_text
            )
        ),
        selectedRowBackground = Color(
            resolveDynamicColor(
                context,
                dynamicContext,
                MaterialR.attr.colorPrimaryContainer,
                R.color.drawer_item_background_checked
            )
        ),
        selectedContent = Color(
            resolveDynamicColor(
                context,
                dynamicContext,
                MaterialR.attr.colorOnPrimaryContainer,
                R.color.drawer_item_text
            )
        ),
        profileBackground = Color(
            resolveDynamicColor(
                context,
                dynamicContext,
                MaterialR.attr.colorPrimaryContainer,
                R.color.drawer_item_background_checked
            )
        ),
        profileContent = Color(
            resolveDynamicColor(
                context,
                dynamicContext,
                MaterialR.attr.colorOnPrimaryContainer,
                R.color.drawer_item_text
            )
        ),
        avatarBackground = Color(
            resolveDynamicColor(
                context,
                dynamicContext,
                AppCompatR.attr.colorPrimary,
                R.color.md_theme_light_primary
            )
        ),
        avatarContent = Color.White
    )
}

private fun resolveDynamicColor(
    context: Context,
    dynamicContext: Context,
    @AttrRes attr: Int,
    @ColorRes fallbackColorRes: Int
): Int {
    return MaterialColors.getColor(dynamicContext, attr, ContextCompat.getColor(context, fallbackColorRes))
}

private fun isRowEnabled(row: DrawerAdapter.DrawerRow, connected: Boolean): Boolean {
    if (row is DrawerAdapter.DrawerHeader) {
        return row.id == DrawerAdapter.HEADER_CONNECTED_SERVER
    }
    return when (row.id) {
        DrawerAdapter.ITEM_SERVER,
        DrawerAdapter.ITEM_QR_CODE,
        DrawerAdapter.ITEM_INFO,
        DrawerAdapter.ITEM_DISCONNECT -> true
        DrawerAdapter.ITEM_CALL,
        DrawerAdapter.ITEM_CHAT -> connected
        else -> true
    }
}

private fun isRowSelected(row: DrawerAdapter.DrawerRow, selectedItemId: Int): Boolean {
    if (row.id == DrawerAdapter.ITEM_DISCONNECT) {
        return false
    }
    return row.id == when (selectedItemId) {
        DrawerAdapter.ITEM_FAVOURITES -> DrawerAdapter.HEADER_CONNECTED_SERVER
        else -> selectedItemId
    }
}
