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

package com.tencent.bkrepo.common.artifact.cache.redis

import com.tencent.bkrepo.common.artifact.cache.EldestRemovedListener
import com.tencent.bkrepo.common.artifact.cache.OrderedCache
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.RedisScript

/**
 * 基于Redis实现的分布式LRU缓存
 * 为了避免阻塞提高性能，缓存满时将会异步执行LRU策略进行缓存清理，此时依然可以继续存放数据，可能会出现缓存大小超过限制的情况
 */
class RedisLRUCache(
    private val cacheName: String,
    private val redisTemplate: RedisTemplate<String, String>,
    private var capacity: Int = 0,
    private val listeners: MutableList<EldestRemovedListener<String, Any?>> = ArrayList(),
) : OrderedCache<String, Any?> {
    /**
     * 记录当前缓存的总权重
     */
    private val totalWeightKey = "$cacheName:total_weight"

    /**
     * 记录每个key权重
     */
    private val weightKey = "$cacheName:weight"

    /**
     * 缓存LRU队列，score为缓存写入时刻的时间戳
     */
    private val zsetKey = "$cacheName:zset"

    /**
     * 存放缓存实际值
     */
    private val hashKey = "$cacheName:hash"

    private val putScript = RedisScript.of(SCRIPT_PUT, Any::class.java)
    private val getScript = RedisScript.of(SCRIPT_GET, Any::class.java)
    private val removeScript = RedisScript.of(SCRIPT_REM, Any::class.java)

    private var maxWeight: Long = 0L
    private var weightSupplier: ((k: String, v: Any?) -> Long) = { _, _ -> 0 }

    override fun put(key: String, value: Any?): Any? {
        val keys = listOf(zsetKey, hashKey, totalWeightKey, weightKey)
        val weight = weightSupplier.invoke(key, value)
        val oldVal = redisTemplate.execute(putScript, keys, score(), key, value, weight)
        checkAndEvictEldest()
        return oldVal
    }

    override fun get(key: String): Any? {
        return redisTemplate.execute(getScript, listOf(zsetKey, hashKey), score(), key)
    }

    override fun containsKey(key: String): Boolean {
        return redisTemplate.opsForHash<String, Any?>().hasKey(hashKey, key) ?: false
    }

    override fun remove(key: String): Any? {
        val keys = listOf(zsetKey, hashKey, totalWeightKey, weightKey)
        return redisTemplate.execute(removeScript, keys, key)
    }

    override fun count(): Long {
        return redisTemplate.opsForHash<String, Any?>().size(hashKey) ?: 0L
    }

    override fun weight(): Long {
        return redisTemplate.opsForValue().get(totalWeightKey)?.toLong() ?: 0L
    }

    override fun setMaxWeight(max: Long) {
        this.maxWeight = max
    }

    override fun getMaxWeight(): Long = maxWeight

    override fun setCapacity(capacity: Int) {
        this.capacity = capacity
    }

    override fun getCapacity(): Int = capacity

    override fun setKeyWeightSupplier(supplier: (k: String, v: Any?) -> Long) {
        this.weightSupplier = supplier
    }

    override fun eldestKey(): String? {
        return redisTemplate.opsForZSet().range(zsetKey, 0L, 0L)?.firstOrNull()
    }

    override fun addEldestRemovedListener(listener: EldestRemovedListener<String, Any?>) {
        this.listeners.add(listener)
    }

    override fun getEldestRemovedListeners(): List<EldestRemovedListener<String, Any?>> {
        return listeners
    }

    private fun checkAndEvictEldest() {
        val opsForValue = redisTemplate.opsForValue()
        val opsForHash = redisTemplate.opsForHash<String, Any?>()
        while ((opsForValue.get(totalWeightKey)?.toLong() ?: 0L) > maxWeight ||
            (opsForHash.size(hashKey) ?: 0L) > capacity
        ) {
            val eldestKey = eldestKey()
            val value = eldestKey?.let { remove(it) }
            value?.let { listeners.forEach { it.onEldestRemoved(eldestKey, value) } }
        }
    }

    private fun score() = System.currentTimeMillis().toDouble()

    companion object {
        private const val SCRIPT_PUT = """
            redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])
            local oldWeight = redis.call('HGET', KEYS[4], ARGV[2])
            if (oldWeight != nil) then
              redis.call('DECRBY', KEYS[3], oldWeight)
            end
            redis.call('HSET', KEYS[4], ARGV[2], ARGV[4])
            redis.call('HSET', KEYS[2], ARGV[2], ARGV[3])
            redis.call('INCRBY', KEYS[3], ARGV[3])
            return oldVal
        """

        private const val SCRIPT_GET = """
            redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])
            return redis.call('HGET', KEYS[2], ARGV[2])
        """

        private const val SCRIPT_REM = """
            local oldWeight = redis.call('HGET', KEYS[4], ARGV[1])
            if oldWeight == nil then
              return nil
            end
            redis.call('DECRBY', KEYS[3], oldWeight)
            redis.call('HDEL', KEYS[2], ARGV[1])
            redis.call('HDEL', KEYS[4], ARGV[1])
            redis.call('ZREM', KEYS[1], ARGV[1])
            return oldVal
        """
    }
}