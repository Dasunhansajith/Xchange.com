package com.example.marketplace.service;

import com.example.marketplace.model.SellerApplication;
import com.example.marketplace.repository.SellerApplicationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SellerService {
    @Autowired
    private SellerApplicationRepository repository;

    public SellerApplication apply(SellerApplication application, String userId) {
        application.setUserId(userId);
        application.setStatus("PENDING");
        application.setAppliedAt(LocalDateTime.now());
        return repository.save(application);
    }

    public List<SellerApplication> getApplications() {
        return repository.findAll();
    }

    public void updateStatus(String id, String status) {
        SellerApplication app = repository.findById(id).orElseThrow();
        app.setStatus(status);
        repository.save(app);
    }
}
