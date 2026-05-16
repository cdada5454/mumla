/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package se.lublin.mumla.channel

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.util.Log
import android.view.KeyEvent.KEYCODE_ENTER
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo.IME_NULL
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.exifinterface.media.ExifInterface
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.regex.Pattern
import se.lublin.humla.IHumlaService
import se.lublin.humla.model.IChannel
import se.lublin.humla.model.IMessage
import se.lublin.humla.util.HumlaDisconnectedException
import se.lublin.humla.util.HumlaObserver
import se.lublin.humla.util.IHumlaObserver
import se.lublin.mumla.R
import se.lublin.mumla.service.IChatMessage
import se.lublin.mumla.util.BitmapUtils
import se.lublin.mumla.util.HumlaServiceFragment
import com.google.android.material.R as MaterialR

class ChannelChatFragment : HumlaServiceFragment(), ChatTargetProvider.OnChatTargetSelectedListener {
    private val serviceObserver: IHumlaObserver = object : HumlaObserver() {
        override fun onMessageLogged(message: IMessage) {
            addChatMessage(IChatMessage.TextMessage(message), true)
        }

        override fun onLogInfo(message: String) {
            addChatMessage(IChatMessage.InfoMessage(IChatMessage.InfoMessage.Type.INFO, message), true)
        }

        override fun onLogWarning(message: String) {
            addChatMessage(IChatMessage.InfoMessage(IChatMessage.InfoMessage.Type.WARNING, message), true)
        }

        override fun onLogError(message: String) {
            addChatMessage(IChatMessage.InfoMessage(IChatMessage.InfoMessage.Type.ERROR, message), true)
        }

        override fun onUserJoinedChannel(user: se.lublin.humla.model.IUser?, newChannel: IChannel?, oldChannel: IChannel?) {
            val currentService = service
            if (currentService != null && currentService.isConnected) {
                val session = currentService.HumlaSession()
                if (user == session.sessionUser && targetProvider.getChatTarget() == null) {
                    updateChatTargetText(null)
                }
            }
        }
    }

    private lateinit var chatList: RecyclerView
    private lateinit var chatLayoutManager: LinearLayoutManager
    private var chatAdapter: ChannelChatAdapter? = null
    private lateinit var chatTextEdit: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var composerCard: MaterialCardView
    private var keyboardAnimator: ValueAnimator? = null
    private var currentKeyboardOffset = 0f
    private lateinit var targetProvider: ChatTargetProvider
    private val imagePicker: ActivityResultLauncher<String> =
        registerForActivityResult(GetContent(), this::onImagePicked)
    private val readPermissionRequester: ActivityResultLauncher<String> =
        registerForActivityResult(RequestPermission(), this::onReadPermissionGranted)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        try {
            targetProvider = parentFragment as ChatTargetProvider
        } catch (exception: ClassCastException) {
            throw ClassCastException(parentFragment.toString() + " must implement ChatTargetProvider")
        }
    }

    override fun onResume() {
        super.onResume()
        targetProvider.registerChatTargetListener(this)
    }

    override fun onPause() {
        super.onPause()
        targetProvider.unregisterChatTargetListener(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_chat, container, false)
        chatList = view.findViewById(R.id.chat_list)
        chatLayoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, true)
        chatList.layoutManager = chatLayoutManager
        chatList.itemAnimator = null
        chatTextEdit = view.findViewById(R.id.chatTextEdit)
        composerCard = view.findViewById(R.id.chat_composer_card)
        composerCard.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateChatListBottomPadding()
        }
        (view as ChatKeyboardFrameLayout).setKeyboardHeightDelegate(
            object : ChatKeyboardFrameLayout.KeyboardHeightDelegate {
                override fun onKeyboardHeightChanged(height: Int) {
                    animateKeyboardOffset(height.toFloat())
                }
            }
        )

        val imageSendButton = view.findViewById<ImageButton>(R.id.chatImageSend)
        imageSendButton.setOnClickListener {
            if (SDK_INT <= Build.VERSION_CODES.S_V2) {
                if (checkSelfPermission(requireContext(), READ_EXTERNAL_STORAGE) != PERMISSION_GRANTED) {
                    readPermissionRequester.launch(READ_EXTERNAL_STORAGE)
                } else {
                    imagePicker.launch("image/*")
                }
            } else {
                imagePicker.launch("image/*")
            }
        }

        sendButton = view.findViewById(R.id.chatTextSend)
        sendButton.setOnClickListener(this::sendMessageFromEditor)
        applyChatComposerColors(composerCard, imageSendButton, sendButton)

        chatTextEdit.setOnEditorActionListener { view, actionId, event ->
            if (actionId == IME_NULL && event != null && event.keyCode == KEYCODE_ENTER) {
                sendMessageFromEditor(view)
                true
            } else {
                false
            }
        }

        chatTextEdit.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                sendButton.isEnabled = chatTextEdit.text.isNotEmpty()
                updateChatTextEditHeight()
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit

            override fun afterTextChanged(s: Editable) = Unit
        })

        updateChatTargetText(targetProvider.getChatTarget())
        return view
    }

    private fun updateChatTextEditHeight() {
        val lineCount = chatTextEdit.lineCount.coerceIn(1, 4)
        val targetHeight = (44 + (lineCount - 1) * 22).dpToPx()
        val layoutParams = chatTextEdit.layoutParams
        if (layoutParams.height != targetHeight) {
            layoutParams.height = targetHeight
            chatTextEdit.layoutParams = layoutParams
        }
    }

    override fun onDestroyView() {
        keyboardAnimator?.cancel()
        keyboardAnimator = null
        currentKeyboardOffset = 0f
        super.onDestroyView()
    }

    private fun animateKeyboardOffset(targetOffset: Float) {
        if (!::composerCard.isInitialized || !::chatList.isInitialized) return
        keyboardAnimator?.cancel()
        keyboardAnimator = ValueAnimator.ofFloat(currentKeyboardOffset, targetOffset).apply {
            duration = KEYBOARD_ANIMATION_DURATION
            addUpdateListener { animator ->
                applyKeyboardOffset(animator.animatedValue as Float)
            }
            start()
        }
    }

    private fun applyKeyboardOffset(offset: Float) {
        currentKeyboardOffset = offset
        val translation = -offset
        chatList.translationY = translation
        composerCard.translationY = translation
    }

    private fun updateChatListBottomPadding() {
        if (!::chatList.isInitialized || !::composerCard.isInitialized || composerCard.height == 0) return
        val layoutParams = composerCard.layoutParams as ViewGroup.MarginLayoutParams
        val bottomPadding = composerCard.height + layoutParams.bottomMargin + CHAT_LIST_COMPOSER_GAP_DP.dpToPx()
        if (chatList.paddingBottom != bottomPadding) {
            chatList.setPadding(chatList.paddingLeft, chatList.paddingTop, chatList.paddingRight, bottomPadding)
        }
    }

    private fun isChatScrolledToBottom(): Boolean {
        val adapter = chatAdapter ?: return true
        return adapter.itemCount == 0 || chatLayoutManager.findFirstVisibleItemPosition() <= 0
    }

    private fun applyChatComposerColors(
        composerCard: MaterialCardView,
        imageSendButton: ImageButton,
        sendButton: ImageButton
    ) {
        val dynamicContext = DynamicColors.wrapContextIfAvailable(requireContext())
        val surfaceColor = MaterialColors.getColor(
            dynamicContext,
            MaterialR.attr.colorPrimaryContainer,
            ContextCompat.getColor(requireContext(), R.color.chat_composer_surface)
        )
        val contentColor = MaterialColors.getColor(
            dynamicContext,
            MaterialR.attr.colorOnPrimaryContainer,
            Color.BLACK
        )
        val primaryColor = MaterialColors.getColor(
            dynamicContext,
            androidx.appcompat.R.attr.colorPrimary,
            ContextCompat.getColor(requireContext(), R.color.md_theme_light_primary)
        )
        composerCard.setCardBackgroundColor(surfaceColor)
        imageSendButton.setColorFilter(contentColor)
        chatTextEdit.setTextColor(contentColor)
        chatTextEdit.setHintTextColor(contentColor and 0x99ffffff.toInt())
        sendButton.setColorFilter(primaryColor)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.fragment_chat, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_clear_chat) {
            clear()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    fun addChatMessage(message: IChatMessage, scroll: Boolean) {
        val adapter = chatAdapter ?: return
        if (!shouldDisplayMessage(message)) {
            return
        }
        val shouldScroll = scroll && (isChatScrolledToBottom() || isSelfAuthoredTextMessage(message))
        adapter.addLatest(message)

        if (shouldScroll) {
            chatList.post {
                moveScrollToLastMessage()
            }
        }
    }

    private fun moveScrollToLastMessage() {
        chatLayoutManager.scrollToPositionWithOffset(0, 0)
    }

    private fun isSelfAuthoredTextMessage(message: IChatMessage): Boolean {
        var selfAuthored = false
        val currentService = service ?: return false
        message.accept(object : IChatMessage.Visitor {
            override fun visit(message: IChatMessage.TextMessage) {
                selfAuthored = try {
                    message.message.actor == currentService.HumlaSession().sessionId
                } catch (exception: HumlaDisconnectedException) {
                    false
                }
            }

            override fun visit(message: IChatMessage.InfoMessage) = Unit
        })
        return selfAuthored
    }

    private fun onReadPermissionGranted(isGranted: Boolean) {
        if (isGranted) {
            imagePicker.launch("image/*")
        } else {
            Toast.makeText(requireContext(), "Permission denied to read storage", Toast.LENGTH_LONG).show()
        }
    }

    private fun onImagePicked(uri: Uri?) {
        if (uri == null) {
            return
        }
        val currentService = service
        if (currentService == null || !currentService.isConnected) {
            return
        }

        var flipped = false
        var rotationDeg = 0
        try {
            requireContext().contentResolver.openInputStream(uri).use { imageStream ->
                if (imageStream == null) {
                    Log.w(TAG, "openInputStream(uri) failed for orientation")
                } else {
                    val exif = ExifInterface(imageStream)
                    flipped = exif.isFlipped
                    rotationDeg = exif.rotationDegrees
                }
            }
        } catch (exception: IOException) {
            Log.w(TAG, "exception when getting orientation: $exception")
        }
        Log.d(TAG, "flipped:$flipped rotationDeg:$rotationDeg")

        var bitmap = try {
            requireContext().contentResolver.openInputStream(uri).use { imageStream ->
                if (imageStream == null) {
                    Log.w(TAG, "openInputStream(uri) failed")
                    return
                }
                BitmapFactory.decodeStream(imageStream)
            }
        } catch (exception: IOException) {
            Log.w(TAG, "exception when opening stream: $exception")
            return
        }
        if (bitmap == null) {
            Log.w(TAG, "decode to bitmap failed")
            return
        }

        if (flipped || rotationDeg > 0) {
            val matrix = Matrix()
            if (flipped) {
                matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
            }
            if (rotationDeg > 0) {
                matrix.postRotate(rotationDeg.toFloat())
            }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
        }

        val resized = BitmapUtils.resizeKeepingAspect(bitmap, 600, 400)
        val preview = ImageView(requireContext())
        preview.setImageBitmap(resized)
        preview.adjustViewBounds = true
        preview.scaleType = ImageView.ScaleType.FIT_CENTER
        preview.maxHeight = Resources.getSystem().displayMetrics.heightPixels / 3
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.image_confirm_send)
            .setPositiveButton(android.R.string.ok) { _, _ -> onImageConfirmed(resized) }
            .setNegativeButton(android.R.string.cancel, null)
            .setView(preview)
            .create()
            .show()
    }

    private fun onImageConfirmed(resized: Bitmap) {
        val maxSize = service!!.HumlaSession().serverSettings.imageMessageLength
        var quality = 97
        var compressed: ByteArray?
        do {
            val stream = ByteArrayOutputStream()
            if (!resized.compress(Bitmap.CompressFormat.JPEG, quality, stream)) {
                Log.w(TAG, "compress failed, quality==$quality")
            } else {
                compressed = stream.toByteArray()
                if (4 * (compressed.size / 3) + 4 < maxSize || maxSize == 0) {
                    break
                } else {
                    Log.d(TAG, "compress(quality==$quality) >= $maxSize bytes")
                }
            }
            compressed = null
            quality -= 10
        } while (quality > 0)

        if (compressed == null) {
            Log.w(TAG, "all compress attempts failed")
            return
        }

        val imageStr = Base64.encodeToString(compressed, Base64.NO_WRAP)
        val encoded = URLEncoder.encode(imageStr)
        sendMessage("<img src=\"data:image/jpeg;base64,$encoded\"/>")
    }

    private fun sendMessageFromEditor(view: View) {
        if (chatTextEdit.length() == 0) {
            return
        }
        val message = chatTextEdit.text.toString()
        try {
            sendMessage(message)
            chatTextEdit.setText("")
        } catch (exception: HumlaDisconnectedException) {
            Log.d(TAG, "exception from sendMessage: $exception")
        }
    }

    @Throws(HumlaDisconnectedException::class)
    private fun sendMessage(message: String) {
        val currentService = service
        if (currentService == null) {
            Log.d(TAG, "getService()==null in sendMessage")
            return
        }
        val session = currentService.HumlaSession()
        val formattedMessage = markupOutgoingMessage(message)
        val target = targetProvider.getChatTarget()
        val responseMessage = when {
            target == null -> session.sendChannelTextMessage(session.sessionChannel.id, formattedMessage, false)
            target.user != null -> session.sendUserTextMessage(target.user!!.session, formattedMessage)
            target.channel != null -> session.sendChannelTextMessage(target.channel!!.id, formattedMessage, false)
            else -> return
        }
        addChatMessage(IChatMessage.TextMessage(responseMessage), true)
    }

    private fun markupOutgoingMessage(message: String): String {
        val matcher = LINK_PATTERN.matcher(message)
        return matcher.replaceAll("<a href=\"$1\">$1</a>")
            .replace("\n", "<br>")
    }

    fun clear() {
        chatAdapter?.clear()
        service?.clearMessageLog()
    }

    fun updateChatTargetText(target: ChatTargetProvider.ChatTarget?) {
        val currentService = service
        if (currentService == null || !currentService.isConnected) {
            return
        }

        val session = currentService.HumlaSession()
        val hint = when {
            target == null && session.sessionChannel != null ->
                getString(R.string.messageToChannel, session.sessionChannel.name)
            target?.user != null ->
                getString(R.string.messageToUser, target.user.name)
            target?.channel != null ->
                getString(R.string.messageToChannel, target.channel.name)
            else -> null
        }
        chatTextEdit.hint = hint
        chatTextEdit.requestLayout()
    }

    private fun refreshDisplayedMessages() {
        val currentService = service ?: return
        val adapter = chatAdapter ?: return
        adapter.replaceMessages(currentService.getMessageLog().filter { shouldDisplayMessage(it) })
        chatList.post {
            moveScrollToLastMessage()
        }
    }

    private fun shouldDisplayMessage(chatMessage: IChatMessage): Boolean {
        var display = targetProvider.getChatTarget() == null
        chatMessage.accept(object : IChatMessage.Visitor {
            override fun visit(message: IChatMessage.TextMessage) {
                display = shouldDisplayTextMessage(message.message)
            }

            override fun visit(message: IChatMessage.InfoMessage) {
                display = targetProvider.getChatTarget() == null
            }
        })
        return display
    }

    private fun shouldDisplayTextMessage(message: IMessage): Boolean {
        val currentService = service ?: return false
        if (!currentService.isConnected) return false
        val target = targetProvider.getChatTarget()
        if (isPrivateTextMessage(message)) {
            val targetUser = target?.user ?: return false
            return isPrivateTextMessageWithUser(message, targetUser.session)
        }
        if (target?.user != null) {
            return false
        }
        val channel = target?.channel ?: runCatching { currentService.HumlaSession().sessionChannel }.getOrNull()
        return channel != null && message.targetChannels.any { it.id == channel.id }
    }

    private fun isPrivateTextMessage(message: IMessage): Boolean {
        return message.targetUsers.isNotEmpty() &&
            message.targetChannels.isEmpty() &&
            message.targetTrees.isEmpty()
    }

    private fun isPrivateTextMessageWithUser(message: IMessage, session: Int): Boolean {
        val selfSession = runCatching { service?.HumlaSession()?.sessionId ?: -1 }.getOrDefault(-1)
        return if (message.actor == selfSession) {
            message.targetUsers.any { it.session == session }
        } else {
            message.actor == session
        }
    }

    override fun onServiceBound(service: IHumlaService?) {
        if (this.service == null || service == null) {
            return
        }

        chatAdapter = ChannelChatAdapter(
            requireActivity(),
            service,
            this.service!!.getMessageLog().filter { shouldDisplayMessage(it) }
        )
        chatList.adapter = chatAdapter
        chatList.post {
            moveScrollToLastMessage()
        }
    }

    override fun getServiceObserver(): IHumlaObserver = serviceObserver

    override fun onChatTargetSelected(target: ChatTargetProvider.ChatTarget?) {
        updateChatTargetText(target)
        refreshDisplayedMessages()
    }

    private fun Int.dpToPx(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()

    private class ChannelChatAdapter(
        private val context: Context,
        private val service: IHumlaService,
        messages: List<IChatMessage>,
    ) : RecyclerView.Adapter<ChannelChatAdapter.ChatViewHolder>() {
        private val messages = ArrayList(messages.asReversed())

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
            val view = ChatMessageCellView(context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
            }
            return ChatViewHolder(view)
        }

        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
            val message = messages[position]
            holder.cellView.setMessage(
                createCellMessage(message),
                ChatMessageCellView.GroupPosition(
                    newerSameOwner = position > 0 && sameBubbleGroup(message, messages[position - 1]),
                    olderSameOwner = position < messages.lastIndex && sameBubbleGroup(message, messages[position + 1])
                )
            )
        }

        override fun getItemCount(): Int = messages.size

        fun addLatest(message: IChatMessage) {
            messages.add(0, message)
            notifyItemInserted(0)
        }

        fun clear() {
            val size = messages.size
            messages.clear()
            notifyItemRangeRemoved(0, size)
        }

        fun replaceMessages(newMessages: List<IChatMessage>) {
            messages.clear()
            messages.addAll(newMessages.asReversed())
            notifyDataSetChanged()
        }

        private fun createCellMessage(message: IChatMessage): ChatMessageCellView.Message {
            var body = message.getBody()
            var outgoing = false
            var info = false
            message.accept(object : IChatMessage.Visitor {
                override fun visit(message: IChatMessage.TextMessage) {
                    body = message.message.message
                    outgoing = isSelfAuthored(message)
                }

                override fun visit(message: IChatMessage.InfoMessage) {
                    info = true
                }
            })
            return ChatMessageCellView.Message(
                body = body,
                receivedTime = message.getReceivedTime(),
                outgoing = outgoing,
                info = info,
                media = body.contains("<img", ignoreCase = true)
            )
        }

        private fun sameBubbleGroup(first: IChatMessage, second: IChatMessage): Boolean {
            val firstKey = bubbleGroupKey(first) ?: return false
            val secondKey = bubbleGroupKey(second) ?: return false
            return firstKey == secondKey &&
                kotlin.math.abs(first.getReceivedTime() - second.getReceivedTime()) < GROUP_TIME_WINDOW_MS
        }

        private fun bubbleGroupKey(message: IChatMessage): String? {
            var key: String? = null
            message.accept(object : IChatMessage.Visitor {
                override fun visit(message: IChatMessage.TextMessage) {
                    key = if (isSelfAuthored(message)) "self" else "user:${message.message.actor}"
                }

                override fun visit(message: IChatMessage.InfoMessage) = Unit
            })
            return key
        }

        private fun isSelfAuthored(message: IChatMessage.TextMessage): Boolean {
            return try {
                message.message.actor == service.HumlaSession().sessionId
            } catch (exception: HumlaDisconnectedException) {
                false
            }
        }

        class ChatViewHolder(val cellView: ChatMessageCellView) : RecyclerView.ViewHolder(cellView)

        private companion object {
            const val GROUP_TIME_WINDOW_MS = 5 * 60 * 1000L
        }
    }

    companion object {
        private const val KEYBOARD_ANIMATION_DURATION = 250L
        private const val CHAT_LIST_COMPOSER_GAP_DP = 8
        private val TAG = ChannelChatFragment::class.java.name
        private val LINK_PATTERN: Pattern = Pattern.compile("(https?://\\S+)")
    }
}
