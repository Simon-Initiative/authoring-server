package edu.cmu.oli.content.models.persistance.entities;

import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;
import edu.cmu.oli.content.models.persistance.JsonWrapper;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.AccessType;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.*;

/**
 * An entity that models an OLI content package.
 *
 * @author Raphael Gachuhi
 */
@Entity
@DynamicUpdate
@Table(name = "content_package", uniqueConstraints = { @UniqueConstraint(columnNames = { "id", "version" }) })
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@NamedQueries({ @NamedQuery(name = "ContentPackage.findAll", query = "SELECT p FROM ContentPackage p"),
        @NamedQuery(name = "ContentPackage.findByGuid", query = "SELECT p FROM ContentPackage p WHERE p.guid = :guid"),
        @NamedQuery(name = "ContentPackage.findById", query = "SELECT p FROM ContentPackage p WHERE p.id = :id"),
        @NamedQuery(name = "ContentPackage.findByVersion", query = "SELECT p FROM ContentPackage p WHERE p.version = :version"),
        @NamedQuery(name = "ContentPackage.findByIdAndVersion", query = "SELECT p FROM ContentPackage p WHERE p.id = :id AND p.version = :version"),
        @NamedQuery(name = "ContentPackage.findByTitle", query = "SELECT p FROM ContentPackage p WHERE p.title = :title"),
        @NamedQuery(name = "ContentPackage.findByMoreLink", query = "SELECT p FROM ContentPackage p WHERE p.moreLink = :moreLink"),
        @NamedQuery(name = "ContentPackage.findByVisible", query = "SELECT p FROM ContentPackage p WHERE p.visible = :visible"),
        @NamedQuery(name = "ContentPackage.findByLanguage", query = "SELECT p FROM ContentPackage p WHERE p.language = :language"),
        @NamedQuery(name = "ContentPackage.findByExpiration", query = "SELECT p FROM ContentPackage p WHERE p.expiration = :expiration"),
        @NamedQuery(name = "ContentPackage.findByDateCreated", query = "SELECT p FROM ContentPackage p WHERE p.dateCreated = :dateCreated"),
        @NamedQuery(name = "ContentPackage.findByDateUpdated", query = "SELECT p FROM ContentPackage p WHERE p.dateUpdated = :dateUpdated"),
        @NamedQuery(name = "ContentPackage.findByParentPackage", query = "SELECT p FROM ContentPackage p WHERE p.parentPackage = :parentPackage"),
        @NamedQuery(name = "ContentPackage.findByPackageFamily", query = "SELECT p FROM ContentPackage p WHERE p.packageFamily = :packageFamily"),
        @NamedQuery(name = "ContentPackage.findByDeploymentStatus", query = "SELECT p FROM ContentPackage p WHERE p.deploymentStatus = :deploymentStatus"),
        @NamedQuery(name = "ContentPackage.findByActiveDataset", query = "SELECT p FROM ContentPackage p WHERE p.activeDataset= :dataset") })
public class ContentPackage implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(ContentPackage.class);

    private static final long serialVersionUID = 1L;
    @Expose()
    @Version
    private long rev;

    public enum DeploymentStatus {
        REQUESTING_PRODUCTION, PRODUCTION, REQUESTING_QA, QA, DEVELOPMENT
    }

    @Expose()
    @Id
    @Basic(optional = false)
    @NotNull
    @Size(min = 1, max = 32)
    @Column(name = "guid")
    private String guid;

    @Expose()
    @NotNull
    @Column(name = "editable")
    private Boolean editable = Boolean.TRUE;

    @Expose()
    @Size(max = 50)
    @Column(name = "id")
    private String id;

    @Expose()
    @Size(max = 25)
    @Column(name = "version")
    private String version;

    @Expose()
    @Size(max = 125)
    @Column(name = "type")
    private String type = "x-oli-package";

    @Expose()
    @Size(max = 255)
    @Column(name = "title")
    private String title;

    @Expose()
    @Lob
    @Column(name = "description")
    private String description;

    @Expose()
    @Size(max = 225)
    @Column(name = "more_link")
    private String moreLink;

    @Expose()
    @NotNull
    @Column(name = "visible")
    private Boolean visible = Boolean.TRUE;

    @Expose()
    @Size(max = 15)
    @Column(name = "language")
    private String language;

    @Expose()
    @Size(max = 225)
    @Column(name = "theme")
    private String theme;

    @Expose()
    @Size(max = 32)
    @Column(name = "parent_package")
    private String parentPackage;

    @Expose()
    @Size(max = 255)
    @Column(name = "package_family")
    private String packageFamily;

    @Expose()
    @Enumerated(EnumType.STRING)
    @Column(name = "deployment_status")
    private DeploymentStatus deploymentStatus = DeploymentStatus.DEVELOPMENT;

    @Expose()
    @JoinColumn(name = "active_dataset_guid", referencedColumnName = "guid")
    @OneToOne(cascade = CascadeType.ALL)
    private Dataset activeDataset;

    @Schema(hidden = true)
    @XmlTransient
    @OneToMany(mappedBy = "contentPackage", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Dataset> datasets = new ArrayList<>();

    @Expose()
    @Column(name = "expiration")
    @Temporal(TemporalType.TIMESTAMP)
    private Date expiration;

    @Schema(hidden = true)
    @Expose()
    @Column(name = "metadata", columnDefinition = "json")
    @Type(type = "json")
    private JsonWrapper metadata;

    @Schema(hidden = true)
    @Expose()
    @Column(name = "options", columnDefinition = "json")
    @Type(type = "json")
    private JsonWrapper options;

    @Schema(hidden = true)
    @Column(name = "errors", columnDefinition = "json")
    @Type(type = "json")
    private JsonWrapper errors;

    @Schema(hidden = true)
    @Expose()
    @Column(name = "package_json", columnDefinition = "json")
    @Type(type = "json")
    private JsonWrapper doc;

    @Expose()
    @Column(name = "date_created", columnDefinition = "DATETIME", updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp
    private Date dateCreated;

    @Schema(hidden = true)
    @Column(name = "date_updated")
    @Temporal(TemporalType.TIMESTAMP)
    @UpdateTimestamp
    private Date dateUpdated;

    @Schema(hidden = true)
    @Size(max = 255)
    @Column(name = "volume_location")
    private String volumeLocation;

    @Schema(hidden = true)
    @Size(max = 255)
    @Column(name = "source_location")
    private String sourceLocation;

    @Schema(hidden = true)
    @Size(max = 255)
    @Column(name = "web_content_volume")
    private String webContentVolume;

    @Expose()
    @JoinColumn(name = "file_guid", referencedColumnName = "guid")
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private FileNode fileNode;

    @Expose()
    @JoinColumn(name = "icon_guid", referencedColumnName = "guid")
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private WebContent icon;

    @Expose()
    @Enumerated(EnumType.STRING)
    @Column(name = "build_status")
    private BuildStatus buildStatus = BuildStatus.READY;

    @Schema(hidden = true)
    @XmlTransient
    @OneToMany(mappedBy = "contentPackage", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Resource> resources = new ArrayList<>();

    @Schema(hidden = true)
    @Column(name = "objectives_index", columnDefinition = "json")
    @Type(type = "json")
    private JsonWrapper objectivesIndex;

    @Schema(hidden = true)
    @Column(name = "skills_index", columnDefinition = "json")
    @Type(type = "json")
    private JsonWrapper skillsIndex;

    @Schema(hidden = true)
    @XmlTransient
    @OneToMany(mappedBy = "contentPackage", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WebContent> webContents = new ArrayList<>();

    @XmlTransient
    @Schema(hidden = true)
    @OneToMany(mappedBy = "contentPackage", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Edge> edges = new HashSet<>();

    @Schema(hidden = true)
    @Expose()
    @Column(name = "misc", columnDefinition = "json")
    @Type(type = "json")
    private JsonWrapper misc = new JsonWrapper(new JsonObject());

    public ContentPackage() {
        this.init();
    }

    public ContentPackage(String pkgId, String version) {
        this.init();
        this.id = pkgId;
        this.version = version;
    }

    private void init() {
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

    public Boolean getEditable() {
        return this.editable;
    }

    public void setEditable(Boolean editingLocked) {
        this.editable = editingLocked;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMoreLink() {
        return moreLink;
    }

    public void setMoreLink(String moreLink) {
        this.moreLink = moreLink;
    }

    public Boolean getVisible() {
        return visible;
    }

    public void setVisible(Boolean visible) {
        this.visible = visible;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public DeploymentStatus getDeploymentStatus() {
        return deploymentStatus;
    }

    public void setDeploymentStatus(DeploymentStatus deploymentStatus) {
        this.deploymentStatus = deploymentStatus;
    }

    public Dataset getActiveDataset() {
        return activeDataset;
    }

    public void setActiveDataset(Dataset dataset) {
        this.activeDataset = dataset;
    }

    public String getPackageFamily() {
        return packageFamily;
    }

    public void setPackageFamily(String packageFamily) {
        this.packageFamily = packageFamily;
    }

    public String getParentPackage() {
        return parentPackage;
    }

    public void setParentPackage(String parentPackage) {
        this.parentPackage = parentPackage;
    }

    public Date getExpiration() {
        return expiration;
    }

    public void setExpiration(Date expiration) {
        this.expiration = expiration;
    }

    public JsonWrapper getMetadata() {
        return metadata;
    }

    public void setMetadata(JsonWrapper metadata) {
        this.metadata = metadata;
    }

    public JsonWrapper getOptions() {
        return options;
    }

    public void setOptions(JsonWrapper options) {
        this.options = options;
    }

    public JsonWrapper getErrors() {
        return errors;
    }

    public void setErrors(JsonWrapper errors) {
        this.errors = errors;
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

    public FileNode getFileNode() {
        return fileNode;
    }

    public void setFileNode(FileNode fileNode) {
        this.fileNode = fileNode;
    }

    public WebContent getIcon() {
        return icon;
    }

    public void setIcon(WebContent icon) {
        this.icon = icon;
    }

    public String getVolumeLocation() {
        return volumeLocation;
    }

    public void setVolumeLocation(String volumeLocation) {
        if (this.volumeLocation == null)
            this.volumeLocation = volumeLocation;
    }

    public String getSourceLocation() {
        return sourceLocation;
    }

    public void setSourceLocation(String sourceLocation) {
        this.sourceLocation = sourceLocation;
    }

    public String getWebContentVolume() {
        return webContentVolume;
    }

    public void setWebContentVolume(String webContentVolume) {
        this.webContentVolume = webContentVolume;
    }

    public JsonWrapper getDoc() {
        return doc;
    }

    public void setDoc(JsonWrapper packageJson) {
        this.doc = packageJson;
    }

    public BuildStatus getBuildStatus() {
        return buildStatus;
    }

    public void setBuildStatus(BuildStatus buildStatus) {
        this.buildStatus = buildStatus;
    }

    public List<Resource> getResources() {
        return resources;
    }

    public JsonWrapper getObjectivesIndex() {
        return objectivesIndex;
    }

    public void setObjectivesIndex(JsonWrapper objectivesIndex) {
        this.objectivesIndex = objectivesIndex;
    }

    public JsonWrapper getSkillsIndex() {
        return skillsIndex;
    }

    public void setSkillsIndex(JsonWrapper skillsIndex) {
        this.skillsIndex = skillsIndex;
    }

    public void addResource(Resource resource) {
        this.resources.add(resource);
    }

    public List<WebContent> getWebContents() {
        return webContents;
    }

    public void addWebContent(WebContent webContent) {
        this.webContents.add(webContent);
    }

    public void removeWebContent(WebContent webContent) {
        this.webContents.remove(webContent);
    }

    public Set<Edge> getEdges() {
        return edges;
    }

    public void addEdge(Edge edge) {
        if (this.edges.contains(edge)) {
            this.edges.forEach(e -> {
                if (e.equals(edge)) {
                    e.setMetadata(edge.getMetadata());
                    return;
                }
            });
            return;
        }
        this.edges.add(edge);
    }

    public void removeEdge(Edge edge) {
        this.edges.remove(edge);
    }

    public JsonWrapper getMisc() {
        return misc;
    }

    public void setMisc(JsonWrapper misc) {
        this.misc = misc;
    }

    public ContentPackage shallowClone() {
        ContentPackage shallowClone = new ContentPackage();
        shallowClone.id = this.id;
        shallowClone.version = this.version;
        shallowClone.title = this.title;
        shallowClone.description = this.description;
        shallowClone.sourceLocation = this.sourceLocation;
        shallowClone.doc = this.doc;
        shallowClone.volumeLocation = this.volumeLocation;
        shallowClone.metadata = this.metadata;
        shallowClone.language = this.language;
        shallowClone.visible = this.visible;
        shallowClone.dateCreated = this.dateCreated;
        shallowClone.dateUpdated = this.dateUpdated;
        shallowClone.webContentVolume = this.webContentVolume;
        shallowClone.type = this.type;
        shallowClone.moreLink = this.moreLink;
        shallowClone.expiration = this.expiration;
        shallowClone.options = this.options;
        shallowClone.theme = this.theme;
        shallowClone.parentPackage = this.parentPackage;
        shallowClone.packageFamily = this.packageFamily;
        shallowClone.deploymentStatus = this.deploymentStatus;
        shallowClone.activeDataset = this.activeDataset;
        shallowClone.misc = this.misc;
        return shallowClone;
    }

    public ContentPackage cloneVersion(String id, String version, String sourceLocation, String volumeLocation,
            String webContentVolume) {
        ContentPackage versionClone = new ContentPackage(id, version);
        versionClone.type = this.type;
        versionClone.title = this.title;
        versionClone.description = this.description;
        versionClone.moreLink = this.moreLink;
        versionClone.language = this.language;
        versionClone.theme = this.theme;
        versionClone.parentPackage = this.guid;
        versionClone.packageFamily = this.packageFamily;
        versionClone.deploymentStatus = DeploymentStatus.DEVELOPMENT;
        versionClone.activeDataset = this.activeDataset;
        versionClone.expiration = this.expiration;
        versionClone.metadata = this.metadata;
        versionClone.options = this.options;
        versionClone.errors = this.errors;
        JsonObject pkgJson = this.doc.getJsonObject().getAsJsonObject();
        pkgJson.addProperty("@id", versionClone.id);
        pkgJson.addProperty("@version", versionClone.version);
        versionClone.doc = new JsonWrapper(pkgJson);
        versionClone.volumeLocation = volumeLocation;
        versionClone.sourceLocation = sourceLocation;
        versionClone.webContentVolume = webContentVolume;
        versionClone.fileNode = this.fileNode != null ? this.fileNode.cloneVersion(volumeLocation) : null;
        versionClone.icon = this.icon != null ? this.icon.cloneVersion(webContentVolume) : null;
        versionClone.buildStatus = BuildStatus.PROCESSING;
        versionClone.misc = this.misc;

        this.resources.forEach(resource -> {
            if (resource.getResourceState() != ResourceState.DELETED) {
                Resource resourceClone = resource.cloneVersion(volumeLocation);
                resourceClone.setContentPackage(versionClone);
                versionClone.addResource(resourceClone);
            }
        });

        versionClone.objectivesIndex = this.objectivesIndex;
        versionClone.skillsIndex = this.skillsIndex;

        this.webContents.forEach(webContent -> {
            WebContent webcontentClone = webContent.cloneVersion(webContentVolume);
            webcontentClone.setContentPackage(versionClone);
            versionClone.addWebContent(webcontentClone);
        });

        this.edges.forEach(edge -> {
            Edge edgeClone = edge.cloneVersion(versionClone.id, versionClone.version);
            edgeClone.setContentPackage(versionClone);
            versionClone.addEdge(edgeClone);
        });

        return versionClone;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 73 * hash + Objects.hashCode(this.id);
        hash = 73 * hash + Objects.hashCode(this.version);
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
        final ContentPackage other = (ContentPackage) obj;
        return Objects.equals(this.id, other.id) && Objects.equals(this.version, other.version);
    }

    @Override
    public String toString() {
        return "ContentPackage{" + "guid=" + guid + ", id=" + id + ", version=" + version + ", title=" + title + '}';
    }
}
