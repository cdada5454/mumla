package se.lublin.humla.net

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Deprecated("This shouldn't be needed. Redundant inheritance with limited shared code.")
abstract class HumlaNetworkThread : Runnable {
    private var executor: ExecutorService? = null
    private var sendExecutor: ExecutorService? = null
    private var receiveExecutor: ExecutorService? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var initialized = false

    protected fun startThreads() {
        if (initialized) {
            throw IllegalArgumentException("Threads already initialized.")
        }
        executor = Executors.newSingleThreadExecutor()
        sendExecutor = Executors.newSingleThreadExecutor()
        receiveExecutor = Executors.newSingleThreadExecutor()
        executor!!.execute(this)
        initialized = true
    }

    protected fun stopThreads() {
        if (!initialized) {
            Log.e(TAG, "Error in stopThreads: Threads already shutdown")
            return
        }
        sendExecutor!!.shutdown()
        receiveExecutor!!.shutdownNow()
        executor!!.shutdownNow()
        sendExecutor = null
        receiveExecutor = null
        executor = null
        initialized = false
    }

    protected fun executeOnSendThread(runnable: Runnable) {
        sendExecutor?.execute(runnable)
    }

    protected fun executeOnReceiveThread(runnable: Runnable) {
        sendExecutor?.execute(runnable)
    }

    protected fun executeOnMainThread(runnable: Runnable) {
        mainHandler.post(runnable)
    }

    protected fun getMainHandler(): Handler = mainHandler

    companion object {
        private val TAG = HumlaNetworkThread::class.java.name
    }
}
