package com.shiyuan.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.shiyuan.dao.entity.db.Donation;
import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dao.repository.DonationRepository;
import com.shiyuan.dao.repository.PlayerRepository;
import com.shiyuan.service.DonationService;

@Service
public class DonationServiceImpl implements DonationService {

    @Autowired
    DonationRepository donationRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Override
    public List<Donation> getAllDonations() {
        Iterable<Donation> di = donationRepository.findAllByOrderByDonationDateDesc();
        List<Donation> dList = new ArrayList<>();
        di.forEach(dList::add);
        return dList;
    }

    @Override
    public Donation getDonationById(Long id) {
        return donationRepository.findById(id).orElse(null);
    }

    @Override
    public Donation createDonation(Donation donation) {
        donation.setId(null); // ensure insert, not update
        resolvePlayer(donation);
        return donationRepository.save(donation);
    }

    @Override
    public Donation updateDonation(Long id, Donation donation) {
        Optional<Donation> existing = donationRepository.findById(id);
        if (existing.isEmpty()) {
            return null;
        }
        donation.setId(id);
        resolvePlayer(donation);
        return donationRepository.save(donation);
    }

    private void resolvePlayer(Donation donation) {
        if (donation.getPlayer() != null && donation.getPlayer().getId() != null) {
            Player player = playerRepository.findById(donation.getPlayer().getId()).orElse(null);
            donation.setPlayer(player);
        } else {
            donation.setPlayer(null);
        }
    }

    @Override
    public boolean deleteDonation(Long id) {
        if (!donationRepository.existsById(id)) {
            return false;
        }
        donationRepository.deleteById(id);
        return true;
    }
}
