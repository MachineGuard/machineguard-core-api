package com.machineguard.platform.iam;

import com.machineguard.platform.iam.infrastructure.security.WriteSessionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties="spring.datasource.url=${MACHINEGUARD_TEST_DB_URL:jdbc:h2:mem:writecoverage;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE}")
@AutoConfigureMockMvc
class WriteRouteCoverageIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    @Test void everyApplicationWriteIsGuardedOrAnExplicitCredentialBoundary() throws Exception {
        Set<String> publicObserved=new HashSet<>(),gatewayObserved=new HashSet<>();int guarded=0;
        for(var entry:mappings.getHandlerMethods().entrySet()) {
            if(!entry.getValue().getBeanType().getPackageName().startsWith("com.machineguard.platform"))continue;
            var mapping=entry.getKey();assertFalse(mapping.getMethodsCondition().getMethods().isEmpty(),"Declare HTTP methods explicitly");
            for(var method:mapping.getMethodsCondition().getMethods())for(String path:mapping.getPatternValues()) {
                if(Set.of("GET","HEAD","OPTIONS").contains(method.name()))continue;
                if("POST".equals(method.name()) && WriteSessionPolicy.PUBLIC_POSTS.contains(path))publicObserved.add(path);
                else if("POST".equals(method.name()) && WriteSessionPolicy.GATEWAY_POSTS.contains(path))gatewayObserved.add(path);
                else {
                    assertTrue(WriteSessionPolicy.requiresHumanSession(method.name(),path),method+" "+path);
                    String concrete=path.replaceAll("\\{[^}]+}","00000000-0000-0000-0000-000000000001");
                    mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method.name()),concrete))
                        .andExpect(status().isUnauthorized());guarded++;
                }
            }
        }
        assertEquals(WriteSessionPolicy.PUBLIC_POSTS,publicObserved);assertEquals(WriteSessionPolicy.GATEWAY_POSTS,gatewayObserved);
        assertTrue(guarded>=15);assertTrue(WriteSessionPolicy.requiresHumanSession("POST","/new-route"));
    }
}
