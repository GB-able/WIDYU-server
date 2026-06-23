package com.widyu.auth.repository;

import com.widyu.auth.WsConnectionToken;
import org.springframework.data.repository.CrudRepository;

public interface WsConnectionTokenRepository extends CrudRepository<WsConnectionToken, String> {
}
