package se.lublin.mumla.channel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import se.lublin.humla.IHumlaService
import se.lublin.humla.model.IUser
import se.lublin.humla.net.Permissions
import se.lublin.humla.util.HumlaException
import se.lublin.humla.util.HumlaObserver
import se.lublin.humla.util.IHumlaObserver
import se.lublin.mumla.R
import se.lublin.mumla.app.MumlaActivity
import se.lublin.mumla.service.IMumlaService
import se.lublin.mumla.util.BitmapUtils
import se.lublin.mumla.util.HumlaServiceFragment

class UserProfileFragment : HumlaServiceFragment() {
    private var sessionId = -1
    private var isSelfProfile = false
    private var currentUser: IUser? = null
    private var selectedAvatarBytes: ByteArray? = null
    private var pendingPersistentComment: String? = null
    private var pendingPersistentAvatarBytes: ByteArray? = null
    private var immersiveProfile = false

    private lateinit var heroHeader: FrameLayout
    private lateinit var heroImage: ImageView
    private lateinit var heroEdgeBlur: EdgeBlurView
    private lateinit var heroLetter: TextView
    private lateinit var heroNameText: TextView
    private lateinit var selfAvatarContainer: FrameLayout
    private lateinit var selfAvatarImage: ImageView
    private lateinit var selfAvatarLetter: TextView
    private lateinit var nameText: TextView
    private lateinit var idText: TextView
    private lateinit var commentTitle: TextView
    private lateinit var commentView: TextView
    private lateinit var commentEdit: EditText
    private lateinit var changeAvatarButton: Button
    private lateinit var saveButton: Button
    private lateinit var callButton: Button
    private lateinit var chatButton: Button
    private lateinit var otherActions: LinearLayout

    private val avatarPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        requireContext().contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        val bytes = createAvatarTexture(uri)
        if (bytes == null) {
            Toast.makeText(requireContext(), R.string.user_profile_avatar_failed, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        selectedAvatarBytes = bytes
        selfAvatarImage.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    }

    private val observer: IHumlaObserver = object : HumlaObserver() {
        override fun onDisconnected(e: HumlaException?) {
            activity?.supportFragmentManager?.popBackStack()
        }

        override fun onUserStateUpdated(user: IUser?) {
            if (user?.session == sessionId) {
                requireActivity().runOnUiThread {
                    bindUser(user)
                    submitPendingPersistentProfileIfRegistered(user)
                }
            }
        }

        override fun onUserRemoved(user: IUser?, reason: String?) {
            if (user?.session == sessionId) {
                activity?.supportFragmentManager?.popBackStack()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionId = requireArguments().getInt(ARG_SESSION)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        return ScrollView(context).apply {
            setBackgroundColor(Color.WHITE)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL

                heroHeader = FrameLayout(context).apply {
                    visibility = View.GONE
                    setBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_light_primary))
                    heroImage = ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                    addView(heroImage, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ))
                    heroEdgeBlur = EdgeBlurView(context)
                    addView(heroEdgeBlur, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ))
                    heroLetter = TextView(context).apply {
                        textSize = 72f
                        setTextColor(Color.WHITE)
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                    }
                    addView(heroLetter, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ))
                    addView(View(context).apply {
                        background = GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            intArrayOf(Color.TRANSPARENT, 0x66000000)
                        )
                    }, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(124),
                        Gravity.BOTTOM
                    ))
                    heroNameText = TextView(context).apply {
                        textSize = 28f
                        setTextColor(Color.WHITE)
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.BOTTOM or Gravity.START
                        setPadding(dp(24), 0, dp(24), dp(22))
                    }
                    addView(heroNameText, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM
                    ))
                }
                addView(heroHeader, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(288)
                ))

                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(dp(24), dp(28), dp(24), dp(28))

                selfAvatarContainer = FrameLayout(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(ContextCompat.getColor(context, R.color.md_theme_light_primary))
                    }
                    selfAvatarImage = ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(ContextCompat.getColor(context, R.color.md_theme_light_primary))
                        }
                        clipToOutline = true
                    }
                    addView(selfAvatarImage, FrameLayout.LayoutParams(dp(128), dp(128), Gravity.CENTER))
                    selfAvatarLetter = TextView(context).apply {
                        textSize = 48f
                        setTextColor(Color.WHITE)
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                    }
                    addView(selfAvatarLetter, FrameLayout.LayoutParams(dp(128), dp(128), Gravity.CENTER))
                }
                addView(selfAvatarContainer, LinearLayout.LayoutParams(dp(128), dp(128)))

                nameText = TextView(context).apply {
                    textSize = 22f
                    setTextColor(Color.rgb(32, 33, 36))
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(0, dp(18), 0, 0)
                }
                addView(nameText, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ))

                otherActions = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    visibility = View.GONE
                    callButton = capsuleButton(R.string.user_menu_private_call, R.drawable.call_24px).apply {
                        setOnClickListener { service?.startPrivateCall(sessionId) }
                    }
                    addView(callButton, LinearLayout.LayoutParams(
                        0,
                        dp(46),
                        1f
                    ).apply {
                        rightMargin = dp(6)
                    })
                    chatButton = capsuleButton(R.string.chat, R.drawable.conversation_24px).apply {
                        setOnClickListener { (requireActivity() as? MumlaActivity)?.openChatWithUser(sessionId) }
                    }
                    addView(chatButton, LinearLayout.LayoutParams(
                        0,
                        dp(46),
                        1f
                    ).apply {
                        leftMargin = dp(6)
                    })
                }
                addView(otherActions, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(18)
                })

                idText = TextView(context).apply {
                    textSize = 13f
                    setTextColor(0x99000000.toInt())
                    gravity = Gravity.CENTER
                    setPadding(0, dp(4), 0, dp(22))
                }
                addView(idText, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ))

                changeAvatarButton = capsuleButton(R.string.user_profile_change_avatar).apply {
                    setOnClickListener { avatarPicker.launch(arrayOf("image/*")) }
                }
                addView(changeAvatarButton, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(46)
                ).apply {
                    bottomMargin = dp(18)
                })

                commentTitle = TextView(context).apply {
                    setText(R.string.user_profile_comment)
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.rgb(32, 33, 36))
                    gravity = Gravity.START
                }
                addView(commentTitle, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ))

                commentView = TextView(context).apply {
                    textSize = 15f
                    setTextColor(Color.rgb(60, 64, 67))
                    setPadding(0, dp(10), 0, dp(18))
                }
                addView(commentView, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ))

                commentEdit = EditText(context).apply {
                    minLines = 1
                    maxLines = 4
                    minHeight = 0
                    minimumHeight = 0
                    includeFontPadding = false
                    gravity = Gravity.BOTTOM or Gravity.START
                    setTextColor(Color.rgb(32, 33, 36))
                    hint = getString(R.string.user_profile_comment_hint)
                    setPadding(paddingLeft, 0, paddingRight, dp(1))
                }
                addView(commentEdit, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ))

                saveButton = capsuleButton(R.string.save).apply {
                    setOnClickListener { saveProfile() }
                }
                addView(saveButton, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(46)
                ).apply {
                    topMargin = dp(18)
                })

                })
            })
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUser()
    }

    override fun onServiceBound(service: IHumlaService?) {
        refreshUser()
    }

    override fun getServiceObserver(): IHumlaObserver = observer

    private fun refreshUser() {
        val currentService = service ?: return
        if (!currentService.isConnected) return
        if (sessionId < 0) {
            sessionId = currentService.HumlaSession().sessionId
        }
        val user = currentService.HumlaSession().getUser(sessionId) ?: currentService.HumlaSession().sessionUser
        bindUser(user)
        if (user.comment == null && user.commentHash != null) {
            currentService.HumlaSession().requestComment(user.session)
        }
        if (user.texture == null && user.textureHash != null) {
            currentService.HumlaSession().requestAvatar(user.session)
        }
    }

    private fun bindUser(user: IUser) {
        currentUser = user
        val currentService = service
        isSelfProfile = currentService?.isConnected == true &&
            user.session == currentService.HumlaSession().sessionId
        (activity as? AppCompatActivity)?.supportActionBar?.title =
            if (isSelfProfile) getString(R.string.user_profile_self_title) else user.name

        nameText.text = user.name
        idText.text = if (user.userId >= 0) {
            getString(R.string.user_profile_registered_id, user.userId)
        } else {
            getString(R.string.user_profile_session_id, user.session)
        }
        bindAvatar(user)
        val comment = user.comment.orEmpty()
        commentView.text = if (comment.isBlank()) {
            getString(R.string.user_profile_no_comment)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(comment, Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(comment)
            }
        }
        if (!commentEdit.hasFocus()) {
            commentEdit.setText(comment)
        }
        changeAvatarButton.visibility = if (isSelfProfile) View.VISIBLE else View.GONE
        commentEdit.visibility = if (isSelfProfile) View.VISIBLE else View.GONE
        saveButton.visibility = if (isSelfProfile) View.VISIBLE else View.GONE
        selfAvatarContainer.visibility = if (isSelfProfile) View.VISIBLE else View.GONE
        nameText.visibility = if (isSelfProfile) View.VISIBLE else View.GONE
        heroHeader.visibility = if (isSelfProfile) View.GONE else View.VISIBLE
        otherActions.visibility = if (isSelfProfile) View.GONE else View.VISIBLE
        setImmersiveProfileEnabled(!isSelfProfile)
    }

    private fun bindAvatar(user: IUser) {
        heroNameText.text = user.name
        val texture = selectedAvatarBytes ?: user.texture
        if (texture != null) {
            val bitmap = BitmapFactory.decodeByteArray(texture, 0, texture.size)
            selfAvatarImage.setImageBitmap(bitmap)
            heroImage.setImageBitmap(bitmap)
            heroEdgeBlur.setBitmap(bitmap)
            selfAvatarLetter.visibility = View.GONE
            heroLetter.visibility = View.GONE
            return
        }
        selfAvatarImage.setImageDrawable(null)
        heroImage.setImageDrawable(null)
        heroEdgeBlur.setBitmap(null)
        selfAvatarImage.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_primary))
        heroImage.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_primary))
        val initial = user.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        selfAvatarLetter.visibility = View.VISIBLE
        heroLetter.visibility = View.VISIBLE
        selfAvatarLetter.text = initial
        heroLetter.text = initial
    }

    private fun saveProfile() {
        val currentService = service ?: return
        if (!currentService.isConnected || !isSelfProfile) return
        val user = currentUser ?: return
        val comment = commentEdit.text.toString()
        val avatarBytes = selectedAvatarBytes
        if (user.userId < 0) {
            val canSelfRegister = currentService.HumlaSession().permissions and
                (Permissions.SelfRegister or Permissions.Register or Permissions.Write) > 0
            if (!canSelfRegister) {
                Toast.makeText(requireContext(), R.string.user_profile_register_denied, Toast.LENGTH_SHORT).show()
                return
            }
            pendingPersistentComment = comment
            pendingPersistentAvatarBytes = avatarBytes
            currentService.HumlaSession().registerUser(sessionId)
            Toast.makeText(requireContext(), R.string.user_profile_registering, Toast.LENGTH_SHORT).show()
            return
        }
        submitPersistentProfile(comment, avatarBytes)
    }

    private fun submitPendingPersistentProfileIfRegistered(user: IUser) {
        if (!isSelfProfile || user.userId < 0) {
            return
        }
        val comment = pendingPersistentComment ?: return
        val avatarBytes = pendingPersistentAvatarBytes
        pendingPersistentComment = null
        pendingPersistentAvatarBytes = null
        submitPersistentProfile(comment, avatarBytes)
    }

    private fun submitPersistentProfile(comment: String, avatarBytes: ByteArray?) {
        val currentService = service ?: return
        currentService.HumlaSession().setUserComment(sessionId, comment)
        avatarBytes?.let {
            currentService.HumlaSession().setUserTexture(sessionId, it)
        }
        selectedAvatarBytes = null
        Toast.makeText(requireContext(), R.string.user_profile_saved, Toast.LENGTH_SHORT).show()
    }

    private fun createAvatarTexture(uri: Uri): ByteArray? {
        val bitmap = requireContext().contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return null
        val size = bitmap.width.coerceAtMost(bitmap.height)
        val left = (bitmap.width - size) / 2
        val top = (bitmap.height - size) / 2
        val cropped = Bitmap.createBitmap(bitmap, left, top, size, size)
        val scaled = Bitmap.createScaledBitmap(cropped, 256, 256, true)
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, output)
        return output.toByteArray()
    }

    private fun capsuleButton(textRes: Int, iconRes: Int? = null): Button {
        return Button(requireContext()).apply {
            setText(textRes)
            isAllCaps = false
            setTextColor(Color.WHITE)
            iconRes?.let {
                val icon = ContextCompat.getDrawable(requireContext(), it)?.mutate()
                icon?.setTint(Color.WHITE)
                setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
                compoundDrawablePadding = dp(8)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 999f
                setColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_primary))
            }
            setPadding(dp(18), 0, dp(18), 0)
        }
    }

    private fun setImmersiveProfileEnabled(enabled: Boolean) {
        if (immersiveProfile == enabled) return
        immersiveProfile = enabled
        val appBar = (requireActivity().findViewById<View>(R.id.toolbar)?.parent as? View)
        if (enabled) {
            (activity as? AppCompatActivity)?.supportActionBar?.hide()
            appBar?.visibility = View.GONE
        } else {
            appBar?.visibility = View.VISIBLE
            (activity as? AppCompatActivity)?.supportActionBar?.show()
        }
    }

    override fun onDestroyView() {
        setImmersiveProfileEnabled(false)
        super.onDestroyView()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class EdgeBlurView(context: Context) : View(context) {
        private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        private val sourceRect = Rect()
        private val destinationRect = RectF()
        private var blurredBitmap: Bitmap? = null

        fun setBitmap(bitmap: Bitmap?) {
            blurredBitmap = bitmap?.let { BitmapUtils.blurForBackground(it) }
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val bitmap = blurredBitmap ?: return
            if (width == 0 || height == 0) {
                return
            }
            updateCenterCropSource(bitmap)
            destinationRect.set(0f, 0f, width.toFloat(), height.toFloat())
            drawEdge(
                canvas = canvas,
                top = 0f,
                bottom = edgeHeight(104).toFloat(),
                shader = LinearGradient(
                    0f,
                    0f,
                    0f,
                    edgeHeight(104).toFloat(),
                    intArrayOf(Color.BLACK, 0xb3000000.toInt(), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.48f, 1f),
                    Shader.TileMode.CLAMP
                )
            )
            val bottomEdgeHeight = edgeHeight(132).toFloat()
            drawEdge(
                canvas = canvas,
                top = height - bottomEdgeHeight,
                bottom = height.toFloat(),
                shader = LinearGradient(
                    0f,
                    height - bottomEdgeHeight,
                    0f,
                    height.toFloat(),
                    intArrayOf(Color.TRANSPARENT, 0xc2000000.toInt(), Color.BLACK),
                    floatArrayOf(0f, 0.58f, 1f),
                    Shader.TileMode.CLAMP
                )
            )
        }

        private fun drawEdge(canvas: Canvas, top: Float, bottom: Float, shader: Shader) {
            val bitmap = blurredBitmap ?: return
            val layer = canvas.saveLayer(0f, top, width.toFloat(), bottom, null)
            canvas.save()
            canvas.clipRect(0f, top, width.toFloat(), bottom)
            canvas.drawBitmap(bitmap, sourceRect, destinationRect, imagePaint)
            canvas.restore()
            maskPaint.shader = shader
            canvas.drawRect(0f, top, width.toFloat(), bottom, maskPaint)
            maskPaint.shader = null
            canvas.restoreToCount(layer)
        }

        private fun updateCenterCropSource(bitmap: Bitmap) {
            val viewRatio = width.toFloat() / height.toFloat()
            val bitmapRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
            if (bitmapRatio > viewRatio) {
                val cropWidth = (bitmap.height * viewRatio).toInt()
                val left = (bitmap.width - cropWidth) / 2
                sourceRect.set(left, 0, left + cropWidth, bitmap.height)
            } else {
                val cropHeight = (bitmap.width / viewRatio).toInt()
                val top = (bitmap.height - cropHeight) / 2
                sourceRect.set(0, top, bitmap.width, top + cropHeight)
            }
        }

        private fun edgeHeight(value: Int): Int {
            return (value * resources.displayMetrics.density).toInt().coerceAtMost(height)
        }
    }

    companion object {
        private const val ARG_SESSION = "session"

        fun newInstance(session: Int): UserProfileFragment {
            return UserProfileFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_SESSION, session)
                }
            }
        }
    }
}
