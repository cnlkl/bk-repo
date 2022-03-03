package com.tencent.bkrepo.job.batch.base

import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.job.BATCH_SIZE
import com.tencent.bkrepo.job.ID
import com.tencent.bkrepo.job.MIN_OBJECT_ID
import com.tencent.bkrepo.job.executor.BlockThreadPoolTaskExecutorDecorator
import com.tencent.bkrepo.job.executor.IdentityTask
import java.util.concurrent.CountDownLatch
import kotlin.system.measureNanoTime
import net.javacrumbs.shedlock.core.LockingTaskExecutor
import org.bson.types.ObjectId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query

/**
 * MongoDb抽象批处理作业Job
 * */
abstract class MongoDbBatchJob<T> : BatchJob() {
    /**
     * 需要操作的表名列表
     * */
    abstract fun collectionNames(): List<String>

    /**
     * 需要处理数据的查询语句
     * */
    abstract fun buildQuery(): Query

    /**
     * 处理单条数据函数
     * @param row 单个数据
     * @param collectionName 当前数据所在表
     * */
    abstract fun run(row: T, collectionName: String, context: JobContext)

    /**
     * 将map对象化
     * @param row 查询返回的表单个数据map
     * */
    abstract fun mapToObject(row: Map<String, Any?>): T

    /**
     * 并发级别
     * 默认序列化，即顺序执行
     * */
    open val concurrentLevel: JobConcurrentLevel = JobConcurrentLevel.SERIALIZE

    /**
     * 每次批处理作业大小
     * */
    protected open val batchSize: Int = BATCH_SIZE

    @Autowired
    private lateinit var lockingTaskExecutor: LockingTaskExecutor

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    /**
     * job批处理执行器
     * */
    @Autowired
    private lateinit var executor: BlockThreadPoolTaskExecutorDecorator

    override fun doStart(jobContext: JobContext) {
        try {
            val collectionNames = collectionNames()
            if (concurrentLevel == JobConcurrentLevel.COLLECTION) {
                // 使用闭锁来保证表异步生产任务的结束
                val countDownLatch = CountDownLatch(collectionNames.size)
                runAsync(collectionNames, true) {
                    runCollection(it, jobContext)
                    countDownLatch.countDown()
                }
                countDownLatch.await()
            } else {
                collectionNames.forEach {
                    runCollection(it, jobContext)
                }
            }
        } finally {
            if (concurrentLevel != JobConcurrentLevel.SERIALIZE) {
                executor.completeAndGet(taskId, WAIT_TIMEOUT)
            }
        }
    }

    /**
     * 处理单个表数据
     * */
    private fun runCollection(collectionName: String, context: JobContext) {
        if (!isRunning()) {
            logger.info("Job[${getJobName()}] already stop.")
            return
        }
        logger.info("Job[${getJobName()}]: Start collection $collectionName.")
        val pageSize = batchSize
        var querySize: Int
        var lastId = ObjectId(MIN_OBJECT_ID)
        var sum = 0L
        measureNanoTime {
            do {
                val data = mongoTemplate.find<Map<String, Any?>>(
                    buildQuery().addCriteria(Criteria.where(ID).gt(lastId)).limit(batchSize),
                    collectionName
                )
                if (data.isEmpty()) {
                    break
                }
                if (concurrentLevel >= JobConcurrentLevel.ROW) {
                    runAsync(data) { runRow(it, collectionName, context) }
                } else {
                    data.forEach { runRow(it, collectionName, context) }
                }
                sum += data.size
                querySize = data.size
                lastId = data.last()[ID] as ObjectId
                report(context)
            } while (querySize == pageSize && isRunning())
        }.apply {
            val elapsedTime = HumanReadable.time(this)
            logger.info("Job[${getJobName()}]: collection $collectionName run completed,sum [$sum] elapse $elapsedTime")
        }
    }

    /**
     * 异步执行task列表
     * 通过信号量控制最大任务数,达到最大任务数，线程会阻塞等待
     * @param tasks 任务列表
     * @param block 处理函数
     * */
    private fun <T> runAsync(
        tasks: Iterable<T>,
        produce: Boolean = false,
        block: (it: T) -> Unit
    ) {
        tasks.forEach {
            val task = IdentityTask(taskId, Runnable { block(it) })
            executor.executeWithId(task, produce)
        }
    }

    /**
     * 在上下文中执行单个任务
     * @param data 待处理的数据
     * @param collectionName 数据所在表名
     * @param context 任务上下文
     * */
    private fun runRow(
        data: Map<String, Any?>,
        collectionName: String,
        context: JobContext
    ) {
        try {
            val resultMap = data.toMutableMap()
            resultMap[JAVA_ID] = resultMap[ID].toString()
            run(mapToObject(resultMap), collectionName, context)
            context.success.incrementAndGet()
        } catch (e: Exception) {
            context.failed.incrementAndGet()
            logger.error(e.message, e)
        } finally {
            context.total.incrementAndGet()
        }
    }

    companion object {
        private val logger = LoggerHolder.jobLogger

        // 用于dao层转换
        private const val JAVA_ID = "id"

        // 30s单条记录执行超时时间
        private const val WAIT_TIMEOUT = 30_000L
    }
}