/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.stream.binder.pulsar.autoconfigurate

import com.tencent.bkrepo.common.stream.binder.pulsar.PulsarMessageChannelBinder
import com.tencent.bkrepo.common.stream.binder.pulsar.properties.PulsarBinderConfigurationProperties
import com.tencent.bkrepo.common.stream.binder.pulsar.properties.PulsarExtendedBindingProperties
import com.tencent.bkrepo.common.stream.binder.pulsar.properties.PulsarProperties
import com.tencent.bkrepo.common.stream.binder.pulsar.provisioning.PulsarMessageQueueProvisioner
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
    PulsarProperties::class,
    PulsarExtendedBindingProperties::class
)
class PulsarBinderAutoConfiguration {

    @Bean
    fun configurationProperties(
        pulsarProperties: PulsarProperties?
    ): PulsarBinderConfigurationProperties? {
        return PulsarBinderConfigurationProperties(pulsarProperties)
    }

    @Bean
    fun pulsarMessageQueueProvisioner(): PulsarMessageQueueProvisioner {
        return PulsarMessageQueueProvisioner()
    }

    @Bean
    fun pulsarMessageChannelBinder(
        provisioningProvider: PulsarMessageQueueProvisioner,
        bindingProperties: PulsarExtendedBindingProperties,
        pulsarBinderProperties: PulsarBinderConfigurationProperties
    ): PulsarMessageChannelBinder? {
        return if (pulsarBinderProperties.pulsarProperties == null) {
            null
        } else {
            PulsarMessageChannelBinder(
                provisioningProvider,
                bindingProperties,
                pulsarBinderProperties
            )
        }
    }

//    @Configuration(proxyBeanMethods = false)
//    @ConditionalOnClass(HealthIndicator::class)
//    @ConditionalOnEnabledHealthIndicator("pulsar")
//    internal class PulsarBinderHealthIndicatorConfiguration {
//        @Bean
//        fun pulsarBinderHealthIndicator(): PulsarBinderHealthIndicator {
//            return PulsarBinderHealthIndicator()
//        }
//    }
}