package com.jpmc.midascore.component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.jpmc.midascore.foundation.Transaction;

@Component
public class TransactionListener {
    
    private static final Logger logger = LoggerFactory.getLogger(TransactionListener.class);
    
    private final DatabaseConduit databaseConduit;

    public TransactionListener(DatabaseConduit databaseConduit) {
        this.databaseConduit = databaseConduit;
    }
    
    @KafkaListener(topics = "${general.kafka-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void listen(Transaction transaction) {
        logger.info("Received transaction: {}", transaction);
        
        boolean success = databaseConduit.processTransaction(
            transaction.getSenderId(),
            transaction.getRecipientId(),
            transaction.getAmount()
        );

        if (success) {
            logger.info("Successfully processed transaction: {}", transaction);
        } else {
            logger.warn("Failed to process transaction: {}. Invalid transaction or insufficient funds.", transaction);
        }
    }
}
