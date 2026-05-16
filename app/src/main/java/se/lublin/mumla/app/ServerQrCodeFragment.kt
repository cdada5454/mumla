package se.lublin.mumla.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import java.util.EnumMap
import se.lublin.humla.Constants
import se.lublin.humla.model.Server
import se.lublin.mumla.R
import se.lublin.mumla.servers.ServerEditFragment
import se.lublin.mumla.util.HumlaServiceFragment
import com.google.android.material.R as MaterialR

class ServerQrCodeFragment : HumlaServiceFragment() {
    private lateinit var qrImage: ImageView
    private lateinit var addressText: TextView
    private lateinit var summaryText: TextView
    private lateinit var scanButton: TextView
    private var scannerOverlay: FrameLayout? = null
    private var scannerView: QrScannerView? = null

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            showScanner()
        } else {
            Toast.makeText(requireContext(), R.string.server_qr_camera_denied, Toast.LENGTH_SHORT).show()
        }
    }

    private val scannerBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            hideScanner()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, scannerBackCallback)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(32), dp(24), dp(24))

            qrImage = ImageView(context).apply {
                adjustViewBounds = true
                layoutParams = LinearLayout.LayoutParams(dp(280), dp(280))
            }
            addView(qrImage)

            addressText = TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 16f
                setTextColor(Color.BLACK)
                setPadding(0, dp(18), 0, dp(8))
            }
            addView(addressText, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))

            summaryText = TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(0x99000000.toInt())
            }
            addView(summaryText, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))

            scanButton = TextView(context).apply {
                text = getString(R.string.server_qr_scan)
                gravity = Gravity.CENTER
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(resolveAppBarContentColor(context))
                minHeight = dp(48)
                setPadding(dp(20), 0, dp(20), 0)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 999f
                    setColor(resolveAppBarBackgroundColor(context))
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { openScanner() }
            }
            addView(scanButton, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(48)
            ).apply {
                topMargin = dp(24)
            })
        }
    }

    override fun onResume() {
        super.onResume()
        renderQrCode()
        scannerView?.start()
    }

    override fun onPause() {
        scannerView?.stop()
        super.onPause()
    }

    override fun onDestroyView() {
        hideScanner()
        super.onDestroyView()
    }

    override fun onServiceBound(service: se.lublin.humla.IHumlaService?) {
        renderQrCode()
    }

    private fun renderQrCode() {
        if (!::qrImage.isInitialized) {
            return
        }
        val currentService = service
        val server = currentService?.targetServer
        if (currentService?.isConnected != true || server == null) {
            qrImage.setImageDrawable(null)
            addressText.text = getString(R.string.server_qr_unavailable)
            summaryText.text = getString(R.string.server_qr_scan_hint)
            return
        }

        val url = buildMumbleUrl(server)
        qrImage.setImageBitmap(createQrBitmap(url, dp(280)))
        addressText.text = url
        summaryText.text = getString(R.string.server_qr_summary)
    }

    private fun openScanner() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            showScanner()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun showScanner() {
        if (scannerView != null) {
            return
        }
        val parent = requireActivity().findViewById<FrameLayout>(android.R.id.content)
        scannerBackCallback.isEnabled = true
        scanButton.text = getString(R.string.server_qr_scan_stop)
        scannerView = QrScannerView(requireContext()) { scannedText ->
            requireActivity().runOnUiThread {
                handleScannedText(scannedText)
            }
        }
        scannerOverlay = FrameLayout(requireContext()).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = true
            addView(scannerView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            addView(ScannerFrameView(requireContext()), FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            addView(ImageButton(requireContext()).apply {
                setImageResource(R.drawable.close_24px)
                setColorFilter(Color.WHITE)
                setBackgroundColor(Color.TRANSPARENT)
                scaleType = ImageView.ScaleType.CENTER
                setPadding(dp(16), dp(16), dp(16), dp(16))
                contentDescription = getString(android.R.string.cancel)
                setOnClickListener { hideScanner() }
            }, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.TOP or Gravity.START).apply {
                setMargins(dp(12), dp(28), dp(12), dp(12))
            })
        }.also {
            parent.addView(it, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        scannerView?.start()
    }

    private fun hideScanner() {
        scannerView?.stop()
        scannerOverlay?.let { overlay ->
            (overlay.parent as? ViewGroup)?.removeView(overlay)
        }
        scannerView = null
        scannerOverlay = null
        scannerBackCallback.isEnabled = false
        if (::scanButton.isInitialized) {
            scanButton.text = getString(R.string.server_qr_scan)
        }
    }

    private fun handleScannedText(text: String) {
        val server = parseServerQr(text)
        if (server == null) {
            Toast.makeText(requireContext(), R.string.server_qr_scan_invalid, Toast.LENGTH_SHORT).show()
            scannerView?.resumeScanning()
            return
        }
        hideScanner()
        ServerEditFragment.createServerEditDialog(server, ServerEditFragment.Action.ADD_ACTION)
            .show(parentFragmentManager, "serverEdit")
    }

    private fun parseServerQr(text: String): Server? {
        val trimmed = text.trim()
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull()
        if (uri?.scheme.equals("mumble", ignoreCase = true)) {
            val host = uri?.host?.takeIf { it.isNotBlank() } ?: return null
            val port = uri.port.takeIf { it > 0 } ?: Constants.DEFAULT_PORT
            return Server(-1, "", host, port, "", "")
        }

        val raw = trimmed.removePrefix("mumble://")
        val hostPort = raw.substringBefore('/').substringBefore('?')
        if (hostPort.isBlank()) {
            return null
        }
        val host = if (hostPort.startsWith("[")) {
            hostPort.substringAfter("[").substringBefore("]")
        } else {
            hostPort.substringBefore(":")
        }
        if (host.isBlank()) {
            return null
        }
        val portText = if (hostPort.startsWith("[")) {
            hostPort.substringAfter("]", "").removePrefix(":")
        } else {
            hostPort.substringAfter(":", "")
        }
        val port = portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: Constants.DEFAULT_PORT
        return Server(-1, "", host, port, "", "")
    }

    private fun buildMumbleUrl(server: Server): String {
        val port = if (server.port == 0) Constants.DEFAULT_PORT else server.port
        val host = if (server.host.contains(":") && !server.host.startsWith("[")) {
            "[${server.host}]"
        } else {
            server.host
        }
        return "mumble://$host:$port"
    }

    private fun createQrBitmap(content: String, size: Int): Bitmap {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            put(EncodeHintType.MARGIN, 2)
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
        }
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            val offset = y * size
            for (x in 0 until size) {
                pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun resolveAppBarBackgroundColor(context: Context): Int {
        val dynamicContext = DynamicColors.wrapContextIfAvailable(context)
        val fallback = MaterialColors.getColor(context, android.R.attr.colorPrimary, Color.BLACK)
        return MaterialColors.getColor(dynamicContext, MaterialR.attr.colorPrimaryContainer, fallback)
    }

    private fun resolveAppBarContentColor(context: Context): Int {
        val dynamicContext = DynamicColors.wrapContextIfAvailable(context)
        val fallback = MaterialColors.getColor(context, MaterialR.attr.colorOnPrimary, Color.WHITE)
        return MaterialColors.getColor(dynamicContext, MaterialR.attr.colorOnPrimaryContainer, fallback)
    }

    private class QrScannerView(
        context: Context,
        private val onResult: (String) -> Unit
    ) : FrameLayout(context) {
        private val textureView = TextureView(context)
        private val reader = MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }
        private var cameraThread: HandlerThread? = null
        private var cameraHandler: Handler? = null
        private var cameraDevice: CameraDevice? = null
        private var captureSession: CameraCaptureSession? = null
        private var imageReader: ImageReader? = null
        private var decoded = false

        init {
            addView(textureView, LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }

        fun start() {
            if (cameraThread != null) {
                return
            }
            cameraThread = HandlerThread("qr-scanner").also {
                it.start()
                cameraHandler = Handler(it.looper)
            }
            if (textureView.isAvailable) {
                openCamera()
            } else {
                textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        openCamera()
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                }
            }
        }

        fun stop() {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
            cameraThread?.quitSafely()
            cameraThread = null
            cameraHandler = null
        }

        fun resumeScanning() {
            decoded = false
            reader.reset()
        }

        private fun openCamera() {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { cameraId ->
                cameraManager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull() ?: return
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createSession(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                }
            }, cameraHandler)
        }

        private fun createSession(camera: CameraDevice) {
            val surfaceTexture = textureView.surfaceTexture ?: return
            surfaceTexture.setDefaultBufferSize(1280, 720)
            val previewSurface = Surface(surfaceTexture)
            imageReader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2).apply {
                setOnImageAvailableListener({ reader ->
                    reader.acquireLatestImage()?.use { decodeImage(it) }
                }, cameraHandler)
            }
            val decodeSurface = imageReader?.surface ?: return
            camera.createCaptureSession(listOf(previewSurface, decodeSurface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(previewSurface)
                        addTarget(decodeSurface)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    }.build()
                    session.setRepeatingRequest(request, null, cameraHandler)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) = Unit
            }, cameraHandler)
        }

        private fun decodeImage(image: Image) {
            if (decoded) {
                return
            }
            val data = extractLuminance(image)
            val source = PlanarYUVLuminanceSource(
                data,
                image.width,
                image.height,
                0,
                0,
                image.width,
                image.height,
                false
            )
            try {
                val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
                decoded = true
                onResult(result.text)
            } catch (_: NotFoundException) {
                reader.reset()
            } catch (_: RuntimeException) {
                reader.reset()
            }
        }

        private fun extractLuminance(image: Image): ByteArray {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val width = image.width
            val height = image.height
            val data = ByteArray(width * height)
            var outputOffset = 0
            for (row in 0 until height) {
                val rowStart = row * rowStride
                if (pixelStride == 1) {
                    buffer.position(rowStart)
                    buffer.get(data, outputOffset, width)
                    outputOffset += width
                } else {
                    for (col in 0 until width) {
                        data[outputOffset++] = buffer.get(rowStart + col * pixelStride)
                    }
                }
            }
            return data
        }
    }

    private class ScannerFrameView(context: Context) : View(context) {
        private val outsidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66000000
            style = Paint.Style.FILL
        }
        private val frameFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x22FFFFFF
            style = Paint.Style.FILL
        }
        private val frameStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = dp(3).toFloat()
        }
        private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = dp(5).toFloat()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val overlayPath = Path()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val size = (width.coerceAtMost(height) * 0.66f).coerceAtMost(dp(300).toFloat())
            val left = (width - size) / 2f
            val top = (height - size) / 2f
            val right = left + size
            val bottom = top + size
            val frame = RectF(left, top, right, bottom)
            val radius = dp(24).toFloat()

            overlayPath.reset()
            overlayPath.fillType = Path.FillType.EVEN_ODD
            overlayPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            overlayPath.addRoundRect(frame, radius, radius, Path.Direction.CCW)
            canvas.drawPath(overlayPath, outsidePaint)

            canvas.drawRoundRect(frame, radius, radius, frameFillPaint)
            canvas.drawRoundRect(frame, radius, radius, frameStrokePaint)
            canvas.drawRoundRect(frame, radius, radius, cornerPaint)
        }

        private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    }
}
