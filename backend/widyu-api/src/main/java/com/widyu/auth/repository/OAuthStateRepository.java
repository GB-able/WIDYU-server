package com.widyu.auth.repository;

import com.widyu.auth.OAuthState;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OAuthStateRepository extends CrudRepository<OAuthState, String> {
}
