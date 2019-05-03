package edu.cmu.oli.content.models.persistance.entities;

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
import java.io.Serializable;
import java.util.Date;

/**
 * An entity representing a directional edge between two content resources.
 *
 * @author Raphael Gachuhi
 */
@Entity
@Table(name = "edge")
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@NamedQueries({
        @NamedQuery(name = "Edge.findAll", query = "SELECT r FROM Edge r")
        ,
        @NamedQuery(name = "Edge.findByGuid", query = "SELECT r FROM Edge r WHERE r.guid = :guid")
        ,
        @NamedQuery(name = "Edge.findByRelationship", query = "SELECT r FROM Edge r WHERE r.relationship = :relationship")
        ,
        @NamedQuery(name = "Edge.findByPurpose", query = "SELECT r FROM Edge r WHERE r.purpose = :purpose")
        ,
        @NamedQuery(name = "Edge.findByDateCreated", query = "SELECT r FROM Edge r WHERE r.dateCreated = :dateCreated")
        ,
        @NamedQuery(name = "Edge.findByDateUpdated", query = "SELECT r FROM Edge r WHERE r.dateUpdated = :dateUpdated")})
public class Edge implements Serializable {

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
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "relationship")
    private EdgeType relationship;

    @Expose()
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose")
    private PurposeType purpose;

    @Expose()
    @NotNull
    @Size(max = 500)
    @Column(name = "sourceId")
    private String sourceId;

    @Expose()
    @Transient
    private String sourceGuid;

    @Expose()
    @Size(max = 100)
    @Column(name = "source_type")
    private String sourceType;

    @Expose()
    @NotNull
    @Size(max = 500)
    @Column(name = "destinationId")
    private String destinationId;

    @Expose()
    @Transient
    private String destinationGuid;

    @Expose()
    @Size(max = 100)
    @Column(name = "destination_type")
    private String destinationType;

    @Expose()
    @NotNull
    @Size(max = 100)
    @Column(name = "reference_type")
    private String referenceType = "unknown";

    @Expose()
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private EdgeStatus status = EdgeStatus.NOT_VALIDATED;

    @Schema(hidden = true)
    @Expose()
    @Column(name = "metadata", columnDefinition = "json")
    @Type(type = "json")
    private JsonWrapper metadata;

    @Schema(hidden = true)
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
    @JoinColumn(name = "content_package_guid", referencedColumnName = "guid")
    @ManyToOne(fetch = FetchType.LAZY)
    private ContentPackage contentPackage;

    public Edge() {
        this.dateCreated = new Date();
        this.dateUpdated = (Date) dateCreated.clone();
    }

    public Edge(EdgeType relationship, String sourceId, String destinationId, String sourceType,
                String destinationType, String referenceType) {
        this.relationship = relationship;
        this.dateCreated = new Date();
        this.dateUpdated = (Date) dateCreated.clone();
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.sourceType = sourceType;
        this.destinationType = destinationType;
        this.referenceType = referenceType;
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public void setDestinationId(String destinationId) {
        this.destinationId = destinationId;
    }

    public PurposeType getPurpose() {
        return purpose;
    }

    public void setPurpose(PurposeType purpose) {
        this.purpose = purpose;
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

    public EdgeType getRelationship() {
        return relationship;
    }

    public void setRelationship(EdgeType relationship) {
        this.relationship = relationship;
    }

    public ContentPackage getContentPackage() {
        return contentPackage;
    }

    public void setContentPackage(ContentPackage contentPackage) {
        this.contentPackage = contentPackage;
    }

    public EdgeStatus getStatus() {
        return status;
    }

    public void setStatus(EdgeStatus status) {
        this.status = status;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getDestinationType() {
        return destinationType;
    }

    public void setDestinationType(String destinationType) {
        this.destinationType = destinationType;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public JsonWrapper getMetadata() {
        return metadata;
    }

    public void setMetadata(JsonWrapper metadata) {
        this.metadata = metadata;
    }

    public String getSourceGuid() {
        return sourceGuid;
    }

    public void setSourceGuid(String sourceGuid) {
        this.sourceGuid = sourceGuid;
    }

    public String getDestinationGuid() {
        return destinationGuid;
    }

    public void setDestinationGuid(String destinationGuid) {
        this.destinationGuid = destinationGuid;
    }

    public Edge cloneVersion(String packageId, String version) {
        Edge versionClone = new Edge();
        versionClone.relationship = this.relationship;
        versionClone.purpose = this.purpose;
        versionClone.sourceId = packageId + ":" + version + ":" + this.sourceId.split(":")[2];
        versionClone.sourceType = this.sourceType;
        versionClone.destinationId = packageId + ":" + version + ":" + this.destinationId.split(":")[2];
        versionClone.destinationType = this.destinationType;
        versionClone.referenceType = this.referenceType;
        versionClone.status = EdgeStatus.NOT_VALIDATED;
        versionClone.metadata = this.metadata;
        return versionClone;

    }

    @Override
    public String toString() {
        return "Edge{" +
                "relationship=" + relationship +
                ", purpose=" + purpose +
                ", dateCreated=" + dateCreated +
                ", dateUpdated=" + dateUpdated +
                ", sourceId='" + sourceId + '\'' +
                ", sourceType='" + sourceType + '\'' +
                ", destinationId='" + destinationId + '\'' +
                ", destinationType='" + destinationType + '\'' +
                ", status=" + status +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Edge edge = (Edge) o;

        if (!sourceId.equals(edge.sourceId)) return false;
        if (!destinationId.equals(edge.destinationId)) return false;

        return metadata.equals(edge.getMetadata());
    }

    @Override
    public int hashCode() {
        int result = sourceId.hashCode();
        result = 31 * result + destinationId.hashCode();
        return result;
    }
}
