/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.plugins.plugins.base;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.roda.core.common.iterables.CloseableIterable;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.common.RodaConstants.PreservationEventType;
import org.roda.core.data.exceptions.AuthorizationDeniedException;
import org.roda.core.data.exceptions.GenericException;
import org.roda.core.data.exceptions.InvalidParameterException;
import org.roda.core.data.exceptions.NotFoundException;
import org.roda.core.data.exceptions.RequestNotValidException;
import org.roda.core.data.utils.JsonUtils;
import org.roda.core.data.v2.ip.StoragePath;
import org.roda.core.data.v2.jobs.PluginType;
import org.roda.core.data.v2.jobs.Report;
import org.roda.core.index.IndexService;
import org.roda.core.index.utils.SolrUtils;
import org.roda.core.model.ModelService;
import org.roda.core.model.utils.ModelUtils;
import org.roda.core.plugins.AbstractPlugin;
import org.roda.core.plugins.Plugin;
import org.roda.core.plugins.PluginException;
import org.roda.core.storage.Binary;
import org.roda.core.storage.Resource;
import org.roda.core.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReindexRodaEntityPlugin<T extends Serializable> extends AbstractPlugin<T> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReindexRodaEntityPlugin.class);
  private boolean clearIndexes = true;
  private boolean recursiveListing = false;
  private Class<T> clazz = null;

  @Override
  public void init() throws PluginException {
    // do nothing
  }

  @Override
  public void shutdown() {
    // do nothing
  }

  @Override
  public String getName() {
    return "Reindex Roda entity";
  }

  @Override
  public String getDescription() {
    return "Cleanup indexes and recreate them from data in storage";
  }

  @Override
  public String getVersionImpl() {
    return "1.0";
  }

  @Override
  public void setParameterValues(Map<String, String> parameters) throws InvalidParameterException {
    super.setParameterValues(parameters);
    if (parameters != null) {
      if (parameters.get(RodaConstants.PLUGIN_PARAMS_CLEAR_INDEXES) != null) {
        clearIndexes = Boolean.parseBoolean(parameters.get(RodaConstants.PLUGIN_PARAMS_CLEAR_INDEXES));
      }
      if (parameters.get(RodaConstants.PLUGIN_PARAMS_CLASS_CANONICAL_NAME) != null) {
        try {
          String classCanonicalName = parameters.get(RodaConstants.PLUGIN_PARAMS_CLASS_CANONICAL_NAME);
          clazz = (Class<T>) Class.forName(classCanonicalName);
        } catch (ClassNotFoundException e) {
          // do nothing
        }
      }
      if (parameters.get(RodaConstants.PLUGIN_PARAMS_RECURSIVE_LISTING) != null) {
        recursiveListing = Boolean.parseBoolean(parameters.get(RodaConstants.PLUGIN_PARAMS_RECURSIVE_LISTING));
      }
    }
  }

  @Override
  public Report execute(IndexService index, ModelService model, StorageService storage, List<T> list)
    throws PluginException {

    CloseableIterable<Resource> listResourcesUnderDirectory = null;
    try {
      StoragePath containerPath = ModelUtils.getContainerPath(clazz);
      listResourcesUnderDirectory = storage.listResourcesUnderContainer(containerPath, recursiveListing);
      LOGGER.debug("Reindexing Roda entities under {}", containerPath);

      for (Resource resource : listResourcesUnderDirectory) {
        if (!resource.isDirectory()) {
          Binary binary = (Binary) resource;
          InputStream inputStream = binary.getContent().createInputStream();
          String jsonString = IOUtils.toString(inputStream);
          T object = JsonUtils.getObjectFromJson(jsonString, clazz);
          IOUtils.closeQuietly(inputStream);
          index.reindex(clazz, object);
        }
      }

    } catch (NotFoundException | GenericException | AuthorizationDeniedException | RequestNotValidException
      | IOException e) {
      LOGGER.error("Error reindexing Roda entity", e);
    } finally {
      IOUtils.closeQuietly(listResourcesUnderDirectory);
    }

    return new Report();

  }

  @Override
  public Report beforeAllExecute(IndexService index, ModelService model, StorageService storage)
    throws PluginException {
    if (clearIndexes) {
      LOGGER.debug("Clearing indexes");
      try {
        index.clearIndex(SolrUtils.getIndexName(clazz));
      } catch (GenericException e) {
        throw new PluginException("Error clearing index", e);
      }
    } else {
      LOGGER.debug("Skipping clear indexes");
    }

    return new Report();
  }

  @Override
  public Report beforeBlockExecute(IndexService index, ModelService model, StorageService storage)
    throws PluginException {
    // do nothing
    return null;
  }

  @Override
  public Report afterBlockExecute(IndexService index, ModelService model, StorageService storage)
    throws PluginException {
    // do nothing
    return null;
  }

  @Override
  public Report afterAllExecute(IndexService index, ModelService model, StorageService storage) throws PluginException {
    LOGGER.debug("Optimizing indexes");
    try {
      index.optimizeIndex(SolrUtils.getIndexName(clazz));
    } catch (GenericException e) {
      throw new PluginException("Error optimizing index", e);
    }

    return new Report();
  }

  @Override
  public Plugin<T> cloneMe() {
    return new ReindexRodaEntityPlugin<T>();
  }

  @Override
  public PluginType getType() {
    return PluginType.MISC;
  }

  @Override
  public boolean areParameterValuesValid() {
    return true;
  }

  // TODO FIX
  @Override
  public PreservationEventType getPreservationEventType() {
    return null;
  }

  @Override
  public String getPreservationEventDescription() {
    return "Reindex Roda entity " + clazz.getSimpleName();
  }

  @Override
  public String getPreservationEventSuccessMessage() {
    return "All " + clazz.getSimpleName() + " were reindexed with success";
  }

  @Override
  public String getPreservationEventFailureMessage() {
    return "An error occured while reindexing all " + clazz.getSimpleName();
  }

  @Override
  public List<String> getCategories() {
    // TODO Auto-generated method stub
    return null;
  }

}
