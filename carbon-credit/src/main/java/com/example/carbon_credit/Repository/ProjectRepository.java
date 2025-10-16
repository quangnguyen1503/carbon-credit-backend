package com.example.carbon_credit.Repository;

import com.example.carbon_credit.Model.Project;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends MongoRepository<Project, String> {


}

