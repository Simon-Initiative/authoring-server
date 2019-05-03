package edu.cmu.oli.content.models.persistance.entities;

import com.google.gson.annotations.Expose;
import edu.cmu.oli.content.AppUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UpdateTimestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.UUID;

/**
 * @author Raphael Gachuhi
 */
@Entity
@Table(name = "web_content")
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@NamedQueries({
        @NamedQuery(name = "WebContent.findAll", query = "SELECT w FROM WebContent w"),
        @NamedQuery(name = "WebContent.findByGuid", query = "SELECT w FROM WebContent w WHERE w.guid = :guid"),
        @NamedQuery(name = "WebContent.findByFileNode", query = "SELECT w FROM WebContent w WHERE w.fileNode = :fileNode"),
        @NamedQuery(name = "WebContent.findByDateCreated", query = "SELECT w FROM WebContent w WHERE w.dateCreated = :dateCreated")})

public class WebContent implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(ContentPackage.class);

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
    @Size(max = 125)
    @Column(name = "type")
    private String type = "x-oli-webcontent";

    @Expose()
    @Size(max = 32)
    @Column(name = "md5")
    private String md5;

    @Expose()
    @Enumerated(EnumType.STRING)
    @Column(name = "web_content_state")
    private ResourceState resourceState = ResourceState.ACTIVE;

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

    @Schema(hidden = true)
    @JoinColumn(name = "content_package_guid", referencedColumnName = "guid")
    @ManyToOne(fetch = FetchType.LAZY)
    private ContentPackage contentPackage;

    @Expose()
    @JoinColumn(name = "file_guid", referencedColumnName = "guid")
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    private FileNode fileNode;

    public WebContent() {
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
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
        String filepath = fileNode.getVolumeLocation() + File.separator + fileNode.getPathTo();
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(filepath));
            MessageDigest digest = MessageDigest.getInstance("MD5");
            this.md5 = AppUtils.convertByteArrayToHexString(digest.digest(bytes));
        } catch (IOException | NoSuchAlgorithmException e) {
        }
    }

    public ContentPackage getContentPackage() {
        return contentPackage;
    }

    public void setContentPackage(ContentPackage contentPackage) {
        this.contentPackage = contentPackage;
    }

    public ResourceState getResourceState() {
        return resourceState;
    }

    public void setResourceState(ResourceState resourceState) {
        this.resourceState = resourceState;
    }

    public WebContent cloneVersion(String webContentVolume) {
        WebContent versionClone = new WebContent();
        versionClone.type = this.type;
        versionClone.md5 = this.md5;
        versionClone.resourceState = this.resourceState;
        versionClone.fileNode = this.fileNode.cloneVersion(webContentVolume);

        return versionClone;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (guid != null ? guid.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof WebContent)) {
            return false;
        }
        WebContent other = (WebContent) object;
        return !((this.guid == null && other.guid != null) || (this.guid != null && !this.guid.equals(other.guid)));
    }

    @Override
    public String toString() {
        return "WebContent{" + "guid=" + guid + '}';
    }
}
