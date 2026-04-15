package com.example.marketplace.service;

import com.example.marketplace.model.Ad;
import com.example.marketplace.repository.AdRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AdService {
    @Autowired
    private AdRepository adRepository;

    public List<Ad> getActiveAds() {
        return adRepository.findByActiveTrue();
    }
}
