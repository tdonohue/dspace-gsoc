/*
 * WorkspaceItem.java
 *
 * Version: $Revision$
 *
 * Date: $Date$
 *
 * Copyright (c) 2001, Hewlett-Packard Company and Massachusetts
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

package org.dspace.content;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.authorize.ResourcePolicy;
//import org.dspace.content.DCDate;
//import org.dspace.content.DCValue;
//import org.dspace.content.Item;
//import org.dspace.content.Bitstream;
//import org.dspace.content.Collection;
//import org.dspace.content.InProgressSubmission;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.eperson.EPerson;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;
import org.dspace.workflow.WorkflowItem;

/**
 * Class representing an item in the process of being submitted by a user
 *
 * @author   Robert Tansley
 * @version  $Revision$
 */
public class WorkspaceItem implements InProgressSubmission
{
    /** log4j category */
    private static Logger log = Logger.getLogger(WorkspaceItem.class);

    /** The item this workspace object pertains to */
    private Item item;
    
    /** Our context */
    private Context ourContext;

    /** The table row corresponding to this workspace item */
    private TableRow wiRow;

    /** The collection the item is being submitted to */
    private Collection collection;


    /**
     * Construct a workspace item corresponding to the given database row
     *
     * @param context  the context this object exists in
     * @param row      the database row
     */
    WorkspaceItem(Context context, TableRow row)
        throws SQLException
    {
        ourContext = context;
        wiRow = row;

        item = Item.find(context, wiRow.getIntColumn("item_id"));
        collection = Collection.find(context,
            wiRow.getIntColumn("collection_id"));

        // Cache ourselves
        context.cache(this, row.getIntColumn("workspace_item_id"));
    }


    /**
     * Get a workspace item from the database.  The item, collection and
     * submitter are loaded into memory.
     *
     * @param  context  DSpace context object
     * @param  id       ID of the workspace item
     *   
     * @return  the workspace item, or null if the ID is invalid.
     */
    public static WorkspaceItem find(Context context, int id)
        throws SQLException
    {
        // First check the cache
        WorkspaceItem fromCache =
            (WorkspaceItem) context.fromCache(WorkspaceItem.class, id);
            
        if (fromCache != null)
        {
            return fromCache;
        }

        TableRow row = DatabaseManager.find(context,
            "workspaceitem",
            id);

        if (row == null)
        {
            if (log.isDebugEnabled())
            {
                log.debug(LogManager.getHeader(context,
                    "find_workspace_item",
                    "not_found,workspace_item_id=" + id));
            }

            return null;
        }
        else
        {
            if (log.isDebugEnabled())
            {
                log.debug(LogManager.getHeader(context,
                    "find_workspace_item",
                    "workspace_item_id=" + id));
            }

            return new WorkspaceItem(context, row);
        }
    }
    

    /**
     * Create a new workspace item, with a new ID.  An Item is also
     * created.  The submitter is the current user in the context.
     *
     * @param  context   DSpace context object
     * @param  coll      Collection being submitted to
     * @param  template  if <code>true</code>, the workspace item starts as a 
     *                   copy of the collection's template item
     *
     * @return  the newly created workspace item
     */
    public static WorkspaceItem create(Context context,
        Collection coll,
        boolean template)
        throws AuthorizeException, SQLException
    {
        // Check the user has permission to ADD to the collection
        AuthorizeManager.authorizeAction(context, coll, Constants.ADD);

        // Create an item
        Item i = Item.create(context);
        i.setSubmitter(context.getCurrentUser());

        // Now create the policies for the submitter and workflow
        // users to modify item and contents
        // contents = bitstreams, bundles

        // submitter can read item
        String mypolicy = "u"+context.getCurrentUser().getID();
        
        ResourcePolicy rp = ResourcePolicy.create(context);
        rp.setResourceType(Constants.ITEM);
        rp.setResourceID(i.getID());
        rp.setPolicy(mypolicy);
        rp.setActionID(Constants.READ);
        rp.update();
        
        // submitter can write item
        rp = ResourcePolicy.create(context);
        rp.setResourceType(Constants.ITEM);
        rp.setResourceID(i.getID());
        rp.setPolicy(mypolicy);
        rp.setActionID(Constants.WRITE);
        rp.update();
        
        // submitter can add to item
        rp = ResourcePolicy.create(context);
        rp.setResourceType(Constants.ITEM);
        rp.setResourceID(i.getID());
        rp.setPolicy(mypolicy);
        rp.setActionID(Constants.ADD);
        rp.update();

        // submitter can add to item
        rp = ResourcePolicy.create(context);
        rp.setResourceType(Constants.ITEM);
        rp.setResourceID(i.getID());
        rp.setPolicy(mypolicy);
        rp.setActionID(Constants.REMOVE);
        rp.update();
        
        // submitter can add to bundles
        rp = ResourcePolicy.create(context);
        rp.setResourceType(Constants.BUNDLE);
        rp.setContainerType(Constants.ITEM);
        rp.setContainerID(i.getID());
        rp.setPolicy(mypolicy);
        rp.setActionID(Constants.ADD);
        rp.update();

        // submitter can read bundles        
        rp = ResourcePolicy.create(context);
        rp.setResourceType(Constants.BUNDLE);
        rp.setContainerType(Constants.ITEM);
        rp.setContainerID(i.getID());
        rp.setPolicy(mypolicy);
        rp.setActionID(Constants.READ);
        rp.update();

        // submitter can write bundles
        rp = ResourcePolicy.create(context);
        rp.setResourceType(Constants.BUNDLE);
        rp.setContainerType(Constants.ITEM);
        rp.setContainerID(i.getID());
        rp.setPolicy(mypolicy);
        rp.setActionID(Constants.WRITE);
        rp.update();

        // submitter can delete bundles
        rp = ResourcePolicy.create(context);
        rp.setResourceType(Constants.BUNDLE);
        rp.setContainerType(Constants.ITEM);
        rp.setContainerID(i.getID());
        rp.setPolicy(mypolicy);
        rp.setActionID(Constants.DELETE);
        rp.update();
                
        // submitter can read bitstreams
        rp = ResourcePolicy.create(context);
        rp.setResourceType(Constants.BITSTREAM);
        rp.setContainerType(Constants.ITEM);
        rp.setContainerID(i.getID());
        rp.setPolicy(mypolicy);
        rp.setActionID(Constants.READ);
        rp.update();

        // submitter can write bitstreams
        rp = ResourcePolicy.create(context);
        rp.setResourceType(Constants.BITSTREAM);
        rp.setContainerType(Constants.ITEM);
        rp.setContainerID(i.getID());
        rp.setPolicy(mypolicy);
        rp.setActionID(Constants.WRITE);
        rp.update();
        
        // submitter can delete bitstreams
        rp = ResourcePolicy.create(context);
        rp.setResourceType(Constants.BITSTREAM);
        rp.setContainerType(Constants.ITEM);
        rp.setContainerID(i.getID());
        rp.setPolicy(mypolicy);
        rp.setActionID(Constants.DELETE);
        rp.update();


        // Copy template if appropriate
        Item templateItem = coll.getTemplateItem();

        if (template && templateItem != null)
        {
            DCValue[] dc = templateItem.getDC(Item.ANY, Item.ANY, Item.ANY);
            for (int n = 0; n < dc.length; n++)
            {
                i.addDC(dc[n].element,
                    dc[n].qualifier,
                    dc[n].language,
                    dc[n].value);
            }
        }
        else
        {
            // Just set default language
            i.addDC("language", "iso", null, 
                ConfigurationManager.getProperty("default.language"));
        }

        i.update();
        
        // Create the workspace item row
        TableRow row = DatabaseManager.create(context, "workspaceitem");

        row.setColumn("item_id", i.getID());
        row.setColumn("collection_id", coll.getID());

        log.info(LogManager.getHeader(context,
            "create_workspace_item",
            "workspace_item_id=" + row.getIntColumn("workspace_item_id") +
                "item_id=" + i.getID() + "collection_id=" + coll.getID()));

        DatabaseManager.update(context, row );

        return new WorkspaceItem(context, row);
    }



    /**
     * Get all workspace items for a particular e-person.  These are ordered by
     * workspace item ID, since this should likely keep them in the order in
     * which they were created.
     *
     * @param context   the context object
     * @param ep        the eperson
     *
     * @return  the corresponding workspace items
     */
    public static WorkspaceItem[] findByEPerson(Context context, EPerson ep)
        throws SQLException
    {
        List wsItems = new ArrayList();

        TableRowIterator tri = DatabaseManager.query(context,
            "workspaceitem",
            "SELECT workspaceitem.* FROM workspaceitem, item WHERE " +
                "workspaceitem.item_id=item.item_id AND " +
                "item.submitter_id=" + ep.getID() +
                " ORDER BY workspaceitem.workspace_item_id;");

        while (tri.hasNext())
        {
            TableRow row = tri.next();
            
            // Check the cache
            WorkspaceItem wi = (WorkspaceItem) context.fromCache(
                WorkspaceItem.class, row.getIntColumn("workspace_item_id"));
            
            if (wi == null)
            {
                wi = new WorkspaceItem(context, row);
            }
            
            wsItems.add(wi);
        }
        
        WorkspaceItem[] wsArray = new WorkspaceItem[wsItems.size()];
        wsArray = (WorkspaceItem[]) wsItems.toArray(wsArray);

        return wsArray;
    }


    /**
     * Get the internal ID of this workspace item
     *
     * @return the internal identifier
     */
    public int getID()
    {
        return wiRow.getIntColumn("workspace_item_id");
    }


    /**
     * Get the value of the stage reached column
     *
     * @return  the value of the stage reached column
     */
    public int getStageReached()
    {
        return wiRow.getIntColumn("stage_reached");
    }


    /**
     * Set the value of the stage reached column
     *
     * @param  v  the value of the stage reached column
     */
    public void setStageReached(int v)
    {
        wiRow.setColumn("stage_reached", v);
    }


//    /**
//     * Start the relevant workflow for this workspace item.  The entry in
//     * workspaceitem is removed, a workflow item is created, and the
//     * relevant workflow initiated.  If there is no workflow, i.e. the
//     * item goes straight into the archive, <code>null</code> is returned.
//     * <P>
//     * An accession date is assigned and a provenance description added.
//     * NO license is added.
//     *
//     * @return   the workflow item, or <code>null</code> if the item went
//     *           straight into the main archive
//     */
/*    public WorkflowItem startWorkflow()
        throws SQLException, AuthorizeException
    {
        // FIXME Check auth  (this code is being moved to WorkflowManager

        log.info(LogManager.getHeader(ourContext,
            "start_workflow",
            "workspace_item_id=" + getID() +
                "item_id=" + item.getID() +
                "collection_id=" + collection.getID()));

        // Set accession date
        DCDate d = DCDate.getCurrent();
        item.addDC("date", "accessioned", null, d.toString());

        // Get non-internal format bitstreams
        Bitstream[] bitstreams = item.getNonInternalBitstreams();

        String provMessage = "";

        // Create provenance description
        if( item.getSubmitter() != null )
        {
            provMessage = "Submitted by" + 
            item.getSubmitter().getFullName() + " (" +
            item.getSubmitter().getEmail() + ").  DSpace accession date:" +
            d.toString() + "\n Submission has " + bitstreams.length +
            " bitstreams:\n";
        }
        else // null submitter
        {
            provMessage = "Submitted by unknown (probably automated)" + 
                          "  DSpace accession date:" +
            d.toString() + "\n Submission has " + bitstreams.length +
            " bitstreams:\n";            
        }
        
        // Add sizes and checksums of bitstreams
        for (int j = 0; j < bitstreams.length; j++)
        {
            provMessage = provMessage + bitstreams[j].getName() + ": " +
                bitstreams[j].getSize() + " bytes, checksum: " +
                bitstreams[j].getChecksum() + " (" + 
                bitstreams[j].getChecksumAlgorithm() + ")\n";
        }
                    
        // Add message to the DC
        item.addDC("description", "provenance", "en", provMessage);

        return null;
    }
*/    

    /**
     * Update the workspace item, including the unarchived item.
     */
    public void update()
        throws SQLException, AuthorizeException
    {
        // Authorisation is checked by the item.update() method below
    
        log.info(LogManager.getHeader(ourContext,
            "update_workspace_item",
            "workspace_item_id=" + getID()));

        // Update the item
        item.update();
        
        // Update ourselves
        DatabaseManager.update(ourContext, wiRow);
    }


    /**
     * Delete the workspace item.  The entry in workspaceitem, the
     * unarchived item and its contents are all removed (multiple inclusion
     * notwithstanding.)
     */
    public void delete_all()
        throws SQLException, AuthorizeException, IOException
    {
        // Check authorisation.  We check permissions on the enclosed item.
        AuthorizeManager.authorizeAction(ourContext, item, Constants.DELETE);
    
        log.info(LogManager.getHeader(ourContext,
            "delete_workspace_item",
            "workspace_item_id=" + getID() +
                "item_id=" + item.getID() +
                "collection_id=" + collection.getID()));

        // Remove from cache
        ourContext.removeCached(this, getID());

        // Need to delete the workspaceitem row first since it refers
        // to item ID
        DatabaseManager.delete(ourContext, wiRow);
        
        // Delete item
        item.deleteWithContents();
    }


    /**
     * Delete just the workspace item, not the item.
     */
    public void delete()
        throws SQLException, AuthorizeException, IOException
    {
        // Check authorisation.  We check permissions on the enclosed item.
        AuthorizeManager.authorizeAction(ourContext, item, Constants.WRITE);
    
        log.info(LogManager.getHeader(ourContext,
            "delete_workspace_item",
            "workspace_item_id=" + getID() +
                "item_id=" + item.getID() +
                "collection_id=" + collection.getID()));

        // Remove from cache
        ourContext.removeCached(this, getID());

        // Need to delete the workspaceitem row first since it refers
        // to item ID
        DatabaseManager.delete(ourContext, wiRow);
    }


    // InProgressSubmission methods

    public Item getItem()
    {
        return item;
    }
    

    public Collection getCollection()
    {
        return collection;
    }

    
    public EPerson getSubmitter()
    {
        return item.getSubmitter();
    }
    

    public boolean hasMultipleFiles()
    {
        return wiRow.getBooleanColumn("multiple_files");
    }

    
    public void setMultipleFiles(boolean b)
    {
        wiRow.setColumn("multiple_files", b);
    }
    

    public boolean hasMultipleTitles()
    {
        return wiRow.getBooleanColumn("multiple_titles");
    }
    

    public void setMultipleTitles(boolean b)
    {
        wiRow.setColumn("multiple_titles", b);
    }


    public boolean isPublishedBefore()
    {
        return wiRow.getBooleanColumn("published_before");
    }

    
    public void setPublishedBefore(boolean b)
    {
        wiRow.setColumn("published_before", b);
    }
}
