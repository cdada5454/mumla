package se.lublin.humla.net

import android.util.Log
import java.io.FileInputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.io.IOException
import java.security.KeyManagementException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import java.security.UnrecoverableKeyException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class HumlaSSLSocketFactory @Throws(
    NoSuchAlgorithmException::class,
    KeyManagementException::class,
    KeyStoreException::class,
    UnrecoverableKeyException::class,
    NoSuchProviderException::class,
    IOException::class,
    CertificateException::class
) constructor(
    keystore: KeyStore?,
    keystorePassword: String?,
    trustStorePath: String?,
    trustStorePassword: String,
    trustStoreFormat: String
) {
    private val context: SSLContext = SSLContext.getInstance("TLS")
    private val trustWrapper: HumlaTrustManagerWrapper

    init {
        val keyManagerFactory = KeyManagerFactory.getInstance("X509")
        keyManagerFactory.init(keystore, keystorePassword?.toCharArray() ?: CharArray(0))

        trustWrapper = if (trustStorePath != null) {
            val trustStore = KeyStore.getInstance(trustStoreFormat)
            FileInputStream(trustStorePath).use { stream ->
                trustStore.load(stream, trustStorePassword.toCharArray())
            }
            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(trustStore)
            Log.i(TAG, "Using custom trust store $trustStorePath with system trust store")
            HumlaTrustManagerWrapper(trustManagerFactory.trustManagers[0] as X509TrustManager)
        } else {
            Log.i(TAG, "Using system trust store")
            HumlaTrustManagerWrapper(null)
        }

        context.init(keyManagerFactory.keyManagers, arrayOf<TrustManager>(trustWrapper), null)
    }

    @Throws(IOException::class)
    fun createTorSocket(host: String, port: Int, proxyHost: String, proxyPort: Int): SSLSocket {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxyHost, proxyPort))
        val socket = Socket(proxy)
        socket.connect(InetSocketAddress.createUnresolved(host, port))
        return context.socketFactory.createSocket(socket, host, port, true) as SSLSocket
    }

    @Throws(IOException::class)
    fun createSocket(host: String, port: Int): SSLSocket =
        context.socketFactory.createSocket(InetAddress.getByName(host), port) as SSLSocket

    fun getServerChain(): Array<X509Certificate>? = trustWrapper.serverChain

    private class HumlaTrustManagerWrapper(
        private val trustManager: X509TrustManager?
    ) : X509TrustManager {
        private val defaultTrustManager: X509TrustManager
        var serverChain: Array<X509Certificate>? = null
            private set

        init {
            val defaultManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            defaultManagerFactory.init(null as KeyStore?)
            defaultTrustManager = defaultManagerFactory.trustManagers[0] as X509TrustManager
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            try {
                defaultTrustManager.checkClientTrusted(chain, authType)
            } catch (exception: CertificateException) {
                trustManager?.checkClientTrusted(chain, authType) ?: throw exception
            }
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            serverChain = chain
            try {
                defaultTrustManager.checkServerTrusted(chain, authType)
            } catch (exception: CertificateException) {
                trustManager?.checkServerTrusted(chain, authType) ?: throw exception
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = defaultTrustManager.acceptedIssuers
    }

    companion object {
        private val TAG = HumlaSSLSocketFactory::class.java.name
    }
}
