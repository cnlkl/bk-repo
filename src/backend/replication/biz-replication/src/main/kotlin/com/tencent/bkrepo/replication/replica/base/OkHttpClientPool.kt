package com.tencent.bkrepo.replication.replica.base

import com.tencent.bkrepo.common.artifact.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.replication.replica.base.interceptor.SocketInterceptor
import com.tencent.bkrepo.replication.replica.base.interceptor.progress.ProgressInterceptor
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.net.InetAddress
import java.net.Socket
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

/**
 * OkHttpClient池，提供OkHttpClient复用
 * */
object OkHttpClientPool {
    private val clientCache = ConcurrentHashMap<ClusterInfo, OkHttpClient>()
    fun getHttpClient(
        clusterInfo: ClusterInfo,
        readTimeout: Duration,
        writeTimeout: Duration,
        vararg interceptors: Interceptor
    ): OkHttpClient {
        return clientCache.getOrPut(clusterInfo) {
            val builder = HttpClientBuilderFactory.create(clusterInfo.certificate)
                .protocols(listOf(Protocol.HTTP_1_1))
                .readTimeout(readTimeout)
                .retryOnConnectionFailure(false)
                .writeTimeout(writeTimeout)
                .socketFactory(SocketFactorProxy(SocketFactory.getDefault()))
            interceptors.forEach {
                builder.addInterceptor(
                    it
                )
            }
//            val logInterceptor = HttpLoggingInterceptor()
//            logInterceptor.level = HttpLoggingInterceptor.Level.HEADERS
//            builder.addInterceptor(logInterceptor)
            builder.addNetworkInterceptor(ProgressInterceptor())
            builder.addNetworkInterceptor(SocketInterceptor())
            builder.connectionPool(ConnectionPool(5, 1, TimeUnit.MINUTES))
            builder.build()
        }
    }
}

private class SocketFactorProxy(private val proxiedFactory: SocketFactory) : SocketFactory() {
    override fun createSocket(): Socket {
        return init(proxiedFactory.createSocket())
    }

    override fun createSocket(host: String?, port: Int): Socket {
        return init(proxiedFactory.createSocket(host, port))
    }

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket {
        return init(proxiedFactory.createSocket(host, port, localHost, localPort))
    }

    override fun createSocket(host: InetAddress?, port: Int): Socket {
        return init(proxiedFactory.createSocket(host, port))
    }

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket {
        return init(proxiedFactory.createSocket(address, port, localAddress, localPort))
    }

    private fun init(socket: Socket): Socket {
        socket.setSoLinger(true, 0)
        return socket
    }
}
