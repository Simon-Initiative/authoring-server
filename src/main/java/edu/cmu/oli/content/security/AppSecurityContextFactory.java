package edu.cmu.oli.content.security;

import org.keycloak.KeycloakSecurityContext;

import javax.ejb.Stateless;
import javax.servlet.http.HttpServletRequest;

/**
 * @author Raphael Gachuhi
 */
@Stateless
public class AppSecurityContextFactory {

    public AppSecurityContext extractSecurityContext(HttpServletRequest httpServletRequest) {
        KeycloakSecurityContext session = (KeycloakSecurityContext) httpServletRequest.getAttribute(KeycloakSecurityContext.class.getName());

        return new AppSecurityContext(session.getTokenString(), session.getToken().getPreferredUsername(),
                session.getToken().getGivenName(), session.getToken().getFamilyName(), session.getToken().getEmail(),
                session.getToken().getRealmAccess().getRoles());
    }
}
