/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2019. Carnegie Mellon University
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

import com.google.gson.annotations.Expose;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

/**
 * An entity representing a dataset for student performance in a course
 *
 * @author Zach Bluedorn
 */
@Entity
@Table(name = "dataset", indexes = { @Index(columnList = "guid", name = "dataset_guid"),
    @Index(columnList = "package_guid", name = "package_guid") })
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@NamedQueries({ @NamedQuery(name = "Dataset.findAll", query = "SELECT r FROM Dataset r"),
    @NamedQuery(name = "Dataset.findByGuid", query = "SELECT r FROM Dataset r WHERE r.guid = :guid"),
    @NamedQuery(name = "Dataset.findByPackageGuid", query = "SELECT r FROM Dataset r WHERE r.contentPackage.guid = :packageGuid"),
    @NamedQuery(name = "Dataset.findByDateCreated", query = "SELECT r FROM Dataset r WHERE r.dateCreated = :dateCreated"),
    @NamedQuery(name = "Dataset.findByDateCompleted", query = "SELECT r FROM Dataset r WHERE r.dateCompleted = :dateCompleted"),
    @NamedQuery(name = "Dataset.findProcessing", query = "SELECT r FROM Dataset r WHERE r.dateCompleted is null"),
    @NamedQuery(name = "Dataset.findCompleted", query = "SELECT r FROM Dataset r WHERE r.dateCompleted is not null"),
    @NamedQuery(name = "Dataset.findProcessingByPackageGuid", query = "SELECT r FROM Dataset r WHERE r.dateCompleted is null AND r.contentPackage.guid = :packageGuid"),
    @NamedQuery(name = "Dataset.findCompletedByPackageGuid", query = "SELECT r FROM Dataset r WHERE r.dateCompleted is not null AND r.contentPackage.guid = :packageGuid"),
    @NamedQuery(name = "Dataset.resetProcessing", query = "UPDATE Dataset SET datasetStatus = edu.cmu.oli.content.models.persistance.entities.DatasetStatus.FAILED WHERE datasetStatus = edu.cmu.oli.content.models.persistance.entities.DatasetStatus.PROCESSING") })
public class Dataset implements Serializable {

  private static final long serialVersionUID = 1L;

  @Expose()
  @Id
  @Basic(optional = false)
  @NotNull
  @Size(min = 1, max = 32)
  @Column(name = "guid")
  private String guid;

  @Schema(hidden = true)
  @JoinColumn(name = "package_guid", referencedColumnName = "guid")
  @ManyToOne(fetch = FetchType.LAZY)
  private ContentPackage contentPackage;

  @JoinColumn(name = "dataset_blob_guid", referencedColumnName = "guid")
  @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
  private DatasetBlob body;

  @Expose()
  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "dataset_status")
  private DatasetStatus datasetStatus = DatasetStatus.PROCESSING;

  @Expose()
  @Schema(hidden = true)
  @Column(name = "date_created", columnDefinition = "DATETIME", updatable = false)
  @Temporal(TemporalType.TIMESTAMP)
  @CreationTimestamp
  private Date dateCreated;

  @Expose()
  @Schema(hidden = true)
  @Column(name = "date_completed")
  @Temporal(TemporalType.TIMESTAMP)
  private Date dateCompleted;

  @Expose()
  @Size(max = 1000)
  @Column(name = "message")
  private String message;

  public Dataset() {
    this.guid = UUID.randomUUID().toString().replaceAll("-", "");
    this.dateCreated = new Date();
    this.datasetStatus = DatasetStatus.PROCESSING;
  }

  public String getGuid() {
    return guid;
  }

  public void setGuid(String guid) {
    this.guid = guid;
  }

  public void setDatasetBlob(DatasetBlob blob) {
    this.body = blob;
  }

  public DatasetBlob getDatasetBlob() {
    return body;
  }

  public void setDatasetStatus(DatasetStatus status) {
    this.datasetStatus = status;
  }

  public DatasetStatus getDatasetStatus() {
    return datasetStatus;
  }

  public Date getDateCreated() {
    return dateCreated;
  }

  public void setDateCreated(Date dateCreated) {
    this.dateCreated = dateCreated;
  }

  public Date getDateCompleted() {
    return dateCompleted;
  }

  public void setDateCompleted(Date dateCompleted) {
    this.dateCompleted = dateCompleted;
  }

  public ContentPackage getContentPackage() {
    return contentPackage;
  }

  public void setContentPackage(ContentPackage contentPackage) {
    this.contentPackage = contentPackage;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Dataset cloneDataset(String packageId) {
    Dataset datasetClone = new Dataset();
    datasetClone.contentPackage = this.contentPackage;
    datasetClone.body = this.body;
    datasetClone.datasetStatus = this.datasetStatus;
    datasetClone.message = this.message;
    return datasetClone;
  }

  @Override
  public String toString() {
    return "Dataset{guid=" + guid + ", packageGuid=" + contentPackage.getGuid() + ", status=" + datasetStatus.toString()
        + ", dateCreated=" + dateCreated + ", dateCompleted=" + dateCompleted + ", message=" + message + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    Dataset dataset = (Dataset) o;

    if (!contentPackage.equals(dataset.contentPackage))
      return false;

    if (!datasetStatus.equals(dataset.getDatasetStatus()))
      return false;

    return body.equals(dataset.getDatasetBlob());
  }

  @Override
  public int hashCode() {
    return body.hashCode();
  }
}
