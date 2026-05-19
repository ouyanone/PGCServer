package com.shiyuan.service;

import java.util.List;

import com.shiyuan.dao.entity.db.Donation;

public interface DonationService {

    /** Returns all donations ordered by date descending. */
    List<Donation> getAllDonations();

    /** Returns a single donation by id, or null if not found. */
    Donation getDonationById(Long id);

    /** Creates and persists a new donation. */
    Donation createDonation(Donation donation);

    /** Updates an existing donation. Returns null if id not found. */
    Donation updateDonation(Long id, Donation donation);

    /** Deletes a donation by id. Returns false if not found. */
    boolean deleteDonation(Long id);
}
