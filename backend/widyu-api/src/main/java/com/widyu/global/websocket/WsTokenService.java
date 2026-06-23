package com.widyu.global.websocket;

import com.widyu.auth.WsConnectionToken;
import com.widyu.auth.repository.WsConnectionTokenRepository;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WsTokenService {

    private final WsConnectionTokenRepository wsConnectionTokenRepository;
    private final MemberUtil memberUtil;

    public String issueToken() {
        Member member = memberUtil.getCurrentMember();
        WsConnectionToken token = WsConnectionToken.create(member.getId());
        wsConnectionTokenRepository.save(token);
        return token.getId();
    }

    public Long validateAndConsume(String tokenId) {
        WsConnectionToken token = wsConnectionTokenRepository.findById(tokenId).orElse(null);
        if (token == null) {
            return null;
        }
        wsConnectionTokenRepository.delete(token);
        return token.getMemberId();
    }
}
