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

package com.tencent.bkrepo.auth.controller.user

import com.tencent.bk.sdk.iam.dto.callback.request.CallbackRequestDTO
import com.tencent.bk.sdk.iam.dto.callback.response.CallbackBaseResponseDTO
import com.tencent.bkrepo.auth.constant.AUTH_CONFIG_PREFIX
import com.tencent.bkrepo.auth.constant.AUTH_CONFIG_TYPE_NAME
import com.tencent.bkrepo.auth.constant.AUTH_CONFIG_TYPE_VALUE_BKIAMV3
import com.tencent.bkrepo.auth.constant.AUTH_CONFIG_TYPE_VALUE_DEVOPS
import com.tencent.bkrepo.auth.pojo.bkiam.BkResult
import com.tencent.bkrepo.auth.service.bkiamv3.callback.BkiamCallbackService
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/external/bkiam/callback")
@ConditionalOnExpression(
    "'\${$AUTH_CONFIG_PREFIX.$AUTH_CONFIG_TYPE_NAME}'.equals('$AUTH_CONFIG_TYPE_VALUE_DEVOPS')" +
        " or '\${$AUTH_CONFIG_PREFIX.$AUTH_CONFIG_TYPE_NAME}'.equals('$AUTH_CONFIG_TYPE_VALUE_BKIAMV3')"
)
class BkiamCallbackController @Autowired constructor(private val bkiamCallbackService: BkiamCallbackService) {

    @ApiOperation("项目列表")
    @PostMapping("/project")
    fun queryProject(
        @RequestHeader("Authorization")
        @ApiParam("token")
        token: String,
        @ApiParam("回调信息")
        @RequestBody request: CallbackRequestDTO
    ): CallbackBaseResponseDTO? {
        return bkiamCallbackService.queryProject(token, request)
    }

    @ApiOperation("仓库列表")
    @PostMapping("/repo")
    fun queryRepo(
        @RequestHeader("Authorization")
        @ApiParam("token")
        token: String,
        @ApiParam("回调信息")
        @RequestBody request: CallbackRequestDTO
    ): CallbackBaseResponseDTO? {
        return bkiamCallbackService.queryRepo(token, request)
    }

    @ApiOperation("节点列表")
    @PostMapping("/node")
    fun queryNode(
        @RequestHeader("Authorization")
        @ApiParam("token")
        token: String,
        @ApiParam("回调信息")
        @RequestBody request: CallbackRequestDTO
    ): CallbackBaseResponseDTO? {
        return bkiamCallbackService.queryNode(token, request)
    }

    @ApiOperation("健康检查")
    @GetMapping("/health")
    fun health(): BkResult<Boolean> {
        return BkResult(true)
    }
}