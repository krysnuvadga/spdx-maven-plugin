/*
 * Copyright 2014 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.spdx.maven;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.License;

import org.apache.maven.plugin.logging.Log;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.spdx.rdfparser.SpdxRdfConstants;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.ConjunctiveLicenseSet;
import org.spdx.rdfparser.license.SpdxListedLicense;
import org.spdx.rdfparser.license.SpdxNoAssertionLicense;

/**
 * Singleton class which maps Maven license objects to SPDX licenses.
 * 
 * The license mapping uses the JSON file from the spdx.org/licenses/licenses.json file
 * 
 * If the site spdx.org/licenses is not accessible, then static version of the file will be used
 * 
 * @author Gary O'Neall
 *
 */
public class MavenToSpdxLicenseMapper
{
    private static final String SPDX_LICENSE_URL_PREFIX = "http://spdx.org/licenses/";
    private static final String LISTED_LICENSE_JSON_URL = SPDX_LICENSE_URL_PREFIX + "licenses.json";
    private static final String LISTED_LICENSE_JSON_PATH = "org/spdx/maven/licenses.json";

    static MavenToSpdxLicenseMapper instance;
    private Map<String, String> urlStringToSpdxLicenseId;
    
    private MavenToSpdxLicenseMapper(Log log) throws LicenseMapperException {
        // Can not instantiate directly - singleton class
        InputStream is = null;
        try
        {
            URL listedLicenseJsonUrl  = new URL( LISTED_LICENSE_JSON_URL );
            //TODO: Uncomment the line below once the JSON file has been uploaded to the SPDX listed license website
            //is = listedLicenseJsonUrl.openStream();
        }
        catch ( MalformedURLException e )
        {
            if (log != null) {
                log.warn( "Invalid JSON URL for SPDX listed licenses.  Using cached version" );
            }
        }
        catch ( IOException e )
        {
            if (log != null) {
                log.warn( "Unable to access the JSON file for SPDX listed licenses.  Using cached version" );
            }
        }
        if (is == null) {
            // use the cached version
            is = LicenseManager.class.getClassLoader().getResourceAsStream(LISTED_LICENSE_JSON_PATH);
        }
        InputStreamReader reader = new InputStreamReader( is );
        try {
            initializeUrlMap( reader, log );
        } finally {
            try
            {
                reader.close();
            }
            catch ( IOException e )
            {
               if (log != null) {
                   log.warn( "IO error closing listed license reader: "+e.getMessage() );
               }
            }
        }
       
    }
    
    public static MavenToSpdxLicenseMapper getInstance( Log log ) throws LicenseMapperException {
        if (instance == null) {
            instance = new MavenToSpdxLicenseMapper( log );
        }
        return instance;
    }

    public AnyLicenseInfo mapMavenLicenses( List<License> mavenLicenses )
    {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * Initialize the urlSTringToSpdxLicense map with the SPDX listed licenses
     * @param jsonReader Reader for the JSON input file containing the listed licenses
     * @param log Optional logger
     * @throws LicenseMapperException 
     */
    private void initializeUrlMap( Reader jsonReader, Log log ) throws LicenseMapperException
    {
        JSONParser parser = new JSONParser();       
        Object parsedObject = null;
        try
        {
            parsedObject = parser.parse( jsonReader );
        }
        catch ( IOException e1 )
        {
            if (log != null) {
                log.error( "I/O error parsing listed licenses JSON file: "+e1.getMessage() );
            }
            throw( new LicenseMapperException( "I/O Error parsing listed licenses" ));
        }
        catch ( ParseException e1 )
        {
            if (log != null) {
                log.error( "JSON parsing error parsing listed licenses JSON file: "+e1.getMessage() );
            }
            throw( new LicenseMapperException( "JSON parsing error parsing listed licenses" ));
        }
        JSONObject listedLicenseSource = (JSONObject)parsedObject;
        
        JSONArray listedLicenses = (JSONArray)listedLicenseSource.get( "licenses" );
        urlStringToSpdxLicenseId = new HashMap<String, String>();
        for ( int i = 0; i < listedLicenses.size(); i++ ) {
            JSONObject listedLicense = (JSONObject)listedLicenses.get( i );
            String licenseId = (String)listedLicense.get( SpdxRdfConstants.PROP_LICENSE_ID );
            this.urlStringToSpdxLicenseId.put( SPDX_LICENSE_URL_PREFIX + licenseId, licenseId );
            JSONArray urls = (JSONArray)listedLicense.get( SpdxRdfConstants.RDFS_PROP_SEE_ALSO );
            if ( urls != null ) {
                for ( int j = 0; j < urls.size(); j++ ) {
                    String url = (String)urls.get( j );
                    if (this.urlStringToSpdxLicenseId.containsKey( url )) {
                        String oldLicenseId = (urlStringToSpdxLicenseId.get( url ));
                        if (log != null) {
                            log.warn( "Duplicate URL for SPDX listed license.  Replacing " +
                                                oldLicenseId + " with " + licenseId +" for " + url);
                        }
                    }
                    this.urlStringToSpdxLicenseId.put( url, licenseId );
                }
            }
        }
    }
    
    /**
     * Map a list of Maven licenses to an SPDX license.  If no licenses
     * are supplied, SpdxNoAssertion license is returned.  if a single
     * license is supplied, and a URL can be found matching a listed license,
     * the listed license is returned.  if a single
     * license is supplied, and a URL can not be found matching a listed license,
     * SpdxNoAssertion is returned.  If
     * multiple licenses are supplied, a conjunctive license is returned
     * containing all mapped SPDX licenses.
     * @return
     * @throws LicenseManagerException 
     */
    public AnyLicenseInfo mavenLicenseListToSpdxLicense( List<License> licenseList ) throws LicenseManagerException {
        if ( licenseList == null ) {
            return new SpdxNoAssertionLicense();
        }
        List<AnyLicenseInfo> spdxLicenses = new ArrayList<AnyLicenseInfo>();
        Iterator<License> iter = licenseList.iterator();
        while( iter.hasNext() ) {
            License license = iter.next();
            SpdxListedLicense listedLicense = mavenLicenseToSpdxListedLicense( license );
            if (listedLicense != null) {
                spdxLicenses.add( listedLicense );
            }
        }
        if ( spdxLicenses.size() < 1) {
            return new SpdxNoAssertionLicense();
        } else if ( spdxLicenses.size() == 1 ) {
            return spdxLicenses.get( 0 );
        } else {
            AnyLicenseInfo[] licensesInSet = spdxLicenses.toArray( new AnyLicenseInfo[spdxLicenses.size()] );
            AnyLicenseInfo conjunctiveLicense = new ConjunctiveLicenseSet( licensesInSet );
            return conjunctiveLicense;
        }
    }

    private SpdxListedLicense mavenLicenseToSpdxListedLicense( License license )
    {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * @return Map of URL's to listed license ID's
     */
    public Map<? extends String, ? extends String> getMap()
    {
        return this.urlStringToSpdxLicenseId;
    }
    
    
}