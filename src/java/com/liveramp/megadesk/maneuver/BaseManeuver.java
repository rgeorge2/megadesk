package com.liveramp.megadesk.maneuver;

import com.liveramp.megadesk.device.Device;
import com.liveramp.megadesk.device.Read;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class BaseManeuver<T, SELF extends BaseManeuver> implements Maneuver<T, SELF> {

  private static final Logger LOGGER = Logger.getLogger(BaseManeuver.class);

  private String id;
  private List<Read> reads = Collections.emptyList();
  private List<Device> writes = Collections.emptyList();

  public BaseManeuver(String id) {
    this.id = id;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public List<Read> getReads() {
    return reads;
  }

  @Override
  public List<Device> getWrites() {
    return writes;
  }

  @Override
  @SuppressWarnings("unchecked")
  public SELF reads(Read... reads) {
    this.reads = Arrays.asList(reads);
    return (SELF) this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public SELF writes(Device... writes) {
    this.writes = Arrays.asList(writes);
    return (SELF) this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void acquire() throws Exception {
    LOGGER.info("Attempting maneuver '" + getId() + "'");
    // Acquire all locks in order
    // TODO: potential dead locks
    getLock().acquire();
    for (Read read : reads) {
      read.getDevice().getReadLock().acquire(getId(), read.getDataCheck(), true);
    }
    for (Device write : writes) {
      write.getWriteLock().acquire(getId(), true);
    }
  }

  @Override
  public void release() throws Exception {
    LOGGER.info("Completing maneuver '" + getId() + "'");
    // Make sure this process is allowed to complete this maneuver
    if (!getLock().isAcquiredInThisProcess()) {
      throw new IllegalStateException("Cannot complete maneuver '" + getId() + "' that is not acquired by this process.");
    }
    // Release all locks in order
    for (Read read : reads) {
      read.getDevice().getReadLock().release(getId());
    }
    for (Device write : writes) {
      write.getWriteLock().release(getId());
    }
    getLock().release();
  }

  @Override
  @SuppressWarnings("unchecked")
  public void write(Device device, Object data) throws Exception {
    if (!getLock().isAcquiredInThisProcess()) {
      throw new IllegalStateException("Cannot set data of device '" + device.getId() + "' from maneuver '" + getId() + "' that is not acquired by this process.");
    }
    if (!getWrites().contains(device)) {
      throw new IllegalStateException("Cannot set data of device '" + device.getId() + "' from maneuver '" + getId() + "' that does not write it.");
    }
    device.setData(getId(), data);
  }

  @Override
  public T getData() throws Exception {
    if (!getLock().isAcquiredInThisProcess()) {
      getLock().acquire();
      try {
        return doGetData();
      } finally {
        getLock().release();
      }
    } else {
      return doGetData();
    }
  }

  @Override
  public void setData(T data) throws Exception {
    if (!getLock().isAcquiredInThisProcess()) {
      getLock().acquire();
      try {
        doSetData(data);
      } finally {
        getLock().release();
      }
    } else {
      doSetData(data);
    }
  }

  protected abstract T doGetData() throws Exception;

  protected abstract void doSetData(T data) throws Exception;

  protected abstract ManeuverLock getLock();
}