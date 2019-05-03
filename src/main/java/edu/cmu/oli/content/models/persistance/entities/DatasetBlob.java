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

package edu.cmu.oli.content.models.persistance.entities;

import com.google.gson.JsonElement;
import com.google.gson.annotations.Expose;
import edu.cmu.oli.content.models.persistance.JsonWrapper;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

/**
 * @author Eli Knebel
 */
@Entity
@Table(name = "dataset_blob")
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@NamedQueries({ @NamedQuery(name = "DatasetBlob.findAll", query = "SELECT r FROM DatasetBlob r"),
        @NamedQuery(name = "DatasetBlob.findByGuid", query = "SELECT r FROM DatasetBlob r WHERE r.guid = :guid") })
public class DatasetBlob implements Serializable {
    private static final Logger log = LoggerFactory.getLogger(DatasetBlob.class);

    private static final long serialVersionUID = 1L;

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
    @Column(name = "json_payload", columnDefinition = "json")
    @Type(type = "json")
    private JsonWrapper jsonPayload;

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

    public DatasetBlob() {
        this.dateCreated = new Date();
        this.dateUpdated = (Date) dateCreated.clone();
    }

    public DatasetBlob(JsonWrapper jsonPayload) {
        this.dateCreated = new Date();
        this.dateUpdated = (Date) dateCreated.clone();
        this.jsonPayload = jsonPayload;
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public JsonWrapper getJsonPayload() {
        return jsonPayload;
    }

    public void setJsonPayload(JsonWrapper body) {
        this.jsonPayload = body;
    }

    public String getBody() {
        if (jsonPayload != null) {
            return jsonPayload.getAsString();
        }
        return null;
    }

    public JsonElement getBodyJson() {
        if (jsonPayload != null) {
            return jsonPayload.getJsonObject();
        }
        return null;
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

    public DatasetBlob cloneVersion() {
        DatasetBlob versionClone = new DatasetBlob();
        versionClone.jsonPayload = jsonPayload != null ? new JsonWrapper(this.jsonPayload.getAsString()) : null;

        return versionClone;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 53 * hash + Objects.hashCode(this.guid);
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
        final DatasetBlob other = (DatasetBlob) obj;
        return Objects.equals(this.guid, other.guid);
    }

    @Override
    public String toString() {
        return "DatasetBlob{" + "guid=" + guid + '}';
    }
}
