package edu.cmu.oli.content.models;

import com.google.gson.annotations.Expose;
import edu.cmu.oli.content.AppUtils;

/**
 * @author Raphael Gachuhi
 */
public final class ResourceEditLock {

    @Expose
    long lockMaxDuration = 1000L * 60L * 5L; // 5 minutes

    @Expose
    String lockId;

    @Expose
    String resourceId;

    @Expose
    String lockedBy;

    // System milliseconds
    @Expose
    long lockedAt;

    public ResourceEditLock(long lockMaxDuration, String resourceId, String lockedBy, long lockedAt) {
        this.lockId = AppUtils.generateGUID();
        this.lockMaxDuration = lockMaxDuration;
        this.resourceId = resourceId;
        this.lockedBy = lockedBy;
        this.lockedAt = lockedAt;
    }

    public ResourceEditLock(String lockId, long lockMaxDuration, String resourceId, String lockedBy, long lockedAt) {
        this.lockId = lockId;
        this.lockMaxDuration = lockMaxDuration;
        this.resourceId = resourceId;
        this.lockedBy = lockedBy;
        this.lockedAt = lockedAt;
    }

    public ResourceEditLock withUpdatedLockedAt(long lockedAt) {
        return new ResourceEditLock(this.lockId, this.lockMaxDuration, this.resourceId, this.lockedBy, lockedAt);
    }

    public String getLockId() {
        return lockId;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public long getLockedAt() {
        return lockedAt;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ResourceEditLock that = (ResourceEditLock) o;

        if (lockedAt != that.lockedAt) return false;
        return resourceId.equals(that.resourceId) && lockedBy.equals(that.lockedBy);
    }

    @Override
    public int hashCode() {
        int result = resourceId.hashCode();
        result = 31 * result + lockedBy.hashCode();
        result = 31 * result + (int) (lockedAt ^ (lockedAt >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "ResourceEditLock{" +
                "resourceId='" + resourceId + '\'' +
                ", lockedBy='" + lockedBy + '\'' +
                ", lockedAt=" + lockedAt +
                '}';
    }
}
