package com.machineguard.platform.support;

import com.machineguard.platform.iam.domain.model.Role;
import com.machineguard.platform.iam.infrastructure.security.JwtTokenService;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Persist coherent identities/sessions for HTTP tests that do not exercise the login workflow. */
public final class SessionFixtures {
    private SessionFixtures() { }
    public static String token(JdbcTemplate jdbc,JwtTokenService tokens,UUID user,UUID org,Role role,UUID ignoredFixtureSid) {
        var at=OffsetDateTime.parse("2026-01-01T00:00:00Z");var end=OffsetDateTime.parse("2030-01-01T00:00:00Z");
        if(jdbc.queryForObject("SELECT count(*) FROM organizations WHERE id=?",Integer.class,org)==0)
            jdbc.update("INSERT INTO organizations(id,name,subscription_plan,status,created_at,updated_at) VALUES (?,'Fixture','FREE','ACTIVE',?,?)",org,at,at);
        var existing=jdbc.queryForList("SELECT organization_id,role FROM users WHERE id=?",user);
        if(!existing.isEmpty() && (!existing.getFirst().get("organization_id").toString().equals(org.toString())
                || !existing.getFirst().get("role").equals(role.name())))
            user=UUID.nameUUIDFromBytes((user+":"+org+":"+role).getBytes(StandardCharsets.UTF_8));
        if(jdbc.queryForObject("SELECT count(*) FROM users WHERE id=?",Integer.class,user)==0)
            jdbc.update("INSERT INTO users(id,organization_id,email,password_hash,full_name,role,status,created_at,updated_at) VALUES (?,?,?,'not-a-login-password','Synthetic fixture',?,'ACTIVE',?,?)",
                user,org,user+"@sessions.invalid",role.name(),at,at);
        UUID sid=UUID.randomUUID();
        jdbc.update("INSERT INTO authentication_sessions(id,user_id,refresh_token_hash,issued_at,expires_at) VALUES (?,?,?,?,?)",
                sid,user,JwtTokenService.tokenHash(sid.toString()),at,end);
        return tokens.issueAccessToken(user,org,role,sid).value();
    }
}
