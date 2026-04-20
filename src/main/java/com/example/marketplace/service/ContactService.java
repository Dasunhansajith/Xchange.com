package com.example.marketplace.service;

import com.example.marketplace.model.ContactMessage;
import com.example.marketplace.repository.ContactRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class ContactService {
    @Autowired
    private ContactRepository repository;

    public ContactMessage saveMessage(ContactMessage message) {
        message.setCreatedAt(LocalDateTime.now());
        return repository.save(message);
    }
}
