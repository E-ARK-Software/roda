/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.wui.api.controllers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.transform.TransformerException;

import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.roda.core.RodaCoreFactory;
import org.roda.core.common.ConsumesOutputStream;
import org.roda.core.common.EntityResponse;
import org.roda.core.common.IdUtils;
import org.roda.core.common.StreamResponse;
import org.roda.core.common.UserUtility;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.exceptions.AlreadyExistsException;
import org.roda.core.data.exceptions.AuthorizationDeniedException;
import org.roda.core.data.exceptions.GenericException;
import org.roda.core.data.exceptions.InvalidParameterException;
import org.roda.core.data.exceptions.IsStillUpdatingException;
import org.roda.core.data.exceptions.JobAlreadyStartedException;
import org.roda.core.data.exceptions.NotFoundException;
import org.roda.core.data.exceptions.RODAException;
import org.roda.core.data.exceptions.RequestNotValidException;
import org.roda.core.data.v2.formats.Format;
import org.roda.core.data.v2.index.IndexResult;
import org.roda.core.data.v2.index.IsIndexed;
import org.roda.core.data.v2.index.facet.Facets;
import org.roda.core.data.v2.index.filter.Filter;
import org.roda.core.data.v2.index.select.SelectedItems;
import org.roda.core.data.v2.index.sort.Sorter;
import org.roda.core.data.v2.index.sublist.Sublist;
import org.roda.core.data.v2.ip.AIP;
import org.roda.core.data.v2.ip.File;
import org.roda.core.data.v2.ip.IndexedAIP;
import org.roda.core.data.v2.ip.IndexedFile;
import org.roda.core.data.v2.ip.IndexedRepresentation;
import org.roda.core.data.v2.ip.Permissions;
import org.roda.core.data.v2.ip.Permissions.PermissionType;
import org.roda.core.data.v2.ip.Representation;
import org.roda.core.data.v2.ip.TransferredResource;
import org.roda.core.data.v2.ip.metadata.DescriptiveMetadata;
import org.roda.core.data.v2.ip.metadata.PreservationMetadata.PreservationMetadataType;
import org.roda.core.data.v2.jobs.Report;
import org.roda.core.data.v2.jobs.Reports;
import org.roda.core.data.v2.log.LogEntry.LOG_ENTRY_STATE;
import org.roda.core.data.v2.risks.IndexedRisk;
import org.roda.core.data.v2.risks.Risk;
import org.roda.core.data.v2.risks.RiskIncidence;
import org.roda.core.data.v2.user.User;
import org.roda.core.data.v2.validation.ValidationException;
import org.roda.core.index.utils.IterableIndexResult;
import org.roda.core.storage.ContentPayload;
import org.roda.core.storage.fs.FSPathContentPayload;
import org.roda.wui.client.browse.BrowseItemBundle;
import org.roda.wui.client.browse.DescriptiveMetadataEditBundle;
import org.roda.wui.client.browse.DescriptiveMetadataVersionsBundle;
import org.roda.wui.client.browse.PreservationEventViewBundle;
import org.roda.wui.client.browse.SupportedMetadataTypeBundle;
import org.roda.wui.client.planning.MitigationPropertiesBundle;
import org.roda.wui.client.planning.RiskMitigationBundle;
import org.roda.wui.client.planning.RiskVersionsBundle;
import org.roda.wui.common.ControllerAssistant;
import org.roda.wui.common.RodaWuiController;

/**
 * FIXME 1) verify all checkObject*Permissions (because now also a permission
 * for insert is available)
 */
public class Browser extends RodaWuiController {

  private Browser() {
    super();
  }

  public static BrowseItemBundle retrieveItemBundle(User user, String aipId, Locale locale)
    throws AuthorizationDeniedException, GenericException, NotFoundException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, aipId);
    UserUtility.checkAIPPermissions(user, aip, PermissionType.READ);

    // delegate
    BrowseItemBundle itemBundle = BrowserHelper.retrieveItemBundle(aipId, locale);

    // register action
    controllerAssistant.registerAction(user, aipId, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_AIP_ID_PARAM,
      aipId);

    return itemBundle;
  }

  public static DescriptiveMetadataEditBundle retrieveDescriptiveMetadataEditBundle(User user, String aipId,
    String representationId, String metadataId, String type, String version, Locale locale)
    throws AuthorizationDeniedException, GenericException, RequestNotValidException, NotFoundException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, aipId);

    UserUtility.checkAIPPermissions(user, aip, PermissionType.READ);
    String repUUID = representationId;

    if (representationId != null) {
      IndexedRepresentation rep = BrowserHelper.retrieve(IndexedRepresentation.class, representationId);
      representationId = rep.getId();
    }

    // delegate
    DescriptiveMetadataEditBundle bundle = BrowserHelper.retrieveDescriptiveMetadataEditBundle(user, aip,
      representationId, metadataId, type, version, locale);

    // register action
    controllerAssistant.registerAction(user, aipId, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_AIP_ID_PARAM,
      aipId, RodaConstants.CONTROLLER_REPRESENTATION_UUID_PARAM, repUUID, RodaConstants.CONTROLLER_METADATA_ID_PARAM,
      metadataId);

    return bundle;
  }

  public static DescriptiveMetadataEditBundle retrieveDescriptiveMetadataEditBundle(User user, String aipId,
    String representationId, String metadataId, Locale locale)
    throws AuthorizationDeniedException, GenericException, RequestNotValidException, NotFoundException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, aipId);

    UserUtility.checkAIPPermissions(user, aip, PermissionType.READ);
    String repUUID = representationId;

    if (representationId != null) {
      IndexedRepresentation rep = BrowserHelper.retrieve(IndexedRepresentation.class, representationId);
      representationId = rep.getId();
    }

    // delegate
    DescriptiveMetadataEditBundle bundle = BrowserHelper.retrieveDescriptiveMetadataEditBundle(user, aip,
      representationId, metadataId, locale);

    // register action
    controllerAssistant.registerAction(user, aipId, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_AIP_ID_PARAM,
      aipId, RodaConstants.CONTROLLER_REPRESENTATION_UUID_PARAM, repUUID, RodaConstants.CONTROLLER_METADATA_ID_PARAM,
      metadataId);

    return bundle;
  }

  public static DescriptiveMetadataVersionsBundle retrieveDescriptiveMetadataVersionsBundle(User user, String aipId,
    String representationId, String metadataId, Locale locale)
    throws AuthorizationDeniedException, GenericException, RequestNotValidException, NotFoundException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, aipId);

    UserUtility.checkAIPPermissions(user, aip, PermissionType.READ);

    String repUUID = representationId;
    IndexedRepresentation rep = null;

    if (representationId != null) {
      rep = BrowserHelper.retrieve(IndexedRepresentation.class, representationId);
      representationId = rep.getId();
    }

    // delegate
    DescriptiveMetadataVersionsBundle bundle = BrowserHelper.retrieveDescriptiveMetadataVersionsBundle(aipId,
      representationId, metadataId, locale);
    bundle.setRepresentation(rep);

    // register action
    controllerAssistant.registerAction(user, aipId, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_AIP_ID_PARAM,
      aipId, RodaConstants.CONTROLLER_REPRESENTATION_UUID_PARAM, repUUID, RodaConstants.CONTROLLER_METADATA_ID_PARAM,
      metadataId);

    return bundle;
  }

  public static <T extends IsIndexed> IndexResult<T> find(final Class<T> classToReturn, final Filter filter,
    final Sorter sorter, final Sublist sublist, final Facets facets, final User user, final boolean justActive)
    throws GenericException, AuthorizationDeniedException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user, classToReturn);

    // delegate
    final IndexResult<T> ret = BrowserHelper.find(classToReturn, filter, sorter, sublist, facets, user, justActive);

    // register action

    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_CLASS_PARAM,
      classToReturn.getSimpleName(), RodaConstants.CONTROLLER_FILTER_PARAM, filter,
      RodaConstants.CONTROLLER_SORTER_PARAM, sorter, RodaConstants.CONTROLLER_SUBLIST_PARAM, sublist);

    return ret;
  }

  public static <T extends IsIndexed> IterableIndexResult<T> findAll(final Class<T> classToReturn, final Filter filter,
    final Sorter sorter, final Sublist sublist, final User user, final boolean justActive)
    throws GenericException, AuthorizationDeniedException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user, classToReturn);

    // delegate
    final IterableIndexResult<T> result = BrowserHelper.findAll(classToReturn, filter, sorter, sublist, user,
      justActive);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_CLASS_PARAM,
      classToReturn.getSimpleName(), RodaConstants.CONTROLLER_FILTER_PARAM, filter,
      RodaConstants.CONTROLLER_SORTER_PARAM, sorter, RodaConstants.CONTROLLER_SUBLIST_PARAM, sublist,
      RodaConstants.CONTROLLER_JUST_ACTIVE_PARAM, justActive);

    return result;
  }

  public static <T extends IsIndexed> Long count(final User user, final Class<T> classToReturn, final Filter filter)
    throws AuthorizationDeniedException, GenericException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user, classToReturn);

    // delegate
    final Long count = BrowserHelper.count(classToReturn, filter, user);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_CLASS_PARAM,
      classToReturn.getSimpleName(), RodaConstants.CONTROLLER_FILTER_PARAM, filter.toString());

    return count;
  }

  public static <T extends IsIndexed> T retrieve(final User user, final Class<T> classToReturn, final String id)
    throws AuthorizationDeniedException, GenericException, NotFoundException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user, classToReturn);

    // delegate
    final T ret = BrowserHelper.retrieve(classToReturn, id);

    // checking object permissions
    UserUtility.checkObjectPermissions(user, ret, PermissionType.READ);

    // register action
    String aipId = null;
    if (classToReturn.equals(IndexedAIP.class)) {
      aipId = id;
    }
    controllerAssistant.registerAction(user, aipId, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_CLASS_PARAM,
      classToReturn.getSimpleName());

    return ret;
  }

  public static <T extends IsIndexed> List<T> retrieve(final User user, final Class<T> classToReturn,
    final SelectedItems<T> selectedItems)
    throws AuthorizationDeniedException, GenericException, NotFoundException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user, classToReturn);

    final List<T> objects = BrowserHelper.retrieve(classToReturn, selectedItems);
    for (T obj : objects) {
      UserUtility.checkObjectPermissions(user, obj, PermissionType.READ);
    }

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_SELECTED_ITEMS_PARAM,
      selectedItems);

    return objects;
  }

  public static <T extends IsIndexed> void delete(final User user, final Class<T> classToReturn,
    final SelectedItems<T> ids)
    throws AuthorizationDeniedException, GenericException, NotFoundException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user, classToReturn);
    UserUtility.checkObjectPermissions(user, ids, PermissionType.DELETE);

    // delegate
    BrowserHelper.delete(user, classToReturn, ids);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_CLASS_PARAM,
      classToReturn.getSimpleName());
  }

  public static <T extends IsIndexed> List<String> suggest(final User user, final Class<T> classToReturn,
    final String field, final String query) throws AuthorizationDeniedException, GenericException, NotFoundException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user, classToReturn);

    // delegate
    // TODO suggest must integrate the user filter to disallow view of
    // information with permission
    final List<String> ret = BrowserHelper.suggest(classToReturn, field, query,user);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_CLASS_PARAM,
      classToReturn.getSimpleName(), RodaConstants.CONTROLLER_FIELD_PARAM, field, RodaConstants.CONTROLLER_QUERY_PARAM,
      query);

    return ret;
  }

  public static List<IndexedAIP> retrieveAncestors(User user, IndexedAIP aip)
    throws AuthorizationDeniedException, GenericException, NotFoundException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    UserUtility.checkAIPPermissions(user, aip, PermissionType.READ);

    // delegate
    List<IndexedAIP> ancestors = BrowserHelper.retrieveAncestors(aip);

    // register action
    controllerAssistant.registerAction(user, aip.getId(), LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_AIP_PARAM,
      aip.toString());

    return ancestors;
  }

  /*
   * ---------------------------------------------------------------------------
   * ---------------- REST related methods - start -----------------------------
   * ---------------------------------------------------------------------------
   */
  public static EntityResponse retrieveAIPRepresentation(User user, String representationUUID, String acceptFormat)
    throws AuthorizationDeniedException, GenericException, NotFoundException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // validate input
    BrowserHelper.validateGetAIPRepresentationParams(acceptFormat);

    IndexedRepresentation representation = BrowserHelper.retrieve(IndexedRepresentation.class, representationUUID);

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, representation.getAipId());
    UserUtility.checkAIPPermissions(user, aip, PermissionType.READ);

    // delegate
    EntityResponse aipRepresentation = BrowserHelper.retrieveAIPRepresentation(representation, acceptFormat);

    // register action
    controllerAssistant.registerAction(user, representation.getAipId(), LOG_ENTRY_STATE.SUCCESS,
      RodaConstants.REPRESENTATION_ID, representation.getId());

    return aipRepresentation;
  }

  public static StreamResponse retrieveAIPRepresentationPart(User user, String representationUUID, String part)
    throws GenericException, NotFoundException, AuthorizationDeniedException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    IndexedRepresentation representation = BrowserHelper.retrieve(IndexedRepresentation.class, representationUUID);

    // check user permissions
    controllerAssistant.checkRoles(user);
    UserUtility.checkRepresentationPermissions(user, representation, PermissionType.READ);

    // delegate
    StreamResponse aipRepresentation = BrowserHelper.retrieveAIPRepresentationPart(representation, part);

    // register action
    controllerAssistant.registerAction(user, representation.getAipId(), LOG_ENTRY_STATE.SUCCESS,
      RodaConstants.REPRESENTATION_ID, representation.getId(), RodaConstants.CONTROLLER_PART_PARAM, part);

    return aipRepresentation;
  }

  public static EntityResponse listAIPDescriptiveMetadata(User user, String aipId, String start, String limit,
    String acceptFormat)
    throws AuthorizationDeniedException, GenericException, NotFoundException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // validate input
    BrowserHelper.validateListAIPDescriptiveMetadataParams(acceptFormat);

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, aipId);
    UserUtility.checkAIPPermissions(user, aip, PermissionType.READ);

    // delegate
    EntityResponse aipDescriptiveMetadataList = BrowserHelper.listAIPDescriptiveMetadata(aipId, start, limit,
      acceptFormat);

    // register action
    controllerAssistant.registerAction(user, aipId, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_AIP_ID_PARAM,
      aipId, RodaConstants.CONTROLLER_START_PARAM, start, RodaConstants.CONTROLLER_LIMIT_PARAM, limit);

    return aipDescriptiveMetadataList;
  }

  public static EntityResponse listRepresentationDescriptiveMetadata(User user, String representationId, String start,
    String limit, String acceptFormat)
    throws AuthorizationDeniedException, GenericException, NotFoundException, RequestNotValidException {
    ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // validate input
    BrowserHelper.validateListAIPDescriptiveMetadataParams(acceptFormat);

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedRepresentation representation = BrowserHelper.retrieve(IndexedRepresentation.class, representationId);
    UserUtility.checkRepresentationPermissions(user, representation, PermissionType.READ);

    // delegate
    EntityResponse aipDescriptiveMetadataList = BrowserHelper.listRepresentationDescriptiveMetadata(
      representation.getAipId(), representation.getId(), start, limit, acceptFormat);

    // register action
    controllerAssistant.registerAction(user, representationId, LOG_ENTRY_STATE.SUCCESS,
      RodaConstants.CONTROLLER_REPRESENTATION_ID_PARAM, representationId, RodaConstants.CONTROLLER_START_PARAM, start,
      RodaConstants.CONTROLLER_LIMIT_PARAM, limit);

    return aipDescriptiveMetadataList;
  }

  public static EntityResponse retrieveAIPDescriptiveMetadata(User user, String aipId, String metadataId,
    String acceptFormat, String language) throws AuthorizationDeniedException, GenericException, TransformerException,
    NotFoundException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // validate input
    BrowserHelper.validateGetAIPDescriptiveMetadataParams(acceptFormat);

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, aipId);
    UserUtility.checkAIPPermissions(user, aip, PermissionType.READ);

    // delegate
    EntityResponse aipDescritiveMetadata = BrowserHelper.retrieveAIPDescritiveMetadata(aipId, metadataId, acceptFormat,
      language);

    // register action
    controllerAssistant.registerAction(user, aipId, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_METADATA_ID_PARAM,
      metadataId);

    return aipDescritiveMetadata;

  }

  public static EntityResponse retrieveRepresentationDescriptiveMetadata(User user, String representationId,
    String metadataId, String versionId, String acceptFormat, String language)
    throws AuthorizationDeniedException, GenericException, NotFoundException, RequestNotValidException {
    ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // validate input
    BrowserHelper.validateGetAIPDescriptiveMetadataParams(acceptFormat);

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedRepresentation representation = BrowserHelper.retrieve(IndexedRepresentation.class, representationId);
    UserUtility.checkRepresentationPermissions(user, representation, PermissionType.READ);

    // delegate
    EntityResponse aipDescritiveMetadata;
    if (versionId == null) {
      aipDescritiveMetadata = BrowserHelper.retrieveRepresentationDescriptiveMetadata(representation.getAipId(),
        representation.getId(), metadataId, acceptFormat, language);
    } else {
      aipDescritiveMetadata = BrowserHelper.retrieveRepresentationDescriptiveMetadataVersion(representation.getAipId(),
        representation.getId(), metadataId, versionId, acceptFormat, language);
    }

    // register action
    controllerAssistant.registerAction(user, representationId, LOG_ENTRY_STATE.SUCCESS,
      RodaConstants.CONTROLLER_METADATA_ID_PARAM, metadataId);

    return aipDescritiveMetadata;

  }

  public static EntityResponse retrieveAIPDescriptiveMetadataVersion(User user, String aipId, String metadataId,
    String versionId, String acceptFormat, String language) throws AuthorizationDeniedException, GenericException,
    TransformerException, NotFoundException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // validate input
    BrowserHelper.validateGetAIPDescriptiveMetadataParams(acceptFormat);

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, aipId);
    UserUtility.checkAIPPermissions(user, aip, PermissionType.READ);

    // delegate
    EntityResponse aipDescritiveMetadata = BrowserHelper.retrieveAIPDescritiveMetadataVersion(aipId, metadataId,
      versionId, acceptFormat, language);

    // register action
    controllerAssistant.registerAction(user, aipId, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_METADATA_ID_PARAM,
      metadataId, RodaConstants.CONTROLLER_VERSION_ID_PARAM, versionId);

    return aipDescritiveMetadata;

  }

  public static EntityResponse listAIPPreservationMetadata(User user, String aipId, String acceptFormat)
    throws AuthorizationDeniedException, GenericException, NotFoundException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // validate input
    BrowserHelper.validateListAIPPreservationMetadataParams(acceptFormat);

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, aipId);
    UserUtility.checkAIPPermissions(user, aip, PermissionType.READ);

    // delegate
    EntityResponse aipPreservationMetadataList = BrowserHelper.listAIPPreservationMetadata(aipId, acceptFormat);

    // register action
    controllerAssistant.registerAction(user, aipId, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_AIP_ID_PARAM,
      aipId);

    return aipPreservationMetadataList;
  }

  public static EntityResponse retrieveAIPRepresentationPreservationMetadata(User user, String representationId,
    String startAgent, String limitAgent, String startEvent, String limitEvent, String startFile, String limitFile,
    String acceptFormat, String language) throws AuthorizationDeniedException, GenericException, TransformerException,
    NotFoundException, RequestNotValidException, IOException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // validate input
    BrowserHelper.validateGetAIPRepresentationPreservationMetadataParams(acceptFormat, language);

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedRepresentation rep = BrowserHelper.retrieve(IndexedRepresentation.class, representationId);
    UserUtility.checkRepresentationPermissions(user, rep, PermissionType.READ);

    // delegate
    EntityResponse aipRepresentationPreservationMetadata = BrowserHelper.retrieveAIPRepresentationPreservationMetadata(
      rep.getAipId(), rep.getId(), startAgent, limitAgent, startEvent, limitEvent, startFile, limitFile, acceptFormat,
      language);

    // register action
    controllerAssistant.registerAction(user, rep.getAipId(), LOG_ENTRY_STATE.SUCCESS,
      RodaConstants.CONTROLLER_REPRESENTATION_ID_PARAM, representationId, RodaConstants.CONTROLLER_START_AGENT_PARAM,
      startAgent, RodaConstants.CONTROLLER_LIMIT_AGENT_PARAM, limitAgent, RodaConstants.CONTROLLER_START_EVENT_PARAM,
      startEvent, RodaConstants.CONTROLLER_LIMIT_EVENT_PARAM, limitEvent, RodaConstants.CONTROLLER_START_FILE_PARAM,
      startFile, RodaConstants.CONTROLLER_LIMIT_FILE_PARAM, limitFile);

    return aipRepresentationPreservationMetadata;

  }

  public static StreamResponse retrieveAIPRepresentationPreservationMetadataFile(User user, String fileUUID,
    String acceptFormat)
    throws AuthorizationDeniedException, GenericException, NotFoundException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // validate input
    BrowserHelper.validateListAIPPreservationMetadataParams(acceptFormat);

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedFile file = BrowserHelper.retrieve(IndexedFile.class, fileUUID);
    UserUtility.checkFilePermissions(user, file, PermissionType.READ);

    // delegate
    StreamResponse aipRepresentationPreservationMetadataFile = BrowserHelper
      .retrieveAIPRepresentationPreservationMetadataFile(file.getAipId(), file.getRepresentationId(), file.getId(),
        acceptFormat);

    // register action
    controllerAssistant.registerAction(user, file.getAipId(), LOG_ENTRY_STATE.SUCCESS,
      RodaConstants.CONTROLLER_FILE_UUID_PARAM, fileUUID);

    return aipRepresentationPreservationMetadataFile;
  }

  public static void createOrUpdatePreservationMetadataWithAIP(User user, String aipId, String fileId, InputStream is,
    FormDataContentDisposition fileDetail, boolean create) throws AuthorizationDeniedException, GenericException,
    NotFoundException, RequestNotValidException, ValidationException, AlreadyExistsException {

    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, aipId);
    UserUtility.checkAIPPermissions(user, aip, PermissionType.UPDATE);

    String id = fileId == null ? fileDetail.getFileName() : fileId;

    // delegate
    BrowserHelper.createOrUpdateAIPRepresentationPreservationMetadataFile(aipId, null, new ArrayList<>(), id, is,
      fileDetail, create);

    // register action
    controllerAssistant.registerAction(user, aipId, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_AIP_ID_PARAM,
      aipId);
  }

  public static void createOrUpdatePreservationMetadataWithRepresentation(User user, String representationUUID,
    String fileId, InputStream is, FormDataContentDisposition fileDetail, boolean create)
    throws AuthorizationDeniedException, GenericException, NotFoundException, RequestNotValidException,
    ValidationException, AlreadyExistsException {

    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedRepresentation rep = BrowserHelper.retrieve(IndexedRepresentation.class, representationUUID);
    UserUtility.checkRepresentationPermissions(user, rep, PermissionType.UPDATE);

    String id = fileId == null ? fileDetail.getFileName() : fileId;

    // delegate
    BrowserHelper.createOrUpdateAIPRepresentationPreservationMetadataFile(rep.getAipId(), rep.getId(),
      new ArrayList<>(), id, is, fileDetail, create);

    // register action
    controllerAssistant.registerAction(user, rep.getAipId(), LOG_ENTRY_STATE.SUCCESS,
      RodaConstants.CONTROLLER_REPRESENTATION_UUID_PARAM, representationUUID);
  }

  public static void createOrUpdatePreservationMetadataWithFile(User user, String fileUUID, InputStream is,
    FormDataContentDisposition fileDetail, boolean create) throws AuthorizationDeniedException, GenericException,
    NotFoundException, RequestNotValidException, ValidationException, AlreadyExistsException {

    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedFile file = BrowserHelper.retrieve(IndexedFile.class, fileUUID);
    UserUtility.checkFilePermissions(user, file, PermissionType.UPDATE);

    // delegate
    BrowserHelper.createOrUpdateAIPRepresentationPreservationMetadataFile(file.getAipId(), file.getRepresentationId(),
      file.getPath(), file.getId(), is, fileDetail, create);

    // register action
    controllerAssistant.registerAction(user, file.getAipId(), LOG_ENTRY_STATE.SUCCESS,
      RodaConstants.CONTROLLER_FILE_UUID_PARAM, fileUUID);
  }

  public static void deletePreservationMetadataWithAIP(User user, String aipId, String id, String type)
    throws AuthorizationDeniedException, GenericException, NotFoundException, RequestNotValidException,
    ValidationException, AlreadyExistsException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, aipId);
    UserUtility.checkAIPPermissions(user, aip, PermissionType.UPDATE);

    // delegate
    BrowserHelper.deletePreservationMetadataFile(PreservationMetadataType.valueOf(type), aipId, null, id, false);

    // register action
    controllerAssistant.registerAction(user, aipId, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_AIP_ID_PARAM,
      aipId, RodaConstants.CONTROLLER_ID_PARAM, id);

  }

  public static void deletePreservationMetadataWithRepresentation(User user, String representationUUID, String id,
    String type) throws AuthorizationDeniedException, GenericException, NotFoundException, RequestNotValidException,
    ValidationException, AlreadyExistsException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedRepresentation rep = BrowserHelper.retrieve(IndexedRepresentation.class, representationUUID);
    UserUtility.checkRepresentationPermissions(user, rep, PermissionType.UPDATE);

    // delegate
    BrowserHelper.deletePreservationMetadataFile(PreservationMetadataType.valueOf(type), rep.getAipId(), rep.getId(),
      id, false);

    // register action
    controllerAssistant.registerAction(user, rep.getAipId(), LOG_ENTRY_STATE.SUCCESS,
      RodaConstants.CONTROLLER_REPRESENTATION_UUID_PARAM, representationUUID, RodaConstants.CONTROLLER_ID_PARAM, id);

  }

  /*
   * ---------------------------------------------------------------------------
   * ---------------- REST related methods - end -------------------------------
   * ---------------------------------------------------------------------------
   */

  public static IndexedAIP moveAIPInHierarchy(SelectedItems<IndexedAIP> selected, String parentId, User user)
    throws AuthorizationDeniedException, GenericException, NotFoundException, RequestNotValidException,
    AlreadyExistsException, ValidationException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    UserUtility.checkAIPPermissions(user, selected, PermissionType.UPDATE);

    if (parentId != null) {
      IndexedAIP parentAip = BrowserHelper.retrieve(IndexedAIP.class, parentId);
      UserUtility.checkAIPPermissions(user, parentAip, PermissionType.CREATE);
    }

    // delegate
    IndexedAIP returnAIP = BrowserHelper.moveAIPInHierarchy(selected, parentId, user);

    // register action
    controllerAssistant.registerAction(user, parentId, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_SELECTED_PARAM,
      selected, RodaConstants.CONTROLLER_TO_PARENT_PARAM, parentId);

    return returnAIP;
  }

  public static AIP createAIP(User user, String parentId, String type) throws AuthorizationDeniedException,
    GenericException, NotFoundException, RequestNotValidException, AlreadyExistsException {
    if (parentId == null) {
      return createAIPTop(user, type);
    } else {
      return createAIPBelow(user, parentId, type);
    }
  }

  public static AIP createAIPTop(User user, String type) throws AuthorizationDeniedException, GenericException,
    NotFoundException, RequestNotValidException, AlreadyExistsException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    Permissions permissions = new Permissions();

    permissions.setUserPermissions(user.getId(), new HashSet<PermissionType>(Arrays.asList(PermissionType.values())));

    // delegate
    String parentId = null;
    AIP aip = BrowserHelper.createAIP(user, parentId, type, permissions);

    // register action
    controllerAssistant.registerAction(user, aip.getId(), LOG_ENTRY_STATE.SUCCESS);

    return aip;
  }

  public static AIP createAIPBelow(User user, String parentId, String type) throws AuthorizationDeniedException,
    GenericException, NotFoundException, RequestNotValidException, AlreadyExistsException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    Permissions permissions = new Permissions();

    if (parentId != null) {
      IndexedAIP parentSDO = BrowserHelper.retrieve(IndexedAIP.class, parentId);
      UserUtility.checkAIPPermissions(user, parentSDO, PermissionType.CREATE);
      Permissions parentPermissions = parentSDO.getPermissions();

      for (String name : parentPermissions.getUsernames()) {
        permissions.setUserPermissions(name, parentPermissions.getUserPermissions(name));
      }

      for (String name : parentPermissions.getGroupnames()) {
        permissions.setGroupPermissions(name, parentPermissions.getGroupPermissions(name));
      }
    } else {
      throw new RequestNotValidException("Creating AIP that should be below another with a null parentId");
    }

    permissions.setUserPermissions(user.getId(), new HashSet<PermissionType>(Arrays.asList(PermissionType.values())));

    // delegate
    AIP aip = BrowserHelper.createAIP(user, parentId, type, permissions);

    // register action
    controllerAssistant.registerAction(user, aip.getId(), LOG_ENTRY_STATE.SUCCESS,
      RodaConstants.CONTROLLER_PARENT_ID_PARAM, parentId);

    return aip;
  }

  public static AIP updateAIP(User user, AIP aip) throws AuthorizationDeniedException, GenericException,
    RequestNotValidException, NotFoundException, AlreadyExistsException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedAIP indexedAip = BrowserHelper.retrieve(IndexedAIP.class, aip.getId());
    UserUtility.checkAIPPermissions(user, indexedAip, PermissionType.UPDATE);

    // delegate
    AIP updatedAip = BrowserHelper.updateAIP(user, aip);

    // register action
    controllerAssistant.registerAction(user, aip.getId(), LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_AIP_PARAM,
      aip);

    return updatedAip;
  }

  public static String deleteAIP(User user, SelectedItems<IndexedAIP> aips)
    throws AuthorizationDeniedException, GenericException, NotFoundException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    UserUtility.checkAIPPermissions(user, aips, PermissionType.DELETE);

    // delegate
    String parentId = BrowserHelper.deleteAIP(aips, user);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_SELECTED_PARAM, aips);

    return parentId;
  }

  public static void deleteRepresentation(User user, SelectedItems<IndexedRepresentation> representations)
    throws AuthorizationDeniedException, GenericException, NotFoundException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    UserUtility.checkRepresentationPermissions(user, representations, PermissionType.DELETE);

    // delegate
    BrowserHelper.deleteRepresentation(representations, user);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_SELECTED_PARAM,
      representations);
  }

  public static void deleteFile(User user, SelectedItems<IndexedFile> files)
    throws AuthorizationDeniedException, GenericException, NotFoundException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    UserUtility.checkFilePermissions(user, files, PermissionType.DELETE);

    // delegate
    BrowserHelper.deleteFile(files, user);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_SELECTED_PARAM, files);
  }

  public static DescriptiveMetadata createDescriptiveMetadataFile(User user, String aipId, String representationId,
    String metadataId, String metadataType, String metadataVersion, ContentPayload metadataPayload)
    throws AuthorizationDeniedException, GenericException, ValidationException, NotFoundException,
    RequestNotValidException, AlreadyExistsException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, aipId);

    UserUtility.checkAIPPermissions(user, aip, PermissionType.UPDATE);

    String repUUID = representationId;

    if (representationId != null) {
      IndexedRepresentation rep = BrowserHelper.retrieve(IndexedRepresentation.class, representationId);
      representationId = rep.getId();
    }

    // delegate
    DescriptiveMetadata ret = BrowserHelper.createDescriptiveMetadataFile(aipId, representationId, metadataId,
      metadataType, metadataVersion, metadataPayload);

    // register action
    controllerAssistant.registerAction(user, aip.getId(), LOG_ENTRY_STATE.SUCCESS,
      RodaConstants.CONTROLLER_AIP_ID_PARAM, aipId, RodaConstants.CONTROLLER_REPRESENTATION_UUID_PARAM, repUUID,
      RodaConstants.CONTROLLER_METADATA_ID_PARAM, metadataId);

    return ret;
  }

  public static DescriptiveMetadata updateDescriptiveMetadataFile(User user, String aipId, String representationId,
    String metadataId, String metadataType, String metadataVersion, ContentPayload metadataPayload)
    throws AuthorizationDeniedException, GenericException, ValidationException, NotFoundException,
    RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, aipId);

    UserUtility.checkAIPPermissions(user, aip, PermissionType.UPDATE);

    String repUUID = representationId;

    if (representationId != null) {
      IndexedRepresentation rep = BrowserHelper.retrieve(IndexedRepresentation.class, representationId);
      representationId = rep.getId();
    }

    // delegate
    Map<String, String> properties = new HashMap<String, String>();
    properties.put(RodaConstants.VERSION_USER, user.getId());
    properties.put(RodaConstants.VERSION_ACTION, RodaConstants.VersionAction.UPDATED.toString());

    DescriptiveMetadata ret = BrowserHelper.updateDescriptiveMetadataFile(aipId, representationId, metadataId,
      metadataType, metadataVersion, metadataPayload, properties);

    // register action
    controllerAssistant.registerAction(user, aipId, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_AIP_ID_PARAM,
      aipId, RodaConstants.CONTROLLER_REPRESENTATION_UUID_PARAM, repUUID, RodaConstants.CONTROLLER_METADATA_ID_PARAM,
      metadataId);

    return ret;
  }

  public static void deleteDescriptiveMetadataFile(User user, String aipId, String representationId, String metadataId)
    throws AuthorizationDeniedException, GenericException, NotFoundException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, aipId);

    UserUtility.checkAIPPermissions(user, aip, PermissionType.DELETE);

    String repUUID = representationId;

    if (representationId != null) {
      IndexedRepresentation rep = BrowserHelper.retrieve(IndexedRepresentation.class, representationId);
      representationId = rep.getId();
    }

    // delegate
    BrowserHelper.deleteDescriptiveMetadataFile(aipId, representationId, metadataId);

    // register action
    controllerAssistant.registerAction(user, aip.getId(), LOG_ENTRY_STATE.SUCCESS,
      RodaConstants.CONTROLLER_AIP_ID_PARAM, aipId, RodaConstants.CONTROLLER_REPRESENTATION_UUID_PARAM, repUUID,
      RodaConstants.CONTROLLER_METADATA_ID_PARAM, metadataId);
  }

  public static void deleteRepresentationDescriptiveMetadataFile(User user, String representationId, String metadataId)
    throws AuthorizationDeniedException, GenericException, NotFoundException, RequestNotValidException {
    ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedRepresentation representation = BrowserHelper.retrieve(IndexedRepresentation.class, representationId);
    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, representation.getAipId());
    UserUtility.checkAIPPermissions(user, aip, PermissionType.DELETE);

    // delegate
    BrowserHelper.deleteDescriptiveMetadataFile(representation.getAipId(), representation.getId(), metadataId);

    // register action
    controllerAssistant.registerAction(user, aip.getId(), LOG_ENTRY_STATE.SUCCESS,
      RodaConstants.CONTROLLER_REPRESENTATION_UUID_PARAM, representationId, RodaConstants.CONTROLLER_METADATA_ID_PARAM,
      metadataId);
  }

  public static DescriptiveMetadata retrieveDescriptiveMetadataFile(User user, String aipId, String metadataId)
    throws AuthorizationDeniedException, GenericException, NotFoundException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, aipId);
    UserUtility.checkAIPPermissions(user, aip, PermissionType.READ);

    // delegate
    DescriptiveMetadata dm = BrowserHelper.retrieveMetadataFile(aipId, metadataId);

    // register action
    controllerAssistant.registerAction(user, aipId, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_AIP_ID_PARAM,
      aipId, RodaConstants.CONTROLLER_METADATA_ID_PARAM, metadataId);

    return dm;
  }

  public static Representation createRepresentation(User user, String aipId, String representationId, String type)
    throws AuthorizationDeniedException, GenericException, RequestNotValidException, NotFoundException,
    AlreadyExistsException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, aipId);
    UserUtility.checkAIPPermissions(user, aip, PermissionType.CREATE);

    // delegate
    Representation updatedRep = BrowserHelper.createRepresentation(aipId, representationId, type);

    // register action
    controllerAssistant.registerAction(user, aipId, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_AIP_ID_PARAM,
      aipId, RodaConstants.CONTROLLER_REPRESENTATION_ID_PARAM, representationId, RodaConstants.CONTROLLER_TYPE_PARAM,
      type);

    return updatedRep;
  }

  public static Representation updateRepresentation(User user, Representation representation)
    throws AuthorizationDeniedException, GenericException, RequestNotValidException, NotFoundException,
    AlreadyExistsException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, representation.getAipId());
    UserUtility.checkAIPPermissions(user, aip, PermissionType.UPDATE);

    // delegate
    Representation updatedRep = BrowserHelper.updateRepresentation(user, representation);

    // register action
    controllerAssistant.registerAction(user, representation.getAipId(), LOG_ENTRY_STATE.SUCCESS,
      RodaConstants.CONTROLLER_REPRESENTATION_PARAM, representation);

    return updatedRep;
  }

  public static void deleteRepresentation(User user, String representationUUID)
    throws AuthorizationDeniedException, GenericException, NotFoundException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedRepresentation rep = BrowserHelper.retrieve(IndexedRepresentation.class, representationUUID);
    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, rep.getAipId());
    UserUtility.checkAIPPermissions(user, aip, PermissionType.DELETE);

    // delegate
    BrowserHelper.deleteRepresentation(rep.getAipId(), rep.getId());

    // register action
    controllerAssistant.registerAction(user, aip.getId(), LOG_ENTRY_STATE.SUCCESS,
      RodaConstants.CONTROLLER_AIP_ID_PARAM, rep.getAipId(), RodaConstants.CONTROLLER_REPRESENTATION_ID_PARAM,
      rep.getId());
  }

  public static void deleteRepresentationFile(User user, String fileUUID)
    throws AuthorizationDeniedException, GenericException, NotFoundException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedFile file = BrowserHelper.retrieve(IndexedFile.class, fileUUID);
    UserUtility.checkFilePermissions(user, file, PermissionType.DELETE);

    // delegate
    BrowserHelper.deleteRepresentationFile(fileUUID);

    // register action
    controllerAssistant.registerAction(user, file.getAipId(), LOG_ENTRY_STATE.SUCCESS, RodaConstants.FILE_AIP_ID,
      file.getAipId(), RodaConstants.FILE_REPRESENTATION_ID, file.getRepresentationId(), RodaConstants.FILE_PATH,
      file.getPath(), RodaConstants.FILE_FILE_ID, file.getId());
  }

  public static EntityResponse retrieveAIPRepresentationFile(User user, String fileUuid, String acceptFormat)
    throws GenericException, AuthorizationDeniedException, NotFoundException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // validate input
    BrowserHelper.validateGetAIPRepresentationFileParams(acceptFormat);

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedFile file = RodaCoreFactory.getIndexService().retrieve(IndexedFile.class, fileUuid);
    UserUtility.checkFilePermissions(user, file, PermissionType.READ);

    // delegate
    EntityResponse aipRepresentationFile = BrowserHelper.retrieveAIPRepresentationFile(fileUuid, acceptFormat);

    // register action
    controllerAssistant.registerAction(user, file.getAipId(), LOG_ENTRY_STATE.SUCCESS,
      RodaConstants.FILE_REPRESENTATION_ID, file.getRepresentationId(), RodaConstants.FILE_PATH, file.getPath(),
      RodaConstants.FILE_FILE_ID, file.getId());

    return aipRepresentationFile;
  }

  public static DescriptiveMetadata updateAIPDescriptiveMetadataFile(User user, String aipId, String metadataId,
    String metadataType, String metadataVersion, InputStream is, FormDataContentDisposition fileDetail)
    throws GenericException, AuthorizationDeniedException, NotFoundException, RequestNotValidException,
    AlreadyExistsException, ValidationException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, aipId);
    UserUtility.checkAIPPermissions(user, aip, PermissionType.UPDATE);

    // delegate
    Map<String, String> properties = new HashMap<String, String>();
    properties.put(RodaConstants.VERSION_USER, user.getId());
    properties.put(RodaConstants.VERSION_ACTION, RodaConstants.VersionAction.UPDATED.toString());

    DescriptiveMetadata ret = BrowserHelper.createOrUpdateAIPDescriptiveMetadataFile(aipId, null, metadataId,
      metadataType, metadataVersion, properties, is, fileDetail, false);

    // register action
    controllerAssistant.registerAction(user, aipId, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_AIP_ID_PARAM,
      aipId, RodaConstants.CONTROLLER_METADATA_ID_PARAM, metadataId);
    return ret;
  }

  public static DescriptiveMetadata updateRepresentationDescriptiveMetadataFile(User user, String representationId,
    String metadataId, String metadataType, String metadataVersion, InputStream is,
    FormDataContentDisposition fileDetail) throws GenericException, AuthorizationDeniedException, NotFoundException,
    RequestNotValidException, AlreadyExistsException, ValidationException {
    ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedRepresentation representation = BrowserHelper.retrieve(IndexedRepresentation.class, representationId);
    UserUtility.checkRepresentationPermissions(user, representation, PermissionType.UPDATE);

    // delegate
    Map<String, String> properties = new HashMap<String, String>();
    properties.put(RodaConstants.VERSION_USER, user.getId());
    properties.put(RodaConstants.VERSION_ACTION, RodaConstants.VersionAction.UPDATED.toString());

    DescriptiveMetadata ret = BrowserHelper.createOrUpdateAIPDescriptiveMetadataFile(representation.getAipId(),
      representation.getId(), metadataId, metadataType, metadataVersion, properties, is, fileDetail, false);

    // register action
    controllerAssistant.registerAction(user, representationId, LOG_ENTRY_STATE.SUCCESS,
      RodaConstants.CONTROLLER_REPRESENTATION_UUID_PARAM, representationId, RodaConstants.CONTROLLER_METADATA_ID_PARAM,
      metadataId);
    return ret;
  }

  public static DescriptiveMetadata createAIPDescriptiveMetadataFile(User user, String aipId, String metadataId,
    String metadataType, String metadataVersion, InputStream is, FormDataContentDisposition fileDetail)
    throws GenericException, AuthorizationDeniedException, NotFoundException, RequestNotValidException,
    AlreadyExistsException, ValidationException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, aipId);
    UserUtility.checkAIPPermissions(user, aip, PermissionType.UPDATE);

    // delegate
    Map<String, String> properties = new HashMap<String, String>();
    properties.put(RodaConstants.VERSION_USER, user.getId());
    properties.put(RodaConstants.VERSION_ACTION, RodaConstants.VersionAction.CREATED.toString());

    DescriptiveMetadata ret = BrowserHelper.createOrUpdateAIPDescriptiveMetadataFile(aipId, null, metadataId,
      metadataType, metadataVersion, properties, is, fileDetail, true);

    // register action
    controllerAssistant.registerAction(user, aipId, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_AIP_ID_PARAM,
      aipId, RodaConstants.CONTROLLER_METADATA_ID_PARAM, metadataId);
    return ret;
  }

  public static DescriptiveMetadata createRepresentationDescriptiveMetadataFile(User user, String representationId,
    String metadataId, String metadataType, String metadataVersion, InputStream is,
    FormDataContentDisposition fileDetail) throws GenericException, AuthorizationDeniedException, NotFoundException,
    RequestNotValidException, AlreadyExistsException, ValidationException {
    ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedRepresentation representation = BrowserHelper.retrieve(IndexedRepresentation.class, representationId);
    UserUtility.checkRepresentationPermissions(user, representation, PermissionType.UPDATE);

    // delegate
    Map<String, String> properties = new HashMap<String, String>();
    properties.put(RodaConstants.VERSION_USER, user.getId());
    properties.put(RodaConstants.VERSION_ACTION, RodaConstants.VersionAction.CREATED.toString());

    DescriptiveMetadata ret = BrowserHelper.createOrUpdateAIPDescriptiveMetadataFile(representation.getAipId(),
      representation.getId(), metadataId, metadataType, metadataVersion, properties, is, fileDetail, true);

    // register action
    controllerAssistant.registerAction(user, representationId, LOG_ENTRY_STATE.SUCCESS,
      RodaConstants.CONTROLLER_REPRESENTATION_UUID_PARAM, representationId, RodaConstants.CONTROLLER_METADATA_ID_PARAM,
      metadataId);
    return ret;
  }

  public static TransferredResource createTransferredResourcesFolder(User user, String parentUUID, String folderName,
    boolean forceCommit)
    throws AuthorizationDeniedException, GenericException, RequestNotValidException, NotFoundException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};
    // check user permissions
    controllerAssistant.checkRoles(user);

    UserUtility.checkTransferredResourceAccess(user, Arrays.asList(parentUUID));

    // delegate
    try {
      TransferredResource transferredResource = BrowserHelper.createTransferredResourcesFolder(parentUUID, folderName,
        forceCommit);

      // register action
      controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_PARENT_PARAM,
        parentUUID, RodaConstants.CONTROLLER_FOLDERNAME_PARAM, folderName, RodaConstants.CONTROLLER_SUCCESS_PARAM,
        true);
      return transferredResource;
    } catch (GenericException e) {
      // register action
      controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_PARENT_PARAM,
        parentUUID, RodaConstants.CONTROLLER_FOLDERNAME_PARAM, folderName, RodaConstants.CONTROLLER_SUCCESS_PARAM,
        false, RodaConstants.CONTROLLER_ERROR_PARAM, e.getMessage());
      throw e;
    }
  }

  public static void deleteTransferredResources(User user, SelectedItems<TransferredResource> selected)
    throws AuthorizationDeniedException, GenericException, NotFoundException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    BrowserHelper.deleteTransferredResources(selected, user);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_SELECTED_PARAM,
      selected);
  }

  public static TransferredResource reindexTransferredResource(User user, String path)
    throws IsStillUpdatingException, AuthorizationDeniedException, NotFoundException, GenericException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    TransferredResource resource = BrowserHelper.reindexTransferredResource(path);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_PATH_PARAM, path);
    return resource;
  }

  public static TransferredResource createTransferredResourceFile(User user, String parentUUID, String fileName,
    InputStream inputStream, boolean forceCommit) throws AuthorizationDeniedException, GenericException,
    AlreadyExistsException, RequestNotValidException, NotFoundException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    UserUtility.checkTransferredResourceAccess(user, Arrays.asList(parentUUID));

    // delegate
    try {
      TransferredResource transferredResource = BrowserHelper.createTransferredResourceFile(parentUUID, fileName,
        inputStream, forceCommit);

      // register action
      controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_PATH_PARAM, parentUUID,
        RodaConstants.CONTROLLER_FILENAME_PARAM, fileName, RodaConstants.CONTROLLER_SUCCESS_PARAM, true);

      return transferredResource;
    } catch (GenericException e) {
      // register action
      controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_PATH_PARAM, parentUUID,
        RodaConstants.CONTROLLER_FILENAME_PARAM, fileName, RodaConstants.CONTROLLER_SUCCESS_PARAM, false,
        RodaConstants.CONTROLLER_ERROR_PARAM, e.getMessage());
      throw e;
    }

  }

  public static ConsumesOutputStream retrieveClassificationPlan(User user, String filename)
    throws GenericException, RequestNotValidException, NotFoundException, AuthorizationDeniedException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check permissions
    controllerAssistant.checkRoles(user);

    // delegate
    ConsumesOutputStream classificationPlan = BrowserHelper.retrieveClassificationPlan(user, filename);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS);

    return classificationPlan;
  }

  public static void updateAllTransferredResources(User user, String subFolderUUID, boolean waitToFinish)
    throws IsStillUpdatingException, AuthorizationDeniedException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check permissions
    controllerAssistant.checkRoles(user);

    BrowserHelper.runTransferredResourceScan(subFolderUUID, waitToFinish);
  }

  public static List<SupportedMetadataTypeBundle> retrieveSupportedMetadata(User user, String aipId, Locale locale)
    throws AuthorizationDeniedException, GenericException, NotFoundException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check permissions
    controllerAssistant.checkRoles(user);

    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, aipId);

    // check object permissions
    UserUtility.checkAIPPermissions(user, aip, PermissionType.READ);

    // delegate
    List<SupportedMetadataTypeBundle> supportedMetadata = BrowserHelper.retrieveSupportedMetadata(user, aip, locale);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.LOCALE, locale);

    return supportedMetadata;
  }

  public static EntityResponse retrieveTransferredResource(User user, String resourceId, String acceptFormat)
    throws AuthorizationDeniedException, NotFoundException, RequestNotValidException, GenericException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    EntityResponse response = BrowserHelper
      .retrieveTransferredResource(BrowserHelper.retrieve(TransferredResource.class, resourceId), acceptFormat);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_RESOURCE_ID_PARAM,
      resourceId);

    return response;
  }

  public static PreservationEventViewBundle retrievePreservationEventViewBundle(User user, String eventId)
    throws AuthorizationDeniedException, NotFoundException, GenericException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    // delegate
    PreservationEventViewBundle resource = BrowserHelper.retrievePreservationEventViewBundle(eventId);

    UserUtility.checkPreservationEventPermissions(user, resource.getEvent(), PermissionType.READ);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS,
      RodaConstants.CONTROLLER_INDEX_PRESERVATION_EVENT_ID_PARAM, eventId);

    return resource;
  }

  public static void revertDescriptiveMetadataVersion(User user, String aipId, String representationId,
    String descriptiveMetadataId, String versionId)
    throws AuthorizationDeniedException, RequestNotValidException, NotFoundException, GenericException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, aipId);

    UserUtility.checkAIPPermissions(user, aip, PermissionType.UPDATE);

    String repUUID = representationId;

    if (representationId != null) {
      IndexedRepresentation rep = BrowserHelper.retrieve(IndexedRepresentation.class, representationId);
      representationId = rep.getId();
    }

    // delegate
    Map<String, String> properties = new HashMap<String, String>();
    properties.put(RodaConstants.VERSION_USER, user.getId());
    properties.put(RodaConstants.VERSION_ACTION, RodaConstants.VersionAction.REVERTED.toString());

    BrowserHelper.revertDescriptiveMetadataVersion(aipId, representationId, descriptiveMetadataId, versionId,
      properties);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_AIP_ID_PARAM, aipId,
      RodaConstants.CONTROLLER_REPRESENTATION_UUID_PARAM, repUUID, RodaConstants.CONTROLLER_METADATA_ID_PARAM,
      descriptiveMetadataId, RodaConstants.CONTROLLER_VERSION_ID_PARAM, versionId);
  }

  public static void deleteDescriptiveMetadataVersion(User user, String aipId, String representationId,
    String descriptiveMetadataId, String versionId)
    throws NotFoundException, GenericException, RequestNotValidException, AuthorizationDeniedException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, aipId);

    UserUtility.checkAIPPermissions(user, aip, PermissionType.DELETE);

    String repUUID = representationId;

    if (representationId != null) {
      IndexedRepresentation rep = BrowserHelper.retrieve(IndexedRepresentation.class, representationId);
      representationId = rep.getId();
    }

    // TODO if not admin, add to filter a constraint for the resource to belong
    // to this user

    // delegate
    BrowserHelper.deleteDescriptiveMetadataVersion(aipId, representationId, descriptiveMetadataId, versionId);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_AIP_ID_PARAM, aipId,
      RodaConstants.CONTROLLER_REPRESENTATION_UUID_PARAM, repUUID, RodaConstants.CONTROLLER_METADATA_ID_PARAM,
      descriptiveMetadataId, RodaConstants.CONTROLLER_VERSION_ID_PARAM, versionId);
  }

  public static void updateAIPPermissions(User user, List<IndexedAIP> aips, Permissions permissions, boolean recursive)
    throws AuthorizationDeniedException, GenericException, NotFoundException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    for (IndexedAIP aip : aips) {
      UserUtility.checkAIPPermissions(user, aip, PermissionType.UPDATE);
      BrowserHelper.updateAIPPermissions(user, aip, permissions, recursive);
    }

    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_AIPS_PARAM, aips,
      RodaConstants.CONTROLLER_PERMISSIONS_PARAM, permissions);
  }

  public static void updateRisk(User user, Risk risk)
    throws AuthorizationDeniedException, NotFoundException, GenericException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    Map<String, String> properties = new HashMap<String, String>();
    properties.put(RodaConstants.VERSION_ACTION, RodaConstants.VersionAction.UPDATED.toString());

    BrowserHelper.updateRisk(risk, user, properties, false);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_RISK_PARAM, risk,
      RodaConstants.CONTROLLER_MESSAGE_PARAM, RodaConstants.VersionAction.UPDATED.toString());
  }

  public static void updateFormat(User user, Format format)
    throws AuthorizationDeniedException, NotFoundException, GenericException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    BrowserHelper.updateFormat(format, false);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_FORMAT_PARAM, format);
  }

  public static Risk createRisk(User user, Risk risk)
    throws AuthorizationDeniedException, NotFoundException, GenericException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    Risk ret = BrowserHelper.createRisk(risk, user, true);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_RISK_PARAM, risk);

    return ret;
  }

  public static Format createFormat(User user, Format format)
    throws AuthorizationDeniedException, NotFoundException, GenericException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    Format ret = BrowserHelper.createFormat(format, false);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_FORMAT_PARAM, format);

    return ret;
  }

  public static void revertRiskVersion(User user, String riskId, String versionId)
    throws AuthorizationDeniedException, NotFoundException, GenericException, RequestNotValidException, IOException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    Map<String, String> properties = new HashMap<String, String>();
    properties.put(RodaConstants.VERSION_ACTION, RodaConstants.VersionAction.REVERTED.toString());

    BrowserHelper.revertRiskVersion(riskId, versionId, properties);

    // register action
    controllerAssistant.registerAction(user, versionId, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_RISK_ID_PARAM,
      riskId, RodaConstants.CONTROLLER_VERSION_ID_PARAM, versionId, RodaConstants.CONTROLLER_MESSAGE_PARAM,
      RodaConstants.VersionAction.REVERTED.toString());
  }

  /**
   * @param user
   *          The user
   * @param selected
   *          The filter to select the AIPs to export
   * @param acceptFormat
   *          The output format
   * @return
   * @throws GenericException
   * @throws AuthorizationDeniedException
   * @throws NotFoundException
   * @throws RequestNotValidException
   * @throws IOException
   */
  public static StreamResponse exportAIP(User user, SelectedItems<IndexedAIP> selected, String acceptFormat)
    throws GenericException, AuthorizationDeniedException, NotFoundException, RequestNotValidException, IOException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // validate input
    BrowserHelper.validateExportAIPParams(acceptFormat);

    // check user permissions
    controllerAssistant.checkRoles(user);
    UserUtility.checkAIPPermissions(user, selected, PermissionType.READ);

    // delegate
    StreamResponse aipExport = BrowserHelper.retrieveAIPs(selected, acceptFormat);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS);

    return aipExport;
  }

  public static StreamResponse retrieveAIPPart(User user, String aipId, String part)
    throws RequestNotValidException, AuthorizationDeniedException, GenericException, NotFoundException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedAIP indexedAIP = BrowserHelper.retrieve(IndexedAIP.class, aipId);
    UserUtility.checkAIPPermissions(user, indexedAIP, PermissionType.READ);

    // delegate
    StreamResponse aip = BrowserHelper.retrieveAIPPart(indexedAIP, part);

    // register action
    controllerAssistant.registerAction(user, aipId, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_PART_PARAM, part);

    return aip;
  }

  public static void deleteRiskVersion(User user, String riskId, String versionId)
    throws AuthorizationDeniedException, NotFoundException, GenericException, RequestNotValidException, IOException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    BrowserHelper.deleteRiskVersion(riskId, versionId);

    // register action
    controllerAssistant.registerAction(user, versionId, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_RISK_ID_PARAM,
      riskId, RodaConstants.CONTROLLER_VERSION_ID_PARAM, versionId);
  }

  public static RiskVersionsBundle retrieveRiskVersions(User user, String riskId)
    throws AuthorizationDeniedException, NotFoundException, GenericException, RequestNotValidException, IOException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    RiskVersionsBundle ret = BrowserHelper.retrieveRiskVersions(riskId);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_RISK_ID_PARAM, riskId);

    return ret;
  }

  public static boolean hasRiskVersions(User user, String id)
    throws AuthorizationDeniedException, RequestNotValidException, GenericException, NotFoundException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    boolean ret = BrowserHelper.hasRiskVersions(id);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_RISK_ID_PARAM, id);

    return ret;
  }

  public static Risk retrieveRiskVersion(User user, String riskId, String selectedVersion)
    throws AuthorizationDeniedException, RequestNotValidException, GenericException, NotFoundException, IOException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    Risk ret = BrowserHelper.retrieveRiskVersion(riskId, selectedVersion);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_RISK_ID_PARAM, riskId,
      RodaConstants.CONTROLLER_SELECTED_VERSION_PARAM, selectedVersion);

    return ret;
  }

  public static RiskMitigationBundle retrieveShowMitigationTerms(User user, int preMitigationProbability,
    int preMitigationImpact, int posMitigationProbability, int posMitigationImpact)
    throws AuthorizationDeniedException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    RiskMitigationBundle ret = BrowserHelper.retrieveShowMitigationTerms(preMitigationProbability, preMitigationImpact,
      posMitigationProbability, posMitigationImpact);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS,
      RodaConstants.CONTROLLER_PRE_MITIGATION_PROBABILITY_PARAM, preMitigationProbability,
      RodaConstants.CONTROLLER_PRE_MITIGATION_IMPACT_PARAM, preMitigationImpact,
      RodaConstants.CONTROLLER_POS_MITIGATION_PROBABILITY_PARAM, posMitigationProbability,
      RodaConstants.CONTROLLER_POS_MITIGATION_IMPACT_PARAM, posMitigationImpact);

    return ret;
  }

  public static List<String> retrieveMitigationSeverityLimits(User user) throws AuthorizationDeniedException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    List<String> ret = BrowserHelper.retrieveShowMitigationTerms();

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS);

    return ret;
  }

  public static MitigationPropertiesBundle retrieveAllMitigationProperties(User user)
    throws AuthorizationDeniedException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    MitigationPropertiesBundle ret = BrowserHelper.retrieveAllMitigationProperties();

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS);

    return ret;
  }

  public static void deleteRisk(User user, SelectedItems<IndexedRisk> selected)
    throws AuthorizationDeniedException, GenericException, RequestNotValidException, NotFoundException,
    InvalidParameterException, JobAlreadyStartedException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    BrowserHelper.deleteRisk(user, selected);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_SELECTED_PARAM,
      selected);
  }

  public static void deleteFormat(User user, SelectedItems<Format> selected)
    throws AuthorizationDeniedException, GenericException, RequestNotValidException, NotFoundException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    BrowserHelper.deleteFormat(user, selected);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_SELECTED_PARAM,
      selected);
  }

  public static void updateRiskCounters(User user)
    throws AuthorizationDeniedException, GenericException, RequestNotValidException, NotFoundException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    BrowserHelper.updateRiskCounters();

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS);
  }

  public static void appraisal(User user, SelectedItems<IndexedAIP> selected, boolean accept, String rejectReason)
    throws GenericException, AuthorizationDeniedException, RequestNotValidException, NotFoundException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    UserUtility.checkAIPPermissions(user, selected, PermissionType.UPDATE);

    // delegate
    BrowserHelper.appraisal(user, selected, accept, rejectReason);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_SELECTED_PARAM, selected,
      RodaConstants.CONTROLLER_ACCEPT_PARAM, accept, RodaConstants.CONTROLLER_REJECT_REASON_PARAM, rejectReason);
  }

  public static IndexedRepresentation retrieveRepresentationById(User user, String representationId)
    throws AuthorizationDeniedException, GenericException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    IndexedRepresentation ret = BrowserHelper.retrieveRepresentationById(user, representationId);

    // check object permissions
    UserUtility.checkRepresentationPermissions(user, ret, PermissionType.READ);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_REPRESENTATION_ID_PARAM,
      representationId);

    return ret;
  }

  public static IndexedFile retrieveFileById(User user, String fileId)
    throws AuthorizationDeniedException, GenericException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    IndexedFile ret = BrowserHelper.retrieveFileById(user, fileId);

    // check object permissions
    UserUtility.checkFilePermissions(user, ret, PermissionType.READ);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_FILE_ID_PARAM, fileId);

    return ret;
  }

  public static String retrieveDescriptiveMetadataPreview(User user, SupportedMetadataTypeBundle bundle)
    throws AuthorizationDeniedException, GenericException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check permissions
    controllerAssistant.checkRoles(user);

    // delegate
    String payload = BrowserHelper.retrieveDescriptiveMetadataPreview(bundle);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_TEMPLATE_PARAM,
      bundle.getLabel());

    return payload;
  }

  public static String renameTransferredResource(User user, String transferredResourceId, String newName)
    throws GenericException, RequestNotValidException, AuthorizationDeniedException, AlreadyExistsException,
    IsStillUpdatingException, NotFoundException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    String ret = BrowserHelper.renameTransferredResource(transferredResourceId, newName);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS,
      RodaConstants.CONTROLLER_TRANSFERRED_RESOURCE_ID_PARAM, transferredResourceId);
    return ret;
  }

  public static String moveTransferredResource(User user, SelectedItems<TransferredResource> selected,
    TransferredResource transferredResource) throws AuthorizationDeniedException, GenericException,
    RequestNotValidException, AlreadyExistsException, IsStillUpdatingException, NotFoundException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    String ret = BrowserHelper.moveTransferredResource(selected, transferredResource);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_SELECTED_PARAM, selected,
      RodaConstants.CONTROLLER_TRANSFERRED_RESOURCE_PARAM, transferredResource);
    return ret;
  }

  public static List<TransferredResource> retrieveSelectedTransferredResource(User user,
    SelectedItems<TransferredResource> selected)
    throws GenericException, RequestNotValidException, AuthorizationDeniedException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    List<TransferredResource> ret = BrowserHelper.retrieveSelectedTransferredResource(selected);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_SELECTED_PARAM,
      selected);
    return ret;
  }

  public static File createFile(User user, String aipId, String representationId, List<String> directoryPath,
    String fileId, InputStream is) throws AuthorizationDeniedException, GenericException, RequestNotValidException,
    NotFoundException, AlreadyExistsException, IOException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, aipId);
    UserUtility.checkAIPPermissions(user, aip, PermissionType.CREATE);

    // delegate
    Path file = Files.createTempFile("descriptive", ".tmp");
    Files.copy(is, file, StandardCopyOption.REPLACE_EXISTING);
    ContentPayload payload = new FSPathContentPayload(file);

    File updatedFile = BrowserHelper.createFile(aipId, representationId, directoryPath, fileId, payload);

    // register action
    controllerAssistant.registerAction(user, aipId, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_AIP_ID_PARAM,
      aipId, RodaConstants.CONTROLLER_REPRESENTATION_ID_PARAM, representationId,
      RodaConstants.CONTROLLER_DIRECTORY_PATH_PARAM, directoryPath, RodaConstants.CONTROLLER_FILE_ID_PARAM, fileId);

    return updatedFile;
  }

  public static File updateFile(User user, File file) throws AuthorizationDeniedException, GenericException,
    RequestNotValidException, NotFoundException, AlreadyExistsException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedAIP aip = BrowserHelper.retrieve(IndexedAIP.class, file.getAipId());
    UserUtility.checkAIPPermissions(user, aip, PermissionType.UPDATE);

    // delegate
    File updatedFile = BrowserHelper.updateFile(user, file);

    // register action
    controllerAssistant.registerAction(user, file.getAipId(), LOG_ENTRY_STATE.SUCCESS,
      RodaConstants.CONTROLLER_FILE_PARAM, file);

    return updatedFile;
  }

  public static void deleteFile(User user, String fileUUID)
    throws AuthorizationDeniedException, GenericException, RequestNotValidException, NotFoundException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);
    IndexedFile file = BrowserHelper.retrieve(IndexedFile.class, fileUUID);
    UserUtility.checkFilePermissions(user, file, PermissionType.DELETE);

    // delegate
    BrowserHelper.deleteRepresentationFile(fileUUID);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_FILE_UUID_PARAM,
      fileUUID);
  }

  public static void updateRiskIncidence(User user, RiskIncidence incidence)
    throws AuthorizationDeniedException, GenericException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    BrowserHelper.updateRiskIncidence(incidence);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_INCIDENCE_PARAM,
      incidence);
  }

  public static Reports listReports(User user, String id, String resourceOrSip, String start, String limit,
    String acceptFormat)
    throws AuthorizationDeniedException, GenericException, NotFoundException, RequestNotValidException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // validate input
    BrowserHelper.validateListingParams(acceptFormat);

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    Reports reportList;

    if (id == null || resourceOrSip == null) {
      reportList = BrowserHelper.listReports(start, limit);
    } else {
      if (RodaConstants.CONTROLLER_SIP_PARAM.equals(resourceOrSip)) {
        reportList = BrowserHelper.listTransferredResourcesReportsWithSIP(id, start, limit);
      } else {
        reportList = BrowserHelper.listTransferredResourcesReports(id, start, limit);
      }
    }

    // register action
    controllerAssistant.registerAction(user, id, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_ID_PARAM, id,
      RodaConstants.CONTROLLER_ID_OBJECT_PARAM, resourceOrSip, RodaConstants.CONTROLLER_START_PARAM, start,
      RodaConstants.CONTROLLER_LIMIT_PARAM, limit);

    return reportList;
  }

  public static Report lastReport(User user, String id, String resourceOrSip, String acceptFormat)
    throws RODAException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // validate input
    BrowserHelper.validateListingParams(acceptFormat);

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    String start = "0";
    String limit = "1";
    Reports reportList;

    if (id == null || resourceOrSip == null) {
      reportList = BrowserHelper.listReports(start, limit);
    } else {
      if (RodaConstants.CONTROLLER_SIP_PARAM.equals(resourceOrSip)) {
        reportList = BrowserHelper.listTransferredResourcesReportsWithSIP(id, start, limit);
      } else {
        if (RodaConstants.CONTROLLER_ID_OBJECT_RESOURCE_PATH.equals(resourceOrSip)) {
          id = IdUtils.getTransferredResourceUUID(id);
        }
        reportList = BrowserHelper.listTransferredResourcesReports(id, start, limit);
      }
    }

    // register action
    controllerAssistant.registerAction(user, id, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_ID_PARAM, id,
      RodaConstants.CONTROLLER_ID_OBJECT_PARAM, resourceOrSip, RodaConstants.CONTROLLER_START_PARAM, start,
      RodaConstants.CONTROLLER_LIMIT_PARAM, limit);

    if (reportList.getReports().isEmpty()) {
      throw new RODAException();
    } else {
      return reportList.getReports().get(0);
    }
  }

  public static void deleteRiskIncidences(User user, SelectedItems<RiskIncidence> selected)
    throws AuthorizationDeniedException, GenericException, RequestNotValidException, NotFoundException,
    InvalidParameterException, JobAlreadyStartedException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    BrowserHelper.deleteRiskIncidences(user, selected);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_SELECTED_PARAM,
      selected);
  }

  public static void updateMultipleIncidences(User user, SelectedItems<RiskIncidence> selected, String status,
    String severity, Date mitigatedOn, String mitigatedBy, String mitigatedDescription)
    throws AuthorizationDeniedException, RequestNotValidException, GenericException, NotFoundException {
    final ControllerAssistant controllerAssistant = new ControllerAssistant() {};

    // check user permissions
    controllerAssistant.checkRoles(user);

    // delegate
    BrowserHelper.updateMultipleIncidences(user, selected, status, severity, mitigatedOn, mitigatedBy,
      mitigatedDescription);

    // register action
    controllerAssistant.registerAction(user, LOG_ENTRY_STATE.SUCCESS, RodaConstants.CONTROLLER_SELECTED_PARAM,
      selected);
  }

}
