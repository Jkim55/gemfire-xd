/*

   Derby - Class com.pivotal.gemfirexd.internal.impl.jdbc.authentication.LDAPAuthenticationSchemeImpl

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

package com.pivotal.gemfirexd.internal.impl.jdbc.authentication;



import com.pivotal.gemfirexd.Constants;
import com.pivotal.gemfirexd.Property;
import com.pivotal.gemfirexd.auth.callback.CredentialInitializer;
import com.pivotal.gemfirexd.auth.callback.UserAuthenticator;
import com.pivotal.gemfirexd.internal.engine.GfxdConstants;
import com.pivotal.gemfirexd.internal.engine.distributed.utils.GemFireXDUtils;
import com.pivotal.gemfirexd.internal.engine.distributed.utils.SecurityUtils;
import com.pivotal.gemfirexd.internal.iapi.error.StandardException;
import com.pivotal.gemfirexd.internal.iapi.jdbc.AuthenticationService;
import com.pivotal.gemfirexd.internal.iapi.reference.MessageId;
import com.pivotal.gemfirexd.internal.iapi.services.i18n.MessageService;
import com.pivotal.gemfirexd.internal.iapi.services.monitor.Monitor;
import com.pivotal.gemfirexd.internal.iapi.services.sanity.SanityManager;
import com.pivotal.gemfirexd.internal.iapi.util.StringUtil;

import javax.naming.*;
import javax.naming.directory.*;

import java.util.Properties;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.sql.SQLException;

/**
 * This is the GemFireXD LDAP authentication scheme implementation.
 *
 * JNDI system/environment properties can be set at the database
 * level as database properties. They will be picked-up and set in
 * the JNDI initial context if any are found.
 *
 * We do connect first to the LDAP server in order to retrieve the
 * user's distinguished name (DN) and then we reconnect and try to
 * authenticate with the user's DN and passed-in password.
 *
 * In 2.0 release, we first connect to do a search (user full DN lookup).
 * This initial lookup can be done through anonymous bind or using special
 * LDAP search credentials that the user may have configured on the
 * LDAP settings for the database or the system.
 * It is a typical operation with LDAP servers where sometimes it is
 * hard to tell/guess in advance a users' full DN's.
 *
 * NOTE: In a future release, we will cache/maintain the user DN within
 * the the Derby database or system to avoid the initial lookup.
 * Also note that LDAP search/retrieval operations are usually very fast.
 *
 * The default LDAP url is ldap:/// (ldap://localhost:389/)
 *
 * @see com.pivotal.gemfirexd.auth.callback.UserAuthenticator 
 *
 */

public final class LDAPAuthenticationSchemeImpl
extends JNDIAuthenticationSchemeBase
// GemStone changes BEGIN
implements CredentialInitializer
//GemStone changes END
{
	private static final String dfltLDAPURL = "ldap://";

	private String searchBaseDN;

	private String leftSearchFilter; // stick in uid in between
	private String rightSearchFilter;
	private boolean useUserPropertyAsDN;

	// Search Auth DN & Password if anonymous search not allowed
	private String searchAuthDN;
	private String searchAuthPW;
	// we only want the user's full DN in return
// GemStone changes BEGIN
	private final FileOutputStream traceOut;
	private static final String[] attrDN = {"dn", "distinguishedName"};
	/* (original code)
	private static final String[] attrDN = {"dn"};								;
	*/
// GemStone changes END

	public LDAPAuthenticationSchemeImpl(JNDIAuthenticationService as, Properties dbProperties) {

		super(as, dbProperties);
// GemStone changes BEGIN
		this.traceOut = (FileOutputStream)this.initDirContextEnv.get(
		    "com.sun.naming.ldap.trace.ber");
// GemStone changes END
	}

	/**
	 * Authenticate the passed-in user's credentials.
	 *
	 * We authenticate against a LDAP Server.
	 *
	 *
	 * @param userName		The user's name used to connect to JBMS system
	 * @param userPassword	The user's password used to connect to JBMS system
	 * @param databaseName	The database which the user wants to connect to.
	 * @param info			Additional jdbc connection info.
	 */
	public String /* GemStone changes boolean */	authenticateUser(String userName,
								 String userPassword,
								 String databaseName,
								 Properties info
								)
								throws java.sql.SQLException
	{
		if ( ((userName == null) || (userName.length() == 0)) ||
			 ((userPassword == null) || (userPassword.length() == 0)) )
		{
			// We don't tolerate 'guest' user for now as well as
			// null password.
			// If a null password is passed upon authenticating a user
			// through LDAP, then the LDAP server might consider this as
			// anonymous bind and therefore no authentication will be done
			// at all.
			return ((userName == null) || (userName.length() == 0))
			    ? "Empty user name" : "Empty password";
		}


		Exception e;
		DirContext ctx = null;
		try {
			Properties env = (Properties) initDirContextEnv.clone();
			String userDN = null;
			//
			// Retrieve the user's DN (Distinguished Name)
			// If we're asked to look it up locally, do it first
			// and if we don't find it, we go against the LDAP
			// server for a look-up (search)
			//
			if (useUserPropertyAsDN) {
				userDN =
					authenticationService.getProperty(
						com.pivotal.gemfirexd.internal.iapi.reference.Property.USER_PROPERTY_PREFIX);
	                        //SQLF:BC
				if (userDN == null) {
                                  userDN =
                                    authenticationService.getProperty(
                                            com.pivotal.gemfirexd.internal.iapi.reference.Property.SQLF_USER_PROPERTY_PREFIX);
				}
			}

			if (userDN == (String) null) {
				userDN = getDNFromUID(userName);
			}
		
// GemStone changes BEGIN
			if (GemFireXDUtils.TraceAuthentication) {
			  SanityManager.DEBUG_PRINT(AuthenticationServiceBase
			      .AuthenticationTrace, "User DN = [" + userDN + ']');
			  GemFireXDUtils.dumpProperties(env, "LDAP connection "
			      + "authentication for uid=" + userName + " with ",
			      AuthenticationServiceBase.AuthenticationTrace,
			      GemFireXDUtils.TraceAuthentication, null);
			}
			/* (original code)
			if (SanityManager.DEBUG)
			{
				if (SanityManager.DEBUG_ON(
						AuthenticationServiceBase.AuthenticationTrace)) {
					SanityManager.DEBUG(AuthenticationServiceBase.AuthenticationTrace,
					"User DN = ["+ userDN+"]\n");
				}
			}
			*/
// GemStone changes END

			env.put(Context.SECURITY_PRINCIPAL, userDN);
			env.put(Context.SECURITY_CREDENTIALS, userPassword);
			
			// Connect & authenticate (bind) to the LDAP server now

			// it is happening right here

                        ctx =   privInitialDirContext(env);
          
            

			// if the above was successfull, then username and
			// password must be correct
			return null;

		} catch (javax.naming.AuthenticationException jndiae) {
			return jndiae.toString();

		} catch (javax.naming.NameNotFoundException jndinnfe) {
			return jndinnfe.toString();

		} catch (javax.naming.NamingException jndine) {
			e = jndine;
		}
// GemStone changes BEGIN
		finally {
                  if (ctx != null) {
                    try {
                      ctx.close();
                    } catch (NamingException nme) {
                      e = nme;
                      if (SanityManager.DEBUG) {
                        if (GemFireXDUtils.TraceAuthentication) {
                          SanityManager.DEBUG_PRINT("warning:"
                              + GfxdConstants.TRACE_AUTHENTICATION,
                              "Exception occurred while closing the context acquired.", e);
                        }
                      }
                      throw getLoginSQLException(e);
                    }
		  }
		  // flush the FileOutputStream
		  if (this.traceOut != null) {
		    try {
		      this.traceOut.flush();
		    } catch (IOException ioe) {
		      // ignore
		    }
		  }
		}
// GemStone changes END

		throw getLoginSQLException(e);
	}

	

    /**
     * Call new InitialDirContext in a privilege block
     * @param env environment used to create the initial DirContext. Null indicates an empty environment.
     * @return an initial DirContext using the supplied environment. 
     */
    private DirContext privInitialDirContext(final Properties env) throws NamingException {
        try {
            return ((InitialDirContext)AccessController.doPrivileged(
                    new PrivilegedExceptionAction() {
                        public Object run() throws SecurityException, NamingException {
                            return new InitialDirContext(env);
                    }
                }));
    } catch (PrivilegedActionException pae) {
            Exception e = pae.getException();
       
            if (e instanceof NamingException)
                    throw (NamingException)e;
            else
                throw (SecurityException)e;
        }   
   
    }   

    /**
	 * This method basically tests and sets default/expected JNDI properties
	 * for the JNDI provider scheme (here it is LDAP).
	 *
	 **/
	protected void setJNDIProviderProperties()
	{

		// check if we're told to use a different initial context factory
		if (initDirContextEnv.getProperty(
							Context.INITIAL_CONTEXT_FACTORY) == (String) null)
		{
			initDirContextEnv.put(Context.INITIAL_CONTEXT_FACTORY,
									  "com.sun.jndi.ldap.LdapCtxFactory");
		}

		// retrieve LDAP server name/port# and construct LDAP url
		if (initDirContextEnv.getProperty(
							Context.PROVIDER_URL) == (String) null)
		{
			// Now we construct the LDAP url and expect to find the LDAP Server
			// name.
			//
			String ldapServer = authenticationService.getProperty(
						com.pivotal.gemfirexd.Property.AUTH_LDAP_SERVER);

			if (ldapServer == (String) null) {

				// we do expect a LDAP Server name to be configured
				Monitor.logTextMessage(
					MessageId.AUTH_NO_LDAP_HOST_MENTIONED,
						 com.pivotal.gemfirexd.Property.AUTH_LDAP_SERVER);

				this.providerURL = dfltLDAPURL + "/";

			} else {

				if (ldapServer.startsWith(dfltLDAPURL) || ldapServer.startsWith("ldaps://") )
					this.providerURL = ldapServer;
				else if (ldapServer.startsWith("//"))
					this.providerURL = "ldap:" + ldapServer;
				else
					this.providerURL = dfltLDAPURL + ldapServer;
			}
			initDirContextEnv.put(Context.PROVIDER_URL, providerURL);
		}

		// check if we should we use a particular authentication method
		// we assume the ldap server supports this authentication method
		// (Netscape DS 3.1.1 does not support CRAM-MD5 for instance)
		if (initDirContextEnv.getProperty(
							Context.SECURITY_AUTHENTICATION) == (String) null)
		{
			// set the default to be clear userName/Password as not of all the
			// LDAP server(s) support CRAM-MD5 (especially ldap v2 ones)
			// Netscape Directory Server 3.1.1 does not support CRAM-MD5
			// (told by Sun JNDI engineering). Netscape DS 4.0 allows SASL
			// plug-ins to be installed and that can be used as authentication
			// method.
			//
			initDirContextEnv.put(Context.SECURITY_AUTHENTICATION,
									  "simple"
									  );
		}

		// Retrieve and set the search base (root) DN to use on the ldap
		// server.
		String ldapSearchBase =
					authenticationService.getProperty(Property.AUTH_LDAP_SEARCH_BASE);
		if (ldapSearchBase != (String) null)
			this.searchBaseDN = ldapSearchBase;
		else
			this.searchBaseDN = "";

		// retrieve principal and credentials for the search bind as the
		// user may not want to allow anonymous binds (for searches)
		this.searchAuthDN =
					authenticationService.getProperty(Property.AUTH_LDAP_SEARCH_DN);
		this.searchAuthPW =
					authenticationService.getProperty(Property.AUTH_LDAP_SEARCH_PW);

		//
		// Construct the LDAP search filter:
		//
		// If we were told to use a special search filter, we do so;
		// otherwise we use our default search filter.
		// The user may have set the search filter 3 different ways:
		//
		// - if %USERNAME% was found in the search filter, then we
		// will substitute this with the passed-in uid at runtime.
		//
		// - if "gemfirexd.user" is the search filter value, then we
		// will assume the user's DN can be found in the system or
		// database property "gemfirexd.user.<uid>" . If the property
		// does not exist, then we will do a normal lookup with our
		// default search filter; otherwise we will perform an
		// authenticated bind to the LDAP server using the found DN.
		//
		// - if neither of the 2 previous values were found, then we use
		// our default search filter and we will substitute insert the
		// uid passed at runtime into our default search filter.
		//
		String searchFilterProp =
					authenticationService.getProperty(Property.AUTH_LDAP_SEARCH_FILTER);
		
		if (searchFilterProp == (String) null)
		{
			// use our default search filter
			this.leftSearchFilter = "(&(objectClass=inetOrgPerson)(uid=";
			this.rightSearchFilter = "))";

		} else if (StringUtil.SQLEqualsIgnoreCase(searchFilterProp,Constants.LDAP_LOCAL_USER_DN)) {

			// use local user DN in gemfirexd.user.<uid>
			this.leftSearchFilter = "(&(objectClass=inetOrgPerson)(uid=";
			this.rightSearchFilter = "))";
			this.useUserPropertyAsDN = true;

		} else if (searchFilterProp.indexOf(
									Constants.LDAP_SEARCH_FILTER_USERNAME) != -1) {

			// user has set %USERNAME% in the search filter
			this.leftSearchFilter = searchFilterProp.substring(0,
				searchFilterProp.indexOf(Constants.LDAP_SEARCH_FILTER_USERNAME));
			this.rightSearchFilter = searchFilterProp.substring(
				searchFilterProp.indexOf(Constants.LDAP_SEARCH_FILTER_USERNAME)+
				(int) Constants.LDAP_SEARCH_FILTER_USERNAME.length());


		} else	{ // add this search filter to ours

			// complement this search predicate to ours
			this.leftSearchFilter = "(&("+searchFilterProp+")"+
									"(objectClass=inetOrgPerson)(uid=";
			this.rightSearchFilter = "))";

		}

		if (SanityManager.DEBUG)
		{
			if (SanityManager.DEBUG_ON(
						AuthenticationServiceBase.AuthenticationTrace)) {

				java.io.PrintWriter iDbgStream =
					SanityManager.GET_DEBUG_STREAM();

				iDbgStream.println(
								"\n\n+ LDAP Authentication Configuration:\n"+
								"   - provider URL ["+this.providerURL+"]\n"+
								"   - search base ["+this.searchBaseDN+"]\n"+
								"   - search filter to be [" +
								this.leftSearchFilter + "<uid>" +
								this.rightSearchFilter + "]\n" +
								"   - use local DN [" +
								(useUserPropertyAsDN ? "true" : "false") +
								"]\n"
								);
			}
		}

		if (SanityManager.DEBUG)
		{
			if (SanityManager.DEBUG_ON(
						AuthenticationServiceBase.AuthenticationTrace)) {
                             
                                // This tracing needs some investigation and cleanup.
                                // 1) It creates the file in user.dir instead of gemfirexd.system.home
                                // 2) It doesn't seem to work. The file is empty after successful
                                //    and unsuccessful ldap connects.  Perhaps the fileOutputStream
                                // is never flushed and closed.
                                // I (Kathey Marsden) wrapped this in a priv block and kept the previous
                                // behaviour that it will not stop processing if file 
                                // creation fails. Perhaps that should be investigated as well.
                                FileOutputStream fos = null;
                                try {
                                    fos =  ((FileOutputStream)AccessController.doPrivileged(
                                                new PrivilegedExceptionAction() {
                                                    public Object run() throws SecurityException, java.io.IOException {
                                                        return new  FileOutputStream("GemFireXDLDAP.out");
                                                    }
                                                }));
                                } catch (PrivilegedActionException pae) {
                                    // If trace file creation fails do not stop execution.                                    
                                }
                                if (fos != null)
                                    initDirContextEnv.put("com.sun.naming.ldap.trace.ber",fos);

				
			}
		}
	}

	
	
	

	/**
	 * Search for the full user's DN in the LDAP server.
	 * LDAP server bind may or not be anonymous.
	 *
	 * If the admin does not want us to do anonymous bind/search, then we
	 * must have been given principal/credentials in order to successfully
	 * bind to perform the user's DN search.
	 *
	 * @exception NamingException if could not retrieve the user DN.
	 **/
	private String getDNFromUID(String uid)
		throws javax.naming.NamingException
	{
		//
		// We bind to the LDAP server here
		// Note that this bind might be anonymous (if anonymous searches
		// are allowed in the LDAP server, or authenticated if we were
		// told/configured to.
		//
		Properties env = null;
		if (this.searchAuthDN != (String) null) {
			env = (Properties) initDirContextEnv.clone();
			env.put(Context.SECURITY_PRINCIPAL, this.searchAuthDN);
			env.put(Context.SECURITY_CREDENTIALS, this.searchAuthPW);
		}
		else
			env = initDirContextEnv;

// GemStone changes BEGIN
		if (GemFireXDUtils.TraceAuthentication) {
		  GemFireXDUtils.dumpProperties(env, "Initializing for DN for uid="
		      + uid + " with ",
		      AuthenticationServiceBase.AuthenticationTrace,
		      GemFireXDUtils.TraceAuthentication, null);
		}
// GemStone changes END
		DirContext ctx = privInitialDirContext(env);

		// Construct Search Filter
		SearchControls ctls = new SearchControls();
		// Set-up a LDAP subtree search scope
		ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);

		// Just retrieve the DN
		ctls.setReturningAttributes(attrDN);

		String searchFilter =
						this.leftSearchFilter + uid + this.rightSearchFilter; 
// GemStone changes BEGIN
		if (GemFireXDUtils.TraceAuthentication) {
		  SanityManager.DEBUG_PRINT(AuthenticationServiceBase
		      .AuthenticationTrace, "Searching for DN for uid="
		          + uid + ", baseDN=" + searchBaseDN
		          + ", searchFilter=" + searchFilter);
		}
// GemStone changes END
		NamingEnumeration results =
						ctx.search(searchBaseDN, searchFilter, ctls);
			
		// If we did not find anything then login failed
		if (results == null || !results.hasMore())
			throw new NameNotFoundException();
			
		SearchResult result = (SearchResult)results.next();
		
// GemStone changes BEGIN
		boolean hasMoreResults;
		try {
		  hasMoreResults = results.hasMore();
		} catch (NamingException ne) {
		  // ignore; can happen with Active Directory
		  hasMoreResults = false;
		}
		if (hasMoreResults)
		/* (original code)
		if (results.hasMore())
		*/
// GemStone changes END
		{
			// This is a login failure as we cannot assume the first one
			// is the valid one.
			if (SanityManager.DEBUG)
			{
				if (SanityManager.DEBUG_ON(
						AuthenticationServiceBase.AuthenticationTrace)) {

					java.io.PrintWriter iDbgStream =
						SanityManager.GET_DEBUG_STREAM();

					iDbgStream.println(
						" - LDAP Authentication request failure: "+
						"search filter [" + searchFilter + "]"+
						", retrieve more than one occurence in "+
						"LDAP server [" + this.providerURL + "]");
				}
			}
			throw new NameNotFoundException();
		}

		NameParser parser = ctx.getNameParser(searchBaseDN);
		Name userDN = parser.parse(searchBaseDN);

		if (userDN == (Name) null)
			// This should not happen in theory
			throw new NameNotFoundException();
		else
			userDN.addAll(parser.parse(result.getName()));
		
		// Return the full user's DN
		return userDN.toString();
	}
// GemStone changes BEGIN

	@Override
	public String toString() {
	  return Constants.AUTHENTICATION_PROVIDER_LDAP;
	}
	
        /**
         * {@link CredentialInitializer#getCredentials(Properties)}
         * @throws StandardException 
         */
        @Override
        public Properties getCredentials(Properties securityProps)
            throws SQLException {
             return SecurityUtils.getCredentials(securityProps);
        }
	
// GemStone changes END
}
