/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.migrate.utils

import java.util.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 由于目前是多线程迁移，可能后提交的任务先完成，需要按顺序记录已完成的任务号用于后续断点重新迁移
 */
class MigratedTaskNumberPriorityQueue {
    private val q = PriorityQueue<Long>()
    private val lock = ReentrantLock()
    private val lastLeftMax = AtomicLong(0L)

    fun offer(e: Long) {
        lock.withLock { q.offer(e) }
    }

    /**
     * 获取最左连续序列的最大值，例如
     * lastLeftMax为0，队列[1,2,3,7,9,10]将返回3
     * lastLeftMax为4，队列[7,8,9，11，13]将返回4
     *
     * @return 最左连续序列的最大值
     */
    fun updateLeftMax(): Long {
        lock.withLock {
            var first = lastLeftMax.get()
            var second = q.peek()
            while (second != null && second - first == 1L) {
                first = q.poll()
                second = q.peek()
            }
            lastLeftMax.set(first)
            return first
        }
    }

    fun size(): Int = q.size
}