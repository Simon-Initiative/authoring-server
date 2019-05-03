/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018. Carnegie Mellon University
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package edu.cmu.oli.content.controllers;

import com.google.gson.*;
import edu.cmu.oli.content.AppUtils;
import edu.cmu.oli.content.ResourceException;
import edu.cmu.oli.content.configuration.ConfigurationCache;
import edu.cmu.oli.content.configuration.Configurations;
import edu.cmu.oli.content.logging.Logging;
import edu.cmu.oli.content.models.ServerName;
import edu.cmu.oli.content.models.persistance.JsonWrapper;
import edu.cmu.oli.content.models.persistance.entities.ContentPackage;
import edu.cmu.oli.content.models.persistance.entities.Edge;
import edu.cmu.oli.content.models.persistance.entities.Resource;
import edu.cmu.oli.content.security.AppSecurityContext;
import edu.cmu.oli.content.security.AppSecurityController;
import edu.cmu.oli.content.security.Secure;
import edu.cmu.oli.content.security.UserInfo;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.nio.file.Paths;
import java.util.*;

@Stateless
public class DeployControllerImpl implements DeployController {

    @Inject
    @Logging
    Logger log;

    @PersistenceContext
    EntityManager em;

    @Inject
    @Secure
    AppSecurityController securityManager;

    @Inject
    SVNSyncController svnSyncController;

    @Inject
    @ConfigurationCache
    Instance<Configurations> configuration;

    @Inject
    LDModelController ldModelController;

    @Override
    public JsonElement deployPackage(AppSecurityContext session, String resourceId,
                                     ServerName previewServer, boolean redeploy) {

        String previewLaunchUrl = "";
        JsonArray servers = configuration.get().getPreviewServers();
        for (JsonElement s : servers) {
            JsonObject server = s.getAsJsonObject();
            if (server.has("name") && server.get("name").getAsString().equals(previewServer.toString())) {
                previewLaunchUrl = server.get("previewLaunchUrl").getAsString();
            }
        }
        if (previewLaunchUrl == "") {
            String message = "previewResource server 'launch URL' not properly configured in the content service";
            throw new ResourceException(Response.Status.NOT_FOUND, resourceId, message);
        }

        WebTarget target = ClientBuilder.newClient().target(previewLaunchUrl);

        Optional<Response> response = getPreviewResponse(session, resourceId, target, redeploy);
        if (!response.isPresent()) {
            String message = "server error getting previewResource";
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, resourceId, message);
        }

        String s = AppUtils.toString(response.get().readEntity(javax.json.JsonObject.class));
        JsonParser jsonParser = new JsonParser();
        JsonObject previewResponse = jsonParser.parse(s).getAsJsonObject();

        log.debug("Json returned from previewResource server " + s);

        if (response.get().getStatus() != Response.Status.OK.getStatusCode()) {
            throw new ResourceException(Response.Status.fromStatusCode(response.get().getStatus()), null, s);
        }
        return previewResponse;
    }

    private Optional<Response> getPreviewResponse(AppSecurityContext session, String resourceId, WebTarget target,
                                                  boolean redeploy) {
        TypedQuery<Resource> q = em.createNamedQuery("Resource.findByGuid", Resource.class);
        q.setParameter("guid", resourceId);
        List<Resource> resultList = q.getResultList();

        if (resultList.isEmpty()) {
            String message = "ContentResource not found " + resourceId;
            throw new ResourceException(Response.Status.NOT_FOUND, resourceId, message);
        }

        Resource resource = resultList.get(0);

        ContentPackage pkg = resource.getContentPackage();
        String orgIdToUse;
        String orgVersion;
        if (resource.getType().equalsIgnoreCase("x-oli-organization")) {
            orgIdToUse = resource.getId();
            orgVersion = "1.0";
            JsonWrapper metadata = resource.getMetadata();
            if (metadata != null) {
                JsonElement version = metadata.getJsonObject().getAsJsonObject().get("version");
                if (version != null) {
                    orgVersion = version.getAsString();
                }
            }
        } else {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Edge> criteria = cb.createQuery(Edge.class);
            Root<Edge> edgeRoot = criteria.from(Edge.class);
            criteria.select(edgeRoot)
                    .where(cb.and(cb.equal(edgeRoot.get("sourceType"), "x-oli-organization"),
                            cb.equal(edgeRoot.get("destinationId"),
                                    pkg.getId() + ":" + pkg.getVersion() + ":" + resource.getId()),
                            cb.equal(edgeRoot.get("contentPackage").get("guid"), pkg.getGuid())));

            List<Edge> edges = em.createQuery(criteria).getResultList();
            if (edges.isEmpty()) {
                String message = "missing";
                log.info("Resource not referenced by any organization " + resource.getId());
                throw new ResourceException(Response.Status.NOT_FOUND, resourceId, message);
            }

            Edge edgeForOrg = null;
            for (Edge edge : edges) {
                edgeForOrg = edge;
                log.info("Edge for previewResource " + edge);
                if (edge.getSourceId().endsWith("default")) {
                    break;
                }
            }

            orgIdToUse = edgeForOrg.getSourceId();
            orgIdToUse = orgIdToUse.substring(orgIdToUse.lastIndexOf(":") + 1);
            orgVersion = ((JsonObject) edgeForOrg.getMetadata().getJsonObject()).get("version").getAsString();
        }
        log.debug("OrgId " + orgIdToUse + " Version " + orgVersion);

        Optional<String> svnRepositoryUrl = svnSyncController
                .svnRepositoryUrl(Paths.get(pkg.getSourceLocation()).toFile());
        if (!svnRepositoryUrl.isPresent()) {
            String message = "Error previewing resource: Resource not part of a valid SVN repository. ";
            log.error(message);
            throw new ResourceException(Response.Status.NOT_FOUND, resourceId, message);
        }

        JsonObject previewUrlInfo = new JsonObject();
        previewUrlInfo.addProperty("svnUrl", svnRepositoryUrl.get());
        previewUrlInfo.addProperty("packageId", pkg.getId());
        previewUrlInfo.addProperty("packageVersion", pkg.getVersion());
        previewUrlInfo.addProperty("resourceId", resource.getId());
        previewUrlInfo.addProperty("orgId", orgIdToUse);
        previewUrlInfo.addProperty("orgVersion", orgVersion);
        previewUrlInfo.addProperty("redeploy", redeploy);

        JsonObject userInfo = new JsonObject();
        userInfo.addProperty("firstName", session.getFirstName());
        userInfo.addProperty("lastName", session.getLastName());
        userInfo.addProperty("email", session.getEmail());
        userInfo.addProperty("userName", session.getPreferredUsername());
        previewUrlInfo.add("userInfo", userInfo);

        return Optional
                .of(target.request(MediaType.APPLICATION_JSON).post(Entity.json(AppUtils.gsonBuilder().create().toJson(previewUrlInfo))));

    }

    @Override
    public void sendRequestDeployEmail(AppSecurityContext session, ContentPackage pkg, String action,
                                       String server, String svnLocation) {

        String serverUrl = System.getenv().get("SERVER_URL");
        String serverName = serverUrl.substring(serverUrl.indexOf("/") + 2);

        JsonArray techTeamEmailsJ = new JsonArray();
        Set<String> techTeamEmails = this.techTeamEmails(pkg.getId());
        techTeamEmails.forEach(m->techTeamEmailsJ.add(m));

        JsonArray authorEmailsj = new JsonArray();
        Set<String> authorEmails = this.authorEmails(pkg.getGuid());
        authorEmails.forEach(email -> {
            if(!techTeamEmails.contains((email))){
                authorEmailsj.add(email);
            }
        });

        log.info("Sending deploy/update request email for course " + pkg.getTitle());

        String subject = (serverName.contains("dev.local") ? "TESTING: PLEASE IGNORE THIS - " : "") + "OLI course " +
                pkg.getTitle() + " is ready for " + action + " to " + server;

        StringBuilder sb = new StringBuilder();
        sb.append("The user ").append(session.getFirstName()).append(" ").append(session.getLastName()).append(" (")
                .append(session.getEmail()).append(") has requested a ").append(action).append(" of the course content package (id=")
                .append(pkg.getId()).append(" and version=").append(pkg.getVersion())
                .append(") to ").append(server).append(".\n\n");

        sb.append("Authors: ");

        authorEmailsj.forEach(author ->{
            sb.append(author.getAsString()).append(";");
        });
        sb.deleteCharAt(sb.length()-1);
        sb.append("\n\n");

        sb.append(packageDetails(pkg));

        // Send email to authors only
        doSendEmail(pkg, authorEmailsj, subject, sb.toString(), Optional.empty());

        if (svnLocation != null) {
            sb.append("SVN Location: " + svnLocation + "\n\n");
        }

        sb.append("please update the corresponding package deployment 'status' on ").append(serverName).append(" using the admin app");

        Map<String, String> model = ldModelController.extractTabSeparatedModel(pkg);

        JsonArray attachments = new JsonArray();
        model.forEach((k,v)->{
            JsonObject att = new JsonObject();
            att.addProperty("filename", k);
            att.addProperty("value", v);
            attachments.add(att);
        });

        // Send email to tech team
        doSendEmail(pkg,techTeamEmailsJ, subject, sb.toString(), Optional.of(attachments));
    }

    @Override
    public void sendStateTransitionEmail(ContentPackage pkg, String subject, String body){

        Set<String> techTeamEmails = this.techTeamEmails(pkg.getId());
        Set<String> authorEmails = this.authorEmails(pkg.getGuid());

        log.info("Sending deploy/update status email for package " + pkg.getTitle());

        StringBuilder sb = new StringBuilder();
        sb.append(body).append(".\n\n");

        sb.append("Authors: ");

        authorEmails.forEach(author ->{
            sb.append(author).append(";");
        });
        sb.deleteCharAt(sb.length()-1);
        sb.append("\n\n");

        authorEmails.addAll(techTeamEmails);
        JsonArray authorEmailsj = new JsonArray();
        authorEmails.forEach(email -> authorEmailsj.add(email));

        sb.append(packageDetails(pkg));

        // Send email to authors only
        doSendEmail(pkg, authorEmailsj, subject, sb.toString(), Optional.empty());
    }

    private Set<String> techTeamEmails(String pkgId){
        Set<String> deployRequestEmails = configuration.get().getDeployRequestEmails();
        Set<String> teachTeamEmails = new HashSet<>();
        if (deployRequestEmails.isEmpty()) {
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, pkgId, "tech team email list cannot be empty");
        } else {
            deployRequestEmails.forEach(e -> teachTeamEmails.add(e));
        }
        return teachTeamEmails;
    }

    private Set<String> authorEmails(String pkgGuid){
        List<UserInfo> allUsers = securityManager.getAllUsers();
//&& !teachTeamEmails.contains(new JsonPrimitive(userInfo.getEmail()))
        Set<String> authorEmails = new HashSet<>();
        allUsers.forEach(userInfo -> {
            final Map<String, List<String>> attributes = userInfo.getAttributes();
            if(attributes != null && attributes.containsKey(pkgGuid)){
                authorEmails.add(userInfo.getEmail());
            }
        });
        return authorEmails;
    }

    private String packageDetails(ContentPackage contentPackage) {
        StringBuilder sb = new StringBuilder();
        sb.append("Here are the package details:\n");
        sb.append("Package Id: " + contentPackage.getId() + "\n");
        sb.append("Version: " + contentPackage.getVersion() + "\n");
        sb.append("Package Title: " + contentPackage.getTitle() + "\n");
        sb.append("Description: " + contentPackage.getDescription() + "\n");
        sb.append("Last Updated: " + contentPackage.getDateUpdated() + "\n\n");
        return sb.toString();
    }

    private void doSendEmail(ContentPackage contentPackage, JsonArray toEmails, String subject,
                             String emailBody, Optional<JsonArray> attachments) {
        JsonObject payload = new JsonObject();
        payload.addProperty("token", configuration.get().getEmailServerToken());
        payload.addProperty("fromEmail", configuration.get().getEmailFrom());
        payload.add("toEmails", toEmails);
        payload.addProperty("subject", subject);
        payload.addProperty("body", emailBody);
        if(attachments.isPresent()) {
            payload.add("attachments", attachments.get());
        }

        WebTarget target = ClientBuilder.newClient().target(configuration.get().getEmailServer());
        Response response = target.request(MediaType.APPLICATION_JSON).post(Entity.json(AppUtils.gsonBuilder().create().toJson(payload)));
        if (response.getStatus() != Response.Status.OK.getStatusCode()) {
            final String message = "Email message sending failed ";
            log.error(message);
            throw new ResourceException(Response.Status.INTERNAL_SERVER_ERROR, contentPackage.getId(), message);
        }
    }
}
