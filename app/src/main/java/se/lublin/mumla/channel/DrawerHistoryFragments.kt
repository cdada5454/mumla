package se.lublin.mumla.channel

import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import se.lublin.humla.IHumlaService
import se.lublin.humla.model.IChannel
import se.lublin.humla.model.IMessage
import se.lublin.humla.model.IUser
import se.lublin.humla.util.HumlaException
import se.lublin.humla.util.HumlaObserver
import se.lublin.humla.util.IHumlaObserver
import se.lublin.mumla.R
import se.lublin.mumla.app.MumlaActivity
import se.lublin.mumla.service.ChatHistoryItem
import se.lublin.mumla.service.PrivateCallHistoryItem
import se.lublin.mumla.util.HumlaServiceFragment
import se.lublin.mumla.util.ModelUtils

class ChatHistoryFragment : HumlaServiceFragment() {
    private var history by mutableStateOf<List<ChatHistoryItem>>(emptyList())
    private var onlineUsers by mutableStateOf<Map<Int, IUser>>(emptyMap())
    private var selfSessionId by mutableStateOf(-1)
    private var serverId by mutableStateOf(-1L)
    private lateinit var avatarPreferences: SharedPreferences

    private val observer: IHumlaObserver = object : HumlaObserver() {
        override fun onMessageLogged(message: IMessage) {
            refreshHistory()
        }

        override fun onUserConnected(user: IUser?) {
            refreshHistory()
        }

        override fun onUserRemoved(user: IUser?, reason: String?) {
            refreshHistory()
        }

        override fun onUserJoinedChannel(user: IUser?, newChannel: IChannel?, oldChannel: IChannel?) {
            refreshHistory()
        }

        override fun onUserStateUpdated(user: IUser?) {
            refreshHistory()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        avatarPreferences = requireContext().getSharedPreferences(LOCAL_AVATAR_PREFS, 0)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val historySessions = history.map { it.session }.toSet()
                val rows = history.map { item ->
                    HistoryRowModel(
                        session = item.session,
                        name = onlineUsers[item.session]?.name ?: item.userName,
                        subtitle = item.preview,
                        time = item.latestTime,
                        user = onlineUsers[item.session],
                        showOnlineIcon = false,
                        enabled = onlineUsers.containsKey(item.session),
                    )
                } + onlineUsers.values
                    .filter { it.session != selfSessionId && it.session !in historySessions }
                    .sortedWith(compareBy({ it.name.lowercase() }, { it.session }))
                    .map { user ->
                        HistoryRowModel(
                            session = user.session,
                            name = user.name,
                            subtitle = user.channel?.name.orEmpty(),
                            time = null,
                            user = user,
                            showOnlineIcon = false,
                            enabled = true,
                        )
                    }
                HistoryList(
                    emptyText = stringResource(R.string.private_call_no_users),
                    rows = rows,
                    serverId = serverId,
                    localAvatarUriFor = { user -> avatarPreferences.getString(localAvatarPreferenceKey(user, serverId), null) },
                    onRowClick = { session -> (requireActivity() as? MumlaActivity)?.openChatWithUser(session) },
                )
            }
        }
    }

    override fun getServiceObserver(): IHumlaObserver = observer

    override fun onServiceBound(service: IHumlaService?) {
        refreshHistory()
    }

    private fun refreshHistory() {
        val currentService = service
        if (currentService == null || !currentService.isConnected) {
            history = emptyList()
            onlineUsers = emptyMap()
            selfSessionId = -1
            serverId = -1L
            return
        }
        serverId = currentService.targetServer?.id ?: -1L
        selfSessionId = runCatching { currentService.HumlaSession().sessionId }.getOrDefault(-1)
        onlineUsers = getOnlineUsers(currentService)
        history = currentService.getRecentChatHistory()
    }
}

class CallHistoryFragment : HumlaServiceFragment() {
    private var history by mutableStateOf<List<PrivateCallHistoryItem>>(emptyList())
    private var onlineUsers by mutableStateOf<Map<Int, IUser>>(emptyMap())
    private var selfSessionId by mutableStateOf(-1)
    private var serverId by mutableStateOf(-1L)
    private lateinit var avatarPreferences: SharedPreferences

    private val observer: IHumlaObserver = object : HumlaObserver() {
        override fun onMessageLogged(message: IMessage) {
            refreshHistory()
        }

        override fun onDisconnected(e: HumlaException?) {
            history = emptyList()
            onlineUsers = emptyMap()
            selfSessionId = -1
            serverId = -1L
        }

        override fun onUserConnected(user: IUser?) {
            refreshHistory()
        }

        override fun onUserRemoved(user: IUser?, reason: String?) {
            refreshHistory()
        }

        override fun onUserJoinedChannel(user: IUser?, newChannel: IChannel?, oldChannel: IChannel?) {
            refreshHistory()
        }

        override fun onUserStateUpdated(user: IUser?) {
            refreshHistory()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        avatarPreferences = requireContext().getSharedPreferences(LOCAL_AVATAR_PREFS, 0)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val historySessions = history.map { it.session }.toSet()
                val rows = history.map { item ->
                    val online = onlineUsers[item.session]
                    HistoryRowModel(
                        session = item.session,
                        name = online?.name ?: item.userName,
                        subtitle = "",
                        time = item.time,
                        user = online,
                        showOnlineIcon = online != null,
                        enabled = online != null,
                    )
                } + onlineUsers.values
                    .filter { it.session != selfSessionId && it.session !in historySessions }
                    .sortedWith(compareBy({ it.name.lowercase() }, { it.session }))
                    .map { user ->
                        HistoryRowModel(
                            session = user.session,
                            name = user.name,
                            subtitle = user.channel?.name.orEmpty(),
                            time = null,
                            user = user,
                            showOnlineIcon = true,
                            enabled = true,
                        )
                    }
                HistoryList(
                    emptyText = stringResource(R.string.private_call_no_users),
                    rows = rows,
                    serverId = serverId,
                    localAvatarUriFor = { user -> avatarPreferences.getString(localAvatarPreferenceKey(user, serverId), null) },
                    onRowClick = { session -> (requireActivity() as? MumlaActivity)?.openUserProfile(session) },
                )
            }
        }
    }

    override fun getServiceObserver(): IHumlaObserver = observer

    override fun onServiceBound(service: IHumlaService?) {
        refreshHistory()
    }

    private fun refreshHistory() {
        val currentService = service
        if (currentService == null || !currentService.isConnected) {
            history = emptyList()
            onlineUsers = emptyMap()
            selfSessionId = -1
            serverId = -1L
            return
        }
        serverId = currentService.targetServer?.id ?: -1L
        selfSessionId = runCatching { currentService.HumlaSession().sessionId }.getOrDefault(-1)
        onlineUsers = getOnlineUsers(currentService)
        history = currentService.getPrivateCallHistory()
    }
}

@Composable
private fun HistoryList(
    emptyText: String,
    rows: List<HistoryRowModel>,
    serverId: Long,
    localAvatarUriFor: (IUser) -> String?,
    onRowClick: (Int) -> Unit,
) {
    if (rows.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.text.BasicText(
                text = emptyText,
                style = TextStyle(
                    color = Color(0xFF666666),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
    ) {
        itemsIndexed(rows, key = { index, row -> "${row.session}:${row.time}:$index" }) { _, row ->
            HistoryRow(
                row = row,
                serverId = serverId,
                localAvatarUri = row.user?.let(localAvatarUriFor),
                onClick = { onRowClick(row.session) },
            )
        }
    }
}

@Composable
private fun HistoryRow(
    row: HistoryRowModel,
    serverId: Long,
    localAvatarUri: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = row.enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HistoryAvatar(
            user = row.user,
            name = row.name,
            serverId = serverId,
            localAvatarUri = localAvatarUri,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 12.dp),
        ) {
            androidx.compose.foundation.text.BasicText(
                text = row.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = Color(0xFF202124),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            if (row.subtitle.isNotBlank()) {
                androidx.compose.foundation.text.BasicText(
                    text = row.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = Color(0xFF6F7672),
                        fontSize = 13.sp,
                    ),
                )
            }
        }
        androidx.compose.foundation.text.BasicText(
            text = row.time?.let(::formatHistoryTime).orEmpty(),
            maxLines = 1,
            style = TextStyle(
                color = Color(0xFF7B827E),
                fontSize = 12.sp,
                textAlign = TextAlign.End,
            ),
        )
        if (row.showOnlineIcon) {
            Spacer(modifier = Modifier.size(10.dp))
            Image(
                painter = painterResource(R.drawable.globe_24px),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color(0xFF22C55E)),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun HistoryAvatar(
    user: IUser?,
    name: String,
    serverId: Long,
    localAvatarUri: String?,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val texture = user?.texture
    val serverBitmap = remember(texture) {
        texture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    val localBitmap = remember(localAvatarUri) {
        localAvatarUri?.let {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(it))?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
        }
    }
    val bitmap = serverBitmap ?: localBitmap
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier.background(historyLetterAvatarColor(serverId, name), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.text.BasicText(
                text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

private fun getOnlineUsers(service: IHumlaService): Map<Int, IUser> {
    return runCatching {
        ModelUtils.getChannelList(service.HumlaSession().rootChannel)
            .flatMap { it.users }
            .associateBy { it.session }
    }.getOrDefault(emptyMap())
}

private fun localAvatarPreferenceKey(user: IUser, serverId: Long): String {
    val identity = when {
        user.hash != null -> "hash:${user.hash}"
        user.userId >= 0 -> "id:${user.userId}"
        else -> "name:${user.name}"
    }
    return "member_avatar:$serverId:$identity"
}

private fun formatHistoryTime(time: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time))
}

private fun historyLetterAvatarColor(serverId: Long, name: String): Color {
    val key = "$serverId:$name"
    val hue = ((key.hashCode().toUInt().toInt() and 0x7fffffff) % 360).toFloat()
    return Color.hsv(hue, 0.72f, 0.82f)
}

private data class HistoryRowModel(
    val session: Int,
    val name: String,
    val subtitle: String,
    val time: Long?,
    val user: IUser?,
    val showOnlineIcon: Boolean,
    val enabled: Boolean,
)

private const val LOCAL_AVATAR_PREFS = "channel_member_local_avatars"
