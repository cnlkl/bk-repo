/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.repository.pojo.packages

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.time.LocalDateTime

/**
 * 包总览信息
 */
@ApiModel("包总览信息")
data class PackageSummary(
    @ApiModelProperty("id")
    val id: String,
    @ApiModelProperty("创建者")
    val createdBy: String,
    @ApiModelProperty("创建时间")
    val createdDate: LocalDateTime,
    @ApiModelProperty("修改者")
    val lastModifiedBy: String,
    @ApiModelProperty("修改时间")
    val lastModifiedDate: LocalDateTime,

    @ApiModelProperty("所属项目id")
    val projectId: String,
    @ApiModelProperty("所属仓库名称")
    val repoName: String,
    @ApiModelProperty("包名称")
    val name: String,
    @ApiModelProperty("包唯一key")
    val key: String,
    @ApiModelProperty("包类型")
    var type: PackageType,
    @ApiModelProperty("最新版名称")
    val latest: String,
    @ApiModelProperty("下载次数")
    val downloads: Long,
    @ApiModelProperty("版本数量")
    var versions: Long,
    @ApiModelProperty("包简要描述")
    var description: String? = null,
    @ApiModelProperty("版本标签")
    val versionTag: Map<String, String>,
    @ApiModelProperty("扩展字段")
    val extension: Map<String, Any>,
    @ApiModelProperty("历史版本")
    val historyVersion: Set<String>
)
