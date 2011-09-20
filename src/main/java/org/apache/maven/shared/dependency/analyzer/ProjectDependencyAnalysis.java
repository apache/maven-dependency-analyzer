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
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;

/**
 * 
 * 
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 */
public class ProjectDependencyAnalysis
{
    // fields -----------------------------------------------------------------

    private final Set<Artifact> usedDeclaredArtifacts;

    private final Set<Artifact> usedUndeclaredArtifacts;

    private final Set<Artifact> unusedDeclaredArtifacts;

    // constructors -----------------------------------------------------------
    
    public ProjectDependencyAnalysis()
    {
        this( null, null, null );
    }

    public ProjectDependencyAnalysis( Set<Artifact> usedDeclaredArtifacts, Set<Artifact> usedUndeclaredArtifacts,
                                      Set<Artifact> unusedDeclaredArtifacts )
    {
        this.usedDeclaredArtifacts = safeCopy( usedDeclaredArtifacts );
        this.usedUndeclaredArtifacts = safeCopy( usedUndeclaredArtifacts );
        this.unusedDeclaredArtifacts = safeCopy( unusedDeclaredArtifacts );
    }

    // public methods ---------------------------------------------------------

    public Set<Artifact> getUsedDeclaredArtifacts()
    {
        return usedDeclaredArtifacts;
    }

    public Set<Artifact> getUsedUndeclaredArtifacts()
    {
        return usedUndeclaredArtifacts;
    }

    public Set<Artifact> getUnusedDeclaredArtifacts()
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
        if ( object instanceof ProjectDependencyAnalysis )
        {
            ProjectDependencyAnalysis analysis = (ProjectDependencyAnalysis) object;
            
            return getUsedDeclaredArtifacts().equals( analysis.getUsedDeclaredArtifacts() )
                && getUsedUndeclaredArtifacts().equals( analysis.getUsedUndeclaredArtifacts() )
                && getUnusedDeclaredArtifacts().equals( analysis.getUnusedDeclaredArtifacts() );
        }
        
        return false;
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
    
    private Set<Artifact> safeCopy( Set<Artifact> set )
    {
        return ( set == null ) ? Collections.<Artifact>emptySet() : Collections.unmodifiableSet( new LinkedHashSet<Artifact>( set ) );
    }
}
