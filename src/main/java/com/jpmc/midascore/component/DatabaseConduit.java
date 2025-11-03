package com.jpmc.midascore.component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.jpmc.midascore.entity.TransactionRecord;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.foundation.Incentive;
import com.jpmc.midascore.foundation.Transaction;
import com.jpmc.midascore.repository.TransactionRepository;
import com.jpmc.midascore.repository.UserRepository;

@Component
public class DatabaseConduit {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConduit.class);

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final RestTemplate restTemplate;
    private final String incentiveUrl = "http://localhost:8080/incentive";

    public DatabaseConduit(UserRepository userRepository, TransactionRepository transactionRepository, RestTemplateBuilder restTemplateBuilder) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.restTemplate = restTemplateBuilder.build();
    }

    public void save(UserRecord userRecord) {
        userRepository.save(userRecord);
    }

    public UserRecord findUserById(long id) {
        return userRepository.findById(id);
    }

    @Transactional
    public boolean processTransaction(long senderId, long recipientId, float amount) {
        UserRecord sender = userRepository.findById(senderId);
        UserRecord recipient = userRepository.findById(recipientId);

        // Validate transaction
        if (sender == null || recipient == null) {
            return false;
        }

        if (sender.getBalance() < amount) {
            return false;
        }

        // At this point the transaction is valid per requirements. Call the incentive API.
        float incentiveAmount = 0.0f;
        try {
            // send the Transaction object; incentive API returns JSON { "amount": <float> }
            Transaction tx = new Transaction(senderId, recipientId, amount);
            Incentive incentive = restTemplate.postForObject(incentiveUrl, tx, Incentive.class);
            if (incentive != null) {
                incentiveAmount = incentive.getAmount();
            }
        } catch (RestClientException e) {
            // If incentive API is unavailable, proceed with 0 incentive but log warning.
            logger.warn("Failed to call incentive API at {}: {}. Proceeding with incentive=0", incentiveUrl, e.getMessage());
            incentiveAmount = 0.0f;
        }

        // Update balances: sender loses only the amount; recipient gains amount + incentive
        sender.setBalance(sender.getBalance() - amount);
        recipient.setBalance(recipient.getBalance() + amount + incentiveAmount);

        // Save updated balances
        userRepository.save(sender);
        userRepository.save(recipient);

        // Record the transaction including incentive
        TransactionRecord transactionRecord = new TransactionRecord(sender, recipient, amount, incentiveAmount);
        transactionRepository.save(transactionRecord);

        return true;
    }
}
