package com.shiyuan.dao.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.shiyuan.dao.entity.db.Donation;

@Repository
public interface DonationRepository extends CrudRepository<Donation, Long>{
	

	Iterable<Donation> findAllByOrderByDonationDateDesc();

}
