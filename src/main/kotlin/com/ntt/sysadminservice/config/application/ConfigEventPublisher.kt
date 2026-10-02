package com.ntt.sysadminservice.config.application

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class ConfigEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>
) {
    private val log = LoggerFactory.getLogger(ConfigEventPublisher::class.java)

    fun publishConfigChangedEvent(key: String, value: String) {
        val topic = "system.config.changed"
        val payload = """{"key": "$key", "timestamp": "${System.currentTimeMillis()}"}"""
        
        kafkaTemplate.send(topic, key, payload).whenComplete { _, ex ->
            if (ex != null) {
                log.error("Failed to publish config changed event for key $key", ex)
            } else {
                log.info("Successfully published config changed event to topic $topic for key $key")
            }
        }
    }
}
