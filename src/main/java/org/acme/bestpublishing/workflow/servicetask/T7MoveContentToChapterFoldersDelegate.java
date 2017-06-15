/*
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package org.acme.bestpublishing.workflow.servicetask;

import org.activiti.engine.delegate.DelegateExecution;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.acme.bestpublishing.constants.BestPubConstants.*;
import static org.acme.bestpublishing.model.BestPubWorkflowModel.*;

/**
 * Called from a workflow to execute the T7 service task to move content from the incoming folder to the
 * book management site and the ISBN's chapter folders, artwork folder, supplementary folder, and styles folder.
 * <p/>
 * Assumption: All content for the ISBN has been imported successfully to Alfresco by the Content Ingestion component.
 *
 * @author martin.bergljung@marversolutions.org
 * @version 1.0
 */
public class T7MoveContentToChapterFoldersDelegate extends BestPubBaseJavaDelegate {
	private static final Logger LOG = LoggerFactory.getLogger(T7MoveContentToChapterFoldersDelegate.class);

	/**
	 * Interface Implementation
	 */

	@Override
	public Logger getLog() {
		return LOG;
	}

	@Override
	public void execute(DelegateExecution exec) throws Exception {
		String isbn = getISBN(exec);
		if (StringUtils.isBlank(isbn)) {
			return;
		}

		String processInfo = getProcInfo(exec, isbn);

		LOG.debug("T7 - Move content to chapter folders {}", processInfo);

		// Setup content matching as failed until we know every piece of content has been moved properly
		boolean contentChapterMatchingOk = false;
		setWorkflowVariable(exec, VAR_CONTENT_CHAPTER_MATCHING_OK, contentChapterMatchingOk, processInfo);

		// Get the /Company Home/Data Dictionary/BestPub/Incoming/Content/{ISBN} folder
		String sourceIsbnFolderPath = INCOMING_CONTENT_FOLDER_PATH + "/" + isbn;
		NodeRef sourceIsbnFolderNodeRef = alfrescoRepoUtilsService.getNodeByDisplayPath(sourceIsbnFolderPath);
		if (sourceIsbnFolderNodeRef == null) {
			LOG.error("Source folder [{}] not found, cannot move content {}", sourceIsbnFolderPath, processInfo);
			return;
		}

		// Get the /Company Home/Data Dictionary/BestPub/Incoming/Content/{ISBN}/Chapters folder
		NodeRef sourceChaptersFolderNodeRef = alfrescoRepoUtilsService.getChildByName(sourceIsbnFolderNodeRef,
				CHAPTERS_FOLDER_NAME);
		if (sourceChaptersFolderNodeRef == null) {
			LOG.error("Source folder [{}] not found, cannot move content {}", sourceIsbnFolderPath + "/" +
					CHAPTERS_FOLDER_NAME, processInfo);
			return;
		}

		// Get the /Company Home/Data Dictionary/BestPub/Incoming/Content/{ISBN}/Artwork folder
		NodeRef sourceArtworkFolderNodeRef = alfrescoRepoUtilsService.getChildByName(sourceIsbnFolderNodeRef,
				ARTWORK_FOLDER_NAME);
		if (sourceArtworkFolderNodeRef == null) {
			LOG.debug("Source folder [{}] not found {}", sourceIsbnFolderPath + "/" + ARTWORK_FOLDER_NAME, processInfo);
		}

		// Get the /Company Home/Data Dictionary/BestPub/Incoming/Content/{ISBN}/Supplementary folder
		NodeRef sourceSupplementaryFolderNodeRef = alfrescoRepoUtilsService.getChildByName(sourceIsbnFolderNodeRef,
				SUPPLEMENTARY_FOLDER_NAME);
		if (sourceSupplementaryFolderNodeRef == null) {
			LOG.error("Source folder [{}] not found, cannot move content {}", sourceIsbnFolderPath + "/" +
					SUPPLEMENTARY_FOLDER_NAME, processInfo);
			return;
		}

		// Get the /Company Home/Data Dictionary/BestPub/Incoming/Content/{ISBN}/Styles folder
		NodeRef sourceStylesFolderNodeRef = alfrescoRepoUtilsService.getChildByName(sourceIsbnFolderNodeRef,
				STYLES_FOLDER_NAME);
		if (sourceStylesFolderNodeRef == null) {
			LOG.error("Source folder [{}] not found, cannot move content {}", sourceIsbnFolderPath + "/" +
					STYLES_FOLDER_NAME, processInfo);
			return;
		}

		// Get the /Company Home/Sites/book-management/documentLibrary/{year}/{ISBN}
		// folder that we want to move the content to
		NodeRef destIsbnFolderNodeRef = bestPubUtilsService.getBaseFolderForIsbn(isbn);
		if (destIsbnFolderNodeRef == null) {
			LOG.error("Destination folder [{}] not found, cannot move content {}", 
					bestPubUtilsService.getBookManagementSiteDocLibPath() + "/{year}/" + isbn, processInfo);
			return;
		}

		// Move the package.opf file that contains the layout of the EPub book
		NodeRef epubPackageFileNodeRef = alfrescoRepoUtilsService.getChildByName(
				sourceIsbnFolderNodeRef, EPUB_PACKAGE_FILE_FILENAME);
		if (epubPackageFileNodeRef != null) {
			getServiceRegistry().getNodeService().moveNode(
					epubPackageFileNodeRef, destIsbnFolderNodeRef, ContentModel.ASSOC_CONTAINS,
					QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, EPUB_PACKAGE_FILE_FILENAME));
			LOG.debug("Moved [{}] to ISBN folder {}", EPUB_PACKAGE_FILE_FILENAME, processInfo);
		} else {
			LOG.error("EPub " + EPUB_PACKAGE_FILE_FILENAME +
							" file with book layout is missing in incoming content folder [{}] {}",	isbn, processInfo);
		}

		// Move chapter files from the /Chapters folder into related chapter folders
		List<ChildAssociationRef> chapterFilesAssociations =
				getServiceRegistry().getNodeService().getChildAssocs(sourceChaptersFolderNodeRef);
		List<String> errorList = new ArrayList<>();
		for (ChildAssociationRef chapterFileAssociation : chapterFilesAssociations) {
			NodeRef chapterFileNodeRef = chapterFileAssociation.getChildRef();
			String chapterFileName = getServiceRegistry().getNodeService().getProperty(
					chapterFileNodeRef, ContentModel.PROP_NAME).toString();

			// Get the destination folder and move file over
			NodeRef destinationChapterFolderNodeRef =
					bestPubUtilsService.getChapterDestinationFolder(chapterFileName, destIsbnFolderNodeRef);
			if (destinationChapterFolderNodeRef != null) {
				getServiceRegistry().getNodeService().moveNode(
						chapterFileNodeRef, destinationChapterFolderNodeRef, ContentModel.ASSOC_CONTAINS,
						QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, chapterFileName));
				LOG.debug("Moved [{}] to matching chapter folder {}", chapterFileName, processInfo);
			} else {
				errorList.add(chapterFileName);
				LOG.warn("Not Moving file [{}], chapter folder could not be found {}", chapterFileName, processInfo);
			}
		}

		// Move artwork files to /Company Home/Sites/book-management/documentLibrary/{year}/{ISBN}/Artwork folder
        if (sourceArtworkFolderNodeRef != null) {
            NodeRef destArtworkFolderNodeRef = alfrescoRepoUtilsService.getOrCreateFolder(
            		destIsbnFolderNodeRef, ARTWORK_FOLDER_NAME);
            List<ChildAssociationRef> artworkFileAssociations =
					getServiceRegistry().getNodeService().getChildAssocs(sourceArtworkFolderNodeRef);
            for (ChildAssociationRef artworkFileAssociation : artworkFileAssociations) {
                String artworkFilename = getServiceRegistry().getNodeService().getProperty(
                		artworkFileAssociation.getChildRef(), ContentModel.PROP_NAME).toString();
                getServiceRegistry().getNodeService().moveNode(
                		artworkFileAssociation.getChildRef(), destArtworkFolderNodeRef, ContentModel.ASSOC_CONTAINS,
                        QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, artworkFilename));
                LOG.debug("Moved artwork file [{}] {}", artworkFilename, processInfo);
            }
        } else {
            LOG.debug("Did not move artwork files, none found in content zip {}", processInfo);
        }

		// Move supplementary files to /Company Home/Sites/book-management/documentLibrary/{year}/{ISBN}/Supplementary folder
		NodeRef destSupplementaryFolderNodeRef = alfrescoRepoUtilsService.getOrCreateFolder(
				destIsbnFolderNodeRef, SUPPLEMENTARY_FOLDER_NAME);
		List<ChildAssociationRef> supplementaryFileAssociations =
				getServiceRegistry().getNodeService().getChildAssocs(sourceSupplementaryFolderNodeRef);
		for (ChildAssociationRef supplementraryFileAssociation : supplementaryFileAssociations) {
			String supplementraryFileName = getServiceRegistry().getNodeService().getProperty(
					supplementraryFileAssociation.getChildRef(), ContentModel.PROP_NAME).toString();
			getServiceRegistry().getNodeService().moveNode(
					supplementraryFileAssociation.getChildRef(), destSupplementaryFolderNodeRef, ContentModel.ASSOC_CONTAINS,
					QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, supplementraryFileName));
			LOG.debug("Moved supplementary file [{}] {}", supplementraryFileName, processInfo);
		}

		// Move style files to /Company Home/Sites/book-management/documentLibrary/{year}/{ISBN}/Styles folder
		NodeRef destStylesFolderNodeRef = alfrescoRepoUtilsService.getOrCreateFolder(
				destIsbnFolderNodeRef, STYLES_FOLDER_NAME);
		List<ChildAssociationRef> styleFileAssociations =
				getServiceRegistry().getNodeService().getChildAssocs(sourceStylesFolderNodeRef);
		for (ChildAssociationRef styleFileAssociation : styleFileAssociations) {
			String styleFileName = getServiceRegistry().getNodeService().getProperty(
					styleFileAssociation.getChildRef(), ContentModel.PROP_NAME).toString();
			getServiceRegistry().getNodeService().moveNode(
					styleFileAssociation.getChildRef(), destStylesFolderNodeRef, ContentModel.ASSOC_CONTAINS,
					QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, styleFileName));
			LOG.debug("Moved style file [{}] {}", styleFileName, processInfo);
		}

		// If all chapter files are matched to chapter folders set chapter matching as successful,
		// then delete all folders in original ISBN folder.
		// Leave the ISBN folder so it can be detected as already published by the Content Ingestion component.
		if (errorList.isEmpty()) {
			contentChapterMatchingOk = true;
			getServiceRegistry().getNodeService().deleteNode(sourceChaptersFolderNodeRef);
			LOG.debug("Removed source [/{}] folder {}", CHAPTERS_FOLDER_NAME, processInfo);
            if (sourceArtworkFolderNodeRef != null) {
                getServiceRegistry().getNodeService().deleteNode(sourceArtworkFolderNodeRef);
                LOG.debug("Removed source [/{}] folder {}", ARTWORK_FOLDER_NAME, processInfo);
            }
			getServiceRegistry().getNodeService().deleteNode(sourceSupplementaryFolderNodeRef);
			LOG.debug("Removed source [/{}] folder {}", SUPPLEMENTARY_FOLDER_NAME, processInfo);
			getServiceRegistry().getNodeService().deleteNode(sourceStylesFolderNodeRef);
			LOG.debug("Removed source [/{}] folder {}", STYLES_FOLDER_NAME, processInfo);
		} else {
			// Add name of the chapter files that don't have matching folder
			// to "contentChapterMatchingErrorList" variable
			setWorkflowVariable(exec, VAR_CONTENT_CHAPTER_MATCHING_ERROR_LIST, errorList, processInfo);
		}

		setWorkflowVariable(exec, VAR_CONTENT_CHAPTER_MATCHING_OK, contentChapterMatchingOk, processInfo);

		LOG.debug("Finished T7 - Move content to chapter folders {}", processInfo);
	}

}
