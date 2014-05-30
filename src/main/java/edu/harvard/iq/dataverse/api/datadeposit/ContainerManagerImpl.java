package edu.harvard.iq.dataverse.api.datadeposit;

import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.DatasetServiceBean;
import edu.harvard.iq.dataverse.DatasetVersion;
import edu.harvard.iq.dataverse.Dataverse;
import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.DataverseUser;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.IndexServiceBean;
import edu.harvard.iq.dataverse.engine.command.Command;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.exception.CommandExecutionException;
import edu.harvard.iq.dataverse.engine.command.impl.DeleteDatasetCommand;
import edu.harvard.iq.dataverse.engine.command.impl.ReleaseDatasetCommand;
import edu.harvard.iq.dataverse.engine.command.impl.ReleaseDataverseCommand;
import edu.harvard.iq.dataverse.export.DDIExportServiceBean;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.inject.Inject;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.swordapp.server.AuthCredentials;
import org.swordapp.server.ContainerManager;
import org.swordapp.server.Deposit;
import org.swordapp.server.DepositReceipt;
import org.swordapp.server.SwordAuthException;
import org.swordapp.server.SwordConfiguration;
import org.swordapp.server.SwordError;
import org.swordapp.server.SwordServerException;
import org.swordapp.server.UriRegistry;

public class ContainerManagerImpl implements ContainerManager {

    private static final Logger logger = Logger.getLogger(ContainerManagerImpl.class.getCanonicalName());

    @EJB
    protected EjbDataverseEngine engineSvc;
    @EJB
    DataverseServiceBean dataverseService;
    @EJB
    DatasetServiceBean datasetService;
    @EJB
    IndexServiceBean indexService;
    @PersistenceContext(unitName = "VDCNet-ejbPU")
    EntityManager em;
    @EJB
    DDIExportServiceBean ddiService;
    @Inject
    SwordAuth swordAuth;
    @Inject
    UrlManager urlManager;
//    SwordConfigurationImpl swordConfiguration = new SwordConfigurationImpl();

    @Override
    public DepositReceipt getEntry(String uri, Map<String, String> map, AuthCredentials authCredentials, SwordConfiguration swordConfiguration) throws SwordServerException, SwordError, SwordAuthException {
        DataverseUser dataverseUser = swordAuth.auth(authCredentials);
        logger.fine("getEntry called with url: " + uri);
        urlManager.processUrl(uri);
        String targetType = urlManager.getTargetType();
        if (!targetType.isEmpty()) {
            logger.fine("operating on target type: " + urlManager.getTargetType());
            if ("study".equals(targetType)) {
                String globalId = urlManager.getTargetIdentifier();
                Dataset dataset = null;
                try {
                    dataset = datasetService.findByGlobalId(globalId);
                } catch (EJBException ex) {
                    throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Could not find study based on global id (" + globalId + ") in URL: " + uri);
                }
                if (dataset != null) {
                    Dataverse dvThatOwnsStudy = dataset.getOwner();
                    if (swordAuth.hasAccessToModifyDataverse(dataverseUser, dvThatOwnsStudy)) {
                        ReceiptGenerator receiptGenerator = new ReceiptGenerator();
                        String baseUrl = urlManager.getHostnamePlusBaseUrlPath(uri);
                        DepositReceipt depositReceipt = receiptGenerator.createReceipt(baseUrl, dataset);
                        if (depositReceipt != null) {
                            return depositReceipt;
                        } else {
                            throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Could not generate deposit receipt.");
                        }
                    } else {
                        throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "User " + dataverseUser.getUserName() + " is not authorized to retrieve entry for " + dataset.getGlobalId());
                    }
                } else {
                    throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Could not find study based on URL: " + uri);
                }
            } else {
                throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Unsupported target type (" + targetType + ") in URL: " + uri);
            }
        } else {
            throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Unable to determine target type from URL: " + uri);
        }
    }

    @Override
    public DepositReceipt replaceMetadata(String uri, Deposit deposit, AuthCredentials authCredentials, SwordConfiguration swordConfiguration) throws SwordError, SwordServerException, SwordAuthException {
        DataverseUser vdcUser = swordAuth.auth(authCredentials);
        logger.fine("replaceMetadata called with url: " + uri);
        urlManager.processUrl(uri);
        String targetType = urlManager.getTargetType();
        if (!targetType.isEmpty()) {
            logger.fine("operating on target type: " + urlManager.getTargetType());
            if ("dataverse".equals(targetType)) {
                throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Metadata replace of dataverse is not supported.");
            } else if ("study".equals(targetType)) {
                logger.fine("replacing metadata for study");
                logger.fine("deposit XML received by replaceMetadata():\n" + deposit.getSwordEntry());

                String globalId = urlManager.getTargetIdentifier();

//                EditStudyService editStudyService;
                Context ctx;
                try {
                    ctx = new InitialContext();
//                    editStudyService = (EditStudyService) ctx.lookup("java:comp/env/editStudy");
                } catch (NamingException ex) {
                    logger.info("problem looking up editStudyService");
                    throw new SwordServerException("problem looking up editStudyService");
                }
//                StudyServiceLocal studyService;
                try {
                    ctx = new InitialContext();
//                    studyService = (StudyServiceLocal) ctx.lookup("java:comp/env/studyService");
                } catch (NamingException ex) {
                    logger.info("problem looking up studyService");
                    throw new SwordServerException("problem looking up studyService");
                }
//                Study studyToLookup;
                Dataset studyToLookup = null;
                try {
                    /**
                     * @todo: why doesn't
                     * editStudyService.setStudyVersionByGlobalId(globalId)
                     * work?
                     */
//                    studyToLookup = studyService.getStudyByGlobalId(globalId);
                } catch (EJBException ex) {
                    throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Could not find study based on global id (" + globalId + ") in URL: " + uri);
                }
                if (studyToLookup != null) {
//                    StudyLock lockOnStudyLookedup = studyToLookup.getStudyLock();
//                    if (lockOnStudyLookedup != null) {
//                        String message = Util.getStudyLockMessage(lockOnStudyLookedup, studyToLookup.getGlobalId());
//                        throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, message);
//                    }
//                    editStudyService.setStudyVersion(studyToLookup.getId());
//                    Study studyToEdit = editStudyService.getStudyVersion().getStudy();
                    Dataset studyToEdit = null;
                    Dataverse dvThatOwnsStudy = studyToEdit.getOwner();
                    if (swordAuth.hasAccessToModifyDataverse(vdcUser, dvThatOwnsStudy)) {
                        Map<String, List<String>> dublinCore = deposit.getSwordEntry().getDublinCore();
                        if (dublinCore.get("title") == null || dublinCore.get("title").get(0) == null || dublinCore.get("title").get(0).isEmpty()) {
                            throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "title field is required");
                        }
                        if (dublinCore.get("date") != null) {
                            String date = dublinCore.get("date").get(0);
                            if (date != null) {
//                                boolean isValid = DateUtil.validateDate(date);
//                                if (!isValid) {
//                                    throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Invalid date: '" + date + "'.  Valid formats are YYYY-MM-DD, YYYY-MM, or YYYY.");
//                                }
                            }
                        }
                        String tmpDirectory = swordConfiguration.getTempDirectory();
                        if (tmpDirectory == null) {
                            throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Could not determine temp directory");
                        }
                        String uploadDirPath = tmpDirectory + File.separator + "import" + File.separator + studyToEdit.getId();
                        Long dcmiTermsHarvetsFormatId = new Long(4);
//                        HarvestFormatType dcmiTermsHarvestFormatType = em.find(HarvestFormatType.class, dcmiTermsHarvetsFormatId);
                        String xmlAtomEntry = deposit.getSwordEntry().getEntry().toString();
//                        File ddiFile = studyService.transformToDDI(xmlAtomEntry, dcmiTermsHarvestFormatType.getStylesheetFileName(), uploadDirPath);
                        // erase all metadata before running ddiService.mapDDI() because
                        // for multivalued fields (such as author) that function appends
                        // values rather than replacing them
                        /**
                         * @todo how best to replace metadata on a dataset in
                         * 4.0?
                         */
//                        studyToEdit.getLatestVersion().setMetadata(new Metadata());
//                        ddiService.mapDDI(ddiFile, studyToEdit.getLatestVersion(), true);
                        try {
//                            editStudyService.save(dvThatOwnsStudy.getId(), vdcUser.getId());
                        } catch (EJBException ex) {
                            // OptimisticLockException
//                            throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Unable to replace cataloging information for study " + studyToEdit.getGlobalId() + " (may be locked). Please try again later.");
                        }
                        ReceiptGenerator receiptGenerator = new ReceiptGenerator();
                        String baseUrl = urlManager.getHostnamePlusBaseUrlPath(uri);
                        DepositReceipt depositReceipt = receiptGenerator.createReceipt(baseUrl, studyToEdit);
                        return depositReceipt;
                    } else {
                        throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "User " + vdcUser.getUserName() + " is not authorized to modify dataverse " + dvThatOwnsStudy.getAlias());
                    }
                } else {
                    throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Could not find study based on global id (" + globalId + ") in URL: " + uri);
                }
            } else {
                throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Unknown target type specified on which to replace metadata: " + uri);
            }
        } else {
            throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "No target specified on which to replace metadata: " + uri);
        }
    }

    @Override
    public DepositReceipt replaceMetadataAndMediaResource(String string, Deposit dpst, AuthCredentials ac, SwordConfiguration sc) throws SwordError, SwordServerException, SwordAuthException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public DepositReceipt addMetadataAndResources(String string, Deposit dpst, AuthCredentials ac, SwordConfiguration sc) throws SwordError, SwordServerException, SwordAuthException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public DepositReceipt addMetadata(String string, Deposit dpst, AuthCredentials ac, SwordConfiguration sc) throws SwordError, SwordServerException, SwordAuthException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public DepositReceipt addResources(String string, Deposit dpst, AuthCredentials ac, SwordConfiguration sc) throws SwordError, SwordServerException, SwordAuthException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void deleteContainer(String uri, AuthCredentials authCredentials, SwordConfiguration sc) throws SwordError, SwordServerException, SwordAuthException {
//        swordConfiguration = (SwordConfigurationImpl) sc;
        DataverseUser vdcUser = swordAuth.auth(authCredentials);
        logger.fine("deleteContainer called with url: " + uri);
        urlManager.processUrl(uri);
        logger.fine("original url: " + urlManager.getOriginalUrl());
        if (!"edit".equals(urlManager.getServlet())) {
            throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "edit servlet expected, not " + urlManager.getServlet());
        }
        String targetType = urlManager.getTargetType();
        if (!targetType.isEmpty()) {
            logger.fine("operating on target type: " + urlManager.getTargetType());

//            StudyServiceLocal studyService;
            Context ctx;
            try {
                ctx = new InitialContext();
//                studyService = (StudyServiceLocal) ctx.lookup("java:comp/env/studyService");
            } catch (NamingException ex) {
                logger.info("problem looking up studyService");
                throw new SwordServerException("problem looking up studyService");
            }

            if ("dataverse".equals(targetType)) {
                /**
                 * @todo throw SWORD error recommending use of 4.0 "native" API
                 * to delete dataverses
                 */
//                String dvAlias = urlManager.getTargetIdentifier();
//                List<VDC> userVDCs = vdcService.getUserVDCs(vdcUser.getId());
//                VDC dataverseToEmpty = vdcService.findByAlias(dvAlias);
//                if (dataverseToEmpty != null) {
//                    if ("Admin".equals(vdcUser.getNetworkRole().getName())) {
//                        if (swordConfiguration.allowNetworkAdminDeleteAllStudies()) {
//
//                            /**
//                             * @todo: this is the deleteContainer method...
//                             * should move this to some sort of "emptyContainer"
//                             * method
//                             */
//                            // curl --insecure -s -X DELETE https://sword:sword@localhost:8181/dvn/api/data-deposit/v1/swordv2/edit/dataverse/sword 
//                            Collection<Study> studies = dataverseToEmpty.getOwnedStudies();
//                            for (Study study : studies) {
//                                logger.info("In dataverse " + dataverseToEmpty.getAlias() + " about to delete study id " + study.getId());
//                                studyService.deleteStudy(study.getId());
//                            }
//                        } else {
//                        throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "DELETE on a dataverse is not supported");
//                        }

//                    } else {
//                        throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Role was " + vdcUser.getNetworkRole().getName() + " but admin required.");
//                    }
//                } else {
//                    throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Couldn't find dataverse to delete from URL: " + uri);
//                }
            } else if ("study".equals(targetType)) {
                String globalId = urlManager.getTargetIdentifier();
                logger.info("globalId: " + globalId);
                if (globalId != null) {
                    Dataset study = null;
                    try {
                        study = datasetService.findByGlobalId(globalId);
                    } catch (EJBException ex) {
                        throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Could not find study based on global id (" + globalId + ") in URL: " + uri);
                    }
                    if (study != null) {
                        Dataverse dvThatOwnsStudy = study.getOwner();
                        if (!swordAuth.hasAccessToModifyDataverse(vdcUser, dvThatOwnsStudy)) {
                            throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "User " + vdcUser.getUserName() + " is not authorized to modify " + dvThatOwnsStudy.getAlias());
                        }
                        DatasetVersion.VersionState studyState = study.getLatestVersion().getVersionState();
                        if (study.isReleased()) {
                            if (studyState.equals(DatasetVersion.VersionState.DRAFT)) {
                                logger.info("destroying working copy version of study " + study.getGlobalId());
                                /**
                                 * @todo in DVN 3.x we had a convenient
                                 * destroyWorkingCopyVersion method. We have
                                 * DeleteDatasetCommand but we need
                                 * DeleteDatasetEditVersionCommand
                                 */
                                // studyService.destroyWorkingCopyVersion(study.getLatestVersion().getId());
                                throw SwordUtil.throwSpecialSwordErrorWithoutStackTrace(UriRegistry.ERROR_METHOD_NOT_ALLOWED, "This dataset has been published and subsequently a draft has been created. You are trying to delete that draft but this is not yet supported: https://redmine.hmdc.harvard.edu/issues/4032");
                            } else if (studyState.equals(DatasetVersion.VersionState.RELEASED)) {
//                                logger.fine("deaccessioning latest version of study " + study.getGlobalId());
//                                studyService.deaccessionStudy(study.getLatestVersion());
                                /**
                                 * @todo revisit this when deaccessioning is
                                 * available in
                                 * https://redmine.hmdc.harvard.edu/issues/4031
                                 */
                                throw SwordUtil.throwSpecialSwordErrorWithoutStackTrace(UriRegistry.ERROR_METHOD_NOT_ALLOWED, "Deaccessioning a dataset is not yet supported: https://redmine.hmdc.harvard.edu/issues/4031");
                            } else if (studyState.equals(DatasetVersion.VersionState.DEACCESSIONED)) {
                                throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Lastest version of dataset " + study.getGlobalId() + " has already been deaccessioned.");
                            } else if (studyState.equals(DatasetVersion.VersionState.ARCHIVED)) {
                                throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Lastest version of study " + study.getGlobalId() + " has been archived and can not be deleted or deaccessioned.");
                            } else if (studyState.equals(DatasetVersion.VersionState.IN_REVIEW)) {
                                throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Lastest version of study " + study.getGlobalId() + " is in review and can not be deleted or deaccessioned.");
                            } else {
                                throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Operation not valid for study " + study.getGlobalId() + " in state " + studyState);
                            }
                        } else {
                            // dataset has never been published, this is just a sanity check (should always be draft)
                            if (studyState.equals(DatasetVersion.VersionState.DRAFT)) {
                                try {
                                    engineSvc.submit(new DeleteDatasetCommand(study, vdcUser));
                                    /**
                                     * @todo re-index after deletion
                                     * https://redmine.hmdc.harvard.edu/issues/3544#note-21
                                     */
                                    logger.info("dataset deleted");
                                } catch (CommandExecutionException ex) {
                                    // internal error
                                    throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Can't delete dataset: " + ex.getMessage());
                                } catch (CommandException ex) {
                                    throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Can't delete dataset: " + ex.getMessage());
                                }
                            } else {
                                // we should never get here. throw an error explaining why
                                throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "dataset is in illegal state (not released yet not in draft)");
                            }
                        }
                    } else {
                        throw new SwordError(404);
                    }
                } else {
                    throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Could not find study to delete from URL: " + uri);
                }
            } else {
                throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Unsupported delete target in URL:" + uri);
            }
        } else {
            throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "No target for deletion specified");
        }
    }

    @Override
    public DepositReceipt useHeaders(String uri, Deposit deposit, AuthCredentials authCredentials, SwordConfiguration swordConfiguration) throws SwordError, SwordServerException, SwordAuthException {
        logger.fine("uri was " + uri);
        logger.fine("isInProgress:" + deposit.isInProgress());
        DataverseUser dataverseUser = swordAuth.auth(authCredentials);
        urlManager.processUrl(uri);
        String targetType = urlManager.getTargetType();
        if (!targetType.isEmpty()) {
            logger.fine("operating on target type: " + urlManager.getTargetType());
            if ("study".equals(targetType)) {
                String globalId = urlManager.getTargetIdentifier();
                if (globalId != null) {
                    Dataset dataset = null;
                    try {
                        dataset = datasetService.findByGlobalId(globalId);
                    } catch (EJBException ex) {
                        throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Could not find study based on global id (" + globalId + ") in URL: " + uri);
                    }
                    if (dataset != null) {
                        Dataverse dvThatOwnsStudy = dataset.getOwner();
                        if (swordAuth.hasAccessToModifyDataverse(dataverseUser, dvThatOwnsStudy)) {
                            if (!deposit.isInProgress()) {
                                /**
                                 * We are considering a draft version of a study
                                 * to be incomplete and are saying that sending
                                 * isInProgress=false means the study version is
                                 * complete and can be released.
                                 *
                                 * 9.2. Deposit Incomplete
                                 *
                                 * "If In-Progress is true, the server SHOULD
                                 * expect the client to provide further updates
                                 * to the item some undetermined time in the
                                 * future. Details of how this is implemented is
                                 * dependent on the server's purpose. For
                                 * example, a repository system may hold items
                                 * which are marked In-Progress in a workspace
                                 * until such time as a client request indicates
                                 * that the deposit is complete." --
                                 * http://swordapp.github.io/SWORDv2-Profile/SWORDProfile.html#continueddeposit_incomplete
                                 */
                                if (!dataset.getLatestVersion().getVersionState().equals(DatasetVersion.VersionState.RELEASED)) {
                                    Command<Dataset> cmd;
                                    try {
                                        /**
                                         * @todo We *could* attempt a minor
                                         * version bump first and if it fails go
                                         * ahead and re-try with a major version
                                         * bump. For simplicity, we decided to
                                         * always do a major version bump but
                                         * the @todo is to think about this a
                                         * bit more before we release 4.0.
                                         */
                                        boolean attemptMinorVersionBump = false;
                                        cmd = new ReleaseDatasetCommand(dataset, dataverseUser, attemptMinorVersionBump);
                                        dataset = engineSvc.submit(cmd);
                                    } catch (CommandException ex) {
                                        String msg = "Unable to publish dataset: " + ex;
                                        logger.severe(msg + ": " + ex.getMessage());
                                        throw SwordUtil.throwRegularSwordErrorWithoutStackTrace(msg);
                                    }
                                    ReceiptGenerator receiptGenerator = new ReceiptGenerator();
                                    String baseUrl = urlManager.getHostnamePlusBaseUrlPath(uri);
                                    DepositReceipt depositReceipt = receiptGenerator.createReceipt(baseUrl, dataset);
                                    return depositReceipt;
                                } else {
                                    throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Latest version of dataset " + globalId + " has already been released.");
                                }
                            } else {
                                throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Pass 'In-Progress: false' header to release a study.");
                            }
                        } else {
                            throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "User " + dataverseUser.getUserName() + " is not authorized to modify dataverse " + dvThatOwnsStudy.getAlias());
                        }
                    } else {
                        throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Could not find study using globalId " + globalId);
                    }
                } else {
                    throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Unable to find globalId for study in URL:" + uri);
                }
            } else if ("dataverse".equals(targetType)) {
                /**
                 * @todo confirm we want to allow dataverses to be released via
                 * SWORD. If so, document the curl example.
                 */
                String dvAlias = urlManager.getTargetIdentifier();
                if (dvAlias != null) {
                    Dataverse dvToRelease = dataverseService.findByAlias(dvAlias);
                    if (dvToRelease != null) {
                        if (!swordAuth.hasAccessToModifyDataverse(dataverseUser, dvToRelease)) {
                            throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "User " + dataverseUser.getUserName() + " is not authorized to modify dataverse " + dvAlias);
                        }
                        if (deposit.isInProgress()) {
                            throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Unpublishing a dataverse is not supported.");
                        }
                        ReleaseDataverseCommand cmd = new ReleaseDataverseCommand(dataverseUser, dvToRelease);
                        try {
                            engineSvc.submit(cmd);
                            ReceiptGenerator receiptGenerator = new ReceiptGenerator();
                            String baseUrl = urlManager.getHostnamePlusBaseUrlPath(uri);
                            DepositReceipt depositReceipt = receiptGenerator.createDataverseReceipt(baseUrl, dvToRelease);
                            return depositReceipt;
                        } catch (CommandException ex) {
                            throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Couldn't publish dataverse " + dvAlias + ": " + ex);
                        }
                    } else {
                        throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Could not find dataverse based on alias in URL: " + uri);
                    }
                } else {
                    throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Unable to find dataverse alias in URL: " + uri);
                }
            } else {
                throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "unsupported target type (" + targetType + ") in URL:" + uri);
            }
        } else {
            throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Target type missing from URL: " + uri);
        }
    }

    @Override
    public boolean isStatementRequest(String uri, Map<String, String> map, AuthCredentials authCredentials, SwordConfiguration swordConfiguration) throws SwordError, SwordServerException, SwordAuthException {
        DataverseUser vdcUser = swordAuth.auth(authCredentials);
        urlManager.processUrl(uri);
        String servlet = urlManager.getServlet();
        if (servlet != null) {
            if (servlet.equals("statement")) {
                return true;
            } else {
                return false;
            }
        } else {
            throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Unable to determine requested IRI from URL: " + uri);
        }

    }

}
