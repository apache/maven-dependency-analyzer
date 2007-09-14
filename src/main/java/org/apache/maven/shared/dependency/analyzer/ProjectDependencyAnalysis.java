package org.apache.maven.shared.dependency.analyzer;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 
 * 
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 */
public class ProjectDependencyAnalysis
{
    // fields -----------------------------------------------------------------

    private final Set usedDeclaredArtifacts;

    private final Set usedUndeclaredArtifacts;

    private final Set unusedDeclaredArtifacts;

    // constructors -----------------------------------------------------------
    
    public ProjectDependencyAnalysis()
    {
        this( null, null, null );
    }

    public ProjectDependencyAnalysis( Set usedDeclaredArtifacts, Set usedUndeclaredArtifacts,
                                      Set unusedDeclaredArtifacts )
    {
        this.usedDeclaredArtifacts = safeCopy(usedDeclaredArtifacts);
        this.usedUndeclaredArtifacts = safeCopy(usedUndeclaredArtifacts);
        this.unusedDeclaredArtifacts = safeCopy(unusedDeclaredArtifacts);
    }

    // public methods ---------------------------------------------------------

    public Set getUsedDeclaredArtifacts()
    {
        return usedDeclaredArtifacts;
    }

    public Set getUsedUndeclaredArtifacts()
    {
        return usedUndeclaredArtifacts;
    }

    public Set getUnusedDeclaredArtifacts()
    {
        return unusedDeclaredArtifacts;
    }
    
    // Object methods ---------------------------------------------------------
    
    /*
     * @see java.lang.Object#hashCode()
     */
    public int hashCode()
    {
        int hashCode = getUsedDeclaredArtifacts().hashCode();
        hashCode = (hashCode * 37) + getUsedUndeclaredArtifacts().hashCode();
        hashCode = (hashCode * 37) + getUnusedDeclaredArtifacts().hashCode();
        
        return hashCode;
    }
    
    /*
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals( Object object )
    {
        boolean equals;
        
        if ( object instanceof ProjectDependencyAnalysis )
        {
            ProjectDependencyAnalysis analysis = (ProjectDependencyAnalysis) object;
            
            equals = getUsedDeclaredArtifacts().equals( analysis.getUsedDeclaredArtifacts() )
                && getUsedUndeclaredArtifacts().equals( analysis.getUsedUndeclaredArtifacts() )
                && getUnusedDeclaredArtifacts().equals( analysis.getUnusedDeclaredArtifacts() );
        }
        else
        {
            equals = false;
        }
        
        return equals;
    }
    
    /*
     * @see java.lang.Object#toString()
     */
    public String toString()
    {
        StringBuffer buffer = new StringBuffer();
        
        if ( !getUsedDeclaredArtifacts().isEmpty() )
        {
            buffer.append( "usedDeclaredArtifacts=" ).append( getUsedDeclaredArtifacts() );
        }
        
        if ( !getUsedUndeclaredArtifacts().isEmpty() )
        {
            if ( buffer.length() > 0)
            {
                buffer.append( "," );
            }
            
            buffer.append( "usedUndeclaredArtifacts=" ).append( getUsedUndeclaredArtifacts() );
        }
        
        if ( !getUnusedDeclaredArtifacts().isEmpty() )
        {
            if ( buffer.length() > 0)
            {
                buffer.append( "," );
            }
            
            buffer.append( "unusedDeclaredArtifacts=" ).append( getUnusedDeclaredArtifacts() );
        }

        buffer.insert( 0, "[" );
        buffer.insert( 0, getClass().getName() );
        
        buffer.append( "]" );
        
        return buffer.toString();
    }
    
    // private methods --------------------------------------------------------
    
    private Set safeCopy( Set set )
    {
        return ( set == null ) ? Collections.EMPTY_SET : Collections.unmodifiableSet( new HashSet( set ) );
    }
}
