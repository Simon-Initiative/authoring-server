package edu.cmu.oli.content.security;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;

/**
 * @author Raphael Gachuhi
 */
@ApplicationScoped
public class SecurityManagerProducer {

    private AppSecurityController appSecurityController;

    public void init(@Observes @Initialized(ApplicationScoped.class) Object doesntMatter) {
        doInit();
    }

    private void doInit() {

    }

    @Produces
    @Secure
    public AppSecurityController expose(InjectionPoint injectionPoint) {
        if (this.appSecurityController == null) {
            this.appSecurityController = new KeyCloakSecurityProxy();
        }
        return appSecurityController;
    }
}
