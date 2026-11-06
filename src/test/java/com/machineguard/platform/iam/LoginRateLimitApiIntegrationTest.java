package com.machineguard.platform.iam;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties="spring.datasource.url=${MACHINEGUARD_TEST_DB_URL:jdbc:h2:mem:loginlimit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE}")
@AutoConfigureMockMvc
class LoginRateLimitApiIntegrationTest {
    @Autowired MockMvc mvc;
    void attempt(String email,String ip,int expected) throws Exception {
        var response=mvc.perform(post("/api/v1/auth/login").with(r->{r.setRemoteAddr(ip);return r;})
            .header("X-Forwarded-For","203.0.113."+System.nanoTime()%200)
            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\",\"password\":\"invalid password\"}"))
            .andReturn().getResponse();
        assertEquals(expected,response.getStatus());
        if(expected==429){int retry=Integer.parseInt(response.getHeader("Retry-After"));assertTrue(retry>=1 && retry<=60);}
    }
    @Test void twentyAttemptsAreLimitedByNormalizedEmailDespiteChangingIp() throws Exception {
        for(int i=0;i<20;i++)attempt(i%2==0?"Limit@f5.invalid":"limit@f5.invalid","127.10.0."+(i+1),i<5?401:429);
    }
    @Test void changingEmailOrForwardedHeaderCannotBypassTheClientIpLimit() throws Exception {
        for(int i=0;i<20;i++)attempt("attempt"+i+"@f5.invalid","127.11.0.1",i<5?401:429);
    }
}
