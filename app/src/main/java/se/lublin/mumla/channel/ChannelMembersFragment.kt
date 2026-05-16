package se.lublin.mumla.channel

import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
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
import se.lublin.mumla.Settings
import se.lublin.mumla.app.MumlaActivity
import se.lublin.mumla.util.HumlaServiceFragment

class ChannelMembersFragment : HumlaServiceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {
    private var channelId = -1
    private var members by mutableStateOf<List<User>>(emptyList())
    private var serverId by mutableStateOf(-1L)
    private var selfSessionId by mutableStateOf(-1)
    private var optimisticSelfMuted by mutableStateOf(false)
    private var optimisticSelfDeafened by mutableStateOf(false)
    private var memberStateVersion by mutableStateOf(0)
    private var localAvatarVersion by mutableStateOf(0)
    private lateinit var avatarPreferences: SharedPreferences
    private lateinit var defaultPreferences: SharedPreferences

    private val observer: IHumlaObserver = object : HumlaObserver() {
        override fun onDisconnected(e: HumlaException?) {
            members = emptyList()
            memberStateVersion++
        }

        override fun onUserConnected(user: IUser?) {
            refreshMembers()
        }

        override fun onUserRemoved(user: IUser?, reason: String?) {
            refreshMembers()
        }

        override fun onUserJoinedChannel(user: IUser?, newChannel: IChannel?, oldChannel: IChannel?) {
            refreshMembers()
        }

        override fun onUserStateUpdated(user: IUser?) {
            refreshMembers()
        }

        override fun onUserTalkStateUpdated(user: IUser?) {
            refreshMembers()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        channelId = arguments?.getInt(ARG_CHANNEL_ID, -1) ?: -1
        avatarPreferences = requireContext().getSharedPreferences(LOCAL_AVATAR_PREFS, 0)
        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext()).also {
            it.registerOnSharedPreferenceChangeListener(this)
        }
        syncOptimisticSelfState()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MembersList(
                    users = members,
                    serverId = serverId,
                    selfSessionId = selfSessionId,
                    optimisticSelfMuted = optimisticSelfMuted,
                    optimisticSelfDeafened = optimisticSelfDeafened,
                    memberStateVersion = memberStateVersion,
                    localAvatarVersion = localAvatarVersion,
                    localAvatarUriFor = { avatarPreferences.getString(localAvatarPreferenceKey(it), null) },
                    onUserClick = { user ->
                        (requireActivity() as? MumlaActivity)?.openUserProfile(user.session)
                    }
                )
            }
        }
    }

    override fun getServiceObserver(): IHumlaObserver {
        return observer
    }

    override fun onDestroy() {
        defaultPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onServiceBound(service: IHumlaService?) {
        refreshMembers()
    }

    private fun refreshMembers() {
        val currentService = service ?: return
        if (!currentService.isConnected) {
            members = emptyList()
            memberStateVersion++
            return
        }

        val channel = currentService.HumlaSession().getChannel(channelId) ?: return
        serverId =
            currentService.targetServer?.id ?: -1
        selfSessionId = currentService.HumlaSession().sessionId
        members = channel.users.filterIsInstance<User>().toList()
        memberStateVersion++
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == Settings.PREF_MUTED || key == Settings.PREF_DEAFENED) {
            syncOptimisticSelfState()
            memberStateVersion++
        }
    }

    private fun syncOptimisticSelfState() {
        val settings = Settings.getInstance(requireContext())
        optimisticSelfMuted = settings.isMuted
        optimisticSelfDeafened = settings.isDeafened
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
        private const val ARG_CHANNEL_ID = "channel_id"
        private const val LOCAL_AVATAR_PREFS = "channel_member_local_avatars"
        private val SpeakingGreen = Color(0xFF22C55E)

        @JvmStatic
        fun newInstance(channelId: Int): ChannelMembersFragment {
            val fragment = ChannelMembersFragment()
            fragment.arguments = Bundle().apply { putInt(ARG_CHANNEL_ID, channelId) }
            return fragment
        }

        @Composable
        private fun MembersList(
            users: List<User>,
            serverId: Long,
            selfSessionId: Int,
            optimisticSelfMuted: Boolean,
            optimisticSelfDeafened: Boolean,
            memberStateVersion: Int,
            localAvatarVersion: Int,
            localAvatarUriFor: (User) -> String?,
            onUserClick: (User) -> Unit
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 10.dp,
                    vertical = 6.dp
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(users, key = { it.session }) { user ->
                    MemberRow(
                        user = user,
                        serverId = serverId,
                        selfSessionId = selfSessionId,
                        optimisticSelfMuted = optimisticSelfMuted,
                        optimisticSelfDeafened = optimisticSelfDeafened,
                        memberStateVersion = memberStateVersion,
                        localAvatarVersion = localAvatarVersion,
                        localAvatarUri = localAvatarUriFor(user),
                        onUserClick = { onUserClick(user) }
                    )
                }
            }
        }

        @Composable
        private fun MemberRow(
            user: User,
            serverId: Long,
            selfSessionId: Int,
            optimisticSelfMuted: Boolean,
            optimisticSelfDeafened: Boolean,
            memberStateVersion: Int,
            localAvatarVersion: Int,
            localAvatarUri: String?,
            onUserClick: () -> Unit
        ) {
            val isSelf = user.session == selfSessionId
            val effectiveSelfMuted = if (isSelf) optimisticSelfMuted else user.isSelfMuted
            val effectiveSelfDeafened = if (isSelf) optimisticSelfDeafened else user.isSelfDeafened
            val speaking = remember(memberStateVersion, user) {
                user.isSpeaking(effectiveSelfMuted, effectiveSelfDeafened)
            }
            val muted = remember(memberStateVersion, user) {
                user.isMutedForAvatar(effectiveSelfMuted)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .clickable(onClick = onUserClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpeakingAvatar(
                    user = user,
                    serverId = serverId,
                    localAvatarVersion = localAvatarVersion,
                    localAvatarUri = localAvatarUri,
                    speaking = speaking,
                    muted = muted
                )
                BasicText(
                    text = user.name,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp, end = 12.dp),
                    maxLines = 1,
                    style = TextStyle(
                        color = Color(0xFF333333),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                StatusArea(user, speaking, effectiveSelfMuted, effectiveSelfDeafened)
            }
        }

        @Composable
        private fun StatusArea(
            user: User,
            speaking: Boolean,
            effectiveSelfMuted: Boolean,
            effectiveSelfDeafened: Boolean
        ) {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MicrophoneStatusIcon(user, effectiveSelfMuted)
                Spacer(modifier = Modifier.size(24.dp))
                SpeakerStatusIcon(user, effectiveSelfDeafened)
                if (speaking) {
                    Spacer(modifier = Modifier.size(24.dp))
                    EqualizerIcon()
                }
            }
        }

        @Composable
        private fun MicrophoneStatusIcon(user: User, effectiveSelfMuted: Boolean) {
            val blocked = effectiveSelfMuted || user.isMuted || user.isSuppressed
            StatusIcon(
                drawable = if (blocked) {
                    R.drawable.ic_action_microphone_muted
                } else {
                    R.drawable.ic_action_microphone
                },
                tint = if (blocked) MutedRed else NormalStateIcon
            )
        }

        @Composable
        private fun SpeakerStatusIcon(user: User, effectiveSelfDeafened: Boolean) {
            val blocked = effectiveSelfDeafened || user.isDeafened
            StatusIcon(
                drawable = if (blocked) {
                    R.drawable.ic_action_audio_muted
                } else {
                    R.drawable.ic_action_audio_on
                },
                tint = if (blocked) MutedRed else NormalStateIcon
            )
        }

        @Composable
        private fun SpeakingAvatar(
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
            val transition = rememberInfiniteTransition(label = "member-speaking")
            val rippleScale by transition.animateFloat(
                initialValue = 0.92f,
                targetValue = 1.35f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "avatar-ripple-scale"
            )
            val rippleAlpha by transition.animateFloat(
                initialValue = 0.55f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "avatar-ripple-alpha"
            )

            Box(
                modifier = Modifier
                    .size(50.dp),
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
                AvatarImage(
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
        private fun AvatarImage(
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
                LetterAvatar(user = user, serverId = serverId, modifier = modifier)
            }
        }

        @Composable
        private fun LetterAvatar(user: User, serverId: Long, modifier: Modifier) {
            Box(
                modifier = modifier.background(letterAvatarColor(serverId, user), CircleShape),
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

        @Composable
        private fun StatusIcon(drawable: Int, tint: Color) {
            Image(
                painter = painterResource(drawable),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(tint)
            )
        }

        @Composable
        private fun EqualizerIcon() {
            val transition = rememberInfiniteTransition(label = "member-equalizer")
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(620, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "equalizer-progress"
            )

            Canvas(modifier = Modifier.size(24.dp)) {
                val phases = floatArrayOf(0f, 0.45f, 0.9f, 1.35f)
                val spacing = size.width / (phases.size + 1)
                val centerY = size.height / 2f
                val minHeight = size.height * 0.22f
                val maxHeight = size.height * 0.78f
                phases.forEachIndexed { index, phase ->
                    val wave = kotlin.math.sin((progress + phase) * Math.PI * 2).toFloat()
                    val normalized = (wave + 1f) / 2f
                    val barHeight = minHeight + (maxHeight - minHeight) * normalized
                    val x = spacing * (index + 1)
                    drawLine(
                        color = SpeakingGreen,
                        start = Offset(x, centerY - barHeight / 2f),
                        end = Offset(x, centerY + barHeight / 2f),
                        strokeWidth = size.width * 0.11f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        private fun User.isSpeaking(effectiveSelfMuted: Boolean, effectiveSelfDeafened: Boolean): Boolean {
            return !effectiveSelfDeafened &&
                !effectiveSelfMuted &&
                !isDeafened &&
                !isMuted &&
                !isSuppressed &&
                (talkState == TalkState.TALKING ||
                    talkState == TalkState.SHOUTING ||
                    talkState == TalkState.WHISPERING)
        }

        private fun User.isMutedForAvatar(effectiveSelfMuted: Boolean): Boolean {
            return effectiveSelfMuted || isMuted || isSuppressed
        }

        private fun letterAvatarColor(serverId: Long, user: User): Color {
            val key = "$serverId:${user.hash ?: user.userId.takeIf { it >= 0 } ?: user.name}"
            val hue = ((key.hashCode().toUInt().toInt() and 0x7fffffff) % 360).toFloat()
            return Color.hsv(hue, 0.72f, 0.82f)
        }

        private val NormalStateIcon = Color(0xFF4B5563)
        private val MutedRed = Color(0xFFEF4444)
    }
}
