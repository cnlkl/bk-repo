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

package com.tencent.bkrepo.common.artifact.cache

import com.tencent.bkrepo.repository.pojo.node.NodeDetail

/**
 * 缓存清理器，用于记录缓存访问情况，在缓存达到限制大小时清理存储层缓存
 */
interface ArtifactCacheCleaner {
    /**
     * 缓存被访问时的回调，用于缓存清理决策
     *
     * @param projectId 被访问制品所属项目
     * @param repoName 被访问制品所属仓库
     * @param fullPath 被访问的制品
     */
    fun onCacheAccessed(projectId: String, repoName: String, fullPath: String)

    /**
     * 缓存被访问时的回调，用于缓存清理决策
     *
     * @param node 被访问的制品
     * @param storageKey 制品所在存储
     */
    fun onCacheAccessed(node: NodeDetail, storageKey: String?)

    /**
     * 存储层缓存被删除时调用
     *
     * @param storageKey 缓存所在的存储
     * @param sha256 被删除的缓存文件的sha256
     */
    fun onCacheDeleted(storageKey: String, sha256: String)
}