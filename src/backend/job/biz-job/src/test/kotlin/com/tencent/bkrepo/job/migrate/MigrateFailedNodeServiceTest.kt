package com.tencent.bkrepo.job.migrate

import com.tencent.bkrepo.common.metadata.dao.file.FileReferenceDao
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.dao.repo.RepositoryDao
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.metadata.service.file.FileReferenceService
import com.tencent.bkrepo.common.metadata.service.file.impl.FileReferenceServiceImpl
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.UT_SHA256
import com.tencent.bkrepo.job.UT_STORAGE_CREDENTIALS_KEY
import com.tencent.bkrepo.job.UT_USER
import com.tencent.bkrepo.job.batch.utils.NodeCommonUtils
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.migrate.dao.MigrateFailedNodeDao
import com.tencent.bkrepo.job.migrate.dao.MigrateRepoStorageTaskDao
import com.tencent.bkrepo.job.migrate.model.TMigrateFailedNode
import com.tencent.bkrepo.job.migrate.model.TMigrateRepoStorageTask
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState
import com.tencent.bkrepo.job.migrate.strategy.MigrateFailedNodeAutoFixStrategy
import com.tencent.bkrepo.job.migrate.strategy.MigrateFailedNodeFixer
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.createNode
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.insertFailedNode
import com.tencent.bkrepo.job.separation.service.SeparationTaskService
import com.tencent.bkrepo.job.service.MigrateArchivedFileService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.time.LocalDateTime

@DisplayName("迁移失败节点服务测试")
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(
    MigrateFailedNodeService::class,
    MigrateFailedNodeDao::class,
    MigrateRepoStorageTaskDao::class,
    MigrateFailedNodeFixer::class,
    FileReferenceServiceImpl::class,
    FileReferenceDao::class,
    RepositoryDao::class,
    NodeDao::class,
    StorageProperties::class,
    RepositoryCommonUtils::class,
)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
class MigrateFailedNodeServiceTest @Autowired constructor(
    private val migrateFailedNodeService: MigrateFailedNodeService,
    private val migrateFailedNodeDao: MigrateFailedNodeDao,
    private val fileReferenceService: FileReferenceService,
    private val mongoTemplate: MongoTemplate,
    private val nodeDao: NodeDao,
) {
    @Autowired
    private lateinit var migrateRepoStorageTaskDao: MigrateRepoStorageTaskDao

    @MockBean
    private lateinit var autoFixStrategy: MigrateFailedNodeAutoFixStrategy

    @MockBean
    private lateinit var separationTaskService: SeparationTaskService

    @MockBean
    private lateinit var migrateRepoStorageService: MigrateRepoStorageService

    @MockBean
    private lateinit var storageCredentialService: StorageCredentialService

    @MockBean
    private lateinit var storageService: StorageService

    @MockBean
    private lateinit var migrateArchivedFileService: MigrateArchivedFileService

    @MockBean
    private lateinit var repositoryService: RepositoryService

    @Autowired
    private lateinit var repositoryCommonUtils: RepositoryCommonUtils

    @BeforeAll
    fun beforeAll() {
        NodeCommonUtils.mongoTemplate = mongoTemplate
        NodeCommonUtils.migrateRepoStorageService = migrateRepoStorageService
        NodeCommonUtils.separationTaskService = separationTaskService
    }

    @BeforeEach
    fun beforeEach() {
        whenever(storageCredentialService.findByKey(anyOrNull())).thenReturn(FileSystemCredentials())
        whenever(autoFixStrategy.fix(any())).thenReturn(true)
        migrateFailedNodeDao.remove(Query())
        migrateRepoStorageTaskDao.remove(Query())
        nodeDao.remove(Query(TNode::projectId.isEqualTo(UT_PROJECT_ID)))
    }

    @Test
    fun testRemoveFailedNode() {
        // remove repo failed node
        migrateFailedNodeDao.insertFailedNode("/a/b/c.txt")
        migrateFailedNodeDao.insertFailedNode("/a/b/d.txt")
        assertEquals(2, migrateFailedNodeDao.count(Query()))
        migrateFailedNodeService.removeFailedNode(UT_PROJECT_ID, UT_REPO_NAME, null)
        assertEquals(0, migrateFailedNodeDao.count(Query()))

        // remove failed node
        val failedNode = migrateFailedNodeDao.insertFailedNode("/a/b/c.txt")
        assertEquals(1, migrateFailedNodeDao.count(Query()))
        migrateFailedNodeService.removeFailedNode(UT_PROJECT_ID, UT_REPO_NAME, failedNode.id!!)
        assertEquals(0, migrateFailedNodeDao.count(Query()))
    }

    @Test
    fun testResetRetryCount() {
        val failedNode1 = migrateFailedNodeDao.insertFailedNode("/a/b/c.txt")
        val failedNode2 = migrateFailedNodeDao.insertFailedNode("/a/b/d.txt")

        // reset repo nodes
        var node1 = migrateFailedNodeDao.findOneToRetry(UT_PROJECT_ID, UT_REPO_NAME)!!
        var node2 = migrateFailedNodeDao.findOneToRetry(UT_PROJECT_ID, UT_REPO_NAME)!!
        assertEquals(1, node1.retryTimes)
        assertEquals(1, node2.retryTimes)
        migrateFailedNodeService.resetRetryCount(UT_PROJECT_ID, UT_REPO_NAME, null)
        assertEquals(0, migrateFailedNodeDao.findById(node1.id!!)!!.retryTimes)
        assertEquals(0, migrateFailedNodeDao.findById(node2.id!!)!!.retryTimes)
        migrateFailedNodeDao.resetMigrating(failedNode1.id!!)
        migrateFailedNodeDao.resetMigrating(failedNode2.id!!)

        // reset single node
        node1 = migrateFailedNodeDao.findOneToRetry(UT_PROJECT_ID, UT_REPO_NAME)!!
        node2 = migrateFailedNodeDao.findOneToRetry(UT_PROJECT_ID, UT_REPO_NAME)!!
        assertEquals(1, node1.retryTimes)
        assertEquals(1, node2.retryTimes)
        migrateFailedNodeService.resetRetryCount(UT_PROJECT_ID, UT_REPO_NAME, failedNode1.id!!)
        assertEquals(0, migrateFailedNodeDao.findById(node1.id!!)!!.retryTimes)
        assertEquals(1, migrateFailedNodeDao.findById(node2.id!!)!!.retryTimes)
    }

    @Test
    fun testAutoFix() {
        val node1 = migrateFailedNodeDao.insertFailedNode("/a/b/c.txt")
        val node2 = migrateFailedNodeDao.insertFailedNode("/a/b/d.txt")
        migrateFailedNodeDao.updateFirst(
            Query(Criteria.where(ID).isEqualTo(node1.id)),
            Update().set(TMigrateFailedNode::retryTimes.name, 2)
        )
        migrateFailedNodeDao.updateFirst(
            Query(Criteria.where(ID).isEqualTo(node2.id)),
            Update().set(TMigrateFailedNode::retryTimes.name, 3)
        )
        assertEquals(2, migrateFailedNodeDao.findById(node1.id!!)!!.retryTimes)
        assertEquals(3, migrateFailedNodeDao.findById(node2.id!!)!!.retryTimes)

        migrateFailedNodeService.autoFix(UT_PROJECT_ID, UT_REPO_NAME)
        Thread.sleep(1000L)
        assertEquals(2, migrateFailedNodeDao.findById(node1.id!!)!!.retryTimes)
        assertEquals(0, migrateFailedNodeDao.findById(node2.id!!)!!.retryTimes)
    }

    @Test
    fun testAutoFixAll() {
        val now = LocalDateTime.now()
        migrateRepoStorageTaskDao.insert(
            TMigrateRepoStorageTask(
                id = null,
                createdBy = UT_USER,
                createdDate = now,
                lastModifiedBy = UT_USER,
                lastModifiedDate = now,
                startDate = now,
                totalCount = 2,
                migratedCount = 2,
                lastMigratedNodeId = "",
                projectId = UT_PROJECT_ID,
                repoName = UT_REPO_NAME,
                srcStorageKey = null,
                dstStorageKey = UT_STORAGE_CREDENTIALS_KEY,
                state = MigrateRepoStorageTaskState.MIGRATING_FAILED_NODE.name,
            )
        )

        val node1 = migrateFailedNodeDao.insertFailedNode("/a/b/c.txt")
        val node2 = migrateFailedNodeDao.insertFailedNode("/a/b/d.txt")
        migrateFailedNodeDao.updateMulti(Query(), Update().set(TMigrateFailedNode::retryTimes.name, 3))
        assertEquals(3, migrateFailedNodeDao.findById(node1.id!!)!!.retryTimes)
        assertEquals(3, migrateFailedNodeDao.findById(node2.id!!)!!.retryTimes)

        migrateFailedNodeService.autoFix()
        Thread.sleep(1000L)
        assertEquals(0, migrateFailedNodeDao.findById(node1.id!!)!!.retryTimes)
        assertEquals(0, migrateFailedNodeDao.findById(node2.id!!)!!.retryTimes)
    }

    @Test
    fun testCorrectMigratedStorageFileReference() {
        val sha2561 = "688787d8ff144c502c7f5cffaafe2cc588d86079f9de88304c26b0cb99ce91c1"
        val sha2562 = "688787d8ff144c502c7f5cffaafe2cc588d86079f9de88304c26b0cb99ce91c2"
        val sha2563 = "688787d8ff144c502c7f5cffaafe2cc588d86079f9de88304c26b0cb99ce91c3"
        val sha2564 = "688787d8ff144c502c7f5cffaafe2cc588d86079f9de88304c26b0cb99ce91c4"

        fun mockData(src: String?, dst: String?) {
            // dst引用不存在
            fileReferenceService.increment(sha2561, src, 2L)

            // src与dst都存在引用
            fileReferenceService.increment(sha2562, src, 1L)
            fileReferenceService.increment(sha2562, dst, 1L)

            // src引用为0
            fileReferenceService.increment(sha2564, src, 0L)

            // src引用不存在
            fileReferenceService.increment(sha2563, dst, 1L)
        }

        fun assertResult(src: String?, dst: String?) {
            // src引用减为0并创建dst引用
            assertEquals(0L, fileReferenceService.get(src, sha2561).count)
            assertEquals(0L, fileReferenceService.get(dst, sha2561).count)

            // src引用减为0，dst引用不变
            assertEquals(0L, fileReferenceService.get(src, sha2562).count)
            assertEquals(1L, fileReferenceService.get(dst, sha2562).count)

            // src引用不变并创建dst引用
            assertEquals(0L, fileReferenceService.get(src, sha2564).count)
            assertEquals(0L, fileReferenceService.get(dst, sha2564).count)

            // dst引用不变
            assertFalse(fileReferenceService.exists(sha2563, src))
            assertEquals(1L, fileReferenceService.get(dst, sha2563).count)
        }

        fun test(src: String?, dst: String?) {
            mockData(src, dst)
            migrateFailedNodeService.correctMigratedStorageFileReference(src, dst)
            Thread.sleep(1000L)
            assertResult(src, dst)
        }

        // default credentials
        test(null, "test-dst")

        // other credentials
        test("test-src2", "test-dst2")
    }

    @Test
    fun testFixMissingFailedNode() {
        val now = LocalDateTime.now()
        migrateRepoStorageTaskDao.insert(
            TMigrateRepoStorageTask(
                id = "",
                createdBy = UT_USER,
                createdDate = now,
                lastModifiedBy = UT_USER,
                lastModifiedDate = now,
                startDate = now,
                totalCount = 4,
                migratedCount = 4,
                lastMigratedNodeId = "",
                projectId = UT_PROJECT_ID,
                repoName = UT_REPO_NAME,
                srcStorageKey = null,
                dstStorageKey = UT_STORAGE_CREDENTIALS_KEY,
                state = MigrateRepoStorageTaskState.MIGRATING_FAILED_NODE.name,
            )
        )
        migrateFailedNodeDao.insertFailedNode()
        nodeDao.createNode(sha256 = UT_SHA256)
        nodeDao.createNode(
            sha256 = "688787d8ff144c502c7f5cffaafe2cc588d86079f9de88304c26b0cb99ce91c1",
            deleted = LocalDateTime.now().minus(Duration.ofMinutes(1L)),
        )
        nodeDao.createNode(
            sha256 = "688787d8ff144c502c7f5cffaafe2cc588d86079f9de88304c26b0cb99ce91c2",
            deleted = LocalDateTime.now().minus(Duration.ofMinutes(2L)),
            archived = true
        )
        nodeDao.createNode(
            sha256 = "688787d8ff144c502c7f5cffaafe2cc588d86079f9de88304c26b0cb99ce91c2",
            deleted = LocalDateTime.now().minus(Duration.ofMinutes(3L)),
        )
        nodeDao.createNode(
            sha256 = "688787d8ff144c502c7f5cffaafe2cc588d86079f9de88304c26b0cb99ce91c2",
            fullPath = "/a/b/d.txt",
            deleted = LocalDateTime.now().minus(Duration.ofMinutes(3L)),
        )
        assertEquals(1L, migrateFailedNodeDao.count(Query()))

        whenever(storageService.exist(anyString(), anyOrNull())).thenReturn(false)
        whenever(migrateArchivedFileService.archivedFileCompleted(anyOrNull(), anyString())).thenReturn(false)
        migrateFailedNodeService.fixMissingFailedNode()
        Thread.sleep(1000L)
        assertEquals(3L, migrateFailedNodeDao.count(Query()))
    }

    @Test
    fun testUpdateNodeArchived() {
        val node = nodeDao.createNode()
        assertEquals(false, node.archived)
        migrateFailedNodeService.updateNodeArchiveStatus(node.projectId, node.id!!, true)
        assertEquals(true, nodeDao.findNode(node.projectId, node.repoName, node.fullPath)!!.archived)
        migrateFailedNodeService.updateNodeArchiveStatus(node.projectId, node.id!!, false)
        assertEquals(false, nodeDao.findNode(node.projectId, node.repoName, node.fullPath)!!.archived)
    }
}
