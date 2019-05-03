package edu.cmu.oli.content.models.persistance.entities;

import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;
import edu.cmu.oli.content.models.persistance.JsonWrapper;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.*;

//import org.hibernate.envers.Audited;
//import org.hibernate.envers.NotAudited;

/**
 * @author Raphael Gachuhi
 */
@Entity
@Table(name = "resource", indexes = {
        @Index(columnList = "id", name = "resource_id_idx")
})
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@NamedQueries({
        @NamedQuery(name = "Resource.findAll", query = "SELECT r FROM Resource r"),
        @NamedQuery(name = "Resource.findByGuid", query = "SELECT r FROM Resource r WHERE r.guid = :guid"),
        @NamedQuery(name = "Resource.findById", query = "SELECT r FROM Resource r WHERE r.id = :id"),
        @NamedQuery(name = "Resource.findByType", query = "SELECT r FROM Resource r WHERE r.type = :type"),
        @NamedQuery(name = "Resource.findByTitle", query = "SELECT r FROM Resource r WHERE r.title = :title"),
        @NamedQuery(name = "Resource.findByShortTitle", query = "SELECT r FROM Resource r WHERE r.shortTitle = :shortTitle"),
        @NamedQuery(name = "Resource.findByDateCreated", query = "SELECT r FROM Resource r WHERE r.dateCreated = :dateCreated"),
        @NamedQuery(name = "Resource.findByDateUpdated", query = "SELECT r FROM Resource r WHERE r.dateUpdated = :dateUpdated"),
        @NamedQuery(name = "Resource.findByLastRevision", query = "SELECT r FROM Resource r WHERE r.lastRevision = :lastRevision"),
        @NamedQuery(name = "Resource.findByLastSession", query = "SELECT r FROM Resource r WHERE r.lastSession = :lastSession")})
//@Audited
public class Resource implements Serializable {

    private static final long serialVersionUID = 1L;
    @Expose()
    @Version
    private long rev;

    @Expose()
    @Id
    @Basic(optional = false)
    @NotNull
    @Size(min = 1, max = 32)
    @Column(name = "guid")
    private String guid;

    @Expose()
    @Size(max = 255)
    @Column(name = "id")
    private String id;

    @Expose()
    @Size(max = 50)
    @Column(name = "type")
    private String type;

    @Expose()
    @Size(max = 255)
    @Column(name = "title")
    private String title;

    @Expose()
    @Size(max = 30)
    @Column(name = "short_title")
    private String shortTitle;

    @Expose()
    @JoinColumn(name = "last_revision", referencedColumnName = "guid")
    @OneToOne(cascade = CascadeType.ALL)
    private Revision lastRevision;

    @Expose()
    @Size(max = 32)
    @Column(name = "last_session")
    private String lastSession;

    @Expose()
    @Column(name = "metadata", columnDefinition = "json")
    @Type(type = "json")
    private JsonWrapper metadata;

    @Expose()
    @Column(name = "date_created", columnDefinition = "DATETIME", updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp
    private Date dateCreated;

    @Expose()
    @Column(name = "date_updated")
    @Temporal(TemporalType.TIMESTAMP)
    @UpdateTimestamp
    private Date dateUpdated;

    @Expose()
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_state")
    private ResourceState resourceState = ResourceState.ACTIVE;

    //@NotAudited
    @JoinColumn(name = "content_package_guid", referencedColumnName = "guid")
    @ManyToOne(fetch = FetchType.LAZY)
    private ContentPackage contentPackage;

    //@NotAudited
    @Expose()
    @JoinColumn(name = "file_guid", referencedColumnName = "guid")
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    private FileNode fileNode;

    @Enumerated(EnumType.STRING)
    @Column(name = "build_status")
    private BuildStatus buildStatus = BuildStatus.PROCESSING;

    //@Expose()
    @Column(name = "errors", columnDefinition = "json")
    @Type(type = "json")
    private JsonWrapper errors;

    @Schema(hidden = true)
    @XmlTransient
    @OneToMany(mappedBy = "resource", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Revision> revisions = new ArrayList<>();

    @XmlTransient
    @Transient
    private String transientRev;

    public Resource() {
        this.guid = UUID.randomUUID().toString().replaceAll("-", "");
        this.dateCreated = new Date();
        this.dateUpdated = (Date) dateCreated.clone();
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getShortTitle() {
        return shortTitle;
    }

    public void setShortTitle(String shortTitle) {
        this.shortTitle = shortTitle;
    }

    public Revision getLastRevision() {
        return lastRevision;
    }

    public void setLastRevision(Revision lastRevision) {
        this.lastRevision = lastRevision;
    }

    public String getLastSession() {
        return lastSession;
    }

    public void setLastSession(String lastSession) {
        this.lastSession = lastSession;
    }

    public JsonWrapper getMetadata() {
        return metadata;
    }

    public void setMetadata(JsonWrapper metadata) {
        this.metadata = metadata;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Date getDateUpdated() {
        return dateUpdated;
    }

    public void setDateUpdated(Date dateUpdated) {
        this.dateUpdated = dateUpdated;
    }

    public ResourceState getResourceState() {
        return resourceState;
    }

    public void setResourceState(ResourceState resourceState) {
        this.resourceState = resourceState;
    }

    public ContentPackage getContentPackage() {
        return contentPackage;
    }

    public void setContentPackage(ContentPackage contentPackage) {
        this.contentPackage = contentPackage;
    }

    public FileNode getFileNode() {
        return fileNode;
    }

    public void setFileNode(FileNode fileNode) {
        this.fileNode = fileNode;
    }


    public BuildStatus getBuildStatus() {
        return buildStatus;
    }

    public void setBuildStatus(BuildStatus buildStatus) {
        this.buildStatus = buildStatus;
    }


    public JsonWrapper getErrors() {
        return errors;
    }

    public void setErrors(JsonWrapper errors) {
        this.errors = errors;
    }

    public List<Revision> getRevisions() {
        return revisions;
    }

    public void setRevisions(List<Revision> revisions) {
        this.revisions = revisions;
    }

    public void addRevision(Revision revision) {
        this.revisions.add(revision);
    }

    public String getTransientRev() {
        return transientRev;
    }

    public Resource cloneVersion(String volumeLocation) {
        Resource versionClone = new Resource();
        versionClone.id = this.id;
        versionClone.type = this.type;
        versionClone.title = this.title;
        versionClone.shortTitle = this.shortTitle;
        if (this.type.equals("x-oli-organization")) {
            JsonObject metadata = this.metadata == null ? new JsonObject() : this.metadata.getJsonObject().getAsJsonObject();
            metadata.addProperty("version", "1.0");
            versionClone.metadata = new JsonWrapper(metadata);
        } else {
            versionClone.metadata = this.metadata;
        }
        versionClone.resourceState = this.resourceState;
        versionClone.fileNode = fileNode != null ? fileNode.cloneVersion(volumeLocation) : null;
        versionClone.buildStatus = this.buildStatus;
        versionClone.errors = this.errors;

        if (this.lastRevision == null) {
            throw new RuntimeException("Package does not yet support revisions: packageid=" + contentPackage.getId() +
                    " version=" + contentPackage.getVersion());
        }

        versionClone.transientRev = this.lastRevision.getGuid();

        return versionClone;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 53 * hash + Objects.hashCode(this.guid);
        hash = 53 * hash + Objects.hashCode(this.id);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Resource other = (Resource) obj;
        return Objects.equals(this.guid, other.guid) && Objects.equals(this.id, other.id);
    }

    @Override
    public String toString() {
        return "Resource{" +
                "guid='" + guid + '\'' +
                ", id='" + id + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}
