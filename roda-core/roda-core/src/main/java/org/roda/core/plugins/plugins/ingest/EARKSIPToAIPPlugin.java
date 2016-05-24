/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.plugins.plugins.ingest;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.roda.core.RodaCoreFactory;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.exceptions.InvalidParameterException;
import org.roda.core.data.v2.ip.AIP;
import org.roda.core.data.v2.ip.TransferredResource;
import org.roda.core.data.v2.jobs.Report;
import org.roda.core.data.v2.jobs.Report.PluginState;
import org.roda.core.index.IndexService;
import org.roda.core.model.ModelService;
import org.roda.core.plugins.Plugin;
import org.roda.core.plugins.PluginException;
import org.roda.core.plugins.plugins.PluginHelper;
import org.roda.core.storage.StorageService;
import org.roda.core.storage.fs.FSUtils;
import org.roda_project.commons_ip.model.SIP;
import org.roda_project.commons_ip.model.impl.eark.EARKSIP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EARKSIPToAIPPlugin extends SIPToAIPPlugin {
  private static final Logger LOGGER = LoggerFactory.getLogger(EARKSIPToAIPPlugin.class);

  public static String UNPACK_DESCRIPTION = "Extracted objects from package in E-ARK SIP format.";

  private boolean createSubmission = false;

  @Override
  public void init() throws PluginException {
  }

  @Override
  public void shutdown() {
    // do nothing
  }

  @Override
  public String getName() {
    return "E-ARK SIP";
  }

  @Override
  public String getDescription() {
    return "E-ARK SIP as a zip file";
  }

  @Override
  public String getVersionImpl() {
    return "1.0";
  }

  @Override
  public void setParameterValues(Map<String, String> parameters) throws InvalidParameterException {
    super.setParameterValues(parameters);

    if (getParameterValues().containsKey(RodaConstants.PLUGIN_PARAMS_CREATE_SUBMISSION)) {
      createSubmission = Boolean.parseBoolean(getParameterValues().get(RodaConstants.PLUGIN_PARAMS_CREATE_SUBMISSION));
    }
  }

  @Override
  public Report beforeAllExecute(IndexService index, ModelService model, StorageService storage)
    throws PluginException {
    // do nothing
    return null;
  }

  @Override
  public Report beforeBlockExecute(IndexService index, ModelService model, StorageService storage)
    throws PluginException {

    return null;
  }

  @Override
  public Report execute(IndexService index, ModelService model, StorageService storage, List<TransferredResource> list)
    throws PluginException {
    Report report = PluginHelper.initPluginReport(this);

    for (TransferredResource transferredResource : list) {
      Report reportItem = PluginHelper.initPluginReportItem(this, transferredResource);

      Path earkSIPPath = Paths.get(transferredResource.getFullPath());
      LOGGER.debug("Converting {} to AIP", earkSIPPath);

      transformTransferredResourceIntoAnAIP(index, model, storage, transferredResource, earkSIPPath, createSubmission,
        reportItem);
      report.addReport(reportItem);

      PluginHelper.createJobReport(this, model, reportItem);

    }
    return report;
  }

  private void transformTransferredResourceIntoAnAIP(IndexService index, ModelService model, StorageService storage,
    TransferredResource transferredResource, Path earkSIPPath, boolean createSubmission, Report reportItem) {
    SIP sip = null;
    try {
      sip = EARKSIP.parse(earkSIPPath);
      reportItem.setSourceObjectOriginalId(sip.getId());

      if (sip.getValidationReport().isValid()) {
        String parentId = PluginHelper.computeParentId(this, index, sip.getParentID());
        AIP aipCreated = EARKSIPToAIPPluginUtils.earkSIPToAIP(sip, earkSIPPath, model, storage, sip.getId(),
          reportItem.getJobId(), parentId);

        PluginHelper.createSubmission(model, createSubmission, earkSIPPath, aipCreated.getId());

        createUnpackingEventSuccess(model, index, transferredResource, aipCreated, UNPACK_DESCRIPTION);
        reportItem.setOutcomeObjectId(aipCreated.getId()).setPluginState(PluginState.SUCCESS);

        if (sip.getParentID() != null && aipCreated.getParentId() == null) {
          reportItem.setPluginDetails(String.format("Parent with id '%s' not found", sip.getParentID()));
        }
        createWellformedEventSuccess(model, index, transferredResource, aipCreated);
        LOGGER.debug("Done with converting {} to AIP {}", earkSIPPath, aipCreated.getId());
      } else {
        reportItem.setPluginState(PluginState.FAILURE).setHtmlPluginDetails(true)
          .setPluginDetails(sip.getValidationReport().toHtml(true, true, true, false, false));
        LOGGER.debug("The SIP {} is not valid", earkSIPPath);
      }

    } catch (Throwable e) {
      reportItem.setPluginState(PluginState.FAILURE).setPluginDetails(e.getMessage());
      LOGGER.error("Error converting " + earkSIPPath + " to AIP", e);
    } finally {
      if (sip != null) {
        Path transferredResourcesAbsolutePath = RodaCoreFactory.getTransferredResourcesScanner().getBasePath()
          .toAbsolutePath();
        if (!sip.getBasePath().toAbsolutePath().toString().startsWith(transferredResourcesAbsolutePath.toString())) {
          FSUtils.deletePathQuietly(sip.getBasePath());
        }
      }
    }
  }

  @Override
  public Report afterBlockExecute(IndexService index, ModelService model, StorageService storage)
    throws PluginException {

    return null;
  }

  @Override
  public Report afterAllExecute(IndexService index, ModelService model, StorageService storage) throws PluginException {
    // do nothing
    return null;
  }

  @Override
  public Plugin<TransferredResource> cloneMe() {
    return new EARKSIPToAIPPlugin();
  }

  @Override
  public boolean areParameterValuesValid() {
    return true;
  }

  @Override
  public List<String> getCategories() {
    // TODO Auto-generated method stub
    return null;
  }

}
