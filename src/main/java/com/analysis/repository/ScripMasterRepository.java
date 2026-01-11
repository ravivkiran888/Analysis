package com.analysis.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.analysis.ScripMaster;

public interface ScripMasterRepository extends MongoRepository<ScripMaster, String> {

	List<ScripMaster> findAll();

}
