/*

   Derby - Class com.pivotal.gemfirexd.internal.iapi.sql.execute.NoPutResultSet

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to you under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

/*
 * Changes for GemFireXD distributed data platform (some marked by "GemStone changes")
 *
 * Portions Copyright (c) 2010-2015 Pivotal Software, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package com.pivotal.gemfirexd.internal.iapi.sql.execute;


import java.util.ArrayList;

import com.gemstone.gemfire.internal.cache.TXState;
import com.pivotal.gemfirexd.internal.engine.store.GemFireContainer;
import com.pivotal.gemfirexd.internal.iapi.error.StandardException;
import com.pivotal.gemfirexd.internal.iapi.reference.SQLState;
import com.pivotal.gemfirexd.internal.iapi.services.io.FormatableBitSet;
import com.pivotal.gemfirexd.internal.iapi.services.loader.GeneratedMethod;
import com.pivotal.gemfirexd.internal.iapi.sql.ResultSet;
import com.pivotal.gemfirexd.internal.iapi.store.access.ConglomerateController;
import com.pivotal.gemfirexd.internal.iapi.store.access.RowLocationRetRowSource;
import com.pivotal.gemfirexd.internal.iapi.types.DataValueDescriptor;
import com.pivotal.gemfirexd.internal.iapi.types.RowLocation;
import com.pivotal.gemfirexd.internal.impl.sql.execute.PlanUtils;

/**
 * The NoPutResultSet interface is used to provide additional
 * operations on result sets that can be used in returning rows
 * up a ResultSet tree.
 * <p>
 * Since the ResulSet operations must also be supported by
 * NoPutResultSets, we extend that interface here as well.
 *
 */
public interface NoPutResultSet extends ResultSet, RowLocationRetRowSource 
// GemStone changes BEGIN
, com.pivotal.gemfirexd.internal.engine.sql.execute.UpdatableResultSet
// GemStone changes END
{
	// method names for use with SQLState.LANG_RESULT_SET_NOT_OPEN exception

	public	static	final	String	ABSOLUTE		=	"absolute";
	public	static	final	String	RELATIVE		=	"relative";
	public	static	final	String	FIRST			=	"first";
	public	static	final	String	NEXT			=	"next";
	public	static	final	String	LAST			=	"last";
	public	static	final	String	PREVIOUS		=	"previous";

	/**
	 * Mark the ResultSet as the topmost one in the ResultSet tree.
	 * Useful for closing down the ResultSet on an error.
	 */
	public void markAsTopResultSet();

	/**
	 * open a scan on the table. scan parameters are evaluated
	 * at each open, so there is probably some way of altering
	 * their values...
	 * <p>
	 * openCore() can only be called on a closed result
	 * set.  see reopenCore if you want to reuse an open
	 * result set.
	 * <p>
	 * For NoPutResultSet open() must only be called on
	 * the top ResultSet. Opening of NoPutResultSet's
	 * below the top result set are implemented by calling
	 * openCore.
	 *
	 * @exception StandardException thrown if cursor finished.
	 */
	public void openCore() throws StandardException;

	/**
     * reopen the scan.  behaves like openCore() but is 
	 * optimized where appropriate (e.g. where scanController
	 * has special logic for us).  
	 * <p>
	 * used by joiners
	 * <p>
	 * scan parameters are evaluated
     * at each open, so there is probably some way of altering
     * their values...  
	 *
	 * @exception StandardException thrown if cursor finished.
     */
	public void reopenCore() throws StandardException;

	/**
     * Return the requested values computed
     * from the next row (if any) for which
     * the restriction evaluates to true.
     * <p>
     * restriction and projection parameters
     * are evaluated for each row.
	 *
	 * @exception StandardException thrown on failure.
	 *
	 * @return the next row in the result
	 */
	public ExecRow	getNextRowCore() throws StandardException;

	/**
	 * Return the point of attachment for this subquery.
	 * (Only meaningful for Any and Once ResultSets, which can and will only
	 * be at the top of a ResultSet for a subquery.)
	 *
	 * @return int	Point of attachment (result set number) for this
	 *			    subquery.  (-1 if not a subquery - also Sanity violation)
	 */
	public int getPointOfAttachment();

	/**
	 * Return the isolation level of the scan in the result set.
	 * Only expected to be called for those ResultSets that
	 * contain a scan.
	 *
	 * @return The isolation level of the scan (in TransactionController constants).
	 */
	public int getScanIsolationLevel();

	/**
	 * Notify a NPRS that it is the source for the specified 
	 * TargetResultSet.  This is useful when doing bulk insert.
	 *
	 * @param trs	The TargetResultSet.
	 */
	public void setTargetResultSet(TargetResultSet trs);

	/**
	 * Set whether or not the NPRS need the row location when acting
	 * as a row source.  (The target result set determines this.)
	 */
	public void setNeedsRowLocation(boolean needsRowLocation);

	/**
	 * Get the estimated row count from this result set.
	 *
	 * @return	The estimated row count (as a double) from this result set.
	 */
	public double getEstimatedRowCount();

	/**
	 * Get the number of this ResultSet, which is guaranteed to be unique
	 * within a statement.
	 */
	public int resultSetNumber();

	/**
	 * Set the current row to the row passed in.
	 *
	 * @param row the new current row
	 *
	 */
	public void setCurrentRow(ExecRow row);

	/**
	 * Do we need to relock the row when going to the heap.
	 *
	 * @return Whether or not we need to relock the row when going to the heap.
	 */

	public boolean requiresRelocking();

// GemStone changes BEGIN

	/**
	 * Initialize local GemFire TXState.
	 */
	public com.gemstone.gemfire.internal.cache.TXState initLocalTXState();

	/**
	 * If the current RowLocation has been locked for read, then indicate
	 * that RowLocation has been qualified for final update and so the
	 * read lock, if any, can be upgraded to a write lock.
	 */
	public void upgradeReadLockToWrite(RowLocation rl,
	    com.pivotal.gemfirexd.internal.engine.store.GemFireContainer container)
	        throws StandardException;

	/**
	 * If the current RowLocation has been locked for read, then indicate
	 * that RowLocation has been qualified for final update and so the
	 * read lock, if any, can be upgraded to a write lock.
	 */
	public void updateRowLocationPostRead() throws StandardException;

	/**
	 * If the current RowLocation has been locked for read, then indicate
	 * that RowLocation has been filtered out of final results and so the
	 * read lock, if any, can be released (or any other cleanup required).
	 * @param localTXState TODO
	 */
	public void filteredRowLocationPostRead(TXState localTXState) throws StandardException;

	/**
	 * Returns true if the scan controller supports a flag to indicate that
	 * next scan key has been picked (e.g. an index key for a GROUP BY scan)
	 */
	public boolean supportsMoveToNextKey();

	
	

	/**
	 * Returns int indicating the current group ID of  the scan controller
	 *  scan key. This can be used to determine if transition to different
	 * scan key has happened.
	 *  Should be invoked only if
	 * {@link #supportsMoveToNextKey()} returns true, else will throw an
	 * {@link UnsupportedOperationException}
	 */
	public int getScanKeyGroupID();
// GemStone changes END

	/**
	 * Is this ResultSet or it's source result set for update
	 *
	 * @return Whether or not the result set is for update.
	 */
	public boolean isForUpdate();

	/* 
	 * New methods for supporting detectability of own changes for
	 * for updates and deletes when using ResultSets of type 
	 * TYPE_SCROLL_INSENSITIVE and concurrency CONCUR_UPDATABLE.
	 */
	
	/**
	 * Updates the resultSet's current row with it's new values after
	 * an update has been issued either using positioned update or
	 * JDBC's udpateRow method.
	 *
	 * @param row new values for the currentRow
	 *
	 * @exception StandardException thrown on failure.
	 */
	public void updateRow(ExecRow row) throws StandardException;
	
	/**
	 * Marks the resultSet's currentRow as deleted after a delete has been 
	 * issued by either by using positioned delete or JDBC's deleteRow
	 * method.
	 *
	 * @exception StandardException thrown on failure.
	 */
	public void markRowAsDeleted() throws StandardException;

	/**
	 * Positions the cursor in the specified rowLocation. Used for
	 * scrollable insensitive result sets in order to position the
	 * cursor back to a row that has already be visited.
	 * 
	 * @param rLoc row location of the current cursor row
	 *
	 * @exception StandardException thrown on failure to
	 *	get location from storage engine
	 *
	 */
	void positionScanAtRowLocation(RowLocation rLoc) 
		throws StandardException;
	

// GemStone changes BEGIN
  /*
   * Set Keys for NonCollocated Join 
   */
  public void setGfKeysForNCJoin(ArrayList<DataValueDescriptor> keys)
      throws StandardException;
  
  /*
   * NCJ usage
   */
  public void forceReOpenCore() throws StandardException;

  public void releasePreviousByteSource();

  /**
   * set the max sorted rows to be fetched as set by OFFSET/FETCH FIRST,NEXT
   * clauses
   */
  public void setMaxSortingLimit(long limit);

// GemStone changes END

  public PlanUtils.Context getNewPlanContext();
  
  public StringBuilder buildQueryPlan(StringBuilder builder, PlanUtils.Context context);

  /**
   * Fetch the (partial) row at the given location.
   * <p>
   * 
   * @param loc
   *          The "RowLocation" which describes the exact row to fetch from the
   *          table.
   * @param destRow
   *          The ExecRow to read the data into.
   * @param validColumns
   *          A description of which columns to return from row on the page into
   *          "destRow." destRow and validColumns work together to describe the
   *          row to be returned by the fetch - see RowUtil for description of
   *          how these three parameters work together to describe a fetched
   *          "row".
   * @param faultIn
   *          It true, then fault-in the value from disk, if required, to memory
   *          LRU list
   * @param container
   *          The container object being used this scan.
   * 
   * @return Returns RowLocation if fetch was successful,null if the record
   *         pointed at no longer represents a valid record. In case of Global
   *         Index the row location passed as parameter is returned with
   *         Location object embedded but in case of Local Index, the
   *         RegionEntry is returned
   * 
   * @exception StandardException
   *              Standard exception policy.
   * 
   * @see ConglomerateController#fetch(RowLocation, ExecRow, FormatableBitSet,
   *      boolean)
   */
  public RowLocation fetch(RowLocation loc, ExecRow destRow,
      FormatableBitSet validColumns, boolean faultIn,
      GemFireContainer container) throws StandardException;
}