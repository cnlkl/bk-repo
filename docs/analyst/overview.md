# 制品分析介绍

制品分析功能主要由`analyst`和`analysis-executor`两个服务构成

`analyst`服务负责管理扫描器、任务执行集群、扫描任务、扫描报告存取

`analysis-executor`是实际执行扫描任务的服务，通过`analyst`服务创建的任务最终都将由`analysis-executor`执行，
执行完后再将扫描结果上报到`analyst`服务

# 如何执行扫描

下面提到的接口详情见api文档

1. 通过`/api/scanners`接口创建扫描器
2. 通过`/api/scan`接口指定使用的扫描器和要扫描的文件，创建扫描任务
3. 通过`/api/scan/tasks/{taskId}`接口获取扫描任务信息，查看当前任务状态和进度
4. 扫描结束后可以通过`api/scan/reports/overview`接口获取指定文件的扫描结果预览信息， 或者通过`/api/scan/reports/detail/{artifactUri}`获取指定文件的详细扫描报告

# 相关设计

## 扫描器

制品扫描对扫描器做了抽象，可以根据不同的扫描器类型编写对应的实现，现在支持了科恩实验室提供的制品扫描器

不同扫描器实现可以自定义扫描器的配置、如何执行扫描、扫描结果如何存取

## 扫描任务

通过指定扫描器和要扫描的文件可以创建一个扫描任务，一个扫描任务扫描的每个文件都对应一个子扫描任务

所有子扫描任务完成后则扫描任务完成

### 扫描任务状态

1. 扫描任务刚创建时处于PENDING状态 
2. 创建后会立即提交扫描调度器，开始提交子任务，此时处于SCANNING_SUBMITTING状态 
3. 所有子任务提交完后改变为SCANNING_SUBMITTED状态 
4. 所有子任务扫描完成结果上报后扫描任务改变为FINISHED状态

会有定时任务将处于PENDING和SCANNING_SUBMITTING超过指定时间的扫描任务重新提交扫描

### 子扫描任务状态

子扫描任务创建后会保存在数据库的任务队列中，如果有其他任务队列实现也会被加入到对应的队列中

1. 子任务刚创建时处于CREATED状态，如果任务数超过项目任务配额将处于BLOCKED状态
2. 子任务被主动拉取时处于PULLED状态，此时可能尚未下发到执行集群
3. 扫描执行器开始执行任务后子任务后会上报状态，此时更新子任务状态为EXECUTING
4. 扫描结束上报结果后子任务从数据库的队列中移除
    
会定时查询数据库中的子扫描任务队列，将CREATED或者处于PULLED、EXECUTING这两个状态过久的任务重新提交执行，
一个子扫描任务最多执行次数有限制，超过限制后会被从数据库中的扫描任务队列移除，不再重试

## 扫描结果

每个文件使用指定版本的扫描器都有一个扫描结果，当使用同一扫描器版本扫描同一个文件时会复用之前的扫描结果，不会实际执行扫描

类似漏洞数量、敏感信息数量这种统计类型数据会存储到通用的扫描结果表中

特定类型扫描器特有的扫描结果会根据不同的扫描器实现进行存取，比如目前实现的arrowhead扫描器结果存储在单独的数据库中

## 相关Node元数据

扫描过程中会将扫描任务状态更新到制品元数据中，key为`scanStatus`，value可选值如下

INIT：等待扫描
RUNNING: 扫描中
STOP：扫描中止
UN_QUALITY：未设置质量规则
QUALITY_PASS：质量规则通过
FAILED：扫描异常
QUALITY_UNPASS：质量规则未通过
