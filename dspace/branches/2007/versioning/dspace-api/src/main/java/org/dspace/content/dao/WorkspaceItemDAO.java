/*
 * WorkspaceItemDAO.java
 *
 * Version: $Revision: 1727 $
 *
 * Date: $Date: 2007-01-19 10:52:10 +0000 (Fri, 19 Jan 2007) $
 *
 * Copyright (c) 2002-2005, Hewlett-Packard Company and Massachusetts
 * Institute of Technology.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * - Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * - Neither the name of the Hewlett-Packard Company nor the name of the
 * Massachusetts Institute of Technology nor the names of their
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDERS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
 * DAMAGE.
 */
package org.dspace.content.dao;

import java.util.List;
import java.util.UUID;

import org.apache.log4j.Logger;

import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.content.Collection;
import org.dspace.content.DCValue;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.storage.dao.CRUD;
import org.dspace.workflow.WorkflowItem;

/**
 * @author James Rutherford
 */
public abstract class WorkspaceItemDAO extends ContentDAO
    implements CRUD<WorkspaceItem>
{
    protected Logger log = Logger.getLogger(WorkspaceItemDAO.class);

    protected Context context;
    protected ItemDAO itemDAO;

    public abstract WorkspaceItem create() throws AuthorizeException;

    /**
     * Create a new workspace item, with a new ID. An Item is also created. The
     * submitter is the current user in the context.
     */
    public abstract WorkspaceItem create(Collection collection,
            boolean template) throws AuthorizeException;

    /**
     * Create a WorkspaceItem from a WorkflowItem. This is for returning Items
     * to a user without submitting it to the archive.
     */
    public abstract WorkspaceItem create(WorkflowItem wfi)
        throws AuthorizeException;

    // FIXME: This should be called something else, but I can't think of
    // anything suitable. The reason this can't go in create() is because we
    // need access to the item that was created, but we can't reach into the
    // subclass to get it (storing it as a protected member variable would be
    // even more filthy).
    protected final WorkspaceItem create(WorkspaceItem wsi,
            Collection collection, boolean template)
        throws AuthorizeException
    {
        // Check the user has permission to ADD to the collection
        AuthorizeManager.authorizeAction(context, collection, Constants.ADD);

        EPerson currentUser = context.getCurrentUser();

        // Create an item
        ItemDAO itemDAO = ItemDAOFactory.getInstance(context);
        Item item = itemDAO.create();
        item.setSubmitter(currentUser);

        // Now create the policies for the submitter and workflow users to
        // modify item and contents (contents = bitstreams, bundles)
        // FIXME: hardcoded workflow steps
        Group stepGroups[] = {
            collection.getWorkflowGroup(1),
            collection.getWorkflowGroup(2),
            collection.getWorkflowGroup(3)
        };

        int actions[] = {
            Constants.READ,
            Constants.WRITE,
            Constants.ADD,
            Constants.REMOVE
        };

        // Give read, write, add, and remove privileges to the current user
        for (int action : actions)
        {
            AuthorizeManager.addPolicy(context, item, action, currentUser);
        }

        // Give read, write, add, and remove privileges to the various
        // workflow groups (if any).
        for (Group stepGroup : stepGroups)
        {
            if (stepGroup != null)
            {
                for (int action : actions)
                {
                    AuthorizeManager.addPolicy(context, item, action,
                            stepGroup);
                }
            }
        }

        // Copy template if appropriate
        Item templateItem = collection.getTemplateItem();

        if (template && (templateItem != null))
        {
            DCValue[] md = templateItem.getMetadata(
                    Item.ANY, Item.ANY, Item.ANY, Item.ANY);

            for (int n = 0; n < md.length; n++)
            {
                item.addMetadata(md[n].schema, md[n].element, md[n].qualifier,
                        md[n].language, md[n].value);
            }
        }

        itemDAO.update(item);

        wsi.setItem(item);
        wsi.setCollection(collection);
        update(wsi);

        log.info(LogManager.getHeader(context, "create_workspace_item",
                "workspace_item_id=" + wsi.getID() +
                "item_id=" + item.getID() +
                "collection_id=" + collection.getID()));

        return wsi;
    }

    public WorkspaceItem create(WorkspaceItem wsi, WorkflowItem wfi)
        throws AuthorizeException
    {
        wsi.setItem(wfi.getItem());
        wsi.setCollection(wfi.getCollection());
        wsi.setMultipleFiles(wfi.hasMultipleFiles());
        wsi.setMultipleTitles(wfi.hasMultipleTitles());
        wsi.setPublishedBefore(wfi.isPublishedBefore());
        update(wsi);

        return wsi;
    }
    
    /**
     * Create a new workspace item, with a new ID. This is FROM an existing Item.
     * This is to allow for new versions of Items to be tucked back into Workspaces.
     * 
     * @param item Item
     * @return WorkspaceItem The new workspace item.
     */
    public WorkspaceItem create(Item item)
    throws AuthorizeException
    {
		WorkspaceItem wsi = this.create(item.getOwningCollection(),false);
		return create(wsi, item, item.getOwningCollection());
		
    }

    /**
	 * Create a new workspace item, with a new ID. This is FROM an existing
	 * Item. This is to allow for new versions of Items to be tucked back into
	 * Workspaces.
	 * 
	 * @param item
	 *            Item
	 * @return WorkspaceItem The new workspace item.
	 */
    protected WorkspaceItem create(WorkspaceItem wsi, Item item, Collection collection)
        throws AuthorizeException
    {
        // Check the user has permission to ADD to the collection
        AuthorizeManager.authorizeAction(context, collection, Constants.ADD);

        EPerson currentUser = context.getCurrentUser();

        // Create an item
        ItemDAO itemDAO = ItemDAOFactory.getInstance(context);
        item.setSubmitter(currentUser);

        // Now create the policies for the submitter and workflow users to
        // modify item and contents (contents = bitstreams, bundles)
        // FIXME: hardcoded workflow steps
        Group stepGroups[] = {
            collection.getWorkflowGroup(1),
            collection.getWorkflowGroup(2),
            collection.getWorkflowGroup(3)
        };

        int actions[] = {
            Constants.READ,
            Constants.WRITE,
            Constants.ADD,
            Constants.REMOVE
        };

        // Give read, write, add, and remove privileges to the current user
        for (int action : actions)
        {
            AuthorizeManager.addPolicy(context, item, action, currentUser);
        }

        // Give read, write, add, and remove privileges to the various
        // workflow groups (if any).
        for (Group stepGroup : stepGroups)
        {
            if (stepGroup != null)
            {
                for (int action : actions)
                {
                    AuthorizeManager.addPolicy(context, item, action,
                            stepGroup);
                }
            }
        }

        itemDAO.update(item);

        wsi.setItem(item);
        wsi.setCollection(collection);
        update(wsi);

        log.info(LogManager.getHeader(context, "create_workspace_item",
                "workspace_item_id=" + wsi.getID() +
                "item_id=" + item.getID() +
                "collection_id=" + collection.getID()));

        return wsi;
    }

    public WorkspaceItem retrieve(int id)
    {
        return (WorkspaceItem) context.fromCache(WorkspaceItem.class, id);
    }

    public WorkspaceItem retrieve(UUID uuid)
    {
        return null;
    }

    /**
     * Update the workspace item, including the unarchived item.
     */
    public void update(WorkspaceItem wsi) throws AuthorizeException
    {
        // Authorisation is checked by the item update
        log.info(LogManager.getHeader(context, "update_workspace_item",
                "workspace_item_id=" + wsi.getID()));

        itemDAO.update(wsi.getItem());
    }

    public void delete(int id) throws AuthorizeException
    {
        WorkspaceItem wsi = retrieve(id);
        update(wsi); // Sync in-memory object before removal

        context.removeCached(wsi, id);

        // Check authorisation. We check permissions on the enclosed item.
        AuthorizeManager.authorizeAction(context, wsi.getItem(),
                Constants.WRITE);

        log.info(LogManager.getHeader(context, "delete_workspace_item",
                    "workspace_item_id=" + id +
                    "item_id=" + wsi.getItem().getID() +
                    "collection_id=" + wsi.getCollection().getID()));
    }

    /**
     * Delete the workspace item. The entry in workspaceitem, the unarchived
     * item and its contents are all removed (multiple inclusion
     * notwithstanding.)
     */
    public void deleteAll(int id) throws AuthorizeException
    {
        WorkspaceItem wsi = retrieve(id);
        update(wsi); // Sync in-memory object before removal
        Item item = wsi.getItem();
        Collection collection = wsi.getCollection();

        /*
         * Authorisation is a special case. The submitter won't have REMOVE
         * permission on the collection, so our policy is this: Only the
         * original submitter or an administrator can delete a workspace item.
         */
        if (!AuthorizeManager.isAdmin(context) &&
                ((context.getCurrentUser() == null) ||
                 (context.getCurrentUser().getID() !=
                  item.getSubmitter().getID())))
        {
            // Not an admit, not the submitter
            throw new AuthorizeException("Must be an administrator or the "
                    + "original submitter to delete a workspace item");
        }

        log.info(LogManager.getHeader(context, "delete_workspace_item",
                "workspace_item_id=" + wsi.getID() +
                "item_id=" + item.getID() +
                "collection_id=" + collection.getID()));

        delete(id);
        itemDAO.delete(wsi.getItem().getID());
    }

    public abstract List<WorkspaceItem> getWorkspaceItems();
    public abstract List<WorkspaceItem> getWorkspaceItems(EPerson eperson);
    public abstract List<WorkspaceItem> getWorkspaceItems(Collection collection);

    /**
     * FIXME: I don't like doing this, but it's the least filthy way I can
     * think of achieving what I want.
     */
    public abstract <T extends WorkspaceItem> void populate(T t);
}
