package com.machineguard.platform.iam.application;

import com.machineguard.platform.iam.application.integration.PilotRequestSubmitted;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PilotRequestEventHandler {
    private final IamService iam;

    public PilotRequestEventHandler(IamService iam) { this.iam = iam; }

    @EventListener
    public void on(PilotRequestSubmitted event) { iam.handlePilotRequest(event); }
}
