/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.replication.replica.base.interceptor

import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import com.tencent.bkrepo.replication.replica.base.interceptor.progress.ProgressRequestBody
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.connection.RealConnection
import okio.Buffer
import okio.Sink
import okio.Timeout
import org.slf4j.LoggerFactory
import java.lang.reflect.Field
import java.net.Socket
import javax.net.ssl.SSLSocket

class SocketInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (url.encodedPath.contains("/replica/blob/push")) {
            // 为push blob接口调用添加socket关闭过程日志
            proxySocket(chain.connection() as RealConnection, chain.request())
        }
        return chain.proceed(chain.request())
    }

    private fun proxySocket(connection: RealConnection, request: Request) {
        val body = request.body as ProgressRequestBody
        val task = getField(ProgressRequestBody::class.java.getDeclaredField("task"), body) as ReplicaTaskInfo
        val pushingBlobSha256 =
            getField(ProgressRequestBody::class.java.getDeclaredField("sha256"), body).toString()

        val bufferSink = getField(RealConnection::class.java.getDeclaredField("sink"), connection)
        val timeoutSinkField = bufferSink.javaClass.getDeclaredField("sink")
        val timeoutSink = getField(timeoutSinkField, bufferSink)
        // okio.AsyncTimeout.sink中返回的匿名内部类对象，第一个field为timeout,第二个为实际sink
        val asyncTimeout = getField(timeoutSink.javaClass.declaredFields[0], timeoutSink)
        val outputStreamSinkField = timeoutSink.javaClass.declaredFields[1]
        val outputStreamSink = getField(outputStreamSinkField, timeoutSink)

        // proxy socket
        val asyncTimeoutSocketField = asyncTimeout.javaClass.getDeclaredField("socket")
        val realSocket = getField(asyncTimeoutSocketField, asyncTimeout)
        asyncTimeoutSocketField.set(asyncTimeout, ProxySocket(task, pushingBlobSha256, realSocket as Socket))


        // proxy sink
        outputStreamSinkField.set(timeoutSink, ProxySink(task, pushingBlobSha256, outputStreamSink as Sink))
    }

    private fun getField(field: Field, obj: Any): Any {
        field.isAccessible = true
        return field.get(obj)
    }

    private class ProxySink(
        private val taskInfo: ReplicaTaskInfo,
        private val sha256: String,
        private val proxiedSink: Sink
    ) : Sink {
        override fun close() {
            proxiedSink.close()
        }

        override fun flush() {
            logger.info("push blob[$sha256], flushing sink, task[$taskInfo]")
            try {
                proxiedSink.flush()
            } catch (e: Exception) {
                logger.info("push blob[$sha256], flush sink failed, task[$taskInfo]")
                e.printStackTrace()
                throw e
            }
            logger.info("push blob[$sha256], flush sink success, task[$taskInfo]")
        }

        override fun timeout(): Timeout {
            return proxiedSink.timeout()
        }

        override fun write(source: Buffer, byteCount: Long) {
            val startTime = System.nanoTime()
            try {
                proxiedSink.write(source, byteCount)
            } catch (e: Exception) {
                logger.info(
                    "push blob[$sha256], write $byteCount byte to sink failed," +
                        " elapsed[${System.nanoTime() - startTime}], task[$taskInfo.id]"
                )
                e.printStackTrace()
                throw e
            }
        }

    }

    private class ProxySocket(
        private val taskInfo: ReplicaTaskInfo,
        private val sha256: String,
        private val proxiedSocket: Socket
    ) : Socket() {
        // SocketAsyncTimeout中只会调用close方法，仅进行测试，所以只需要代理close方法就行
        override fun close() {
            try {
                logger.info("closing socket of push file[$sha256], soLinger[${proxiedSocket.soLinger}] task[$taskInfo]")
                if (proxiedSocket is SSLSocket) {
                    val selfField = proxiedSocket::class.java.superclass.getDeclaredField("self")
                    selfField.isAccessible = true
                    val self = selfField.get(proxiedSocket) as Socket

                    val implField = Socket::class.java.getDeclaredField("impl")
                    implField.isAccessible = true
                    val impl = implField.get(self)
                    val fdUseCount = impl.javaClass.superclass.superclass.getDeclaredField("fdUseCount")
                    fdUseCount.isAccessible = true
                    if (fdUseCount.get(impl) as Int > 0) {
                        self.close()
                    }
                }
                proxiedSocket.close()
            } catch (e: Exception) {
                logger.info("close socket of push file[$sha256] failed, task[$taskInfo]")
                e.printStackTrace()
                throw e
            }
            logger.info("close socket of push file[$sha256] finished, task[$taskInfo]")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SocketInterceptor::class.java)
    }
}
