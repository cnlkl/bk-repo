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

package com.tencent.bkrepo.job.batch.task.clean

import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.dao.util.sharding.HashShardingUtils
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.batch.utils.TimeUtils
import com.tencent.bkrepo.job.config.properties.StorageRollbackJobProperties
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import com.tencent.bkrepo.repository.pojo.file.StoreRecord
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.lt
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 用于存储成功，但是node创建失败的情况下清理垃圾文件
 */
@Component
@EnableConfigurationProperties(StorageRollbackJobProperties::class)
class StorageRollbackJob(
    private val properties: StorageRollbackJobProperties,
    private val storageProperties: StorageProperties,
    private val storageService: StorageService,
) : DefaultContextMongoDbJob<StoreRecord>(properties) {
    override fun collectionNames() = listOf(COLLECTION_NAME)

    override fun buildQuery(): Query {
        val timeoutDateTime = LocalDateTime.now().minus(properties.timeout)
        return Query(StoreRecord::lastModifiedDate.lt(timeoutDateTime))
    }

    override fun mapToEntity(row: Map<String, Any?>): StoreRecord {
        return StoreRecord(
            id = row[ID].toString(),
            createdDate = TimeUtils.parseMongoDateTimeStr(row[StoreRecord::createdDate.name].toString())!!,
            lastModifiedDate = TimeUtils.parseMongoDateTimeStr(row[StoreRecord::createdDate.name].toString())!!,
            sha256 = row[StoreRecord::sha256.name].toString(),
            credentialsKey = row[StoreRecord::credentialsKey.name]?.toString(),
        )
    }

    override fun entityClass() = StoreRecord::class

    override fun run(row: StoreRecord, collectionName: String, context: JobContext) {
        logger.info("start rollback file[${row.sha256}] of storage[${row.credentialsKey}]")
        val fileReferenceCriteria = FileReference::sha256.isEqualTo(row.sha256)
            .and(FileReference::credentialsKey.name).isEqualTo(row.credentialsKey)
        val sharding = HashShardingUtils.shardingSequenceFor(row.sha256, SHARDING_COUNT)
        val fileRefCollectionName = "$COLLECTION_PREFIX$sharding"
        val fileReferenceExists = mongoTemplate.exists(Query(fileReferenceCriteria), fileRefCollectionName)
        val credentials = row.credentialsKey
            ?.let { RepositoryCommonUtils.getStorageCredentials(it) }
            ?: storageProperties.defaultStorageCredentials()

        if (fileReferenceExists) {
            // 文件引用存在时不需要回滚，此时删除store record
            logger.info("file reference[${row.sha256}] of storage[${row.credentialsKey}] exists, skip rollback")
        } else if (storageService.exist(row.sha256, credentials)) {
            // 文件引用不存在时表示制品存储成功后node未成功创建，此时需要删除冗余存储
            // 创建一个计数为0的引用，FileReferenceCleanupJob中会清理该文件
            logger.info("create count 0 reference[${row.sha256}] of storage[${row.credentialsKey}]")
            mongoTemplate.insert(FileReference(null, row.sha256, row.credentialsKey, 0))
        } else {
            // 引用与文件都不存在时表示文件未成功存储，不需要回滚
            logger.warn("file[${row.sha256}] of storage[${row.credentialsKey}] not exists, skip rollback")
        }

        // 处理结束后删除存储记录
        mongoTemplate.remove(Query(Criteria.where(ID).isEqualTo(row.id)))
    }

    data class FileReference(
        val id: String? = null,
        val sha256: String,
        val credentialsKey: String? = null,
        val count: Long
    )

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
        private const val COLLECTION_NAME = "store_record"
        private const val COLLECTION_PREFIX = "file_reference_"
    }
}