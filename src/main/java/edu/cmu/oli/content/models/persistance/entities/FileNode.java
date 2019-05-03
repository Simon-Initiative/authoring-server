package edu.cmu.oli.content.models.persistance.entities;

import com.google.gson.annotations.Expose;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.File;
import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

//import org.hibernate.envers.Audited;

/**
 * Models a single file within OLI content package.
 *
 * @author Raphael Gachuhi
 */
@Entity
@Table(name = "file_node", indexes = {
        @Index(columnList = "volume_location", name = "volume_location_idx")
})
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@NamedQueries({
        @NamedQuery(name = "FileNode.findAll", query = "SELECT f FROM FileNode f"),
        @NamedQuery(name = "FileNode.findByGuid", query = "SELECT f FROM FileNode f WHERE f.guid = :guid"),
        @NamedQuery(name = "FileNode.findByHref", query = "SELECT f FROM FileNode f WHERE f.pathTo = :href"),
        @NamedQuery(name = "FileNode.findByMimeType", query = "SELECT f FROM FileNode f WHERE f.mimeType = :mimeType"),
        @NamedQuery(name = "FileNode.findByDateCreated", query = "SELECT f FROM FileNode f WHERE f.dateCreated = :dateCreated"),
        @NamedQuery(name = "FileNode.findByDateUpdated", query = "SELECT f FROM FileNode f WHERE f.dateUpdated = :dateUpdated")})
//@Audited
public class FileNode implements Serializable {

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
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "uuid")
    @Access(AccessType.PROPERTY)
    private String guid;

    @Expose()
    @Size(max = 500)
    @Column(name = "pathTo")
    private String pathTo;

    @Expose()
    @Size(max = 500)
    @Column(name = "pathFrom")
    private String pathFrom;

    @Expose()
    @Size(max = 255)
    @Column(name = "file_name")
    private String fileName;

    @Expose()
    @Size(max = 75)
    @Column(name = "mime_type")
    private String mimeType;

    @Expose()
    @Column(name = "file_size")
    private long fileSize;

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
    @Size(max = 255)
    @Column(name = "volume_location")
    private String volumeLocation;

    public FileNode() {
        this.dateCreated = new Date();
        this.dateUpdated = (Date) dateCreated.clone();
    }

    public FileNode(String volumeLocation, String pathFrom, String pathTo, String mimeType) {
        this.dateCreated = new Date();
        this.dateUpdated = (Date) dateCreated.clone();
        this.volumeLocation = volumeLocation;
        this.pathFrom = pathFrom;
        this.pathTo = pathTo;
        this.fileName = pathFrom.substring(pathFrom.lastIndexOf(File.separator) + 1);
        this.mimeType = mimeType;
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public String getPathTo() {
        return pathTo;
    }

    public void setPathTo(String href) {
        this.pathTo = href;
    }

    public String getPathFrom() {
        return pathFrom;
    }

    public void setPathFrom(String pathFrom) {
        if (pathFrom != null) {
            this.fileName = pathFrom.substring(pathFrom.lastIndexOf(File.separator) + 1);
        }
        this.pathFrom = pathFrom;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String name) {
        this.fileName = name;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
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

    public String getVolumeLocation() {
        return volumeLocation;
    }

    public void setVolumeLocation(String volumeLocation) {
        this.volumeLocation = volumeLocation;
    }

    public FileNode cloneVersion(String volumeLocation) {
        FileNode versionClone = new FileNode();
        versionClone.pathTo = this.pathTo;
        versionClone.pathFrom = this.pathFrom;
        versionClone.fileName = this.fileName;
        versionClone.mimeType = this.mimeType;
        versionClone.fileSize = this.fileSize;
        versionClone.volumeLocation = volumeLocation;
        return versionClone;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + Objects.hashCode(this.pathTo);
        hash = 97 * hash + Objects.hashCode(this.mimeType);
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
        final FileNode other = (FileNode) obj;
        return Objects.equals(this.pathTo, other.pathTo) && Objects.equals(this.mimeType, other.mimeType);
    }

    @Override
    public String toString() {
        return "FileNode[ guid=" + guid + " ]";
    }
}
