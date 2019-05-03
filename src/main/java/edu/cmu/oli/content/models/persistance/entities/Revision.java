package edu.cmu.oli.content.models.persistance.entities;

import com.google.gson.annotations.Expose;
import edu.cmu.oli.content.AppUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Eli Knebel
 */
@Entity
@Table(name = "revision")
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@NamedQueries({ @NamedQuery(name = "Revision.findAll", query = "SELECT r FROM Revision r"),
        @NamedQuery(name = "Revision.findByGuid", query = "SELECT r FROM Revision r WHERE r.guid = :guid"),

        @NamedQuery(name = "Revision.findByResource", query = "SELECT r FROM Revision r WHERE r.resource = :resource"),
        @NamedQuery(name = "Revision.findByPreviousRevision", query = "SELECT r FROM Revision r WHERE r.previousRevision = :previousRevision"),
        @NamedQuery(name = "Revision.findByAuthor", query = "SELECT r FROM Revision r WHERE r.author = :author"),
        @NamedQuery(name = "Revision.findByRevisionType", query = "SELECT r FROM Revision r WHERE r.revisionType = :revisionType") })
public class Revision implements Serializable {

    private static final long serialVersionUID = 1L;
    @Expose()
    @Version
    private long rev;

    public enum RevisionType {
        USER, SYSTEM, EXTERNAL, REVERT
    }

    @Expose()
    @Id
    @Basic(optional = false)
    @NotNull
    @Size(min = 1, max = 32)
    @Column(name = "guid")
    private String guid;

    @Schema(hidden = true)
    @JoinColumn(name = "resource_guid", referencedColumnName = "guid")
    @ManyToOne(fetch = FetchType.LAZY)
    private Resource resource;

    @Expose()
    @Column(name = "revision_number")
    private long revisionNumber = 0;

    @Expose()
    @Size(min = 32, max = 32)
    @Column(name = "previous_revision")
    private String previousRevision;

    @Expose()
    @Size(min = 32, max = 32)
    @Column(name = "md5")
    private String md5;

    @JoinColumn(name = "revision_blob_guid", referencedColumnName = "guid")
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private RevisionBlob body;

    @Expose()
    @Size(max = 255)
    @Column(name = "author")
    private String author;

    @Expose()
    @Enumerated(EnumType.STRING)
    @Column(name = "revision_type")
    private RevisionType revisionType = RevisionType.USER;

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

    public Revision() {
        guid = UUID.randomUUID().toString().replaceAll("-", "");
        this.dateCreated = new Date();
        this.dateUpdated = (Date) dateCreated.clone();
    }

    public Revision(Resource resource, Revision previousRevision, RevisionBlob body, String author) {
        this(resource, UUID.randomUUID().toString().replaceAll("-", ""), previousRevision, body, author);
    }

    public Revision(Resource resource, String suppliedGuid, Revision previousRevision, RevisionBlob body,
            String author) {
        guid = suppliedGuid;
        this.resource = resource;
        if (previousRevision != null) {
            this.previousRevision = previousRevision.guid;
            if (previousRevision.getResource().equals(resource)) {
                this.revisionNumber = previousRevision.revisionNumber + 1;
            }
        }

        this.body = body;
        this.author = author;
        this.dateCreated = new Date();
        this.dateUpdated = (Date) dateCreated.clone();

        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(body.getBody().getBytes());
            this.md5 = AppUtils.convertByteArrayToHexString(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
        }
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public long getRevisionNumber() {
        return revisionNumber;
    }

    public void setRevisionNumber(long revisionNumber) {
        this.revisionNumber = revisionNumber;
    }

    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    public String getPreviousRevision() {
        return previousRevision;
    }

    public void setPreviousRevision(String previousRevision) {
        this.previousRevision = previousRevision;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public RevisionBlob getBody() {
        return body;
    }

    public void setBody(RevisionBlob body) {
        this.body = body;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public RevisionType getRevisionType() {
        return revisionType;
    }

    public void setRevisionType(RevisionType revisionType) {
        this.revisionType = revisionType;
    }

    public Revision cloneVersion(Resource resource, Revision previousRevision, RevisionBlob body, String author) {
        Revision versionClone = new Revision(resource, previousRevision, body, author);
        versionClone.revisionType = RevisionType.SYSTEM;
        return versionClone;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Revision revision = (Revision) o;
        return revisionNumber == revision.revisionNumber && Objects.equals(guid, revision.guid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(guid, revisionNumber);
    }

    @Override
    public String toString() {
        return "Revision{" + "guid='" + guid + '\'' + "revisionNumber='" + revisionNumber + '\'' + '}';
    }
}
