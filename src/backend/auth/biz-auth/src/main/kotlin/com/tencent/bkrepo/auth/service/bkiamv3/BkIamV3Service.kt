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

package com.tencent.bkrepo.auth.service.bkiamv3

/**
 * bk iamv3接口调用
 */
interface BkIamV3Service {

    /**
     * 生成无权限跳转url
     */
    fun getPermissionUrl(
        userId: String,
        projectId: String,
        repoName: String?,
        resourceType: String,
        action: String,
        resourceId: String,
    ): String?

    /**
     * 鉴权
     */
    fun validateResourcePermission(
        userId: String,
        projectId: String,
        repoName: String?,
        resourceType: String,
        action: String,
        resourceId: String,
        appId: String?
    ): Boolean

    /**
     * 仓库资源id转换
     */
    fun convertRepoResourceId(projectId: String, repoName: String): String?

    /**
     * 节点资源id转换
     */
    fun convertNodeResourceId(projectId: String, repoName: String, fullPath: String): String?

    /**
     * 获取有权限的资源列表
     */
    fun listPermissionResources(
        userId: String,
        projectId: String? = null,
        resourceType: String,
        action: String,
    ): List<String>

    /**
     * 创建项目分级管理员
     */
    fun createGradeManager(
        userId: String,
        projectId: String,
        repoName: String? = null
    ): String?

    /**
     * 删除仓库分级管理员
     */
    fun deleteRepoGradeManager(
        userId: String,
        projectId: String,
        repoName: String
    ): Boolean

    /**
     * 资源id转换
     */
    fun getResourceId(resourceType: String, projectId: String?, repoName: String?, path: String?): String?
}