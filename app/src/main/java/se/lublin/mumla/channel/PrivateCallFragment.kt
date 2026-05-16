package se.lublin.mumla.channel

import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Offset
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
import se.lublin.humla.IHumlaService
import se.lublin.humla.model.IChannel
import se.lublin.humla.model.IUser
import se.lublin.humla.model.TalkState
import se.lublin.humla.model.User
import se.lublin.humla.util.HumlaException
import se.lublin.humla.util.HumlaObserver
import se.lublin.humla.util.IHumlaObserver
import se.lublin.mumla.R
import se.lublin.mumla.app.MumlaActivity
import se.lublin.mumla.util.HumlaServiceFragment
import se.lublin.mumla.util.ModelUtils

class PrivateCallFragment : HumlaServiceFragment() {
    private var users by mutableStateOf<List<User>>(emptyList())
    private var selfSessionId by mutableStateOf(-1)
    private var serverId by mutableStateOf(-1L)
    private var memberStateVersion by mutableStateOf(0)
    private var localAvatarVersion by mutableStateOf(0)
    private lateinit var avatarPreferences: SharedPreferences

    private val observer: IHumlaObserver = object : HumlaObserver() {
        override fun onDisconnected(e: HumlaException?) {
            users = emptyList()
            selfSessionId = -1
            serverId = -1L
            memberStateVersion++
        }

        override fun onUserConnected(user: IUser?) {
            refreshUsers()
        }

        override fun onUserRemoved(user: IUser?, reason: String?) {
            refreshUsers()
        }

        override fun onUserJoinedChannel(user: IUser?, newChannel: IChannel?, oldChannel: IChannel?) {
            refreshUsers()
        }

        override fun onUserStateUpdated(user: IUser?) {
            refreshUsers()
        }

        override fun onUserTalkStateUpdated(user: IUser?) {
            memberStateVersion++
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        avatarPreferences = requireContext().getSharedPreferences(LOCAL_AVATAR_PREFS, 0)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PrivateCallUserList(
                    users = users,
                    selfSessionId = selfSessionId,
                    serverId = serverId,
                    memberStateVersion = memberStateVersion,
                    localAvatarVersion = localAvatarVersion,
                    localAvatarUriFor = { avatarPreferences.getString(localAvatarPreferenceKey(it), null) },
                    onUserClick = { user -> (requireActivity() as? MumlaActivity)?.openUserProfile(user.session) }
                )
            }
        }
    }

    override fun getServiceObserver(): IHumlaObserver = observer

    override fun onServiceBound(service: IHumlaService?) {
        refreshUsers()
    }

    private fun refreshUsers() {
        val currentService = service ?: return
        if (!currentService.isConnected) {
            users = emptyList()
            selfSessionId = -1
            return
        }

        val session = currentService.HumlaSession()
        selfSessionId = session.sessionId
        serverId = currentService.targetServer?.id ?: -1L
        users = ModelUtils.getChannelList(session.rootChannel)
            .flatMap { it.users }
            .filterIsInstance<User>()
            .filter { it.session != selfSessionId }
            .sortedWith(compareBy({ it.name.lowercase() }, { it.session }))
        memberStateVersion++
    }

    private fun localAvatarPreferenceKey(user: User): String {
        val identity = when {
            user.hash != null -> "hash:${user.hash}"
            user.userId >= 0 -> "id:${user.userId}"
            else -> "name:${user.name}"
        }
        return "member_avatar:$serverId:$identity"
    }

    companion object {
        private const val LOCAL_AVATAR_PREFS = "channel_member_local_avatars"
    }
}

@Composable
private fun PrivateCallUserList(
    users: List<User>,
    selfSessionId: Int,
    serverId: Long,
    memberStateVersion: Int,
    localAvatarVersion: Int,
    localAvatarUriFor: (User) -> String?,
    onUserClick: (User) -> Unit
) {
    if (users.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = stringResource(R.string.private_call_no_users),
                style = TextStyle(
                    color = Color(0xFF666666),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
    ) {
        items(users, key = { it.session }) { user ->
            PrivateCallUserRow(
                user = user,
                enabled = user.session != selfSessionId,
                serverId = serverId,
                memberStateVersion = memberStateVersion,
                localAvatarVersion = localAvatarVersion,
                localAvatarUri = localAvatarUriFor(user),
                onClick = { onUserClick(user) }
            )
        }
    }
}

@Composable
private fun PrivateCallUserRow(
    user: User,
    enabled: Boolean,
    serverId: Long,
    memberStateVersion: Int,
    localAvatarVersion: Int,
    localAvatarUri: String?,
    onClick: () -> Unit
) {
    val speaking = remember(memberStateVersion, user) {
        user.isSpeakingForPrivateCall()
    }
    val muted = remember(memberStateVersion, user) {
        user.isMutedForPrivateCallAvatar()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PrivateCallSpeakingAvatar(
            user = user,
            serverId = serverId,
            localAvatarVersion = localAvatarVersion,
            localAvatarUri = localAvatarUri,
            speaking = speaking,
            muted = muted
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 12.dp)
        ) {
            BasicText(
                text = user.name,
                maxLines = 1,
                style = TextStyle(
                    color = Color(0xFF202124),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            BasicText(
                text = user.channel?.name ?: "",
                maxLines = 1,
                style = TextStyle(
                    color = Color(0xFF6F7672),
                    fontSize = 13.sp
                )
            )
        }
        Spacer(modifier = Modifier.size(8.dp))
        Image(
            painter = painterResource(R.drawable.call_24px),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color(0xFF1B7F5F)),
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun PrivateCallSpeakingAvatar(
    user: User,
    serverId: Long,
    localAvatarVersion: Int,
    localAvatarUri: String?,
    speaking: Boolean,
    muted: Boolean
) {
    val accentColor = when {
        muted -> MutedRed
        speaking -> SpeakingGreen
        else -> Color.Transparent
    }
    val highlighted = speaking || muted
    val transition = rememberInfiniteTransition(label = "private-call-user-speaking")
    val rippleScale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "private-call-avatar-ripple-scale"
    )
    val rippleAlpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "private-call-avatar-ripple-alpha"
    )

    Box(
        modifier = Modifier.size(50.dp),
        contentAlignment = Alignment.Center
    ) {
        if (highlighted) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .scale(rippleScale)
                    .alpha(rippleAlpha)
                    .border(2.dp, accentColor, CircleShape)
                    .background(accentColor.copy(alpha = 0.18f), CircleShape)
            )
        }
        PrivateCallAvatarImage(
            user = user,
            serverId = serverId,
            localAvatarVersion = localAvatarVersion,
            localAvatarUri = localAvatarUri,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .then(
                    if (highlighted) {
                        Modifier.border(2.dp, accentColor, CircleShape)
                    } else {
                        Modifier.border(1.dp, Color.Transparent, CircleShape)
                    }
                )
        )
    }
}

@Composable
private fun PrivateCallAvatarImage(
    user: User,
    serverId: Long,
    localAvatarVersion: Int,
    localAvatarUri: String?,
    modifier: Modifier
) {
    val context = LocalContext.current
    val texture = user.texture
    val serverBitmap = remember(texture) {
        texture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    val localBitmap = remember(localAvatarUri, localAvatarVersion) {
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
            contentScale = ContentScale.Crop
        )
    } else {
        PrivateCallLetterAvatar(user = user, serverId = serverId, modifier = modifier)
    }
}

@Composable
private fun PrivateCallLetterAvatar(user: User, serverId: Long, modifier: Modifier) {
    Box(
        modifier = modifier.background(privateCallLetterAvatarColor(serverId, user), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = user.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            style = TextStyle(
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        )
    }
}

private fun User.isSpeakingForPrivateCall(): Boolean {
    return !isSelfDeafened &&
        !isSelfMuted &&
        !isDeafened &&
        !isMuted &&
        !isSuppressed &&
        (talkState == TalkState.TALKING ||
            talkState == TalkState.SHOUTING ||
            talkState == TalkState.WHISPERING)
}

private fun User.isMutedForPrivateCallAvatar(): Boolean {
    return isSelfMuted || isMuted || isSuppressed
}

private fun privateCallLetterAvatarColor(serverId: Long, user: User): Color {
    val key = "$serverId:${user.hash ?: user.userId.takeIf { it >= 0 } ?: user.name}"
    val hue = ((key.hashCode().toUInt().toInt() and 0x7fffffff) % 360).toFloat()
    return Color.hsv(hue, 0.72f, 0.82f)
}

private val SpeakingGreen = Color(0xFF22C55E)
private val MutedRed = Color(0xFFEF4444)
