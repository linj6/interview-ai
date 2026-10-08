package com.linj6.interviewagent.interview.domain;

/** V1 KnowledgeArea，枚举名作为持久化值。 */
public enum KnowledgeArea {
    /** Java 基础与核心机制。 */
    JAVA,
    /** Java 并发编程。 */
    CONCURRENCY,
    /** JVM。 */
    JVM,
    /** MySQL。 */
    MYSQL,
    /** Redis。 */
    REDIS,
    /** 消息队列，不绑定具体产品。 */
    MQ,
    /** Spring 生态及核心机制。 */
    SPRING,
    /** RPC、一致性和服务治理等分布式机制。 */
    DISTRIBUTED_SYSTEMS,
    /** 架构、容量、可用性、扩展性与技术取舍。 */
    SYSTEM_DESIGN;
}
