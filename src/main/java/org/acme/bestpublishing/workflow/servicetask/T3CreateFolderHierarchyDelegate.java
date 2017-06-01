/*
 * All rights reserved.
 * Copyright (c) Ixxus Ltd 2014.
 */
package org.acme.bestpublishing.workflow.servicetask;

import org.acme.bestpublishing.services.AlfrescoWorkflowUtilsService;
import org.activiti.engine.delegate.DelegateExecution;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

import static org.acme.bestpublishing.model.BestPubWorkflowModel.*;

/**
 * Called from a workflow to execute the T3 service task for backlist book (already published book).
 * Creates a new ISBN folder under /Company Home/Sites/RHO with basic book metadata.
 * Then goes through all the chapters titles setup in either T1 (frontlist book) (manually by Editorial Assistant)
 * or in T2 (backlist book) (automatic from ZIP) and creates corresponding number of chapter folders with
 * basic chapter metadata.
 *
 * @author bblommers
 */
public class T3CreateFolderHierarchyDelegate extends BestPubBaseJavaDelegate {
    private static final Logger LOG = LoggerFactory.getLogger(T3CreateFolderHierarchyDelegate.class);

    /**
     * Interface Implementation
     */

    @Override
    public Logger getLog() { return LOG; }

    @Override
    public void execute(DelegateExecution exec) throws Exception {
        String isbn = getISBN(exec);
        if (StringUtils.isBlank(isbn)) {
            return;
        }

        String processInfo = getProcInfo(exec, isbn);

        LOG.debug("T3 - Creating chapter folder hierarchy {}", processInfo);

       String bookSubject = (String)exec.getVariable(VAR_BOOK_GENRE);
       String bookTitle = (String) exec.getVariable(VAR_BOOK_TITLE);
       int chapterCount = (Integer) exec.getVariable(VAR_CHAPTER_COUNT);
       Collection<String> chapterTitles = alfrescoWorkflowUtilsService.getWorkflowPropertyAsCollection(
               exec.getVariable(VAR_CHAPTER_LIST));
       
       boolean foldersCreated = bestPubUtilsService.createChapterFolder(
               processInfo, isbn, bookSubject, bookTitle, chapterCount, chapterTitles);

        setWorkflowVariable(exec, VAR_CHAPTER_FOLDER_HIERARCHY_EXISTS, foldersCreated, processInfo);

        LOG.debug("Finished T3 - Creating chapter folder hierarchy {}", processInfo);
    }

    public void setCreateChapterFolder(CreateChapterFolderService createChapterFolder) {
		this.createChapterFolder = createChapterFolder;
	}

	public void setWorkflowUtilsService(WorkflowUtilsService workflowUtilsService) {
		this.workflowUtilsService = workflowUtilsService;
	}
}