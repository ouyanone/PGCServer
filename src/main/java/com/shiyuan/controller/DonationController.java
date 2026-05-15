package com.shiyuan.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.shiyuan.dao.entity.db.Donation;
import com.shiyuan.service.DonationService;

/**
 * REST API for managing player donations.
 *
 * Base path: /webapi/donations
 *
 * GET    /webapi/donations        — list all donations (newest first)
 * GET    /webapi/donations/{id}   — get donation by id
 * POST   /webapi/donations        — create a new donation
 * PUT    /webapi/donations/{id}   — update an existing donation
 * DELETE /webapi/donations/{id}   — delete a donation
 */
@RestController
public class DonationController extends BaseController {

    @Autowired
    DonationService donationService;

    @GetMapping(path = "/webapi/donations", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Donation>> getAllDonations() {
        List<Donation> dList = donationService.getAllDonations();
        return ResponseEntity.ok(dList);
    }

    @GetMapping(path = "/webapi/donations/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Donation> getDonationById(@PathVariable Long id) {
        Donation donation = donationService.getDonationById(id);
        if (donation == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(donation);
    }

    @PostMapping(path = "/webapi/donations", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Donation> createDonation(@RequestBody Donation donation) {
        Donation created = donationService.createDonation(donation);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping(path = "/webapi/donations/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Donation> updateDonation(@PathVariable Long id, @RequestBody Donation donation) {
        Donation updated = donationService.updateDonation(id, donation);
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping(path = "/webapi/donations/{id}")
    public ResponseEntity<Void> deleteDonation(@PathVariable Long id) {
        boolean deleted = donationService.deleteDonation(id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}
