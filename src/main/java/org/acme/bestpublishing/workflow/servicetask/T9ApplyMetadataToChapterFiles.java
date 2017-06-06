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

import org.acme.bestpublishing.constants.BestPubConstants;
import org.acme.bestpublishing.model.BestPubContentModel;
import org.activiti.engine.delegate.DelegateExecution;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.acme.bestpublishing.model.BestPubWorkflowModel.*;

/**
 * Loop the chapter folders and set the chapter metadata on all xhtml files.
 * Also set the Book Info for each file.
 * Classify Artwork and Supplementary files with Book Info too.
 *
 * @author martin.bergljung@marversolutions.org
 * @version 1.0
 * 
 */
public class T9ApplyMetadataToChapterFiles extends BestPubBaseJavaDelegate {
    private static final Logger LOG = LoggerFactory.getLogger(T9ApplyMetadataToChapterFiles.class);

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

        LOG.debug("T9 - Apply Metadata to chapter folders {}", processInfo);

        // Get the ISBN Folder Node Reference
        NodeRef isbnFolderNodeRef = new NodeRef((String)exec.getVariable(VAR_ISBN_FOLDER_NODEREF));

        // Loop through ISBN child folders and classify files in them
        Set<QName> aspects = new HashSet<>();
        aspects.add(BestPubContentModel.BookInfoAspect.QNAME);
        List<ChildAssociationRef> childrenList = getServiceRegistry().getNodeService().getChildAssocs(isbnFolderNodeRef);
        for (ChildAssociationRef child: childrenList) {
        	NodeRef childNodeRef = child.getChildRef();
        	String childNodeName = (String) getServiceRegistry().getNodeService().getProperty(
        	        childNodeRef, ContentModel.PROP_NAME);
			QName nodeTypeQName = getServiceRegistry().getNodeService().getType(childNodeRef);
        	if (BestPubContentModel.ChapterFolderType.QNAME.equals(nodeTypeQName)) {
				aspects.add(BestPubContentModel.ChapterInfoAspect.QNAME);
        		setFileTypeAndAspects(childNodeRef, aspects, BestPubContentModel.ChapterFileType.QNAME);
        	} else if (BestPubConstants.ARTWORK_FOLDER_NAME.equals(childNodeName)) {
				setFileTypeAndAspects(isbnFolderNodeRef, aspects, BestPubContentModel.ArtworkFileType.QNAME);
        	} else if (BestPubConstants.SUPPLEMENTARY_FOLDER_NAME.equals(childNodeName)) {
				setFileTypeAndAspects(isbnFolderNodeRef, aspects, BestPubContentModel.SupplementaryFileType.QNAME);
            } else if (BestPubConstants.STYLES_FOLDER_NAME.equals(childNodeName)) {
        	    // Classify style sheets as artwork files
                setFileTypeAndAspects(isbnFolderNodeRef, aspects, BestPubContentModel.ArtworkFileType.QNAME);
            } else {
                LOG.error("Skipping unkown ISBN folder {} {}", childNodeName, processInfo);
        	}
        }
        
        LOG.debug("Finished T9 - Apply Metadata to chapter folders {}", processInfo);

    }

    /**
     * Loop the list of files underneath a specific folder from
     * Sites/book-management/{year}/{isbn}, then change the type and apply aspects.
     * 
     * @param folderNodeRef the node reference for the folder which files we are classifying
     * @param aspects the aspects that should be copied from the parent folder
     * @param typeQName the type to set on the files
     */
    private void setFileTypeAndAspects(NodeRef folderNodeRef, Set<QName> aspects, QName typeQName) {
        List<ChildAssociationRef> folderFiles = getServiceRegistry().getNodeService().getChildAssocs(folderNodeRef);
    	for(ChildAssociationRef file: folderFiles) {
			NodeRef fileNodeRef = file.getChildRef();
			getServiceRegistry().getNodeService().setType(fileNodeRef, typeQName);
			alfrescoRepoUtilsService.copyAspects(folderNodeRef, fileNodeRef, aspects);
		}
	}
}
